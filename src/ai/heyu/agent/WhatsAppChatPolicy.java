package ai.heyu.agent;

import java.util.Locale;

/** Read-only context adapter, NOT a file-send compatibility policy or a stable peer ID. */
final class WhatsAppChatPolicy {
 private WhatsAppChatPolicy(){}
 static boolean supportedVersion(String version){
  // Keep this bounded to inspected builds; never enable every future WhatsApp version.
  return "2.26.38.73".equals(version)||"2.26.38.74".equals(version);
 }
 static String normalized(String value){
  return value==null?"":value.replace('\u00a0',' ').replace('\u202f',' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
 }
 static boolean groupControl(String description){
  String d=normalized(description);
  return d.contains("group call")||d.contains("group voice")||d.contains("group video")
   ||d.contains("group info")||d.contains("community info")||d.contains("channel info")
   ||d.contains("群组")||d.contains("群聊")||d.contains("社群")||d.contains("频道");
 }
 static boolean privateStatus(String value){
  String s=normalized(value);
  if(s.isEmpty()||s.equals("online")||s.equals("在线"))return true;
  if(s.equals("typing...")||s.equals("typing…")||s.equals("正在输入...")||s.equals("正在输入…"))return true;
  if(s.equals("recording audio...")||s.equals("recording audio…")||s.equals("正在录音...")||s.equals("正在录音…"))return true;
  // This inspected OnePlus/Chinese build exposes the last-seen value as just HH:mm.
  if(s.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d"))return true;
  if(s.matches("(?:0?[1-9]|1[0-2]):[0-5]\\d ?(?:am|pm)"))return true;
  return s.startsWith("last seen ")||s.startsWith("最后上线时间")||s.startsWith("最后上线于");
 }
 static boolean isPrivate(String version,String status,boolean completeHeader,int voices,int videos,boolean groupSignal){
  if(!supportedVersion(version)||!completeHeader||groupSignal)return false;
  String s=normalized(status);
  if(s.equals("message yourself")||s.equals("给自己发消息"))return true;
  // A nickname, timestamp, avatar or one call button alone never establishes a private peer.
  return privateStatus(s)&&voices==1&&videos==1;
 }
}
