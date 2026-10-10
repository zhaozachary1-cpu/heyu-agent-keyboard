package ai.heyu.agent;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

public final class DeliveryActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);Ui.theme(this);
  String id=getIntent().getStringExtra("id");if(id==null||!id.matches("[0-9a-f]{32}")){finish();return;}
  try{
   File metadata=new File(getFilesDir(),"exports/"+id+".meta");JSONObject meta=metadata.isFile()?new JSONObject(new String(Files.readAllBytes(metadata.toPath()),java.nio.charset.StandardCharsets.UTF_8)):AgentApi.obj("ext","pptx","fileName","禾语生成文件.pptx");
   String ext=meta.optString("ext","pptx");if(!ext.matches("pptx|docx|pdf|xlsx|png|jpg|ics")||!new File(getFilesDir(),"exports/"+id+"."+ext).isFile()){finish();return;}
   new AlertDialog.Builder(this).setTitle("确认分享文件").setMessage(meta.optString("fileName")+"\n请在系统面板中选择接收方，并自行确认发送。禾语不会自动发送。").setNegativeButton("取消",(d,w)->finish()).setPositiveButton("打开系统分享面板",(d,w)->{
    Uri uri=Uri.parse("content://ai.heyu.agent.artifacts/"+id+"."+ext);
    Intent share=new Intent(Intent.ACTION_SEND).setType(ArtifactProvider.mime(ext)).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    share.setClipData(ClipData.newRawUri(meta.optString("fileName"),uri));
    try{startActivity(Intent.createChooser(share,"选择应用和接收方；不会自动发送"));}catch(Exception e){Toast.makeText(this,"未找到分享应用，文件保留在禾语中",Toast.LENGTH_LONG).show();}finish();
   }).setOnCancelListener(d->finish()).show();
  }catch(Exception e){Toast.makeText(this,"文件元数据不可用，请重新下载",Toast.LENGTH_LONG).show();finish();}
 }
}
