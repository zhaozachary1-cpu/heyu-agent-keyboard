package ai.heyu.agent;

/** No Android/AI dependencies. A tap authorizes one file, one visible destination, once. */
final class SendPolicy {
 static final long TIMEOUT_MS=45000;
 static boolean supportedVersion(String version){return "2.26.38.73".equals(version)||"2.26.38.74".equals(version);}
 // Version-scoped native targeted share, measured on the OnePlus .74 build.
 static boolean fastRoute(String version,String ext){return "2.26.38.74".equals(version)&&ext!=null&&ext.matches("pptx|docx|pdf|xlsx|ics");}
 static String recipientAddress(String visiblePhone){
  if(visiblePhone==null||visiblePhone.length()>40||visiblePhone.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))return "";
  String clean=visiblePhone.replaceAll("[\\s()\\-\\u00a0\\u202f]","");
  return clean.matches("\\+[1-9][0-9]{7,14}")?clean.substring(1)+"@s.whatsapp.net":"";
 }
 static boolean delivered(String receipt){return "已送达".equals(receipt)||"已读".equals(receipt)||"Delivered".equals(receipt)||"Read".equals(receipt);}
 static boolean uploaded(String receipt){return delivered(receipt)||"已发送".equals(receipt)||"Sent".equals(receipt);}
 static boolean safeName(String s){return s!=null&&!s.trim().isEmpty()&&s.length()<=180&&!s.matches("(?s).*[\\p{Cc}\\p{Cf}/\\\\].*");}
 static boolean safeArtifact(String id,String ext,String name,String hash,long bytes){
  return id!=null&&id.matches("[0-9a-f]{32}")&&ext!=null&&ext.matches("pptx|docx|pdf|xlsx|png|jpg|ics")
   &&safeName(name)&&name.toLowerCase(java.util.Locale.ROOT).endsWith("."+ext)
   &&hash!=null&&hash.matches("[0-9a-f]{64}")&&bytes>0&&bytes<=12*1024*1024;
 }
 // Only the observed native single-recipient marker is supported. Clipped chips are not proof of a single recipient.
 static int recipientCount(String description,int visibleNames){
  if(visibleNames!=1||description==null)return 0;
  // The inspected .74 Chinese preview prefixes its exact count with a single LRM.
  // Do not strip arbitrary control characters or infer count from clipped chips.
  String marker=description.startsWith("\u200e")?description.substring(1):description;
  return "1 selected recipient".equals(marker)||"1位选定收件人".equals(marker)?1:0;
 }
 static final class Gate {
  final String recipient,fileName;final int chatWindow;final long started;
  boolean canceled=false,consumed=false;
  Gate(String recipient,int window,String fileName,long now){this.recipient=recipient;chatWindow=window;this.fileName=fileName;started=now;}
  boolean alive(long now){return !canceled&&!consumed&&now>=started&&now-started<TIMEOUT_MS;}
  boolean sameChat(String name,int window,boolean draftEmpty,long now){return alive(now)&&recipient.equals(name)&&chatWindow==window&&draftEmpty;}
  boolean preview(String name,String file,int recipients,boolean captionEmpty,boolean opened,long now){
   return alive(now)&&recipient.equals(name)&&fileName.equals(file)&&recipients==1&&captionEmpty&&opened;
  }
  boolean consume(String name,String file,int recipients,boolean captionEmpty,boolean opened,long now){
   if(!preview(name,file,recipients,captionEmpty,opened,now))return false;consumed=true;return true;
  }
  void cancel(){canceled=true;}
 }
}
