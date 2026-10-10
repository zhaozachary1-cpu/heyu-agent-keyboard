package ai.heyu.agent;

import org.json.*;
import java.io.File;
import java.util.*;

/** One owner for drafts, jobs and history. Raw captured conversations are never persisted. */
final class ConversationState {
 final String id;
 String title="",label="",proof="",task="",kind="auto",tone="简洁专业",memory="";
 String job="",message="",error="",workError="",workStatus="",lastReply="";
 long replyAt=0;
 boolean busy=false,canResume=false,open=false,choosing=false,workBusy=false,alive=true;
 int epoch=0,workEpoch=0,replyEpoch=0;
 JSONObject result=null,plan=null;
 final ArrayList<WorkState.Source> sources=new ArrayList<>();
 final ArrayList<JSONObject> history=new ArrayList<>();
 ConversationState(String id){this.id=id;}
 boolean durable(){return !id.equals("unbound")&&alive;}
 JSONObject json(){
  JSONArray items=new JSONArray(),inputs=new JSONArray();for(JSONObject h:history)items.put(h);
  for(WorkState.Source s:sources)inputs.put(AgentApi.obj("name",s.name,"file",s.file.getName(),"id",s.id,"meta",s.meta));
  return AgentApi.obj("version",1,"id",id,"title",title,"label",label,"proof",proof,"task",task,"kind",kind,"tone",tone,"memory",memory,"job",job,"message",message,"error",error,"busy",busy,"canResume",canResume,"open",open,"result",result,"plan",plan,"sources",inputs,"history",items,"lastReply",lastReply,"replyAt",replyAt);
 }
 static ConversationState read(JSONObject o,File files){
  ConversationState s=new ConversationState(o.optString("id"));
  s.title=o.optString("title");s.label=o.optString("label",s.title);s.proof=o.optString("proof");s.task=o.optString("task");s.kind=o.optString("kind","auto");s.tone=o.optString("tone","简洁专业");s.memory=o.optString("memory");s.job=o.optString("job");s.message=o.optString("message");s.error=o.optString("error");s.open=o.optBoolean("open");s.result=o.optJSONObject("result");s.plan=o.optJSONObject("plan");s.lastReply=o.optString("lastReply");s.replyAt=o.optLong("replyAt");
  s.canResume=!s.job.isEmpty()&&(o.optBoolean("busy")||o.optBoolean("canResume"));
  if(s.canResume)s.message="有未完成的原任务；恢复查询不会重复生成";
  else if(o.optBoolean("busy")){s.error="上次提交回执未收到；请先核对原计划，勿重复新建。";s.open=true;}
  JSONArray hs=o.optJSONArray("history");if(hs!=null)for(int i=0;i<hs.length();i++){JSONObject h=hs.optJSONObject(i);if(h!=null&&h.optJSONObject("result")!=null)s.history.add(h);}
  JSONArray ss=o.optJSONArray("sources");if(ss!=null)for(int i=0;i<ss.length();i++){
   JSONObject x=ss.optJSONObject(i);if(x==null||!x.optString("file").matches("[a-zA-Z0-9.-]+"))continue;
   File f=new File(new File(files,"imports"),x.optString("file"));if(!f.isFile())continue;
   WorkState.Source source=new WorkState.Source(x.optString("name"),f);
   // Remote uploads expire; re-upload selected local bytes after a process restart.
   s.sources.add(source);
  }
  // A prepared plan can be reused only to reconcile an interrupted execute, otherwise re-prepare.
  if(!o.optBoolean("busy")&&!s.canResume)s.plan=null;
  return s;
 }
 void remember(JSONObject r,long now){
  String key=r.optString("id",r.optString("fileId"));
  for(JSONObject h:history)if(key.equals(h.optString("id")))return;
  history.add(0,AgentApi.obj("id",key,"at",now,"task",task,"kind",kind,"result",copy(r)));
  while(history.size()>50)history.remove(history.size()-1);
 }
 static JSONObject copy(JSONObject o){try{return o==null?null:new JSONObject(o.toString());}catch(Exception e){return null;}}
}
