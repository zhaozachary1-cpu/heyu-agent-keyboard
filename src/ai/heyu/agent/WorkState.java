package ai.heyu.agent;
import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Manual drafts, imports and plans belong to their captured conversation owner. */
final class WorkState {
 static final String[][] KINDS={{"auto","让禾语判断"},{"ppt","制作演示文稿"},{"document","整理成文档"},{"quote","整理清单或报价"},{"analysis","分析表格"},{"image","处理图片"},{"translation","翻译材料"},{"schedule","整理待办和日程"}};
 static ConversationState current(){return ChatMemory.current();}
 static final class Source {final String name;final File file;String id="";JSONObject meta;Source(String n,File f){name=n;file=f;}}
 static String kindName(String k){for(String[] x:KINDS)if(x[0].equals(k))return x[1];return k;}
 static void changed(){TaskState.notifyChange();}
 static void invalidate(){invalidate(current());}
 static void invalidate(ConversationState s){s.workEpoch++;s.workBusy=false;s.workStatus="";s.plan=null;s.workError="";ChatMemory.save(s);}
 static boolean acceptSource(ConversationState s,int ticket,Source source,String error){
  if(!ConversationPolicy.acceptsWork(s,ticket)){if(source!=null)source.file.delete();return false;}
  s.workBusy=false;s.workStatus="";
  if(error!=null)s.workError=error;
  else if(source!=null){s.sources.add(source);invalidate(s);}
  TaskState.notifyChange(s);return true;
 }
 static boolean hasDraft(){return !current().task.isBlank()||!current().sources.isEmpty();}
 static void select(String k){ConversationState s=current();s.kind=k;s.open=true;s.choosing=false;invalidate();changed();}
 static void reset(Context c){ConversationState s=current();if(s.workBusy)return;s.workEpoch++;for(Source src:new ArrayList<>(s.sources))remove(c,src);s.sources.clear();s.kind="auto";s.plan=null;s.open=false;s.choosing=false;s.task="";s.workError="";s.workStatus="";changed();}
 static void remove(Context c,Source source){ConversationState s=current();if(s.workBusy)return;s.sources.remove(source);source.file.delete();if(!source.id.isEmpty())AgentApi.call(c,"POST","/sources/"+source.id+"/remove",AgentApi.obj(),(d,e)->{});invalidate();changed();}
 static boolean acceptPlan(ConversationState s,int ticket,JSONObject output,String error){if(!ConversationPolicy.acceptsWork(s,ticket))return false;s.workBusy=false;s.workStatus="";if(error!=null)s.workError=error;else s.plan=output;TaskState.notifyChange(s);return true;}
 static void prepare(Context context){
  final ConversationState s=current();if(s.workBusy||s.busy||!ChatMemory.bound())return;
  final String task=ChatMemory.modelTask(s),selected=s.kind;
  if(s.task.isBlank()||task.length()>10000){s.workError="请描述具体要求；目标与聊天偏好合计最多10000字。";changed();return;}
  final Context app=context.getApplicationContext();final int ticket=++s.workEpoch;final ArrayList<Source> inputs=new ArrayList<>(s.sources);
  s.workBusy=true;s.workError="";s.plan=null;s.workStatus="手机正在解析材料并整理计划…";changed();
  AgentApi.IO.execute(()->{
   JSONObject result=null;String failure=null;
   try{
    JSONArray ids=new JSONArray();
    for(Source source:inputs){
     if(source.id.isEmpty()){
      byte[] bytes=Files.readAllBytes(source.file.toPath());
      JSONObject metadata=new JSONObject(new String(AgentApi.request(app,"POST","/sources",AgentApi.obj("name",source.name,"base64",Base64.encodeToString(bytes,Base64.NO_WRAP))),java.nio.charset.StandardCharsets.UTF_8));
      source.id=metadata.getString("id");source.meta=metadata;
     }
     ids.put(source.id);
    }
    result=new JSONObject(new String(AgentApi.request(app,"POST","/prepare",AgentApi.obj("kind",selected,"task",task,"sourceIds",ids,"timezone",TimeZone.getDefault().getID())),java.nio.charset.StandardCharsets.UTF_8));
   }catch(Exception e){failure=e.getMessage()==null?AgentApi.unavailable(app):e.getMessage();}
   final JSONObject output=result;final String err=failure;
   AgentApi.MAIN.post(()->{if(!ConversationPolicy.acceptsWork(s,ticket))return;acceptPlan(s,ticket,output,err);});
  });
 }
}
