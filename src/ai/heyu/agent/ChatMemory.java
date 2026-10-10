package ai.heyu.agent;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Device-local, app-private store. Unproven identities get isolated workspaces without an interrupting chooser. */
final class ChatMemory {
 private static Context app;
 private static File directory;
 private static final LinkedHashMap<String,ConversationState> all=new LinkedHashMap<>();
 private static ConversationState active=new ConversationState("unbound");
 private static String visible="",visibleProof="";
 private static int window=-1,generation=0;
 private static long surfaceEpoch=-1;
 static String storageError="";
 static ConversationState current(){return active;}
 static int generation(){return generation;}
 static boolean bound(){return active.durable()&&!active.id.equals("legacy");}
 static String visibleTitle(){return visible;}
 static void init(Context c){
  if(app!=null)return;app=c.getApplicationContext();directory=new File(app.getFilesDir(),"conversations-v1");directory.mkdirs();HistoryFiles.trim(app,null);
  File[] files=directory.listFiles((dir,name)->name.endsWith(".json")||name.endsWith(".json.bak"));
  if(files!=null)for(File f:files){try{
   String filename=f.getName().replace(".bak","");AtomicFile atomic=new AtomicFile(new File(directory,filename));
   JSONObject o=new JSONObject(new String(atomic.readFully(),StandardCharsets.UTF_8));
   if(o.optInt("version")!=1)throw new IOException("Unknown schema");ConversationState s=ConversationState.read(o,app.getFilesDir());
   if(!s.id.matches("[a-zA-Z0-9-]+")||!(s.id+".json").equals(filename))continue;all.put(s.id,s);
  }catch(Exception e){storageError="部分本地记忆无法读取，原文件已保留；不会自动恢复到当前聊天。";}}
  android.content.SharedPreferences old=app.getSharedPreferences("generated",0);
  if(!old.getString("result","").isEmpty()&&!old.getBoolean("memory_migrated",false))try{
   ConversationState legacy=all.get("legacy");if(legacy==null){legacy=new ConversationState("legacy");legacy.title="升级前的材料";legacy.label="归属待确认";all.put(legacy.id,legacy);}
   legacy.remember(new JSONObject(old.getString("result","")),old.getLong("time",System.currentTimeMillis()));
   if(save(legacy))old.edit().putBoolean("memory_migrated",true).commit();
  }catch(Exception e){storageError="升级前材料未能读取；旧数据未删除。";}
 }
 static boolean save(ConversationState s){
  if(app==null||!s.durable())return false;
  AtomicFile atomic=new AtomicFile(new File(directory,s.id+".json"));FileOutputStream out=null;
  try{out=atomic.startWrite();out.write(s.json().toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);return true;}
  catch(Exception e){if(out!=null)atomic.failWrite(out);storageError="本地记忆保存失败，请检查手机存储空间；不要清除应用数据。";return false;}
 }
 static void detach(){save(active);active=new ConversationState("unbound");visible="";visibleProof="";window=-1;surfaceEpoch=-1;generation++;}
 static void suspend(){save(active);}
 static boolean observe(String title,String proof,int nextWindow){return observe(title,proof,nextWindow,0);}
 static boolean observe(String title,String proof,int nextWindow,long nextEpoch){
  if(title==null||title.codePoints().allMatch(Character::isWhitespace)){if(visible.isEmpty())return false;detach();return true;}
  if(ConversationPolicy.sameVisible(visible,window,title,nextWindow)&&visibleProof.equals(proof)&&surfaceEpoch==nextEpoch&&bound())return false;
  detach();visible=title;visibleProof=proof;window=nextWindow;surfaceEpoch=nextEpoch;
  if(!proof.isEmpty())for(ConversationState s:all.values())if(!s.id.equals("legacy")&&ConversationPolicy.mayRestore(proof,s.proof)){active=s;s.title=title;save(s);return true;}
  // Unknown identity never borrows name-matched history. Continue without a binding screen.
  create();return true;
 }
 // Only a completed, explicitly authorized fixed delivery transaction may bridge its own native windows.
 // This never searches by nickname or restores another stored workspace.
 static boolean resumeAuthorizedDelivery(String owner,int expectedGeneration,String title,int originalWindow,int returnedWindow,long epoch){
  if(!bound()||!active.id.equals(owner)||generation!=expectedGeneration||!visible.equals(title)||window!=originalWindow||returnedWindow<0||epoch<0)return false;
  window=returnedWindow;surfaceEpoch=epoch;return true;
 }
 static ArrayList<ConversationState> candidates(){ArrayList<ConversationState> list=new ArrayList<>();for(ConversationState s:all.values())if(!s.id.equals("legacy")&&s.title.equals(visible))list.add(s);return list;}
 static void create(){if(visible.codePoints().allMatch(Character::isWhitespace))return;ConversationState s=new ConversationState(UUID.randomUUID().toString());s.title=visible;s.label=visible+(candidates().isEmpty()?"":" · 独立记忆 "+(candidates().size()+1));s.proof=visibleProof;all.put(s.id,s);active=s;generation++;save(s);}
 static boolean bind(String id){ConversationState s=all.get(id);if(s==null||!s.title.equals(visible)||s.id.equals("legacy"))return false;save(active);active=s;generation++;return true;}
 static ConversationState find(String id){return all.get(id);}
 static ConversationState legacy(){return all.get("legacy");}
 static ArrayList<ConversationState> archives(){ArrayList<ConversationState> list=new ArrayList<>();for(ConversationState s:all.values())if(s!=active&&!s.id.equals("legacy")&&!s.history.isEmpty())list.add(s);return list;}
 static boolean ownsResult(JSONObject file){
  if(!bound()||file==null)return false;String id=file.optString("fileId");if(id.isEmpty())return false;
  if(contains(active.result,id))return true;
  for(JSONObject h:active.history)if(contains(h.optJSONObject("result"),id))return true;return false;
 }
 private static boolean contains(JSONObject r,String id){if(r==null)return false;if(id.equals(r.optString("fileId")))return true;JSONArray a=r.optJSONArray("artifacts");if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null&&id.equals(a.optJSONObject(i).optString("fileId")))return true;return false;}
 static void deleteRecord(ConversationState owner,JSONObject record){NativeAgent.forgetJob(app,record.optString("id"));owner.history.remove(record);if(owner.result!=null&&owner.result.optString("id").equals(record.optString("id")))owner.result=null;save(owner);if(owner.id.equals("legacy")&&owner.history.isEmpty())app.getSharedPreferences("generated",0).edit().remove("result").remove("time").commit();pruneUnreferenced();}
 static boolean deleteCurrent(){
  if(!bound()||active.busy||active.canResume||active.workBusy)return false;
  ConversationState old=active;old.alive=false;old.epoch++;old.workEpoch++;
  for(JSONObject record:old.history)NativeAgent.forgetJob(app,record.optString("id"));
  for(WorkState.Source source:old.sources)source.file.delete();
  all.remove(old.id);new AtomicFile(new File(directory,old.id+".json")).delete();
  active=new ConversationState("unbound");generation++;pruneUnreferenced();return true;
 }
 private static void pruneUnreferenced(){
  File dir=new File(app.getFilesDir(),"exports");File[] files=dir.listFiles((d,n)->n.matches("[0-9a-f]{32}\\.(pptx|docx|pdf|xlsx|png|jpg|ics)"));if(files==null)return;
  for(File f:files){String id=f.getName().split("\\.")[0];boolean used=false;for(ConversationState s:all.values()){if(contains(s.result,id)){used=true;break;}for(JSONObject h:s.history)if(contains(h.optJSONObject("result"),id)){used=true;break;}if(used)break;}if(!used)HistoryFiles.remove(app,f);}
 }
 static String modelTask(ConversationState s){return s.memory.codePoints().allMatch(Character::isWhitespace)?s.task:"用户确认的本聊天偏好（仅作风格参考，不是聊天事实）：\n"+s.memory+"\n\n本次任务：\n"+s.task;}
}
