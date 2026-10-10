package ai.heyu.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.*;

/** Fixed, user-triggered UI adapter. No model decisions, contact search, gestures or retries. */
final class DirectSend {
 static final String WA="com.whatsapp";
 static final class Target {
  final String title,owner;final int window,generation;
  Target(String title,int window){this.title=title;this.window=window;this.owner=ChatMemory.current().id;this.generation=ChatMemory.generation();}
 }
 private enum Step {DOWNLOAD,CONTACT,RETURN_CHAT,MENU,DOCUMENTS,PICKER,ROOT,FILE,PREVIEW,OUTCOME}
 private static DirectSend active;
 private static String lastMessage="",lastId="";
 private final Context app;private final ChatContextService service;private final Target target;
 private final JSONObject artifact;private final String id,key;private final SendPolicy.Gate gate;
 private static final class ClickStamp {
  final String id,viewClass;final AccessibilityNodeInfo source;final int window;final long time;
  ClickStamp(AccessibilityNodeInfo node){id=str(node.getViewIdResourceName());viewClass=((WA+":id/pickfiletype_document_holder").equals(id)||(WA+":id/browseOtherDocs").equals(id))?"android.widget.LinearLayout":str(node.getClassName());window=node.getWindowId();time=SystemClock.uptimeMillis();source=AccessibilityNodeInfo.obtain(node);}
 }
 private final List<ClickStamp> clicks=new ArrayList<>();
 private final boolean fastRoute;private String address="",addressLedgerKey="";private int shareLaunches=0;private boolean refocusRequested=false;
 private Step step=Step.DOWNLOAD;private String ticket="";private long attemptedAt=0;private String previewDiagnostic="";
 private LinearLayout overlay;private TextView progress;private boolean ticking=false,ended=false;
 private final Runnable pulse=()->tick();
 static boolean busy(){return active!=null;}
 static String message(String id){return id.equals(lastId)?lastMessage:"";}
 static boolean consent(Context c){return c.getSharedPreferences("privacy",0).getBoolean("direct_send",false);}
 static void setConsent(Context c,boolean yes){c.getSharedPreferences("privacy",0).edit().putBoolean("direct_send",yes).apply();if(!yes)cancel("一键发送授权已关闭；没有继续发送");}
 static boolean compatible(Context c){try{return SendPolicy.supportedVersion(c.getPackageManager().getPackageInfo(WA,0).versionName);}catch(Exception e){return false;}}
 static Target target(){
  ChatContextService s=ChatContextService.service();if(s==null||!ChatContextService.keyboardActive||!ChatMemory.bound())return null;
  AccessibilityNodeInfo r=s.getRootInActiveWindow();if(r==null||!WA.equals(str(r.getPackageName())))return null;
  AccessibilityNodeInfo title=oneId(r,WA+":id/conversation_contact_name"),entry=oneId(r,WA+":id/entry");
  if(title==null||entry==null||!entry.isEditable()||entry.isPassword()||!SendPolicy.safeName(str(title.getText())))return null;
  if(!str(title.getText()).equals(ChatMemory.visibleTitle()))return null;return new Target(str(title.getText()),r.getWindowId());
 }
 static boolean used(Context c,JSONObject a,Target t){return t!=null&&(c.getSharedPreferences("delivery-ledger",0).contains(key(a,t))||c.getSharedPreferences("delivery-ledger",0).contains(legacyKey(a,t)));}
 static String prior(Context c,JSONObject a,Target t){return t==null?"":c.getSharedPreferences("delivery-ledger",0).getString(key(a,t),c.getSharedPreferences("delivery-ledger",0).getString(legacyKey(a,t),""));}
 static void cancel(String reason){if(active!=null)active.finish(reason);}
 static void inputHidden(){if(active!=null&&active.step==Step.DOWNLOAD)cancel("键盘或聊天已变化，发送已停止");}
 static void event(AccessibilityEvent event){
  DirectSend a=active;if(a==null||event.getEventType()!=AccessibilityEvent.TYPE_VIEW_CLICKED)return;
  String pkg=str(event.getPackageName());if(!WA.equals(pkg)&&!picker(pkg))return;
  AccessibilityNodeInfo n=event.getSource();String rid=n==null?"":str(n.getViewIdResourceName());
  for(Iterator<ClickStamp> i=a.clicks.iterator();i.hasNext();){
   ClickStamp c=i.next();long age=event.getEventTime()-c.time;
   // Removed-window nodes may no longer resolve. Match their one-shot event by window/class/time, never a different live control.
   if(age>=0&&age<=800&&c.window==event.getWindowId()&&(n!=null?c.id.equals(rid)&&(!rid.isEmpty()||c.source.equals(n)):c.viewClass.equals(str(event.getClassName())))){i.remove();return;}
  }
  if(DeliveryBuild.DRY_RUN)android.util.Log.i("HeyuDeliveryQA","unexpected click id="+rid+" source="+(n!=null)+" class="+event.getClassName()+" step="+a.step);
  a.finish(a.step==Step.OUTCOME?"发送动作已尝试；请查看聊天回执，未自动重试":"检测到手动操作，已停止一键发送；请核对当前页面");
 }
 static void start(Context context,JSONObject data,Target displayed){
  if(active!=null)return;
  ChatContextService s=ChatContextService.service();Target current=target();
  String error=null;
  if(!DeliveryBuild.DRY_RUN&&!consent(context))error="请先了解并开启一键发送";
  else if(!compatible(context))error="此 WhatsApp 版本尚未验证，请使用其他方式分享";
  else if(!ChatMemory.ownsResult(data))error="文件不属于当前确认的聊天，发送已阻止";
  else if(s==null||current==null||displayed==null||!current.owner.equals(displayed.owner)||current.generation!=displayed.generation||!current.title.equals(displayed.title)||current.window!=displayed.window)error="当前聊天已变化，请回到结果卡重新核对接收人";
  else if(used(context,data,current))error="这个文件已尝试发送到此聊天，请先查看聊天记录，避免重复发送";
  if(error!=null){lastId=data.optString("fileId");lastMessage=error;TaskState.notifyChange();Toast.makeText(context,error,Toast.LENGTH_LONG).show();return;}
  if(!SendPolicy.safeArtifact(data.optString("fileId"),data.optString("ext","pptx"),data.optString("fileName"),data.optString("sha256"),data.optLong("bytes"))){Toast.makeText(context,"文件信息不完整，不能一键发送",Toast.LENGTH_LONG).show();return;}
  DirectSend a=new DirectSend(context,s,data,current);active=a;
  if(!a.sameChat()){a.finish("当前输入框已有草稿或聊天不明确，请先保留/清空草稿后再发送文件");return;}
  ChatContextService.clear();a.showOverlay();if(active!=a)return;a.status("下载并校验这一个文件…");a.tick();
  AgentApi.IO.execute(()->{
   try{
    AgentApi.download(a.app,data);String token=DeliveryStore.create(a.app,data);
    AgentApi.MAIN.post(()->{
     if(active!=a||!a.gate.alive(now()))return;
     if(!a.sameChat()){a.finish("下载期间聊天或草稿发生变化，已停止；没有发送");return;}
     a.ticket=token;DeliveryStore.pending=token;DeliveryStore.opened="";DeliveryDocumentsProvider.changed(a.app);
     if(a.fastRoute){a.step=Step.CONTACT;a.status("核对当前联系人的收件地址…");a.click(oneId(a.root(),WA+":id/conversation_contact"));}
     else {a.step=Step.MENU;a.status("打开当前聊天的附件…");a.click(oneId(a.root(),WA+":id/input_attach_button"));}
    });
   }catch(Exception e){AgentApi.MAIN.post(()->{if(active==a)a.finish("文件下载/校验失败；没有发送，请检查服务连接后重试");});}
  });
 }
 private DirectSend(Context c,ChatContextService s,JSONObject data,Target t){app=c.getApplicationContext();service=s;artifact=data;target=t;id=data.optString("fileId");key=key(data,t);gate=new SendPolicy.Gate(t.title,t.window,data.optString("fileName"),now());String v="";try{v=c.getPackageManager().getPackageInfo(WA,0).versionName;}catch(Exception ignored){}fastRoute=SendPolicy.fastRoute(v,data.optString("ext"));}
 private static long now(){return SystemClock.elapsedRealtime();}
 private AccessibilityNodeInfo root(){return service.getRootInActiveWindow();}
 private boolean sameChat(){
  AccessibilityNodeInfo r=root();if(r==null||!WA.equals(str(r.getPackageName())))return false;
  AccessibilityNodeInfo name=oneId(r,WA+":id/conversation_contact_name"),entry=oneId(r,WA+":id/entry");
  return ChatMemory.bound()&&target.owner.equals(ChatMemory.current().id)&&target.generation==ChatMemory.generation()&&name!=null&&entry!=null&&gate.sameChat(str(name.getText()),r.getWindowId(),entry.isFocused()&&AgentIme.emptyWhatsAppEditor(),now());
 }
 private void tick(){
  if(active!=this||ended||ticking)return;ticking=true;
  try{
   if(step==Step.OUTCOME&&now()-attemptedAt>15000){finishOutcome("已提交 WhatsApp，仍在等待回执；请查看原消息，不要重复发送",root());return;}
   if(step!=Step.OUTCOME&&!gate.alive(now())){finish("发送超时已停止；没有自动重试。请核对当前页面");return;}
   if(!DeliveryBuild.DRY_RUN&&!consent(app)){finish("一键发送授权已关闭；已停止");return;}
   AccessibilityNodeInfo r=root();if(r==null)return;
   String pkg=str(r.getPackageName());
   if(!WA.equals(pkg)&&(fastRoute||!picker(pkg))){finish("已离开当前附件流程，发送已停止；没有自动重试");return;}
   AccessibilityNodeInfo chat=oneId(r,WA+":id/conversation_contact_name");
   if(chat!=null&&!target.title.equals(str(chat.getText()))){finish("接收人发生变化，发送已停止");return;}
   switch(step){
    case DOWNLOAD:if(!sameChat())finish("当前聊天或草稿发生变化，已停止发送");break;
    case CONTACT:
     AccessibilityNodeInfo contactTitle=oneId(r,WA+":id/contact_title"),phone=oneId(r,WA+":id/contact_subtitle");
     if(contactTitle!=null&&phone!=null){
      if(!target.title.equals(str(contactTitle.getText()))){finish("联系人资料不匹配，未发送");break;}
      address=SendPolicy.recipientAddress(str(phone.getText()));
      if(address.isEmpty()){finish("无法核对完整收件地址，未发送；可使用备用分享");break;}
      addressLedgerKey=addressKey(artifact,address);
      if(app.getSharedPreferences("delivery-ledger",0).contains(addressLedgerKey)){finish("这份文件已向该联系人尝试交付；请查看原消息，不要重复发送");break;}
      AccessibilityNodeInfo back=readyNode(oneId(r,WA+":id/wds_toolbar_nav_button"));
      if(back!=null){step=Step.RETURN_CHAT;status("已核对收件人，返回当前聊天…");click(back);}
     }break;
    case RETURN_CHAT:
     if(chat!=null){
      AccessibilityNodeInfo entry=oneId(r,WA+":id/entry");
      if(!target.title.equals(str(chat.getText()))||r.getWindowId()!=target.window||!target.owner.equals(ChatMemory.current().id)||target.generation!=ChatMemory.generation()){finish("返回的聊天已变化，未发送");break;}
      // The keyboard can be hidden while coming back from the native contact sheet.
      if(entry==null||!entry.isEditable()||entry.isPassword())break;
      if(!ChatContextService.keyboardActive||!entry.isFocused()){
       if(!refocusRequested){refocusRequested=true;click(entry);}break;
      }
      if(!AgentIme.emptyWhatsAppEditor()){finish("当前聊天已有草稿，未发送文件");break;}
      launchTargetedPreview();
     }break;
    case MENU:
     if(!WA.equals(pkg)){finish("不是当前 WhatsApp 附件页面，已停止");break;}
     AccessibilityNodeInfo doc=readyNode(oneId(r,WA+":id/pickfiletype_document_holder"));
     if(doc!=null){step=Step.DOCUMENTS;status("选择原生文档附件…");click(doc);}break;
    case DOCUMENTS:
     if(!WA.equals(pkg)){finish("附件页面变化，已停止");break;}
     AccessibilityNodeInfo browse=readyNode(oneId(r,WA+":id/browseOtherDocs"));
     if(browse!=null){step=Step.PICKER;status("将本次文件交给系统选择器…");click(browse);}break;
    case PICKER:
     if(picker(pkg)){
      AccessibilityNodeInfo roots=readyNode(oneDescription(r,new String[]{"Show roots","显示根目录","显示位置","Open navigation drawer","打开导航抽屉"}));
      if(roots!=null){step=Step.ROOT;click(roots);}
     }break;
    case ROOT:
     if(!picker(pkg)){finish("文件选择器已关闭，已停止");break;}
     AccessibilityNodeInfo rootsList=oneId(r,pkg+":id/roots_list");
     AccessibilityNodeInfo provider=readyNode(oneTextId(rootsList,"android:id/title",DeliveryDocumentsProvider.TITLE));
     if(provider!=null){step=Step.FILE;status("只选择本次生成的文件…");click(provider);}break;
    case FILE:
     if(!picker(pkg)){finish("文件选择器已变化，已停止");break;}
     AccessibilityNodeInfo file=readyNode(oneTextId(r,"android:id/title",gate.fileName));
     if(file!=null){step=Step.PREVIEW;status("核对原生预览中的文件和接收人…");click(file);}break;
    case PREVIEW:
     if(WA.equals(pkg))checkPreview(r);break;
    case OUTCOME:
     if(WA.equals(pkg)&&chat!=null&&target.title.equals(str(chat.getText()))&&oneId(r,WA+":id/document_file_name")==null){
      AccessibilityNodeInfo fileBubble=oneTextId(r,WA+":id/title",gate.fileName);
      if(fileBubble!=null){
       String receipt=bubbleReceipt(fileBubble);
       if(SendPolicy.delivered(receipt)){finishOutcome("已送达当前联系人",r);break;}
       if(SendPolicy.uploaded(receipt)){finishOutcome("WhatsApp 已发送，等待对方送达回执",r);break;}
       if(bubbleHasRetry(fileBubble)){finishOutcome("WhatsApp 文件上传失败；请在原消息重试，不要重复发送",r);break;}
       status("文件已交给 WhatsApp，正在等待上传回执…");
      }
     }break;
   }
  }catch(Exception e){finish(step==Step.OUTCOME?"发送状态待核对；未自动重试":"页面不符合预期，已停止；没有点击发送");}
  finally{ticking=false;if(active==this&&!ended){AgentApi.MAIN.removeCallbacks(pulse);AgentApi.MAIN.postDelayed(pulse,350);}}
 }
 private void launchTargetedPreview(){
  if(shareLaunches!=0||address.isEmpty()||!gate.alive(now())){finish("交付状态已变化，未再次打开预览");return;}
  try{
   Uri uri=DocumentsContract.buildDocumentUri(DeliveryDocumentsProvider.AUTH,ticket);
   app.grantUriPermission(WA,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
   Intent intent=new Intent(Intent.ACTION_SEND).setPackage(WA).setType(artifact.optString("mime",ArtifactProvider.mime(artifact.optString("ext"))))
    .putExtra(Intent.EXTRA_STREAM,uri).putExtra("jid",address).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
   intent.setClipData(ClipData.newRawUri(gate.fileName,uri));
   step=Step.PREVIEW;shareLaunches++;status("直接打开当前联系人的文件预览…");app.startActivity(intent);
  }catch(Exception e){finish("无法直接打开文件预览，未发送；可使用备用分享");}
 }
 private String bubbleReceipt(AccessibilityNodeInfo file){
  AccessibilityNodeInfo n=file;
  for(int i=0;n!=null&&i<7;i++,n=n.getParent()){AccessibilityNodeInfo status=oneId(n,WA+":id/status");if(status!=null)return str(status.getContentDescription());}
  return "";
 }
 private boolean bubbleHasRetry(AccessibilityNodeInfo file){
  AccessibilityNodeInfo n=file;
  for(int i=0;n!=null&&i<7;i++,n=n.getParent())if(oneId(n,WA+":id/status")!=null){AccessibilityNodeInfo retry=oneId(n,WA+":id/control_btn");return retry!=null&&(str(retry.getContentDescription()).equals("重试")||str(retry.getContentDescription()).equals("Retry"));}
  return false;
 }
 private void finishOutcome(String message,AccessibilityNodeInfo r){
  android.content.SharedPreferences.Editor ledger=app.getSharedPreferences("delivery-ledger",0).edit().putString(key,message);
  if(!addressLedgerKey.isEmpty())ledger.putString(addressLedgerKey,message);ledger.commit();
  if(r!=null&&WA.equals(str(r.getPackageName()))){AccessibilityNodeInfo name=oneId(r,WA+":id/conversation_contact_name"),entry=oneId(r,WA+":id/entry");
   if(name!=null&&entry!=null&&target.title.equals(str(name.getText()))&&entry.isEditable()&&!entry.isPassword())
    ChatMemory.resumeAuthorizedDelivery(target.owner,target.generation,target.title,target.window,r.getWindowId(),ChatContextService.surfaceEpoch());
  }
  finish(message);
 }
 private static String addressKey(JSONObject a,String address){
  try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest((a.optString("sha256")+"\n"+address).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder("peer:");for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}
 }
 // WhatsApp has a distinct PDF preview: the exact filename lives in the toolbar,
 // with document_preview below. Do not accept arbitrary text from another screen.
 private AccessibilityNodeInfo previewFile(AccessibilityNodeInfo r){
  AccessibilityNodeInfo file=oneId(r,WA+":id/document_file_name");if(file!=null)return file;
  if(!"pdf".equals(artifact.optString("ext"))||oneId(r,WA+":id/document_preview")==null)return null;
  AccessibilityNodeInfo toolbar=oneId(r,WA+":id/toolbar");
  return one(toolbar,n->"android.widget.TextView".equals(str(n.getClassName()))&&gate.fileName.equals(str(n.getText())));
 }
 private void checkPreview(AccessibilityNodeInfo r){
  int previewWindow=r.getWindowId();
  AccessibilityNodeInfo file=previewFile(r),recipients=oneId(r,WA+":id/recipients_scroller"),caption=oneId(r,WA+":id/caption"),send=oneId(r,WA+":id/send");
  List<String> names=new ArrayList<>();texts(recipients,names,0);boolean opened=ticket.equals(DeliveryStore.opened);
  if(DeliveryBuild.DRY_RUN){String d="file="+(file!=null)+" pdf="+"pdf".equals(artifact.optString("ext"))+" docImage="+(oneId(r,WA+":id/document_preview")!=null)+" toolbar="+(oneId(r,WA+":id/toolbar")!=null)+" recipients="+(recipients!=null)+" names="+names.size()+" count="+(recipients==null?0:SendPolicy.recipientCount(str(recipients.getContentDescription()),names.size()))+" caption="+empty(caption)+" send="+(send!=null)+" read="+opened;if(!d.equals(previewDiagnostic)){android.util.Log.i("HeyuDeliveryQA","preview "+d);previewDiagnostic=d;}}
  if(file==null||recipients==null||caption==null||send==null)return;
  if(!gate.preview(names.size()==1?names.get(0):"",str(file.getText()),SendPolicy.recipientCount(str(recipients.getContentDescription()),names.size()),empty(caption),opened,now())){
   if(!opened)return; // Wait for WhatsApp to actually open this URI, not just display a title.
   finish("文件、接收人或附言不匹配，已停止；没有点击发送");return;
  }
  if(DeliveryBuild.DRY_RUN){finish("演练通过：真实附件和接收人已核对；没有点击发送");return;}
  // The persisted marker precedes the external side effect. A crash/timeout never retries it.
  android.content.SharedPreferences.Editor marker=app.getSharedPreferences("delivery-ledger",0).edit().putString(key,"发送状态待核对；请先查看聊天，避免重复发送");
  if(!addressLedgerKey.isEmpty())marker.putString(addressLedgerKey,"已尝试交付此文件；请先查看原消息，避免重复发送");
  if(!marker.commit()){finish("无法保存防重复状态，已停止");return;}
  // Refresh once more immediately before the irreversible action, then consume exactly once.
  r=root();if(r==null||!WA.equals(str(r.getPackageName()))||r.getWindowId()!=previewWindow){finish("发送前窗口已变化；没有点击发送");return;}file=previewFile(r);recipients=oneId(r,WA+":id/recipients_scroller");caption=oneId(r,WA+":id/caption");send=oneId(r,WA+":id/send");names.clear();texts(recipients,names,0);
  if(file==null||send==null||!gate.consume(names.size()==1?names.get(0):"",str(file.getText()),recipients==null?0:SendPolicy.recipientCount(str(recipients.getContentDescription()),names.size()),caption!=null&&empty(caption),ticket.equals(DeliveryStore.opened),now())){finish("发送前复核失败；没有点击发送");return;}
  step=Step.OUTCOME;attemptedAt=now();status("正在提交 WhatsApp，勿切换聊天…");click(send);
 }
 // Wait for a stable, refreshed control before advancing a phase. No ACTION_CLICK
 // has occurred when this returns null; an attempted click is still never retried.
 private AccessibilityNodeInfo readyNode(AccessibilityNodeInfo n){
  if(n==null)return null;String pkg=str(n.getPackageName());
  while(!n.isClickable()){n=n.getParent();if(n==null||!pkg.equals(str(n.getPackageName())))return null;}
  if(!n.refresh()||!n.isClickable()||!n.isEnabled()||!n.isVisibleToUser())return null;
  AccessibilityNodeInfo r=root();return r!=null&&pkg.equals(str(r.getPackageName()))&&r.getWindowId()==n.getWindowId()?n:null;
 }
 private void click(AccessibilityNodeInfo n){
  if(active!=this||ended)return;if(n==null){finish("未找到唯一的原生控件，已停止");return;}
  String pkg=str(n.getPackageName());
  while(n!=null&&!n.isClickable()){n=n.getParent();if(n!=null&&!pkg.equals(str(n.getPackageName()))){n=null;break;}}
  if(n==null||!n.isEnabled()||!n.isVisibleToUser()||!n.refresh()){finish("控件不可操作，已停止；未自动重试");return;}
  AccessibilityNodeInfo current=root();if(current==null||!pkg.equals(str(current.getPackageName()))||current.getWindowId()!=n.getWindowId()){finish("操作前窗口已变化，已停止");return;}
  clicks.add(new ClickStamp(n));if(DeliveryBuild.DRY_RUN)android.util.Log.i("HeyuDeliveryQA","click="+str(n.getViewIdResourceName())+" step="+step);if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))finish(step==Step.OUTCOME?"发送状态待核对；未自动重试":"原生控件未接受操作；已停止");
 }
 private void status(String message){if(DeliveryBuild.DRY_RUN)android.util.Log.i("HeyuDeliveryQA","step="+step+" "+message);lastId=id;lastMessage=message;if(progress!=null)progress.setText((DeliveryBuild.DRY_RUN?"演练，不发送":"发送到 "+target.title)+"\n"+message);TaskState.notifyChange();}
 private void showOverlay(){
  try{
   overlay=Ui.column(service);overlay.setPadding(Ui.dp(service,12),Ui.dp(service,8),Ui.dp(service,12),Ui.dp(service,8));overlay.setBackgroundColor(Ui.BG);
   progress=Ui.text(service,"",13,Ui.INK,true);overlay.addView(progress);Ui.addButton(overlay,"停止本次发送",false,()->finish("你已停止；不会继续点击发送，请核对原生附件预览"));
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
   p.gravity=Gravity.TOP;p.y=Ui.dp(service,104);p.setTitle("禾语单次文件交付");((WindowManager)service.getSystemService(Context.WINDOW_SERVICE)).addView(overlay,p);
  }catch(Exception e){finish("无法显示可取消的发送进度，已停止");}
 }
 private void finish(String message){
  if(ended)return;if(DeliveryBuild.DRY_RUN)android.util.Log.i("HeyuDeliveryQA","finish step="+step+" "+message);ended=true;gate.cancel();AgentApi.MAIN.removeCallbacks(pulse);
  if(overlay!=null)try{((WindowManager)service.getSystemService(Context.WINDOW_SERVICE)).removeView(overlay);}catch(Exception ignored){}
  if(active==this)active=null;if(ticket.equals(DeliveryStore.pending)){DeliveryStore.pending="";DeliveryDocumentsProvider.changed(app);}
  address="";lastId=id;lastMessage=message;TaskState.notifyChange();Toast.makeText(app,message,Toast.LENGTH_LONG).show();
 }
 private static boolean picker(String pkg){return "com.google.android.documentsui".equals(pkg)||"com.android.documentsui".equals(pkg);}
 private static boolean empty(AccessibilityNodeInfo n){return n!=null&&n.isEditable()&&!n.isPassword()&&(n.isShowingHintText()||str(n.getText()).isEmpty());}
 private static String str(CharSequence s){return s==null?"":s.toString();}
 private static String key(JSONObject a,Target t){return t.owner+":"+legacyKey(a,t);}
 private static String legacyKey(JSONObject a,Target t){try{byte[] b=MessageDigest.getInstance("SHA-256").digest((a.optString("fileId")+"\n"+a.optString("sha256")+"\n"+t.title).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private interface Match {boolean yes(AccessibilityNodeInfo n);}
 private static AccessibilityNodeInfo one(AccessibilityNodeInfo r,Match m){List<AccessibilityNodeInfo> found=new ArrayList<>();walk(r,m,found,0);return found.size()==1?found.get(0):null;}
 private static void walk(AccessibilityNodeInfo n,Match m,List<AccessibilityNodeInfo> out,int depth){if(n==null||depth>36||out.size()>1)return;if(n.isVisibleToUser()&&m.yes(n))out.add(n);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),m,out,depth+1);}
 private static AccessibilityNodeInfo oneId(AccessibilityNodeInfo r,String id){return one(r,n->id.equals(n.getViewIdResourceName()));}
 private static AccessibilityNodeInfo oneText(AccessibilityNodeInfo r,String text){return one(r,n->text.equals(str(n.getText())));}
 private static AccessibilityNodeInfo oneTextId(AccessibilityNodeInfo r,String id,String text){return one(r,n->id.equals(n.getViewIdResourceName())&&text.equals(str(n.getText())));}
 private static AccessibilityNodeInfo oneDescription(AccessibilityNodeInfo r,String[] values){return one(r,n->Arrays.asList(values).contains(str(n.getContentDescription())));}
 private static void texts(AccessibilityNodeInfo n,List<String> names,int depth){if(n==null||depth>12)return;if(n.isVisibleToUser()&&!str(n.getText()).isEmpty())names.add(str(n.getText()));for(int i=0;i<n.getChildCount();i++)texts(n.getChild(i),names,depth+1);}
}
