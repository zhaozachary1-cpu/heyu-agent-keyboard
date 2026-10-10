package ai.heyu.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Ephemeral, per-keyboard-session consent + debounce. No Android APIs or persistence. */
final class IntentSession {
 String title="", text="";
 boolean allowed=false, busy=false;
 int ticket=0, automaticRequests=0;
 long changedAt=0, lastRequestedAt=0;
 private final LinkedHashSet<String> attempted=new LinkedHashSet<>();
 boolean observe(String nextTitle,String nextText,long now){
  if(title.equals(nextTitle)&&text.equals(nextText))return false;
  if(!title.isEmpty()&&!title.equals(nextTitle)){allowed=false;attempted.clear();automaticRequests=0;}
  title=nextTitle;text=nextText;changedAt=now;ticket++;busy=false;
  return true;
 }
 void reset(){title="";text="";allowed=false;busy=false;ticket++;changedAt=0;lastRequestedAt=0;automaticRequests=0;attempted.clear();}
 void disable(){allowed=false;busy=false;ticket++;}
 boolean canAutomaticallyRequest(long now){
  return allowed&&!busy&&!text.isEmpty()&&now-changedAt>=1000&&now-lastRequestedAt>=8000&&automaticRequests<6&&!attempted.contains(key());
 }
 int begin(long now,boolean explicit){
  if(!allowed||busy||text.isEmpty()||(!explicit&&!canAutomaticallyRequest(now)))return -1;
  busy=true;lastRequestedAt=now;automaticRequests++;attempted.add(key());
  while(attempted.size()>8)attempted.remove(attempted.iterator().next());
  return ++ticket;
 }
 boolean accepts(int expectedTicket,String expectedTitle,String expectedText){
  return allowed&&busy&&ticket==expectedTicket&&title.equals(expectedTitle)&&text.equals(expectedText);
 }
 static boolean hasPptHint(String text){
  String s=text.toLowerCase(Locale.ROOT);
  return s.contains("ppt")||s.contains("powerpoint")||s.contains("power point")||s.contains("presentation")||s.contains("slide")||s.contains("pitch deck")||s.contains("幻灯片")||s.contains("演示文稿")||s.contains("演示稿")||s.contains("汇报材料")||s.contains("路演稿");
 }
 private String key(){
  try{byte[] hash=MessageDigest.getInstance("SHA-256").digest((title+"\n"+text).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format("%02x",b&255));return out.toString();}
  catch(Exception impossible){throw new IllegalStateException(impossible);}
 }
}
