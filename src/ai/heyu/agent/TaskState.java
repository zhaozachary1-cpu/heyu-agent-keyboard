package ai.heyu.agent;
import android.content.Context;
import org.json.*;

/** Async work is addressed to the owner captured at submission, never to the visible chat. */
final class TaskState {
 static Runnable changed;
 static ConversationState current(){return ChatMemory.current();}
 static void restore(Context c){ChatMemory.init(c);}
 static void notifyChange(){notifyChange(current());}
 static void notifyChange(ConversationState owner){ChatMemory.save(owner);if(owner==current()&&changed!=null)changed.run();}
 static void start(Context context,JSONObject previous){start(context,previous,current().task);}
 static void start(Context context,JSONObject previous,String requestedTask){
  ConversationState s=current();if(s.busy||!ChatMemory.bound())return;
  Context app=context.getApplicationContext();int ticket=++s.epoch;
  s.job="";s.busy=true;s.canResume=false;s.result=null;s.error="";s.message="正在提交真实模型任务…";notifyChange(s);
  JSONObject body=AgentApi.obj("task",requestedTask);if(previous!=null)try{body.put("previous",previous);}catch(Exception ignored){}
  AgentApi.call(app,"POST","/tasks",body,(d,e)->created(app,s,ticket,d,e));
 }
 static void startPrepared(Context context,String planId){
  ConversationState s=current();if(s.busy||!ChatMemory.bound())return;Context app=context.getApplicationContext();int ticket=++s.epoch;
  s.job="";s.busy=true;s.canResume=false;s.result=null;s.error="";s.message="已确认计划，正在真实执行…";notifyChange(s);
  AgentApi.call(app,"POST","/execute",AgentApi.obj("planId",planId,"confirmed",true),(d,e)->created(app,s,ticket,d,e));
 }
 private static void created(Context app,ConversationState s,int ticket,JSONObject d,String error){
  String returned=d==null?"":d.optString("id");
  if(!ConversationPolicy.accepts(s,ticket)){if(!returned.isEmpty())cancelRemote(app,returned);return;}
  if(error!=null){s.busy=false;s.error=error+"；重新确认同一计划可查询原执行，不要新建重复任务。";notifyChange(s);return;}
  if(returned.isEmpty()){s.busy=false;s.error="服务没有返回任务编号；请核对原计划。";notifyChange(s);return;}
  s.job=returned;ChatMemory.save(s);poll(app,s,ticket,returned);
 }
 static void clearResult(Context c){ConversationState s=current();s.result=null;s.job="";s.error="";s.message="";ChatMemory.save(s);}
 private static void poll(Context app,ConversationState s,int ticket,String expected){
  AgentApi.call(app,"GET","/jobs/"+expected,null,(d,e)->{
   if(!ConversationPolicy.accepts(s,ticket)||!expected.equals(s.job))return;
   if(e!=null){s.busy=false;s.canResume=true;s.error=e;s.message="连接中断；原任务可能仍在运行，恢复查询不重复生成。";notifyChange(s);return;}
   String status=d.optString("status");s.message=d.optString("message");
   if("ready".equals(status)){publishReady(s,ticket,expected,d);cacheFiles(app,s,d);}
   else if("failed".equals(status)||"canceled".equals(status)){s.busy=false;s.canResume=false;s.error=s.message;}
   notifyChange(s);
   if(s.busy)AgentApi.MAIN.postDelayed(()->{if(ConversationPolicy.accepts(s,ticket))poll(app,s,ticket,expected);},1500);
  });
 }
 static boolean publishReady(ConversationState s,int ticket,String expected,JSONObject result){
  if(!ConversationPolicy.accepts(s,ticket)||!expected.equals(s.job)||!"ready".equals(result.optString("status")))return false;
  s.busy=false;s.canResume=false;s.result=result;s.remember(result,System.currentTimeMillis());ChatMemory.save(s);return true;
 }
 private static void cacheFiles(Context app,ConversationState owner,JSONObject result){
  AgentApi.IO.execute(()->{
   JSONArray artifacts=result.optJSONArray("artifacts");if(artifacts==null)artifacts=new JSONArray().put(result);
   for(int i=0;i<artifacts.length();i++){if(!owner.alive)return;JSONObject a=artifacts.optJSONObject(i);if(a==null||!a.has("fileId"))continue;try{AgentApi.download(app,a);}catch(Exception ignored){/* History retains preview even if bridge bytes expired. */}}
   AgentApi.MAIN.post(()->{if(owner.alive)notifyChange(owner);});
  });
 }
 static boolean publishReply(ConversationState owner,int ticket,String reply){
  if(!owner.alive||owner.replyEpoch!=ticket)return false;
  owner.lastReply=reply;owner.replyAt=System.currentTimeMillis();ChatMemory.save(owner);return true;
 }
 static void resume(Context c){ConversationState s=current();if(s.job.isEmpty()||s.busy||!ChatMemory.bound())return;int ticket=++s.epoch;s.busy=true;s.canResume=false;s.error="";s.message="正在恢复查询原任务…";notifyChange(s);poll(c.getApplicationContext(),s,ticket,s.job);}
 private static void cancelRemote(Context c,String id){AgentApi.call(c,"POST","/jobs/"+id+"/cancel",AgentApi.obj(),(d,e)->{});}
 static void cancel(Context c){ConversationState s=current();s.epoch++;s.busy=false;s.canResume=false;s.message="已停止接收结果；已提交的模型请求可能仍计费";if(!s.job.isEmpty())cancelRemote(c.getApplicationContext(),s.job);s.job="";notifyChange(s);}
}
