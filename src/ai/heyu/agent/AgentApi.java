package ai.heyu.agent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

final class AgentApi {
 static final ExecutorService IO=Executors.newFixedThreadPool(3);
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 interface Done {void finish(JSONObject data,String error);}
 static JSONObject config(Context c){return obj("url",DeepSeekClient.ORIGIN,"transport","phone-direct","model",DeepSeekClient.MODEL);}
 static boolean lan(Context c){return false;}
 static boolean cloud(Context c){return false;}
 static String serviceName(Context c){return "DeepSeek 手机直连";}
 static String routeName(Context c){return "手机直连 DeepSeek";}
 static String unavailable(Context c){return "无法连接 DeepSeek，请检查手机网络和自己的 API Key";}
 static final class Pending {
  volatile boolean canceled=false;volatile HttpURLConnection connection;
  void cancel(){canceled=true;HttpURLConnection c=connection;if(c!=null)new Thread(c::disconnect,"heyu-cancel-reply").start();}
 }
 static byte[] request(Context c,String method,String path,JSONObject body)throws Exception{return request(c,method,path,body,null);}
 private static byte[] request(Context c,String method,String path,JSONObject body,Pending pending)throws Exception{
  return NativeAgent.request(c,method,path,body,pending);
 }
 static void call(Context c,String method,String path,JSONObject body,Done done){
  Context app=c.getApplicationContext();IO.execute(()->{JSONObject data=null;String error=null;try{data=new JSONObject(new String(request(app,method,path,body),StandardCharsets.UTF_8));}catch(Exception e){error=e instanceof ConnectException?unavailable(app):e instanceof SocketTimeoutException?"请求超时，请重试；没有模拟结果":e.getMessage();if(error==null)error="连接失败，请检查 AI 服务";}JSONObject d=data;String err=error;MAIN.post(()->done.finish(d,err));});
 }
 /** Cancellable reads for automatic suggestions. Already submitted provider work cannot be recalled. */
 static Pending callCancelable(Context c,String path,JSONObject body,Done done){
  Pending p=new Pending();Context app=c.getApplicationContext();
  IO.execute(()->{if(p.canceled)return;JSONObject data=null;String error=null;
   try{data=new JSONObject(new String(request(app,"POST",path,body,p),StandardCharsets.UTF_8));}
   catch(Exception e){error=e instanceof ConnectException?unavailable(app):e instanceof SocketTimeoutException?"模型暂未响应":e.getMessage();if(error==null)error="连接失败";}
   JSONObject d=data;String err=error;MAIN.post(()->{if(!p.canceled)done.finish(d,err);});
  });return p;
 }
 static JSONObject obj(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(Exception ignored){}return j;}
 static synchronized File download(Context c,JSONObject result)throws Exception{
  File cached=HistoryFiles.verified(c,result);if(cached!=null)return cached;
  String id=result.getString("fileId");if(!id.matches("[0-9a-f]{32}"))throw new IOException("文件标识无效");
  byte[] bytes=request(c,"GET","/files/"+id,null);
  StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format("%02x",b&255));
  if(!hex.toString().equals(result.getString("sha256")))throw new IOException("文件校验失败，请重新下载");
  File dir=new File(c.getFilesDir(),"exports");dir.mkdirs();

  String ext=result.optString("ext","pptx");if(!ext.matches("pptx|docx|pdf|xlsx|png|jpg|ics"))throw new IOException("不支持的输出类型");
  if(result.optLong("bytes",bytes.length)!=bytes.length)throw new IOException("文件长度校验失败");
  File file=new File(dir,id+"."+ext);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
  try(FileOutputStream out=new FileOutputStream(new File(dir,id+".name"))){out.write(result.getString("fileName").getBytes(StandardCharsets.UTF_8));}
  try(FileOutputStream out=new FileOutputStream(new File(dir,id+".meta"))){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}
  HistoryFiles.trim(c,file);return file;
 }
}
