package ai.heyu.agent;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Verified local artifacts: 30-day age limit, 100 MiB budget. Metadata survives evictions. */
final class HistoryFiles {
 static final long MAX_AGE=30L*24*60*60*1000,MAX_BYTES=100L*1024*1024;
 static String hash(byte[] bytes)throws Exception{StringBuilder h=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))h.append(String.format("%02x",b&255));return h.toString();}
 static File file(Context c,JSONObject a){String id=a.optString("fileId"),ext=a.optString("ext","pptx");if(!id.matches("[0-9a-f]{32}")||!ext.matches("pptx|docx|pdf|xlsx|png|jpg|ics"))return null;return new File(c.getFilesDir(),"exports/"+id+"."+ext);}
 static synchronized File verified(Context c,JSONObject a)throws Exception{File f=file(c,a);if(f==null||!f.isFile()||System.currentTimeMillis()-f.lastModified()>MAX_AGE)return null;byte[] bytes=Files.readAllBytes(f.toPath());return bytes.length==a.optLong("bytes",-1)&&hash(bytes).equals(a.optString("sha256"))?f:null;}
 static boolean available(Context c,JSONObject a){File f=file(c,a);return f!=null&&f.isFile()&&f.length()==a.optLong("bytes",-1)&&System.currentTimeMillis()-f.lastModified()<=MAX_AGE;}
 static synchronized void trim(Context c,File keep){File dir=new File(c.getFilesDir(),"exports");File[] files=dir.listFiles((d,n)->n.matches("[0-9a-f]{32}\\.(pptx|docx|pdf|xlsx|png|jpg|ics)"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();for(File f:files)if(!f.equals(keep)&&(System.currentTimeMillis()-f.lastModified()>MAX_AGE||total>MAX_BYTES)){total-=f.length();remove(c,f);}}
 static void remove(Context c,File f){String id=f.getName().split("\\.")[0];f.delete();new File(f.getParentFile(),id+".name").delete();new File(f.getParentFile(),id+".meta").delete();}
 static String status(Context c,JSONObject a,long at){if(available(c,a))return "已缓存到手机 · 发送时再次校验";return System.currentTimeMillis()-at>86400000?"本地文件可能已清理 · 内容预览仍保留":"本地文件不可用 · 请重新生成";}
}
