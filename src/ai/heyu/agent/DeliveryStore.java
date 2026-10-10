package ai.heyu.agent;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.JSONObject;

/** Only a per-tap ticket is exposed to SAF; never enumerate the export directory. */
final class DeliveryStore {
 static volatile String pending="",opened="";
 static String create(Context c,JSONObject artifact)throws Exception{
  String id=artifact.optString("fileId"),ext=artifact.optString("ext","pptx"),name=artifact.optString("fileName"),hash=artifact.optString("sha256");
  if(!SendPolicy.safeArtifact(id,ext,name,hash,artifact.optLong("bytes")))throw new IOException("文件信息不完整");
  String token=UUID.randomUUID().toString().replace("-","");
  JSONObject m=new JSONObject(artifact.toString());m.put("expires",System.currentTimeMillis()+86400000);m.put("ext",ext);
  File dir=new File(c.getFilesDir(),"delivery-tickets");dir.mkdirs();
  File[] old=dir.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>86400000)f.delete();
  File tmp=new File(dir,token+".tmp");Files.write(tmp.toPath(),m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Files.move(tmp.toPath(),new File(dir,token+".json").toPath());
  verify(c,m);return token;
 }
 static JSONObject read(Context c,String token)throws FileNotFoundException{
  try{
   if(token==null||!token.matches("[0-9a-f]{32}"))throw new Exception();
   JSONObject m=new JSONObject(new String(Files.readAllBytes(new File(c.getFilesDir(),"delivery-tickets/"+token+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(System.currentTimeMillis()>m.getLong("expires")||!SendPolicy.safeArtifact(m.optString("fileId"),m.optString("ext"),m.optString("fileName"),m.optString("sha256"),m.optLong("bytes")))throw new Exception();
   return m;
  }catch(Exception e){throw new FileNotFoundException("授权文件不存在或已过期");}
 }
 static File verify(Context c,JSONObject m)throws Exception{
  File f=new File(c.getFilesDir(),"exports/"+m.getString("fileId")+"."+m.getString("ext"));
  if(!f.isFile()||f.length()!=m.getLong("bytes"))throw new IOException("文件大小不匹配");
  MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
  StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format("%02x",b&255));
  if(!hex.toString().equals(m.getString("sha256")))throw new IOException("文件校验失败");return f;
 }
 static ParcelFileDescriptor open(Context c,String token,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new FileNotFoundException("Read only");
  try{return ParcelFileDescriptor.open(verify(c,read(c,token)),ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){throw new FileNotFoundException("文件校验失败");}
 }
}
