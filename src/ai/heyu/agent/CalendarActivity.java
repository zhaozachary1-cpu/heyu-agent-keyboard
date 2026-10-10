package ai.heyu.agent;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import org.json.*;
import java.util.*;

/** User initiated, permission gated, explicit calendar + field review. Never invites anyone. */
public final class CalendarActivity extends Activity {
 private JSONArray events;private String job,zone;private boolean writing=false;
 @Override public void onCreate(Bundle state){super.onCreate(state);Ui.theme(this);
  try{events=new JSONArray(getIntent().getStringExtra("events"));job=getIntent().getStringExtra("job");zone=getIntent().getStringExtra("timezone");if(events.length()<1||events.length()>8||job==null||!job.matches("[0-9a-f]{32}")){finish();return;}}
  catch(Exception e){finish();return;}
  if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED){
   new AlertDialog.Builder(this).setTitle("允许禾语访问日历？").setMessage("接下来请求系统权限以列出可写日历；系统授权会保留，可在设置中撤销。选择日历并核对时间后才会写入；不读取日历正文、不发邀请、不添加提醒。也可取消并使用已生成的ICS文件。").setPositiveButton("请求系统权限",(d,w)->requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},1)).setNegativeButton("取消",(d,w)->finish()).setOnCancelListener(d->finish()).show();
  }else choose();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(result.length==2&&result[0]==0&&result[1]==0)choose();else message("未写入日历","权限未授予，生成的ICS仍可分享或手动导入。");}
 private void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",(d,w)->finish()).setOnCancelListener(d->finish()).show();}
 private void choose(){
  ArrayList<Long> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
  try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{"_id","calendar_displayName","account_name"},"calendar_access_level >= ? AND visible = 1",new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},null)){
   if(c!=null)while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1)+" · "+c.getString(2));}
  }catch(Exception e){message("无法读取可写日历","没有写入任何事件，可以使用ICS文件。");return;}
  if(ids.isEmpty()){message("没有可写日历","请自行配置日历账户后重试；本次只生成了ICS文件，没有写入日历。");return;}
  new AlertDialog.Builder(this).setTitle("选择写入的日历").setItems(names.toArray(new String[0]),(d,i)->confirm(ids.get(i),names.get(i))).setNegativeButton("取消",(d,w)->finish()).setOnCancelListener(d->finish()).show();
 }
 private void confirm(long calendar,String name){
  StringBuilder text=new StringBuilder("日历："+name+"\n时区："+zone+"\n\n");
  for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e==null){finish();return;}text.append(e.optString("title")).append("\n").append(e.optString("start")).append(" 至 ").append(e.optString("end")).append("\n地点：").append(e.optString("location","未指定")).append("\n\n");}
  text.append("不会发送邀请，也不会添加提醒。同一份结果不会重复写入此日历。");
  new AlertDialog.Builder(this).setTitle("最后核对日程").setMessage(text).setPositiveButton("确认写入 "+events.length()+" 项",(d,w)->write(calendar)).setNegativeButton("取消",(d,w)->finish()).setOnCancelListener(d->finish()).show();
 }
 private void write(long calendar){if(writing)return;writing=true;
  AgentApi.IO.execute(()->{String title="写入失败",text="无法确认结果，请在日历中核对后再试。";
   try{
    ArrayList<ContentProviderOperation> ops=new ArrayList<>();int existed=0;
    for(int i=0;i<events.length();i++){
     JSONObject e=events.getJSONObject(i);String marker="Heyu:"+job+":"+i;boolean found=false;
     try(Cursor c=getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{"_id"},"calendar_id = ? AND description = ? AND deleted = 0",new String[]{String.valueOf(calendar),marker},null)){found=c!=null&&c.moveToFirst();}
     if(found){existed++;continue;}
     long start=e.getLong("startMs"),end=e.getLong("endMs");if(end<=start)throw new IllegalArgumentException();
     ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValue("calendar_id",calendar).withValue("title",e.getString("title")).withValue("dtstart",start).withValue("dtend",end).withValue("eventTimezone",zone).withValue("eventLocation",e.optString("location")).withValue("description",marker).withValue("hasAlarm",0).build());
    }
    if(!ops.isEmpty())getContentResolver().applyBatch(CalendarContract.AUTHORITY,ops);
    for(int i=0;i<events.length();i++){
     JSONObject e=events.getJSONObject(i);String marker="Heyu:"+job+":"+i;
     try(Cursor c=getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{"title","dtstart","dtend"},"calendar_id = ? AND description = ? AND deleted = 0",new String[]{String.valueOf(calendar),marker},null)){
      if(c==null||c.getCount()!=1||!c.moveToFirst()||!e.getString("title").equals(c.getString(0))||e.getLong("startMs")!=c.getLong(1)||e.getLong("endMs")!=c.getLong(2))throw new IllegalStateException("Readback mismatch");
     }
    }
    title="已写入并核对日历";text="本次新增 "+ops.size()+" 项，已存在 "+existed+" 项。未发送邀请或添加提醒。";
   }catch(Exception ignored){}
   final String t=title,m=text;AgentApi.MAIN.post(()->message(t,m));
  });
 }
}
