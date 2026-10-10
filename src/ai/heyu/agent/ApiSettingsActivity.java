package ai.heyu.agent;
import android.app.Activity;import android.os.Bundle;import android.text.InputType;import android.view.*;import android.widget.*;
/** The only user-key entry surface. No key display, export, logging, clipboard or bundled default. */
public final class ApiSettingsActivity extends Activity {
 private TextView status;private EditText input;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);Ui.theme(this);
  ScrollView scroll=new ScrollView(this);LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,24),Ui.dp(this,32),Ui.dp(this,24),Ui.dp(this,32));scroll.addView(root);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return i;});
  root.addView(Ui.text(this,"连接你的 DeepSeek",27,Ui.INK,true));Ui.space(root,10);root.addView(Ui.text(this,"手机直接请求 DeepSeek Flash。文件在本机生成，不需要Mac、局域网服务或USB。API用量由你自己的模型账户承担。",15,Ui.MUTED,false));Ui.space(root,22);
  status=Ui.text(this,ApiCredentials.configured(this)?"已保存本机密钥 · 尚未检查连接":"尚未设置 API Key",14,Ui.MUTED,false);root.addView(status);
  input=new EditText(this);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());input.setHint(ApiCredentials.configured(this)?"输入新Key以替换现有配置":"DeepSeek API Key");input.setContentDescription("DeepSeek API Key 输入");input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setSaveEnabled(false);root.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(this,60)));
  Ui.addButton(root,"保存密钥",true,()->{try{ApiCredentials.save(this,input.getText().toString());input.setText("");status.setText("密钥已在本机加密保存");((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Exception e){status.setText(e.getMessage());}});
  Ui.addButton(root,"测试模型连接",false,()->{status.setText("正在检查 DeepSeek 连接…");AgentApi.IO.execute(()->{String text;try{DeepSeekClient.resetHealth();DeepSeekClient.health(this);text="DeepSeek Flash 已连接 · 手机直连";}catch(Exception e){text=e.getMessage();}final String value=text;runOnUiThread(()->{if(!isFinishing())status.setText(value);});});});
  Ui.addButton(root,"删除本机密钥",false,()->new android.app.AlertDialog.Builder(this).setTitle("删除模型密钥？").setMessage("仅删除本机配置。已有本地文件保留；后续AI请求将停止。服务商已经收到的请求不会撤回。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{ApiCredentials.clear(this);input.setText("");status.setText("本机密钥已删除");}).show());
  Ui.space(root,18);root.addView(Ui.text(this,"密钥通过 Android Keystore 加密保存在本应用内，永不随源码或安装包发布。模型请求会把你确认的文案/材料文字发送至 api.deepseek.com；普通按键与拼音在本机处理。不要填写他人的密钥。",13,Ui.MUTED,false));
  Ui.addButton(root,"返回输入法设置",false,this::finish);
 }
}
