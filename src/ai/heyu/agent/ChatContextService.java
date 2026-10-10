package ai.heyu.agent;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Rect;
import android.view.accessibility.*;
import java.util.*;

public final class ChatContextService extends AccessibilityService {
 private static ChatContextService instance;
 static volatile boolean keyboardActive=false,armed=false;
 static volatile Snapshot latest=null;
 private static long navigationEpoch=0;
 static final class Snapshot {
  final String title,text;final long time,epoch;final int window;final boolean privateChat;
  Snapshot(Header h,String s){title=h.title;text=s;window=h.window;epoch=h.epoch;privateChat=h.privateChat;time=System.currentTimeMillis();}
 }
 static boolean consent(Context c){return c.getSharedPreferences("privacy",0).getBoolean("whatsapp",false);}
 static boolean automaticConsent(Context c){return consent(c)&&c.getSharedPreferences("privacy",0).getBoolean("auto_reply_v1",false);}
 static void setAutomaticConsent(Context c,boolean enabled){c.getSharedPreferences("privacy",0).edit().putBoolean("auto_reply_v1",enabled).apply();if(!enabled)clear();}
 @Override protected void onServiceConnected(){instance=this;navigationEpoch++;}
 @Override public void onDestroy(){DirectSend.cancel("辅助服务已关闭，发送停止；未自动重试");instance=null;navigationEpoch++;clear();super.onDestroy();}
 static long surfaceEpoch(){return navigationEpoch;}
 static boolean connected(){return instance!=null;}
 static ChatContextService service(){return instance;}
 static void clear(){armed=false;latest=null;}
 @Override public void onInterrupt(){DirectSend.cancel("辅助服务被中断，发送停止；未自动重试");navigationEpoch++;clear();}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  DirectSend.event(event);
  // Navigation events contain no message read. Invalidate even while the keyboard is hidden.
  if("com.whatsapp".contentEquals(event.getPackageName()==null?"":event.getPackageName())&&event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!DirectSend.busy())navigationEpoch++;
  // Message capture is owned by the gated IME timer/task scanner, never arbitrary events.
 }
 static final class Header {
  final String title,proof;final int window;final long epoch;final boolean privateChat;
  Header(String t,String p,int w,boolean pc){title=t;proof=p;window=w;privateChat=pc;epoch=navigationEpoch;}
 }
 private static AccessibilityNodeInfo foregroundRoot(){
  ChatContextService s=instance;if(s==null||!keyboardActive||!consent(s))return null;
  AccessibilityNodeInfo active=s.getRootInActiveWindow();
  if(active!=null&&"com.whatsapp".contentEquals(active.getPackageName()==null?"":active.getPackageName()))return active;
  // An IME-owned field may be active, but never look through another focused application.
  if(active!=null&&!"ai.heyu.agent".contentEquals(active.getPackageName()==null?"":active.getPackageName()))return null;
  for(AccessibilityWindowInfo w:s.getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&w.isFocused()){
   AccessibilityNodeInfo root=w.getRoot();return root!=null&&"com.whatsapp".contentEquals(root.getPackageName()==null?"":root.getPackageName())?root:null;
  }
  return null;
 }
 static Header header(){return readHeader(foregroundRoot());}
 private static Header readHeader(AccessibilityNodeInfo root){
  if(root==null||root.getPackageName()==null||!"com.whatsapp".contentEquals(root.getPackageName()))return null;
  List<AccessibilityNodeInfo> titles=root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name"),entries=root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
  if(titles.size()!=1||entries.size()!=1||!titles.get(0).isVisibleToUser()||!entries.get(0).isVisibleToUser()||entries.get(0).isPassword()||!entries.get(0).isEditable())return null;
  CharSequence value=titles.get(0).getText();if(value==null||value.toString().codePoints().allMatch(Character::isWhitespace))return null;
  String status="";for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_status"))if(n.isVisibleToUser()&&n.getText()!=null)status=n.getText().toString();
  // Read-only context and file sending have deliberately separate compatibility scopes.
  String version="";try{version=instance.getPackageManager().getPackageInfo("com.whatsapp",0).versionName;}catch(Exception ignored){}
  List<AccessibilityNodeInfo> bars=root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/toolbar");
  boolean complete=bars.size()==1&&bars.get(0).isVisibleToUser()&&entries.get(0).isEnabled();
  int[] controls={0,0,0};
  if(complete){
   List<AccessibilityNodeInfo> names=bars.get(0).findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name");
   complete=names.size()==1&&names.get(0).equals(titles.get(0));
   calls(bars.get(0),controls,0);
  }
  boolean person=WhatsAppChatPolicy.isPrivate(version,status,complete,controls[0],controls[1],controls[2]>0);
  return new Header(value.toString(),ConversationPolicy.proof(status),root.getWindowId(),person);
 }
 private static void calls(AccessibilityNodeInfo n,int[] found,int depth){
  if(n==null||depth>8||!n.isVisibleToUser())return;
  String d=WhatsAppChatPolicy.normalized(n.getContentDescription()==null?"":n.getContentDescription().toString());
  if(WhatsAppChatPolicy.groupControl(d))found[2]++;
  if(n.isClickable()&&n.isEnabled()){
   if(d.equals("语音通话")||d.equals("voice call"))found[0]++;
   if(d.equals("视频通话")||d.equals("video call"))found[1]++;
  }
  for(int i=0;i<n.getChildCount();i++)calls(n.getChild(i),found,depth+1);
 }
 static Snapshot capture(){
  ChatContextService s=instance;if(s==null||!keyboardActive||!armed||!consent(s)){latest=null;return null;}
  AccessibilityNodeInfo root=foregroundRoot();Header h=readHeader(root);
  if(h==null){latest=null;return null;}
  List<String> messages=new ArrayList<>();walk(root,messages,0);
  if(messages.isEmpty()){latest=null;return null;}
  int start=Math.max(0,messages.size()-12);String text=String.join("\n",messages.subList(start,messages.size()));
  if(text.length()>10000)text=text.substring(text.length()-10000);
  latest=new Snapshot(h,text);return latest;
 }
 private static void walk(AccessibilityNodeInfo n,List<String> out,int depth){
  if(n==null||depth>32||out.size()>80)return;String id=n.getViewIdResourceName();CharSequence t=n.getText();Rect bounds=new Rect();n.getBoundsInScreen(bounds);
  if(n.isVisibleToUser()&&!bounds.isEmpty()&&t!=null&&id!=null&&!n.isEditable()&&!n.isPassword()&&(id.endsWith("/message_text")||id.endsWith("/caption")))out.add(t.toString());
  for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out,depth+1);
 }
}
