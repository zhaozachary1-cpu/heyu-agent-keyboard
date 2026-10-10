package ai.heyu.agent;
import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.io.*;
import java.util.UUID;

/** System-owned picker; selected bytes stay on-device until the prepare confirmation. */
public final class SourcePickerActivity extends Activity {
 private static String pendingOwner="",pendingHost="",pendingTitle="";private static int pendingGeneration=-1,pendingWindow=-1;private static long pendingUntil;private static boolean returning;
 static boolean pending(){if(android.os.SystemClock.elapsedRealtime()>pendingUntil){pendingOwner="";return false;}return !pendingOwner.isEmpty();}
 static boolean begin(Context context,String host){
  if(pending()||!ChatMemory.bound())return false;ChatContextService.Header h=ChatContextService.header();
  if("com.whatsapp".equals(host)&&h==null)return false;
  pendingOwner=ChatMemory.current().id;pendingGeneration=ChatMemory.generation();pendingHost=host;pendingTitle=ChatMemory.visibleTitle();pendingWindow=h==null?-2:h.window;pendingUntil=android.os.SystemClock.elapsedRealtime()+150000;returning=false;return true;
 }
 static boolean skipTransient(String host){
  if(!pending())return false;
  if(!host.equals(pendingHost)&&!host.equals("com.android.documentsui")&&!host.equals("com.google.android.documentsui")&&!host.equals("ai.heyu.agent")){pendingOwner="";return false;}
  return !returning||!host.equals(pendingHost);
 }
 static boolean resume(Context context,String host){
  if(!pending()||!returning||!pendingHost.equals(host))return false;
  boolean ok=ChatMemory.bound()&&pendingOwner.equals(ChatMemory.current().id)&&pendingGeneration==ChatMemory.generation();
  if(ok&&"com.whatsapp".equals(host)){ChatContextService.Header h=ChatContextService.header();ok=h!=null&&pendingTitle.equals(h.title)&&pendingWindow==h.window;if(ok)ok=ChatMemory.resumeAuthorizedDelivery(pendingOwner,pendingGeneration,pendingTitle,pendingWindow,h.window,h.epoch);}
  pendingOwner="";if(ok){WorkState.current().open=true;TaskState.notifyChange();}return ok;
 }
 @Override public void finish(){returning=true;super.finish();}
 private ConversationState owner;private int ticket;
 @Override protected void onSaveInstanceState(Bundle out){if(owner!=null){out.putString("owner",owner.id);out.putInt("ticket",ticket);}super.onSaveInstanceState(out);}

 @Override public void onCreate(Bundle state){super.onCreate(state);Ui.theme(this);ChatMemory.init(this);
  String ownerId=state==null?getIntent().getStringExtra("owner"):state.getString("owner");owner=ChatMemory.find(ownerId);
  if(owner==null||!owner.alive){finish();return;}ticket=state==null?owner.workEpoch:state.getInt("ticket");if(state!=null)return;
  if(owner.workBusy||owner.sources.size()>=4){Toast.makeText(this,"最多选择4份材料；处理中不能换材料",Toast.LENGTH_LONG).show();finish();return;}
  Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
  pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/csv","text/comma-separated-values","application/csv","application/vnd.ms-excel","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","image/png","image/jpeg","image/webp"});
  try{startActivityForResult(pick,1);}catch(Exception e){owner.workError="系统文件选择器不可用";TaskState.notifyChange(owner);finish();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(!ConversationPolicy.acceptsWork(owner,ticket)){finish();return;}
  if(result!=RESULT_OK||data==null||data.getData()==null){finish();return;}
  Uri uri=data.getData();owner.open=true;owner.workBusy=true;owner.workStatus="正在读取你选择的文件（尚未上传）…";TaskState.notifyChange(owner);
  AgentApi.IO.execute(()->{File saved=null;String name="",err=null;
   try{
    long declared=-1;
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
     if(c!=null&&c.moveToFirst()){name=c.getString(0);if(!c.isNull(1))declared=c.getLong(1);}
    }
    if(name==null||name.length()>180||!name.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(txt|csv|xlsx|docx|pdf|png|jpg|jpeg|webp)"))throw new IOException("请选择支持的文档、表格或静态图片");
    if(declared>8*1024*1024)throw new IOException("单文件不能超过8 MB");
    File dir=new File(getFilesDir(),"imports");dir.mkdirs();
    saved=new File(dir,UUID.randomUUID().toString()+name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT));
    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(saved)){
     if(in==null)throw new IOException("无法打开所选文件");byte[] buf=new byte[8192];int count=0,n;
     while((n=in.read(buf))!=-1){count+=n;if(count>8*1024*1024)throw new IOException("单文件不能超过8 MB");out.write(buf,0,n);}
     if(count==0)throw new IOException("文件为空");
    }
    long total=saved.length();for(WorkState.Source s:owner.sources)total+=s.file.length();if(total>24*1024*1024)throw new IOException("材料合计不能超过24 MB");
   }catch(Exception e){err=e.getMessage();if(saved!=null)saved.delete();}
   final File f=saved;final String filename=name,error=err;
   AgentApi.MAIN.post(()->{WorkState.acceptSource(owner,ticket,f==null?null:new WorkState.Source(filename,f),error);finish();});
  });
 }
}
