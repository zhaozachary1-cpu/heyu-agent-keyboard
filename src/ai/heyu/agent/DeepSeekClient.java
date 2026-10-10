package ai.heyu.agent;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
/** Only official HTTPS DeepSeek endpoint. No intermediary, redirect, tool execution or fallback. */
final class DeepSeekClient {
 static final String MODEL="deepseek-flash",ORIGIN="https://api.deepseek.com";
 static final java.util.concurrent.ScheduledExecutorService DEADLINES=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"heyu-api-deadline");t.setDaemon(true);return t;});
 static volatile long healthyAt=0;
 static void resetHealth(){healthyAt=0;}
 static JSONObject health(Context c)throws Exception{
  if(!ApiCredentials.configured(c))throw new Exception("请先填写自己的 DeepSeek API Key");
  if(System.currentTimeMillis()-healthyAt>60000){JSONObject r=wire(c,"GET","/models",null,null);JSONArray models=r.optJSONArray("data");boolean found=false;if(models!=null)for(int i=0;i<models.length();i++)if(MODEL.equals(models.getJSONObject(i).optString("id")))found=true;if(!found)throw new Exception("当前密钥没有可用的 DeepSeek Flash 模型");healthyAt=System.currentTimeMillis();}
  return AgentApi.obj("ok",true,"provider","DeepSeek","model",MODEL,"transport","phone-direct HTTPS","serviceMode","standalone","version","1.0.0","features",new JSONArray().put("reply").put("polish").put("recognize").put("files-local"));
 }
 static final class Result {final JSONObject value,meta;Result(JSONObject v,JSONObject m){value=v;meta=m;}}
 static Result call(Context c,String system,JSONObject payload,int budget,AgentApi.Pending pending)throws Exception{
  if(budget<1||budget>16000)throw new Exception("输出长度超出限制");
  JSONObject request=AgentApi.obj("model",MODEL,"thinking",AgentApi.obj("type","disabled"),"temperature",0,"stream",false,"max_tokens",budget,"response_format",AgentApi.obj("type","json_object"),"messages",new JSONArray().put(AgentApi.obj("role","system","content",system+"\n只输出一个合法 JSON 对象。不得用Markdown包装。" )).put(AgentApi.obj("role","user","content",payload.toString())));
  long start=System.currentTimeMillis();JSONObject data=wire(c,"POST","/chat/completions",request,pending);
  JSONArray choices=data.optJSONArray("choices");if(choices==null||choices.length()!=1||!MODEL.equals(data.optString("model")))throw new Exception("模型返回格式或模型标识不匹配");
  JSONObject choice=choices.getJSONObject(0),message=choice.getJSONObject("message");if(!"stop".equals(choice.optString("finish_reason"))||message.has("tool_calls"))throw new Exception("模型没有完整返回内容，请缩小任务重试");
  String text=message.optString("content");JSONObject result;try{result=new JSONObject(text);}catch(Exception e){throw new Exception("模型未返回有效JSON，没有生成模拟结果");}
  JSONObject meta=AgentApi.obj("requestId",UUID.randomUUID().toString(),"providerRequestId",data.optString("id"),"provider","DeepSeek","model",MODEL,"latencyMs",System.currentTimeMillis()-start,"execution","Android local tools");
  healthyAt=System.currentTimeMillis();return new Result(result,meta);
 }
 private static JSONObject wire(Context c,String method,String route,JSONObject body,AgentApi.Pending pending)throws Exception{
  String key=ApiCredentials.read(c);HttpURLConnection con=(HttpURLConnection)new URL(ORIGIN+route).openConnection();con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(120000);con.setRequestMethod(method);con.setRequestProperty("Authorization","Bearer "+key);con.setRequestProperty("Content-Type","application/json");
  if(pending!=null){pending.connection=con;if(pending.canceled){con.disconnect();throw new IOException("请求已取消");}}
  java.util.concurrent.ScheduledFuture<?> deadline=DEADLINES.schedule(con::disconnect,125,java.util.concurrent.TimeUnit.SECONDS);
  try{
   if(body!=null){con.setDoOutput(true);try(OutputStream out=con.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
   int status=con.getResponseCode();if(status!=200){if(status==401)throw new IOException("DeepSeek Key 无效，请重新填写");if(status==402)throw new IOException("DeepSeek 账户余额不足");if(status==429)throw new IOException("DeepSeek 请求过多，请稍后重试");throw new IOException("DeepSeek 返回 HTTP "+status+"；没有切换其他服务");}
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=con.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(pending!=null&&pending.canceled)throw new IOException("请求已取消");if(bytes.size()+n>2000000)throw new IOException("模型响应过大");bytes.write(b,0,n);}}
   try{return new JSONObject(bytes.toString("UTF-8"));}catch(JSONException e){throw new IOException("DeepSeek响应格式异常；没有使用模拟结果");}
  }catch(java.net.SocketTimeoutException e){throw new IOException("DeepSeek 响应超时，原内容保留");}
  finally{deadline.cancel(false);if(pending!=null)pending.connection=null;con.disconnect();}
 }
}
