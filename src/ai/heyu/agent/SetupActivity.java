package ai.heyu.agent;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public final class SetupActivity extends Activity {
 private LinearLayout content;private TextView status;
 @Override public void onCreate(Bundle b){super.onCreate(b);Ui.theme(this);show();}
 @Override protected void onResume(){super.onResume();refresh();}
 private void show(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);content=Ui.column(this);content.setPadding(Ui.dp(this,22),Ui.dp(this,20),Ui.dp(this,22),Ui.dp(this,24));scroll.addView(content);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  content.addView(Ui.text(this,"禾语  Agent",32,Ui.INK,true));Ui.space(content,5);content.addView(Ui.text(this,"从对话，到可交付的文件。",18,Ui.MUTED,false));Ui.space(content,20);
  LinearLayout card=Ui.card(this);status=Ui.text(this,"检查"+AgentApi.serviceName(this)+"…",13,Ui.INK,false);card.addView(status);content.addView(card);
  Ui.addButton(content,"配置我的 DeepSeek Key",true,()->startActivity(new Intent(this,ApiSettingsActivity.class)));
  Ui.addButton(content,"检查模型连接",false,()->refresh());
  Ui.space(content,15);content.addView(Ui.text(this,"01  启用真实输入法",20,Ui.INK,true));content.addView(Ui.text(this,"在系统中启用「禾语 Agent」，再选择它。升级保留本应用已有记忆与材料，不替换原版禾语。",13,Ui.MUTED,false));
  Ui.addButton(content,"打开系统输入法设置",true,()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
  Ui.addButton(content,"选择禾语 Agent 键盘",false,()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
  Ui.space(content,18);content.addView(Ui.text(this,"02  上下文范围（可选）",20,Ui.INK,true));content.addView(Ui.text(this,"仅 WhatsApp 当前屏幕可见文字；不读取历史数据库。帮我做会在本地查看可见聊天；预览后，只有主动开启本次聊天自动识别才交给公司配置的模型。提炼不等于生成，制作文件另需确认。自动回复建议使用下方独立开关；开启后无需逐次点击读取和确认。",13,Ui.MUTED,false));
  Switch automatic=new Switch(this);Switch consent=new Switch(this);consent.setText("同意上述 WhatsApp 上下文范围");consent.setTextSize(14);consent.setChecked(ChatContextService.consent(this));consent.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,8));consent.setOnCheckedChangeListener((b,checked)->{getSharedPreferences("privacy",0).edit().putBoolean("whatsapp",checked).apply();if(!checked){ChatContextService.setAutomaticConsent(this,false);automatic.setChecked(false);ChatContextService.clear();}});content.addView(consent);
  Ui.addButton(content,"由我开启 WhatsApp 上下文辅助",false,()->{if(!consent.isChecked()){Toast.makeText(this,"请先阅读并确认上下文范围；不确认也可以粘贴上下文",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);try{startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}});
  Ui.space(content,12);content.addView(Ui.text(this,"自动回复建议（独立授权）",18,Ui.INK,true));
  content.addView(Ui.text(this,"开启后，WhatsApp 私聊唤起键盘时，最多12条/10,000字可见文字和本会话风格偏好自动经"+AgentApi.routeName(this)+"提交配置模型；新消息自动更新建议。不上传输入草稿、附件或后台历史。不保存聊天原文，点建议才填入，不自动发送。收起键盘或离开即停止；已提交请求不能撤回。可随时关闭。",13,Ui.MUTED,false));
  automatic.setText("允许当前可见私聊自动生成回复建议");automatic.setContentDescription("允许当前可见私聊自动生成回复建议");automatic.setTextSize(14);automatic.setChecked(ChatContextService.automaticConsent(this));automatic.setOnCheckedChangeListener((b,checked)->{if(checked&&!consent.isChecked()){automatic.setChecked(false);Toast.makeText(this,"请先同意上下文范围",Toast.LENGTH_LONG).show();return;}ChatContextService.setAutomaticConsent(this,checked);});content.addView(automatic);
  Ui.space(content,18);content.addView(Ui.text(this,"单次文件交付（独立授权）",20,Ui.INK,true));
  content.addView(Ui.text(this,"你在结果卡点一键发送后，禾语核对当前联系人的收件地址，打开该联系人的文件预览并发送选中的文件。支持的文档无需重选联系人或文件；其他类型保留原生附件方式。接收人、草稿或页面不明确时停止，不后台重复发送。送达情况以 WhatsApp 回执为准，禾语不会自动撤回。",13,Ui.MUTED,false));
  Switch sendConsent=new Switch(this);sendConsent.setText("允许我逐个点选文件后，一键发到当前聊天");sendConsent.setTextSize(14);sendConsent.setChecked(DirectSend.consent(this));sendConsent.setOnCheckedChangeListener((b,value)->DirectSend.setConsent(this,value));content.addView(sendConsent);
  Ui.space(content,18);content.addView(Ui.text(this,"03  验证与使用",20,Ui.INK,true));content.addView(Ui.text(this,"先用下方本地输入框验证键盘。进入 WhatsApp 私聊后，自动建议出现在键盘上方，点帮我做可制作材料。输入框的文字不会自动作为上下文上传。",13,Ui.MUTED,false));
  EditText input=new EditText(this);input.setHint("本地测试输入框 · 不会发送");input.setMinLines(2);input.setTextSize(15);input.setContentDescription("本地测试输入框");content.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(this,88)));
  Ui.addButton(content,"用公开示例测试真实模型",false,()->{status.setText("正在真实推理：仅发送固定的公开测试句子…");AgentApi.call(this,"POST","/reply",AgentApi.obj("context","Hello, can you send a short introduction to this AI keyboard?","tone","友好中文，不承诺已经完成"),(d,e)->status.setText(e==null?"真实模型返回：\n"+d.optString("reply")+"\n"+d.optJSONObject("meta").optString("model"):e));});
  Ui.addButton(content,"打开 WhatsApp（请选给自己发消息）",true,()->{Intent i=getPackageManager().getLaunchIntentForPackage("com.whatsapp");if(i!=null)startActivity(i);});
  Ui.space(content,18);content.addView(Ui.text(this,"当前独立版 1.0 · 用户自带Key\n• 模型Key由你填写，使用Android Keystore加密保存在本机；不随APK发布。\n• "+"手机直接访问DeepSeek，文件在手机本地生成；不需要Mac、USB或局域网服务。"+"\n• 服务不可用时不伪造结果。\n• 支持PPT、正式文档、清单报价、表格分析、图片处理、双语材料及待办日程。\n• 优先识别聊天任务，也可自己选择目标；先确认计划，再执行。\n• 中英文在同一键盘输入，中文使用本机 AOSP 拼音词典；不上传按键、不学习个人词库。\n• 文件一键发送需要单独开启，再逐个文件点击；只适配 WhatsApp。\n• 不由模型自行发送，不覆盖草稿；其他方式分享仍由你选接收人。\n• 图片处理为缩放/裁剪/拼图/水印/遮挡，不是生图。\n• 任务目标、记忆备注和聊天偏好可直接点开编辑，完成才保存。\n• 语音、滑行输入、个性化纠错、扫描OCR和任意文件编辑暂未接入。",12,Ui.MUTED,false));
 }
 private void refresh(){if(status==null)return;AgentApi.call(this,"GET","/health",null,(d,e)->{if(isFinishing())return;String selected=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);status.setText(e==null?AgentApi.serviceName(this)+"在线 · "+d.optString("model")+"\n服务商："+d.optString("provider")+"\n当前键盘："+(selected!=null&&selected.startsWith(getPackageName())?"禾语 Agent":"尚未选择禾语 Agent"):e);});}
}
