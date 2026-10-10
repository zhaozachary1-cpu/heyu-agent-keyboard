package ai.heyu.agent;
import android.inputmethodservice.InputMethodService;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.io.File;

public final class AgentIme extends InputMethodService {
 private static AgentIme live;
 @Override public void onCreate(){super.onCreate();live=this;Ui.theme(this);navigationBottom=Ui.dp(this,48);pinyin=new PinyinDecoder(this);keys=new KeyboardController(pinyin);}
 /** Read the real editor, never mistake WhatsApp's unflagged placeholder for a draft. */
 static EditorInfo deliveryEditorInfo(){return live==null?null:live.getCurrentInputEditorInfo();}
 static InputConnection deliveryInputConnection(){
  return live!=null&&!live.privateInput&&live.localEdit==null&&live.keys!=null&&live.keys.composition().isEmpty()&&"com.whatsapp".equals(live.host())?live.getCurrentInputConnection():null;
 }
 static boolean emptyWhatsAppEditor(){
  AgentIme ime=live;
  if(ime==null||ime.privateInput||!ChatContextService.keyboardActive||!"com.whatsapp".equals(ime.host()))return false;
  InputConnection ic=ime.getCurrentInputConnection();if(ic==null)return false;
  CharSequence before=ic.getTextBeforeCursor(1,0),after=ic.getTextAfterCursor(1,0),selection=ic.getSelectedText(0);
  return before!=null&&after!=null&&before.length()==0&&after.length()==0&&(selection==null||selection.length()==0);
 }

 private boolean historyOpen=false,historyArchive=false,memoryOpen=false,forgetConfirm=false;
 private String archiveOwner="";
 private JSONObject historyDetail=null;private int scopeWatch=0,scopeGeneration=-1;
 private void resetScopedUi(){
  clearPolish("");stopAutomatic();archiveOwner="";discardLocalEditor();
  session++;replyTicket++;resetTaskIntent();reply="";replyNote="";replyError="";replyBusy=false;autoReply=false;context="";contextTitle="";contextTime=0;revision=null;preview=false;confirmDelivery=false;downloadBusy=false;artifactImage=null;artifactImageId="";editTask=false;manualTask=false;expandedContext=false;historyOpen=false;historyArchive=false;archiveOwner="";historyDetail=null;memoryOpen=false;forgetConfirm=false;tone=ChatMemory.current().tone;
 }
 private boolean syncConversation(){
  if(privateInput||DirectSend.busy()||!ChatContextService.keyboardActive||SourcePickerActivity.skipTransient(host()))return false;
  if("com.whatsapp".equals(host())){
   ChatContextService.Header h=ChatContextService.header();ChatMemory.observe(h==null?"":h.title,h==null?"":h.proof,h==null?-1:h.window,h==null?-1:h.epoch);
  }else ChatMemory.observe("手动任务 · "+host(),"",-2);
  if(scopeGeneration==ChatMemory.generation())return false;scopeGeneration=ChatMemory.generation();resetScopedUi();scheduleTaskScan();return true;
 }
 private void watchScope(){final int ticket=++scopeWatch;AgentApi.MAIN.postDelayed(new Runnable(){public void run(){if(ticket!=scopeWatch||!ChatContextService.keyboardActive)return;if(syncConversation())render();checkPolishDraft();tickAutomatic();AgentApi.MAIN.postDelayed(this,400);}},250);}
 private boolean sameWorkspace(ConversationState s,int generation){syncConversation();return s==ChatMemory.current()&&generation==ChatMemory.generation()&&ChatMemory.bound();}
 private void renderUnavailable(){
  body.addView(Ui.text(this,"等待当前聊天",19,Ui.INK,true));
  label("请在 WhatsApp 私聊输入框唤起键盘，并开启上下文辅助。识别后会自动建立独立会话，不需要选择联系人。");
  Ui.addButton(body,"返回键盘",false,()->showMode("key"));
 }
 private ConversationState historyOwner(){return !archiveOwner.isEmpty()?ChatMemory.find(archiveOwner):historyArchive?ChatMemory.legacy():ChatMemory.current();}
 private String when(long time){return time<=0?"时间未知":new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(time));}
 private JSONObject displayedResult(){return historyDetail==null?TaskState.current().result:historyDetail.optJSONObject("result");}
 private void renderHistory(){
  if(historyDetail!=null){label("历史材料 · "+when(historyDetail.optLong("at"))+(historyArchive?" · 归属待确认":" · 仅当前聊天"));Ui.addButton(body,"返回历史生成记录",false,()->{historyDetail=null;preview=false;artifactImage=null;render();});JSONObject r=displayedResult();if(r!=null){if(r.has("artifacts"))renderArtifacts();else renderResult();}return;}
  ConversationState owner=historyOwner();
  body.addView(Ui.text(this,historyArchive?"以前会话材料 · 只读":"这个会话的生成历史",20,Ui.INK,true));
  label("最近50次成功生成（含修改版本）。文件自动缓存：最长30天、全机100MB；内容记录保留，删除记录不会撤回已发送文件。");
  Ui.addButton(body,"返回当前任务",false,()->{historyOpen=false;historyArchive=false;archiveOwner="";render();});
  if(!historyArchive&&!ChatMemory.bound()){renderUnavailable();}
  else if(owner==null||owner.history.isEmpty())label("还没有生成记录。成功生成的材料会自动出现在这里。");
  else for(JSONObject record:new java.util.ArrayList<>(owner.history)){
   JSONObject r=record.optJSONObject("result");if(r==null)continue;LinearLayout card=Ui.card(this);card.addView(Ui.text(this,r.optString("title",r.optString("fileName","生成材料")),15,Ui.INK,true));
   card.addView(Ui.text(this,when(record.optLong("at"))+" · "+WorkState.kindName(record.optString("kind","auto")),11,Ui.MUTED,false));
   JSONArray files=r.optJSONArray("artifacts");if(files==null)files=new JSONArray().put(r);for(int i=0;i<files.length();i++){JSONObject f=files.optJSONObject(i);if(f!=null&&f.has("fileId"))card.addView(Ui.text(this,f.optString("fileName")+"\n"+HistoryFiles.status(this,f,record.optLong("at")),11,Ui.MUTED,false));}
   Ui.addButton(card,"查看这份材料",false,()->{historyDetail=record;forgetConfirm=false;preview=true;artifactImage=null;artifactImageId="";render();});body.addView(card);Ui.space(body,7);
  }
  if(!historyArchive)for(ConversationState archived:ChatMemory.archives()){
   Ui.addButton(body,"以前会话材料 · "+archived.label+"（"+archived.history.size()+"）",false,()->{archiveOwner=archived.id;historyArchive=true;historyDetail=null;render();});
  }
  ConversationState legacy=ChatMemory.legacy();if(!historyArchive&&legacy!=null&&!legacy.history.isEmpty())Ui.addButton(body,"查看升级前材料 · 归属待确认（"+legacy.history.size()+"）",false,()->{archiveOwner="";historyArchive=true;historyDetail=null;render();});
 }
 private void historyFooter(){
  ConversationState owner=historyOwner();JSONObject record=historyDetail;final int generation=ChatMemory.generation();
  if(historyArchive){
   label("不会把这份旧材料默认为当前联系人的内容，也不会提供一键发送。");
   if(ChatMemory.bound())Ui.addButton(body,"确认把这份材料归到当前聊天",false,()->{if(syncConversation()){render();return;}if(record==null||owner==null||!ChatMemory.bound())return;ConversationState target=ChatMemory.current();JSONObject copy=ConversationState.copy(record);boolean exists=false;for(JSONObject h:target.history)if(h.optString("id").equals(copy.optString("id")))exists=true;if(!exists)target.history.add(0,copy);while(target.history.size()>50)target.history.remove(target.history.size()-1);if(ChatMemory.save(target)){ChatMemory.deleteRecord(owner,record);historyArchive=false;archiveOwner="";historyDetail=null;}render();});
  }
  Ui.addButton(body,forgetConfirm?"确认删除这条本地生成记录":"删除这条记录…",false,()->{syncConversation();if(generation!=ChatMemory.generation()){render();return;}if(!forgetConfirm){forgetConfirm=true;render();return;}if(owner!=null&&record!=null)ChatMemory.deleteRecord(owner,record);historyDetail=null;forgetConfirm=false;render();});
 }
 private void renderMemory(){
  if(!ChatMemory.bound()){renderUnavailable();return;}ConversationState s=ChatMemory.current();final int generation=ChatMemory.generation();
  body.addView(Ui.text(this,"当前会话的独立记忆",20,Ui.INK,true));label("记忆名称："+s.label+"\n任务草稿、选定材料、上次回复和生成历史分开保存。当前窗口内自动恢复；身份不明确的新窗口独立保存，不按昵称合并。");
  editableCard("记忆备注",s.label,"给这份记忆起个名字",()->openLocalEditor(s,generation,"记忆备注",s.label,60,value->{if(value.trim().isEmpty()){toast("备注名不能为空");return false;}s.label=value.trim();ChatMemory.save(s);return true;}));
  label("开启自动建议后，偏好随当前可见消息提交；任务仍先确认再生成，不自动上传历史全文。");
  editableCard("聊天偏好",s.memory,"例如：使用英文，回复简洁",()->openLocalEditor(s,generation,"聊天偏好",s.memory,1500,value->{s.memory=value;WorkState.invalidate(s);return true;}));
  Ui.addButton(body,forgetConfirm?"确认忘记此聊天的全部本地记忆":"忘记此聊天…",false,()->{if(!sameWorkspace(s,generation)){render();return;}if(s.busy||s.canResume||s.workBusy){toast("请先取消或完成这个聊天的任务");return;}if(!forgetConfirm){forgetConfirm=true;render();return;}if(ChatMemory.deleteCurrent()){scopeGeneration=ChatMemory.generation();resetScopedUi();render();}});
 }

 private LinearLayout root,body;private String mode="key",connection="检查 AI 服务…",context="",contextTitle="",reply="",replyNote="",replyError="",tone="简洁专业";
 private boolean privateInput=false,replyBusy=false,autoReply=false,editTask=false,preview=false,downloadBusy=false,confirmDelivery=false,expandedContext=false;
 private final IntentSession intentSession=new IntentSession();
 private JSONObject taskIntent=null;private String intentError="";private boolean intentExpanded=false,intentSourceExpanded=false,manualTask=false;private int intentScanEpoch=0;
 private android.graphics.Bitmap artifactImage=null;private String artifactImageId="";
 private String editorPackage="";
 private int session=0,replyTicket=0;private long contextTime=0;private JSONObject revision=null;
 private PinyinDecoder pinyin; private KeyboardController keys; private KeyboardSurface keyboardSurface;
 private AgentApi.Pending polishRequest;private boolean polishBusy=false,polishApplying=false;
 private int polishTicket=0,polishSession=-1,polishGeneration=-1;
 private InputConnection polishConnection;private DraftEditor.Snapshot polishOriginal,polishUndoResult;
 private String polishNotice="",polishStyleLabel="";private LinearLayout polishBar;private TextView polishStatus,polishAction;
 private LocalEdit localEdit;private int navigationBottom=0,navigationLeft=0,navigationRight=0;
 private void styleNavigation(){
  if(getWindow()==null||getWindow().getWindow()==null)return;Window window=getWindow().getWindow();boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
  window.setNavigationBarColor(Ui.BG);window.setNavigationBarContrastEnforced(false);View decor=window.getDecorView();int flag=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;decor.setSystemUiVisibility(dark?decor.getSystemUiVisibility()&~flag:decor.getSystemUiVisibility()|flag);
  android.view.WindowInsetsController bars=decor.getWindowInsetsController();if(bars!=null)bars.setSystemBarsAppearance(dark?0:android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
 }
 private void keyboardPadding(){root.setPadding(navigationLeft,0,navigationRight,navigationBottom+Ui.dp(this,8));}
 private interface SaveField {boolean save(String value);}
 private final class LocalEdit {
  final ConversationState owner;final int generation,limit;final String title;final SaveField save;final EditText editor;final InputConnection connection;
  LocalEdit(ConversationState owner,int generation,String title,String value,int limit,SaveField save){
   this.owner=owner;this.generation=generation;this.title=title;this.limit=limit;this.save=save;
   editor=new EditText(AgentIme.this){
    @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);post(()->{if(localEdit!=null&&localEdit.editor==this&&keys!=null){Editable text=getText();keys.selectionChanged(getSelectionStart(),getSelectionEnd(),BaseInputConnection.getComposingSpanStart(text),BaseInputConnection.getComposingSpanEnd(text));}});}
   };
   editor.setTextSize(16);editor.setTextColor(Ui.INK);editor.setHintTextColor(Ui.MUTED);editor.setHint("在这里输入…");editor.setContentDescription("内部编辑："+title);
   editor.setShowSoftInputOnFocus(false);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
   editor.setGravity(Gravity.TOP|Gravity.START);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});editor.setText(value);editor.setSelection(editor.length());
   editor.setPadding(Ui.dp(AgentIme.this,14),Ui.dp(AgentIme.this,10),Ui.dp(AgentIme.this,14),Ui.dp(AgentIme.this,10));editor.setBackground(Ui.bg(Ui.SURFACE,Ui.dp(AgentIme.this,16)));
   // Explicit local Editable. Never route a missing/closed field to the host's InputConnection.
   connection=new BaseInputConnection(editor,true){@Override public Editable getEditable(){return editor.getText();}};
  }
 }
 private void refreshKeyboard(){if(keyboardSurface!=null)keyboardSurface.refresh();}
 private void bindHostKeyboard(){
  if(keys==null)return;EditorInfo info=getCurrentInputEditorInfo();int type=info==null?0:info.inputType;int cls=type&InputType.TYPE_MASK_CLASS;
  keys.bind(new KeyboardController.Target(){public InputConnection connection(){return localEdit==null?getCurrentInputConnection():null;}public boolean internal(){return false;}public void done(){}public void changed(){hostKeyboardChanged();}},cls==InputType.TYPE_CLASS_NUMBER||cls==InputType.TYPE_CLASS_PHONE,privateInput);
 }
 // Android may show the same editor again without another onStartInput callback.
 // Rebind only a detached controller: do not reset an active preedit or local editor.
 private void ensureHostKeyboard(){if(localEdit==null&&keys!=null&&!keys.isBound())bindHostKeyboard();}
 private void discardLocalEditor(){if(localEdit==null)return;if(keys!=null)keys.detach();localEdit=null;keyboardSurface=null;bindHostKeyboard();}
 private void openLocalEditor(ConversationState owner,int generation,String title,String value,int limit,SaveField save){
  if(!sameWorkspace(owner,generation)||privateInput){toast("聊天已变化，请重新打开编辑");render();return;}
  stopAutomatic();ChatContextService.clear();keys.finishBest();localEdit=new LocalEdit(owner,generation,title,value,limit,save);final LocalEdit target=localEdit;
  keys.bind(new KeyboardController.Target(){public InputConnection connection(){return localEdit==target&&target.owner.alive&&ChatMemory.current()==target.owner&&ChatMemory.generation()==target.generation?target.connection:null;}public boolean internal(){return true;}public void done(){finishLocalEditor();}public void changed(){refreshKeyboard();}},false,false);
  renderLocalEditor();
 }
 private void finishLocalEditor(){
  LocalEdit edit=localEdit;if(edit==null)return;
  if(!sameWorkspace(edit.owner,edit.generation)||localEdit!=edit){toast("聊天已变化，未保存本次编辑");render();return;}
  keys.finishBest();String value=edit.editor.getText().toString();
  if(!edit.save.save(value))return;
  discardLocalEditor();render();
 }
 private void renderLocalEditor(){
  if(root==null||localEdit==null)return;LocalEdit edit=localEdit;root.removeAllViews();keyboardSurface=null;keyboardPadding();
  LinearLayout head=Ui.row(this);head.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
  head.addView(KeyboardSurface.iconButton(this,"back","取消编辑",v->{discardLocalEditor();render();}),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
  TextView title=Ui.text(this,edit.title,16,Ui.INK,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button done=Ui.button(this,"完成",true,()->finishLocalEditor());head.addView(done,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,40)));root.addView(head);
  LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
  if(edit.editor.getParent()!=null)((ViewGroup)edit.editor.getParent()).removeView(edit.editor);
  boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
  if(landscape)head.setLayoutParams(new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));
  box.addView(edit.editor,new LinearLayout.LayoutParams(-1,Ui.dp(this,landscape?48:104)));
  LinearLayout assist=Ui.row(this);TextView counter=Ui.text(this,edit.editor.length()+" / "+edit.limit,11,Ui.MUTED,false);assist.addView(counter,new LinearLayout.LayoutParams(0,-2,1));
  TextView paste=Ui.text(this,"粘贴",13,Ui.ACCENT,true);paste.setGravity(Gravity.CENTER);paste.setContentDescription("粘贴到当前字段");paste.setBackground(Ui.touch(this,Ui.BG,16));paste.setOnClickListener(v->{if(localEdit!=edit||!sameWorkspace(edit.owner,edit.generation))return;String text=clipboard();if(text.isEmpty()){toast("剪贴板没有文字");return;}keys.punctuation(text);});assist.addView(paste,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,landscape?32:48)));box.addView(assist);root.addView(box);
  edit.editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int f){}public void onTextChanged(CharSequence x,int a,int b,int c){counter.setText(x.length()+" / "+edit.limit);}public void afterTextChanged(Editable x){}});
  keyboardSurface=new KeyboardSurface(this,keys,keyboardActions,true);root.addView(keyboardSurface);root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,-2));edit.editor.requestFocus();
 }
 private void editableCard(String title,String value,String hint,Runnable open){
  LinearLayout card=Ui.card(this);LinearLayout heading=Ui.row(this);heading.addView(Ui.text(this,title,12,Ui.MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
  ImageView icon=new ImageView(this);icon.setImageDrawable(new KeyboardIcon("edit",Ui.MUTED));heading.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,20),Ui.dp(this,20)));card.addView(heading);Ui.space(card,8);
  TextView text=Ui.text(this,value.isEmpty()?hint:value,16,value.isEmpty()?Ui.MUTED:Ui.INK,false);text.setMaxLines(4);text.setEllipsize(TextUtils.TruncateAt.END);card.addView(text);card.setContentDescription("编辑"+title);card.setMinimumHeight(Ui.dp(this,84));card.setBackground(Ui.touch(this,Ui.SURFACE,20));card.setOnClickListener(v->open.run());
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=Ui.dp(this,8);p.bottomMargin=Ui.dp(this,8);body.addView(card,p);
 }
 private final KeyboardSurface.Actions keyboardActions=new KeyboardSurface.Actions(){public void work(){showMode("do");}public void polish(){startPolish();}public void polishSettings(){showMode("polishSettings");}public void more(View anchor){showMore(anchor);}public void switchIme(){AgentIme.this.switchIme();}};
 private void showMode(String next){
  if(privateInput&&!"key".equals(next)&&!"more".equals(next)){toast("安全输入：AI 已禁用");return;}discardLocalEditor();keys.finishBest();
  if(!mode.equals(next)){clearPolish("");resetTaskIntent();autoReply=false;replyTicket++;replyBusy=false;}mode=next;if(!"key".equals(next)&&!"reply".equals(next)){stopAutomatic();ChatContextService.clear();}historyOpen=false;memoryOpen=false;historyDetail=null;editTask=false;manualTask=false;scheduleTaskScan();render();
 }
 // Keep secondary actions in the IME window. A focusable PopupMenu can restart the host editor.
 private void showMore(View anchor){showMode("more");}
 // Polishing acts only on a user-requested host draft; it never reads chat history.
 private boolean polishEligible(){
  EditorInfo info=getCurrentInputEditorInfo();
  return info!=null&&(info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT
   &&(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0&&!privateInput&&localEdit==null
   &&"key".equals(mode)&&ChatContextService.keyboardActive&&!DirectSend.busy()&&keys!=null&&keys.isBound()&&keys.composition().isEmpty();
 }
 private boolean polishOwnerMatches(){
  return polishEligible()&&polishSession==session&&polishGeneration==ChatMemory.generation()
   &&polishConnection!=null&&polishConnection==getCurrentInputConnection();
 }
 private void clearPolish(String notice){
  polishTicket++;if(polishRequest!=null){polishRequest.cancel();polishRequest=null;}
  polishBusy=false;polishOriginal=null;polishUndoResult=null;polishConnection=null;polishNotice=notice;updatePolishUi();
 }
 private void checkPolishDraft(){
  if(polishApplying||(!polishBusy&&polishUndoResult==null))return;
  if(!polishOwnerMatches()){clearPolish("");return;}
  DraftEditor.Snapshot now=DraftEditor.read(polishConnection,DraftEditor.MAX_OUTPUT);
  if(polishBusy&&!polishOriginal.sameState(now))clearPolish("草稿已变化，未替换");
  else if(polishUndoResult!=null&&!polishUndoResult.sameText(now))clearPolish("");
 }
 private void hostKeyboardChanged(){checkPolishDraft();refreshKeyboard();}
 private void startPolish(){
  if(polishBusy)return;syncConversation();ensureHostKeyboard();
  if(!polishEligible()){toast("请在普通聊天输入框完成文字输入后再润色");return;}
  if("com.whatsapp".equals(host())&&!ChatMemory.bound()){toast("当前聊天尚未确认，请稍后重试");return;}
  InputConnection ic=getCurrentInputConnection();DraftEditor.Snapshot source=DraftEditor.read(ic,DraftEditor.MAX_INPUT);
  if(source==null){toast("暂不能安全读取完整草稿；仅支持 4,000 字以内的纯文字");return;}
  if(source.text.codePoints().allMatch(Character::isWhitespace)){toast("先在聊天输入框写点内容，再点 AI 润色");return;}
  clearPolish("");stopAutomatic();polishConnection=ic;polishOriginal=source;polishSession=session;polishGeneration=ChatMemory.generation();
  polishStyleLabel=PolishStyle.label(this);polishBusy=true;polishNotice="正在按「"+polishStyleLabel+"」润色…";final int ticket=++polishTicket;updatePolishUi();
  polishRequest=AgentApi.callCancelable(this,"/polish",AgentApi.obj("text",source.text,"style",PolishStyle.id(this)),(data,error)->{
   syncConversation();if(ticket!=polishTicket)return;polishRequest=null;
   if(!polishOwnerMatches()){clearPolish("");return;}
   if(!source.sameState(DraftEditor.read(ic,DraftEditor.MAX_OUTPUT))){clearPolish("草稿已变化，未替换");return;}
   if(error!=null){clearPolish("润色失败，原文未改");toast(error);return;}
   Object raw=data==null?null:data.opt("text");String value=raw instanceof String?(String)raw:null;
   if(!DraftEditor.validText(value,DraftEditor.MAX_OUTPUT)||value.codePoints().allMatch(Character::isWhitespace)){clearPolish("结果无效，原文未改");return;}
   if(value.equals(source.text)){clearPolish("表达已经自然，无需替换");return;}
   polishApplying=true;DraftEditor.Result result;
   try{result=DraftEditor.replace(ic,source,value,()->ticket==polishTicket&&polishOwnerMatches());}finally{polishApplying=false;}
   polishBusy=false;
   if(result==DraftEditor.Result.APPLIED){
    polishUndoResult=DraftEditor.read(ic,DraftEditor.MAX_OUTPUT);
    polishNotice="已润色 · "+polishStyleLabel+" · 未发送";updatePolishUi();
    AgentApi.MAIN.postDelayed(()->{if(ticket==polishTicket)clearPolish("");},45000);
   }else clearPolish(result==DraftEditor.Result.UNCERTAIN?"输入框未完整确认结果，请检查草稿":result==DraftEditor.Result.STALE?"草稿已变化，未替换":"未能替换，原文未改");
  });
 }
 private void undoPolish(){
  syncConversation();if(polishUndoResult==null||polishOriginal==null)return;
  if(!polishOwnerMatches()){clearPolish("");return;}
  InputConnection ic=polishConnection;DraftEditor.Snapshot current=DraftEditor.read(ic,DraftEditor.MAX_OUTPUT);
  if(!polishUndoResult.sameText(current)){clearPolish("草稿已变化，不再撤销");return;}
  final int ticket=polishTicket;polishApplying=true;DraftEditor.Result result;
  try{result=DraftEditor.replace(ic,current,polishOriginal.text,()->ticket==polishTicket&&polishOwnerMatches());}finally{polishApplying=false;}
  clearPolish(result==DraftEditor.Result.APPLIED?"已恢复原文 · 未发送":result==DraftEditor.Result.UNCERTAIN?"请检查输入框，未能确认撤销结果":"草稿已变化，未撤销");
 }
 private void createPolishBar(){
  polishBar=Ui.row(this);polishBar.setPadding(Ui.dp(this,16),0,Ui.dp(this,8),0);
  polishStatus=Ui.text(this,"",12,Ui.MUTED,false);polishStatus.setMaxLines(2);polishStatus.setEllipsize(TextUtils.TruncateAt.END);polishBar.addView(polishStatus,new LinearLayout.LayoutParams(0,-2,1));
  polishAction=Ui.text(this,"",14,Ui.ACCENT,true);polishAction.setGravity(Gravity.CENTER);polishAction.setBackground(Ui.touch(this,Ui.BG,18));
  polishAction.setOnClickListener(v->{if(polishBusy)clearPolish("已取消，原文未改");else if(polishUndoResult!=null)undoPolish();else clearPolish("");});
  polishBar.addView(polishAction,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));root.addView(polishBar,new LinearLayout.LayoutParams(-1,-2));updatePolishUi();
 }
 private void updatePolishUi(){
  if(keyboardSurface!=null)keyboardSurface.setPolishBusy(polishBusy);
  if(polishBar==null)return;boolean visible=!privateInput&&localEdit==null&&!polishNotice.isEmpty();polishBar.setVisibility(visible?View.VISIBLE:View.GONE);
  if(!visible)return;polishStatus.setText(polishNotice);String action=polishBusy?"取消":polishUndoResult!=null?"撤销":"知道了";polishAction.setText(action);polishAction.setContentDescription(polishBusy?"取消 AI 润色":polishUndoResult!=null?"撤销 AI 润色":"关闭润色提示");
 }
 private void renderPolishSettings(){
  body.addView(Ui.text(this,"让表达更贴近你的风格",20,Ui.INK,true));label("默认自然。选好一次，下次点击 AI 润色直接使用；只润色，不扩写。");Ui.space(body,12);
  int selected=PolishStyle.index(this);
  for(int i=0;i<PolishStyle.IDS.length;i++){
   final int index=i;LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));row.setBackground(Ui.touch(this,selected==i?Ui.LIGHT:Ui.SURFACE,16));
   LinearLayout text=Ui.column(this);text.addView(Ui.text(this,PolishStyle.LABELS[i],16,Ui.INK,true));text.addView(Ui.text(this,PolishStyle.DETAILS[i],12,Ui.MUTED,false));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
   TextView mark=Ui.text(this,selected==i?"✓":"○",20,Ui.ACCENT,true);mark.setGravity(Gravity.CENTER);row.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,32),-1));
   row.setContentDescription("润色风格 "+PolishStyle.LABELS[i]);row.setSelected(selected==i);row.setOnClickListener(v->{PolishStyle.select(this,index);render();});
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(this,8);row.setMinimumHeight(Ui.dp(this,72));body.addView(row,lp);
  }
  label("点击 AI 润色后，仅当前输入框文案会经"+AgentApi.routeName(this)+"交给已配置的模型，不附带聊天记录。不新增事实或承诺，请核对后自行发送。");
  label("润色成功后，撤销所需原文仅在内存保留，最长 45 秒。继续编辑、切换聊天或收起键盘后清除；不会把文案保存到记忆或历史材料。长按 AI 润色也可打开此设置。");
 }

 private void renderMore(){
  if(!privateInput){
   moreRow("spark","帮我回",()->showMode("reply"));
   moreRow("history","历史生成记录",()->{stopAutomatic();resetTaskIntent();historyOpen=true;historyArchive=false;archiveOwner="";memoryOpen=false;historyDetail=null;mode="do";render();});
   moreRow("edit","当前聊天记忆",()->{resetTaskIntent();memoryOpen=true;historyOpen=false;forgetConfirm=false;mode="do";render();});
   moreRow("edit","自动回复建议 · "+(ChatContextService.automaticConsent(this)?"已开启":"未开启"),()->showMode("autoPrivacy"));
   moreRow("edit","AI 润色设置 · "+PolishStyle.label(this),()->showMode("polishSettings"));
   moreRow("paste","粘贴到输入框",()->{keys.punctuation(clipboard());showMode("key");});
  }
  moreRow("language","切换输入法",()->{keys.finishBest();switchIme();});
  Ui.space(body,16);label(privateInput?"安全输入：AI、记忆和剪贴板入口已关闭。":"拼音在本机处理。自动建议开启后，仅当前可见聊天交给模型；点建议才填入，不自动发送。");
  label(connection);if(!ChatMemory.storageError.isEmpty())label(ChatMemory.storageError);
 }
 private void moreRow(String icon,String title,Runnable run){
  LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);row.setBackground(Ui.touch(this,Ui.SURFACE,16));
  ImageView glyph=new ImageView(this);glyph.setImageDrawable(new KeyboardIcon(icon,Ui.MUTED));row.addView(glyph,new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)));
  TextView text=Ui.text(this,title,16,Ui.INK,false);text.setPadding(Ui.dp(this,16),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription(title);row.setOnClickListener(v->run.run());
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));lp.bottomMargin=Ui.dp(this,8);body.addView(row,lp);
 }
 @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd){super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);if(localEdit==null&&keys!=null)keys.selectionChanged(newStart,newEnd,candidatesStart,candidatesEnd);checkPolishDraft();}
 @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK&&(localEdit!=null||!"key".equals(mode))){event.startTracking();return true;}return super.onKeyDown(code,event);}
 @Override public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK&&event.isTracking()&&!event.isCanceled()){if(localEdit!=null){discardLocalEditor();render();return true;}if(!"key".equals(mode)){showMode("key");return true;}}return super.onKeyUp(code,event);}

 @Override public boolean onEvaluateFullscreenMode(){return false;}
 @Override public View onCreateInputView(){TaskState.restore(this);Ui.theme(this);root=Ui.column(this);root.setBackgroundColor(Ui.BG);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());navigationBottom=Math.max(nav.bottom,Ui.dp(this,48));navigationLeft=nav.left;navigationRight=nav.right;keyboardPadding();return insets;});render();return root;}
 @Override public void onStartInput(EditorInfo info,boolean restarting){boolean continuing=ConversationPolicy.sameEditorRestart(restarting,ChatContextService.keyboardActive,editorPackage,info.packageName);super.onStartInput(info,restarting);clearPolish("");stopAutomatic();discardLocalEditor();if(!continuing)mode="key";editorPackage=info.packageName;TaskState.restore(this);DirectSend.inputHidden();if(!continuing&&!DirectSend.busy()&&!SourcePickerActivity.pending()){if(!"com.whatsapp".equals(info.packageName))ChatMemory.detach();scopeGeneration=ChatMemory.generation();resetScopedUi();}resetTaskIntent();manualTask=false;session++;replyTicket++;reply="";context="";contextTitle="";replyError="";replyBusy=false;autoReply=false;ChatContextService.clear();int type=info.inputType;int v=type&InputType.TYPE_MASK_VARIATION;int cls=type&InputType.TYPE_MASK_CLASS;privateInput=(cls==InputType.TYPE_CLASS_TEXT&&(v==InputType.TYPE_TEXT_VARIATION_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))||(cls==InputType.TYPE_CLASS_NUMBER&&v==InputType.TYPE_NUMBER_VARIATION_PASSWORD);if(privateInput){mode="key";if(ChatMemory.bound())ChatMemory.detach();}bindHostKeyboard();}
 @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);ensureHostKeyboard();ChatContextService.keyboardActive=true;if(SourcePickerActivity.resume(this,host()))mode="do";syncConversation();watchScope();scheduleTaskScan();TaskState.changed=()->{if(root!=null)render();};render();AgentApi.call(this,"GET","/health",null,(d,e)->{connection=e==null?AgentApi.serviceName(this)+"在线 · "+d.optString("model"):AgentApi.unavailable(this);if(root!=null)render();});}
 @Override public void onFinishInputView(boolean finishing){clearPolish("");DirectSend.inputHidden();stopAutomatic();resetTaskIntent();ChatContextService.keyboardActive=false;ChatContextService.clear();autoReply=false;replyTicket++;replyBusy=false;context="";contextTitle="";reply="";TaskState.changed=null;scopeWatch++;if(!DirectSend.busy()&&!SourcePickerActivity.pending()){if("com.whatsapp".equals(host()))ChatMemory.suspend();else ChatMemory.detach();}discardLocalEditor();if(keys!=null)keys.detach();keyboardSurface=null;super.onFinishInputView(finishing);}
 @Override public void onFinishInput(){clearPolish("");super.onFinishInput();}
 @Override public void onDestroy(){clearPolish("");if(live==this)live=null;stopAutomatic();scopeWatch++;ChatMemory.detach();resetTaskIntent();TaskState.changed=null;ChatContextService.keyboardActive=false;ChatContextService.clear();if(keys!=null)keys.detach();if(pinyin!=null)pinyin.dispose();super.onDestroy();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 private String host(){EditorInfo i=getCurrentInputEditorInfo();return i==null?"":i.packageName;}
 private void switchIme(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();}
 private void label(String s){body.addView(Ui.text(this,s,12,Ui.MUTED,false));}
 private void smallRow(String a,Runnable ar,String b,Runnable br){LinearLayout row=Ui.row(this);Button x=Ui.button(this,a,false,ar),y=Ui.button(this,b,false,br);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);lp.setMargins(0,Ui.dp(this,7),Ui.dp(this,6),Ui.dp(this,6));row.addView(x,lp);row.addView(y,new LinearLayout.LayoutParams(lp));body.addView(row);}
 private void render(){
  if(root==null)return;syncConversation();Ui.theme(this);
  // Background status/results must never replace a local Editable or its cursor/composition.
  if(localEdit!=null)return;
  if("key".equals(mode)&&keyboardSurface!=null&&keyboardSurface.getParent()==root&&suggestionBand!=null){updateSuggestionBand();updatePolishUi();return;}
  root.removeAllViews();polishBar=null;polishStatus=null;polishAction=null;suggestionBand=null;suggestionStatus=null;suggestionText=null;keyboardSurface=null;root.setBackgroundColor(Ui.BG);keyboardPadding();styleNavigation();
  if("key".equals(mode)){
   createSuggestionBand();createPolishBar();
   keyboardSurface=new KeyboardSurface(this,keys,keyboardActions,false);keyboardSurface.setPolishBusy(polishBusy);root.addView(keyboardSurface);root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,-2));return;
  }
  int screenDp=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
  int height=Math.min(470,(int)(screenDp*.58));root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,Ui.dp(this,height)));
  LinearLayout head=Ui.row(this);head.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);
  head.addView(KeyboardSurface.iconButton(this,"keyboard","返回键盘",v->showMode("key")),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
  LinearLayout titles=Ui.column(this);titles.addView(Ui.text(this,historyOpen?"生成历史":memoryOpen?"聊天记忆":"more".equals(mode)?"更多":"polishSettings".equals(mode)?"AI 润色设置":"autoPrivacy".equals(mode)?"自动回复建议":"reply".equals(mode)?"帮我回":"帮我做",16,Ui.INK,true));
  String name="polishSettings".equals(mode)?"只处理当前草稿 · 不自动发送":privateInput?"安全输入 · AI 已关闭":ChatMemory.bound()?ChatMemory.current().label:ChatMemory.visibleTitle().isEmpty()?"等待识别当前聊天":"当前会话 · "+ChatMemory.visibleTitle();TextView subtitle=Ui.text(this,name,11,Ui.MUTED,false);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);titles.addView(subtitle);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  if(!historyOpen&&!"more".equals(mode)&&!"polishSettings".equals(mode))head.addView(KeyboardSurface.iconButton(this,"history","历史生成记录",v->{resetTaskIntent();historyOpen=true;historyArchive=false;archiveOwner="";historyDetail=null;memoryOpen=false;render();}),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
  if(!"more".equals(mode))head.addView(KeyboardSurface.iconButton(this,"more","更多键盘选项",this::showMore),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));root.addView(head);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);body=Ui.column(this);body.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,16));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if("more".equals(mode))renderMore();else if("polishSettings".equals(mode))renderPolishSettings();else if("autoPrivacy".equals(mode))renderAutoPrivacy();else if(historyOpen)renderHistory();else if(memoryOpen)renderMemory();else if(!ChatMemory.bound())renderUnavailable();else if("reply".equals(mode))renderReply();else renderTask();
 }
 private String draft(){InputConnection ic=getCurrentInputConnection();if(ic==null)return "";CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0)return selected.toString();CharSequence before=ic.getTextBeforeCursor(5000,0),after=ic.getTextAfterCursor(5000,0);return (before==null?"":before.toString())+(after==null?"":after.toString());}
 private String clipboard(){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(!cm.hasPrimaryClip())return "";ClipData c=cm.getPrimaryClip();if(c==null||c.getItemCount()==0)return "";CharSequence t=c.getItemAt(0).getText();return t==null?"":t.toString();}
 private void useContext(String s,String source){if(s.trim().isEmpty()){toast("没有可用文字");return;}if(s.length()>10000){toast("上下文超过 10,000 字，请缩短");return;}context=s;expandedContext=false;contextTitle=source;contextTime=System.currentTimeMillis();reply="";replyError="";replyTicket++;autoReply=false;render();}
 private void renderReply(){
  automaticPanelSignature=automatic.phase+":"+automaticLabel();
  if("com.whatsapp".equals(host())&&!contextTitle.equals("手动粘贴")){
   if(!ChatContextService.automaticConsent(this)){renderAutoPrivacy();return;}
   body.addView(Ui.text(this,"建议自动跟上当前对话",19,Ui.INK,true));
   label("返回键盘后建议仍在键盘上方。无需手动读取；点选才填入，不发送。");
   if(automatic.phase==AutoReplyState.Phase.READY){LinearLayout card=Ui.card(this);card.addView(Ui.text(this,automatic.reply,16,Ui.INK,false));body.addView(card);if(!automatic.note.codePoints().allMatch(Character::isWhitespace))label(automatic.note);Ui.addButton(body,"填入聊天输入框 · 不发送",true,this::insertAutomaticReply);}
   else label(automaticLabel());
   Ui.addButton(body,"语气："+tone,false,()->{tone=tone.equals("简洁专业")?"友好自然":tone.equals("友好自然")?"礼貌英文":"简洁专业";ChatMemory.current().tone=tone;ChatMemory.save(ChatMemory.current());tickAutomatic();render();});
   Ui.addButton(body,"粘贴其他上下文…",false,()->{stopAutomatic();useContext(clipboard(),"手动粘贴");});return;
  }
  if("com.whatsapp".equals(host()))Ui.addButton(body,"返回自动建议",false,()->{context="";contextTitle="";tickAutomatic();render();});
  body.addView(Ui.text(this,"接住对话，帮你组织语言",19,Ui.INK,true));Ui.space(body,5);
  label("只读当前屏幕可见消息；先预览，再交给模型。");if(!ChatMemory.current().memory.codePoints().allMatch(Character::isWhitespace))label("本聊天偏好也将提交："+ChatMemory.current().memory);Ui.addButton(body,"粘贴上下文",false,()->useContext(clipboard(),"手动粘贴"));
  if(context.isEmpty()){if(!ChatMemory.current().lastReply.isEmpty()){label("上次生成的回复 · "+when(ChatMemory.current().replyAt)+"（回看用；重新读取生成后才能填入）");body.addView(Ui.text(this,ChatMemory.current().lastReply,14,Ui.INK,false));}LinearLayout card=Ui.card(this);card.addView(Ui.text(this,"尚未读取聊天",16,Ui.INK,true));card.addView(Ui.text(this,"不会扫描全部历史或联系人。也可以把一段对话复制后，点「粘贴上下文」。",13,Ui.MUTED,false));body.addView(card);return;}
  LinearLayout card=Ui.card(this);card.addView(Ui.text(this,"上下文 · "+contextTitle,12,Ui.MUTED,true));TextView txt=Ui.text(this,context,13,Ui.INK,false);if(!expandedContext)txt.setMaxLines(3);card.addView(txt);body.addView(card);Ui.addButton(body,expandedContext?"收起上下文":"查看将提交的完整上下文（"+context.length()+" 字）",false,()->{expandedContext=!expandedContext;render();});
  smallRow("语气："+tone,()->{tone=tone.equals("简洁专业")?"友好自然":tone.equals("友好自然")?"礼貌英文":"简洁专业";ChatMemory.current().tone=tone;ChatMemory.save(ChatMemory.current());render();},"清除上下文",()->{ChatMemory.current().replyEpoch++;context="";reply="";autoReply=false;replyTicket++;ChatContextService.clear();render();});
  if(replyBusy){label("正在等待真实模型回复…");Ui.addButton(body,"停止接收回复",false,()->{ChatMemory.current().replyEpoch++;replyTicket++;replyBusy=false;autoReply=false;render();});}
  else{Ui.addButton(body,"确认将以上内容交给模型 · 生成回复",true,()->generateReply());}
  if(!replyError.isEmpty())body.addView(Ui.text(this,replyError,13,Ui.ERROR,false));
  if(!reply.isEmpty()){
   Ui.space(body,9);LinearLayout result=Ui.card(this);result.addView(Ui.text(this,reply,15,Ui.INK,false));if(!replyNote.isEmpty())result.addView(Ui.text(this,replyNote,11,Ui.MUTED,false));body.addView(result);
   Ui.addButton(body,"填入聊天输入框 · 不发送",true,()->insertReply());
  }
 }
 private void generateReply(){
  if(syncConversation()){render();return;}if(privateInput||context.isEmpty()||replyBusy||!ChatMemory.bound())return;
  final ConversationState owner=ChatMemory.current();final int generation=ChatMemory.generation();
  final int current=session,ticket=++replyTicket,ownerTicket=++owner.replyEpoch;final String expectedTitle=contextTitle,expectedText=context;
  replyBusy=true;reply="";replyError="";render();
  AgentApi.call(this,"POST","/reply",AgentApi.obj("context",owner.memory.codePoints().allMatch(Character::isWhitespace)?context:"用户确认的风格偏好（不是聊天事实）：\n"+owner.memory+"\n\n当前可见聊天：\n"+context,"tone",tone),(d,e)->{
   if(!sameWorkspace(owner,generation)||current!=session||ticket!=replyTicket)return;replyBusy=false;
   if(!expectedTitle.equals("手动粘贴")&&"com.whatsapp".equals(host())){ChatContextService.Snapshot now=ChatContextService.capture();if(now==null||!now.title.equals(expectedTitle)||!now.text.equals(expectedText)){replyError="聊天或可见消息已变化，已丢弃旧建议；请重新读取";reply="";render();return;}}
   if(e==null)TaskState.publishReply(owner,ownerTicket,d.optString("reply"));
   if(e!=null)replyError=e;else{reply=d.optString("reply");replyNote=d.optString("note");JSONObject m=d.optJSONObject("meta");if(m!=null)connection=m.optString("model")+" · "+(m.optLong("latencyMs")/1000.0)+"s";}render();
  });
 }
 private void insertReply(){
  if(syncConversation()){render();return;}if(privateInput||reply.isEmpty()||!ChatMemory.bound())return;
  if(System.currentTimeMillis()-contextTime>300000){toast("上下文已超过 5 分钟，请重新读取并生成");return;}
  if(!contextTitle.equals("手动粘贴")&&"com.whatsapp".equals(host())){ChatContextService.Snapshot now=ChatContextService.capture();if(now==null||!now.title.equals(contextTitle)||!now.text.equals(context)){toast("聊天或可见消息已变化，请重新读取并生成");return;}}
  InputConnection ic=getCurrentInputConnection();if(ic==null)return;
  CharSequence selection=ic.getSelectedText(0);if(selection!=null&&selection.length()>0){toast("当前选中了文字，请先取消选中，避免覆盖草稿");return;}
  ensureHostKeyboard();if(keys.insertSuggestion(reply)){showMode("key");toast("已填入，请核对后自行发送");}else toast("当前正在输入或连接已变化，请完成输入后重试");
 }
 // Automatic replies are independent of manual/task drafts and never own the host editor.
 private final AutoReplyState automatic=new AutoReplyState();
 private AgentApi.Pending automaticRequest;
 private ChatContextService.Snapshot automaticSnapshot;
 private LinearLayout suggestionBand;private TextView suggestionStatus,suggestionText;
 private String automaticPanelSignature="",automaticReason="等待当前可见消息";
 private long monotonic(){return android.os.SystemClock.elapsedRealtime();}
 private void stopAutomatic(){
  automatic.stop();automaticSnapshot=null;
  if(automaticRequest!=null){automaticRequest.cancel();automaticRequest=null;}
 }
 private boolean automaticEligible(ChatContextService.Header h){
  return !polishBusy&&ChatMemory.bound()&&ChatContextService.connected()&&AutoReplyState.allowed(ChatContextService.automaticConsent(this),ChatContextService.keyboardActive,"com.whatsapp".equals(host()),h!=null&&h.privateChat,privateInput,localEdit!=null,DirectSend.busy(),!historyOpen&&!memoryOpen&&("key".equals(mode)||("reply".equals(mode)&&!contextTitle.equals("手动粘贴"))));
 }
 private String automaticKey(ChatContextService.Snapshot snapshot){
  if(snapshot==null)return "";
  String raw=ChatMemory.current().id+"\u0000"+snapshot.window+":"+snapshot.epoch+"\u0000"+snapshot.text+"\u0000"+tone+"\u0000"+ChatMemory.current().memory;
  try{StringBuilder hex=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hex.append(String.format("%02x",b&255));return hex.toString();}catch(Exception e){throw new IllegalStateException(e);}
 }
 private void tickAutomatic(){
  if(root==null)return;
  ChatContextService.Header header=ChatContextService.header();
  if(!automaticEligible(header)){
   automaticReason=!ChatContextService.connected()?"需要开启 WhatsApp 上下文辅助":header==null?"等待识别当前聊天":!header.privateChat?"暂不能确认是私聊 · 不自动读取":"自动建议已暂停";
   stopAutomatic();if(!mayScanTask())ChatContextService.clear();paintAutomatic();return;
  }
  ChatContextService.armed=true;ChatContextService.Snapshot snapshot=ChatContextService.capture();
  if(snapshot==null||!snapshot.privateChat){automaticReason="当前没有可见文字消息";stopAutomatic();paintAutomatic();return;}
  if(snapshot.window!=header.window||snapshot.epoch!=header.epoch||!snapshot.title.equals(header.title)){stopAutomatic();paintAutomatic();return;}
  String key=automaticKey(snapshot);int previous=automatic.ticket;automatic.observe(key,monotonic());automaticSnapshot=snapshot;
  if(previous!=automatic.ticket&&automaticRequest!=null){automaticRequest.cancel();automaticRequest=null;}
  if(automatic.canStart(monotonic())){
   final ConversationState owner=ChatMemory.current();final int generation=ChatMemory.generation();
   final int ticket=automatic.start(monotonic()),ownerTicket=++owner.replyEpoch;final String expected=key;
   // No history, host draft, attachment or background message is part of this request.
   String input=owner.memory.codePoints().allMatch(Character::isWhitespace)?snapshot.text:"用户填写的风格偏好（不是聊天事实）：\n"+owner.memory+"\n\n当前可见聊天：\n"+snapshot.text;
   automaticRequest=AgentApi.callCancelable(this,"/reply",AgentApi.obj("context",input,"tone",tone,"automatic",true),(data,error)->{
    if(!sameWorkspace(owner,generation)||!automaticEligible(ChatContextService.header())||ticket!=automatic.ticket)return;
    ChatContextService.Snapshot now=ChatContextService.capture();
    if(now==null||!now.privateChat||!expected.equals(automaticKey(now))){tickAutomatic();return;}
    automaticRequest=null;
    if(automatic.complete(ticket,expected,data==null?null:data.optString("reply"),data==null?"":data.optString("note"),error,monotonic())){
     if(automatic.phase==AutoReplyState.Phase.READY){TaskState.publishReply(owner,ownerTicket,automatic.reply);automaticSnapshot=now;}
     paintAutomatic();
    }
   });
  }
  paintAutomatic();
 }
 private String automaticLabel(){
  switch(automatic.phase){
   case LOADING:return "AI 正在理解对话…";
   case WAITING:return "正在分析当前可见消息…";
   case READY:return automatic.reply;
   case LIMITED:return "消息更新较快，稍后自动继续";
   case ERROR:return "暂时无法生成建议 · 30 秒后自动重试";
   default:return automaticReason;
  }
 }
 private void paintAutomatic(){
  if(localEdit!=null)return;
  if("key".equals(mode)){updateSuggestionBand();return;}
  if("reply".equals(mode)&&!contextTitle.equals("手动粘贴")){
   String next=automatic.phase+":"+automaticLabel();if(!next.equals(automaticPanelSignature)){automaticPanelSignature=next;render();}
  }
 }
 private void createSuggestionBand(){
  suggestionBand=Ui.column(this);boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
  suggestionBand.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,6));suggestionBand.setBackground(Ui.touch(this,Ui.SURFACE,16));
  suggestionStatus=Ui.text(this,"AI 自动建议",12,Ui.ACCENT,true);suggestionStatus.setSingleLine(true);suggestionBand.addView(suggestionStatus);
  suggestionText=Ui.text(this,"",14,Ui.INK,false);suggestionText.setMaxLines(landscape?1:2);suggestionText.setEllipsize(TextUtils.TruncateAt.END);suggestionBand.addView(suggestionText,new LinearLayout.LayoutParams(-1,0,1));
  int scaled=(int)Math.ceil((landscape?48:66)*Math.min(getResources().getConfiguration().fontScale,1.4f));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,Math.max(landscape?56:76,scaled)));lp.setMargins(Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,4));root.addView(suggestionBand,lp);
  suggestionBand.setOnClickListener(v->{if(!ChatContextService.automaticConsent(this))showMode("autoPrivacy");else if(automatic.phase==AutoReplyState.Phase.READY)insertAutomaticReply();});updateSuggestionBand();
 }
 private void updateSuggestionBand(){
  if(suggestionBand==null)return;
  boolean visible=!privateInput&&localEdit==null&&"com.whatsapp".equals(host());suggestionBand.setVisibility(visible?View.VISIBLE:View.GONE);if(!visible)return;
  boolean enabled=ChatContextService.automaticConsent(this),ready=enabled&&automatic.phase==AutoReplyState.Phase.READY;
  String text=enabled?automaticLabel():"开启后，回复建议自动出现在这里";
  String caption=enabled?(ready?"AI 建议 · 点按填入，不发送":"AI 自动建议"):"开启自动回复建议";
  suggestionStatus.setText(caption);suggestionText.setText(text);suggestionText.setTextColor(ready?Ui.INK:Ui.MUTED);
  suggestionBand.setClickable(!enabled||ready);suggestionBand.setContentDescription(ready?"AI 回复建议："+automatic.reply:caption+"，"+text);
 }
 private void insertAutomaticReply(){
  if(syncConversation()||automatic.phase!=AutoReplyState.Phase.READY||!automaticEligible(ChatContextService.header())){tickAutomatic();return;}
  ChatContextService.Snapshot now=ChatContextService.capture();
  if(now==null||!now.privateChat||automaticSnapshot==null||!automatic.key.equals(automaticKey(now))||monotonic()-automatic.readyAt>=AutoReplyState.TTL_MS){tickAutomatic();toast("对话已变化，正在更新建议");return;}
  if(!keys.composition().isEmpty()||!emptyWhatsAppEditor()){toast("输入框已有草稿，未覆盖；清空后可点选建议");return;}
  ensureHostKeyboard();if(keys.insertSuggestion(automatic.reply)){if(!"key".equals(mode))showMode("key");toast("已填入，请核对后自行发送");}else toast("当前输入连接已变化，请重新打开键盘后重试");
 }
 private void renderAutoPrivacy(){
  boolean enabled=ChatContextService.automaticConsent(this);
  body.addView(Ui.text(this,enabled?"自动建议已开启":"开启一次，建议自动出现",20,Ui.INK,true));
  label("开启后：在 WhatsApp 私聊唤起键盘，当前屏幕最多12条、10,000字的可见消息，以及你填写的本会话风格偏好，将经"+AgentApi.routeName(this)+"交给当前配置的模型。新可见内容也会自动分析。不会读取数据库、后台聊天、附件或输入草稿，不自动发送。");
  label("离开聊天、收起键盘、进入内部编辑或任务页即停止。可随时在更多中关闭；已提交的模型请求不能撤回。消息原文只在内存处理，不保存原文。");
  if(!ChatContextService.consent(this)||!ChatContextService.connected()){
   label("还需要先同意上下文范围并开启系统辅助权限。未开启前不会自动提交。");
   Ui.addButton(body,"打开上下文设置",true,()->startActivity(new Intent(this,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
  }else Ui.addButton(body,enabled?"关闭自动回复建议":"同意并开启自动回复建议",true,()->{ChatContextService.setAutomaticConsent(this,!enabled);stopAutomatic();showMode("key");tickAutomatic();});
 }

 // Local discovery never uploads anything. Cloud extraction needs session-specific consent.
 private void resetTaskIntent(){intentSession.reset();taskIntent=null;intentError="";intentExpanded=false;intentSourceExpanded=false;intentScanEpoch++;}
 private boolean mayScanTask(){return localEdit==null&&ChatMemory.bound()&&!historyOpen&&!memoryOpen&&!privateInput&&"do".equals(mode)&&ChatContextService.keyboardActive&&"com.whatsapp".equals(host())&&ChatContextService.consent(this)&&ChatContextService.connected()&&!TaskState.current().busy&&!TaskState.current().canResume&&TaskState.current().result==null&&revision==null&&!manualTask&&!WorkState.current().open&&!WorkState.current().choosing&&!WorkState.current().workBusy;}
 private void scheduleTaskScan(){
  final int scan=++intentScanEpoch;
  AgentApi.MAIN.postDelayed(new Runnable(){public void run(){
   if(scan!=intentScanEpoch||!ChatContextService.keyboardActive||!"do".equals(mode))return;
   if(mayScanTask()){
    ChatContextService.armed=true;
    ChatContextService.Snapshot visible=ChatContextService.capture();
    if(visible==null){if(!intentSession.text.isEmpty()){intentSession.reset();taskIntent=null;intentError="当前消息不可见，已暂停识别。";render();}}
    else{
     boolean changed=intentSession.observe(visible.title,visible.text,System.currentTimeMillis());
     if(changed){taskIntent=null;intentError="";intentExpanded=false;intentSourceExpanded=false;render();}
     if(intentSession.canAutomaticallyRequest(System.currentTimeMillis()))extractTaskIntent(false);
    }
   }
   AgentApi.MAIN.postDelayed(this,1200);
  }},350);
 }
 private void extractTaskIntent(boolean explicit){
  if(!mayScanTask())return;
  ChatContextService.armed=true;
  ChatContextService.Snapshot now=ChatContextService.capture();
  if(now==null){toast("当前可见聊天无法读取，请重试或自己写任务");return;}
  // Never transfer a previous chat's consent to another target.
  boolean wasAllowed=intentSession.allowed;
  boolean switched=!intentSession.title.isEmpty()&&!intentSession.title.equals(now.title);
  if(intentSession.observe(now.title,now.text,System.currentTimeMillis()))taskIntent=null;
  if(switched||(!wasAllowed&&!explicit)){render();return;}
  if(explicit)intentSession.allowed=true;
  final int ticket=intentSession.begin(System.currentTimeMillis(),explicit),inputSession=session;
  if(ticket<0)return;
  final String title=intentSession.title,text=intentSession.text;
  taskIntent=null;intentError="";render();
  AgentApi.call(this,"POST","/recognize",AgentApi.obj("context",text),(d,e)->{
   if(inputSession!=session||!mayScanTask()||!intentSession.accepts(ticket,title,text))return;
   ChatContextService.Snapshot latest=ChatContextService.capture();
   if(latest==null||!latest.title.equals(title)||!latest.text.equals(text)){
    if(latest==null)intentSession.reset();else intentSession.observe(latest.title,latest.text,System.currentTimeMillis());
    taskIntent=null;intentError="可见聊天已变化，旧提炼结果已丢弃。";render();return;
   }
   intentSession.busy=false;
   if(e!=null)intentError=e;else{taskIntent=d;JSONObject meta=d.optJSONObject("meta");if(meta!=null)connection=meta.optString("model")+" · 提炼 "+(meta.optLong("latencyMs")/1000.0)+"s";}
   render();
  });
 }
 private void stopTaskRecognition(){intentSession.disable();taskIntent=null;intentError="";render();}
 private boolean intentStillCurrent(){
  if(privateInput||taskIntent==null||!taskIntent.optBoolean("canPrepare")||!mayScanTask())return false;
  ChatContextService.Snapshot now=ChatContextService.capture();
  if(now==null||!now.title.equals(intentSession.title)||!now.text.equals(intentSession.text)){
   if(now==null)intentSession.reset();else intentSession.observe(now.title,now.text,System.currentTimeMillis());
   taskIntent=null;toast("聊天已经变化，请核对新任务后再生成");render();return false;
  }
  return true;
 }
 private void generateRecognizedTask(){
  if(!intentStillCurrent())return;
  if(WorkState.hasDraft()){toast("已有任务草稿，先继续或明确清空草稿；不会被聊天识别覆盖。");return;}
  String prompt=taskIntent.optString("prompt");if(prompt.codePoints().allMatch(Character::isWhitespace)||prompt.length()>10000)return;
  TaskState.current().task=prompt;WorkState.select(taskIntent.optString("kind","auto"));intentSession.disable();manualTask=true;preview=false;confirmDelivery=false;render();
 }
 private void renderTaskDiscovery(){
  label("从可见聊天发现要完成的事，不会自动生成或发送。");
  if(!"com.whatsapp".equals(host())){label("在 WhatsApp 中可识别聊天任务，也可以自己选或描述目标。");return;}
  if(!ChatContextService.consent(this)||!ChatContextService.connected()){label("需要在禾语设置中授权 WhatsApp 可见上下文辅助；不会读取数据库。");return;}
  if(intentSession.text.isEmpty()){label(intentError.isEmpty()?"正在本地查看当前可见消息…":intentError);return;}
  LinearLayout card=Ui.card(this);
  if(intentSession.busy){card.addView(Ui.text(this,"正在识别你要完成的事…",18,Ui.INK,true));card.addView(Ui.text(this,"真实模型正在整理需求，尚未执行。",12,Ui.MUTED,false));}
  else if(taskIntent!=null){
   String status=taskIntent.optString("status");
   String heading=taskIntent.optBoolean("canPrepare")?taskIntent.optString("title","发现一个任务"):"canceled".equals(status)?"这项任务已取消":"ambiguous".equals(status)?"需要明确其中一个任务":"unsupported".equals(status)?"这项要求暂不支持":"暂未发现可执行的任务";
   card.addView(Ui.text(this,heading,18,Ui.INK,true));
   if(taskIntent.optBoolean("canPrepare"))card.addView(Ui.text(this,WorkState.kindName(taskIntent.optString("kind")),12,Ui.INK,true));
   card.addView(Ui.text(this,taskIntent.optString("reason"),13,Ui.MUTED,false));
   JSONArray evidence=taskIntent.optJSONArray("evidence");if(evidence!=null&&evidence.length()>0){TextView quote=Ui.text(this,"原文：“"+evidence.optString(0)+"”",12,Ui.MUTED,false);quote.setMaxLines(4);card.addView(quote);}
  }else{card.addView(Ui.text(this,"聊天里有任务，让禾语接着做",18,Ui.INK,true));card.addView(Ui.text(this,"区分真实需求、已有文件、进度询问和取消指令。当前仅本地查看。",12,Ui.MUTED,false));}
  body.addView(card);
  if(taskIntent!=null&&taskIntent.optBoolean("canPrepare")){
   Ui.addButton(body,"补充材料并核对计划",true,()->generateRecognizedTask());
   Ui.addButton(body,intentExpanded?"收起需求原文":"查看完整需求",false,()->{intentExpanded=!intentExpanded;render();});
   if(intentExpanded)label(taskIntent.optString("prompt"));
  }
  if(!intentError.isEmpty())body.addView(Ui.text(this,intentError,12,Ui.ERROR,false));
  if(!intentSession.allowed){
   Ui.addButton(body,"开启本次聊天自动识别 · 真实模型",true,()->extractTaskIntent(true));
   label("将下方可见文字及变化交给当前模型；切换聊天、标签或收起键盘即停止，最多自动识别6次。执行另需确认。");
  }else smallRow("停止自动识别",()->stopTaskRecognition(),intentSession.busy?"正在识别…":"重新识别",()->{if(!intentSession.busy)extractTaskIntent(true);});
  Ui.addButton(body,intentSourceExpanded?"收起聊天原文":"预览识别来源（"+intentSession.text.length()+" 字）",false,()->{intentSourceExpanded=!intentSourceExpanded;render();});
  if(intentSourceExpanded)label(intentSession.text+"\n仅当前可见内容，发言人未可靠区分，请核对原意。");
 }
 private void renderTask(){
  if(TaskState.current().busy||TaskState.current().canResume){
   body.addView(Ui.text(this,"正在把需求变成结果",21,Ui.INK,true));label(TaskState.current().message);
   if(TaskState.current().busy){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);body.addView(bar);}
   if(TaskState.current().canResume)Ui.addButton(body,"恢复查询原任务 · 不重复生成",true,()->TaskState.resume(this));
   Ui.addButton(body,"取消任务",false,()->{TaskState.cancel(this);WorkState.invalidate();render();});return;
  }
  if(TaskState.current().result!=null){if(TaskState.current().result.has("artifacts"))renderArtifacts();else renderResult();return;}
  if(WorkState.current().choosing){renderGoalChooser();return;}
  if(WorkState.current().open){renderWork();return;}
  body.addView(Ui.text(this,"让对话里的事，直接完成",20,Ui.INK,true));Ui.space(body,8);
  renderTaskDiscovery();Ui.space(body,12);
  // Manual is intentionally secondary to chat recognition, never a permanent tool grid.
  smallRow("自己选要做的事",()->{stopTaskRecognition();WorkState.current().choosing=true;render();},"直接描述目标",()->{intentSession.disable();WorkState.current().open=true;render();});
  if(WorkState.hasDraft())Ui.addButton(body,"继续已有任务草稿",false,()->{intentSession.disable();WorkState.current().open=true;render();});
  if(!TaskState.current().error.isEmpty())label(TaskState.current().error);
 }
 private void renderGoalChooser(){
  body.addView(Ui.text(this,"你想完成什么？",21,Ui.INK,true));label("选的是结果，不是工具。已有描述和材料会保留，可随时改选。");
  for(int i=0;i<WorkState.KINDS.length;i+=2){final String[] a=WorkState.KINDS[i],b=WorkState.KINDS[i+1];smallRow(a[1],()->{WorkState.select(a[0]);render();},b[1],()->{WorkState.select(b[0]);render();});}
  Ui.addButton(body,"返回",false,()->{WorkState.current().choosing=false;render();});
 }

 private void renderWork(){
  final ConversationState draftOwner=ChatMemory.current();
  body.addView(Ui.text(this,"把这件事交给禾语",21,Ui.INK,true));label("描述需求 → 核对计划 → 预览交付");if(!ChatMemory.current().memory.codePoints().allMatch(Character::isWhitespace))label("本聊天偏好也将提交："+ChatMemory.current().memory);
  if(WorkState.current().workBusy){label(WorkState.current().workStatus);ProgressBar bar=new ProgressBar(this);body.addView(bar);label("材料和目标已在本机保留。只向模型提交确认的任务文字，不自动发送文件。");return;}
  smallRow("目标："+WorkState.kindName(WorkState.current().kind),()->{WorkState.current().choosing=true;render();},"返回聊天识别",()->{WorkState.current().open=false;manualTask=false;intentSession.reset();scheduleTaskScan();render();});
  editableCard("任务目标",draftOwner.task,"点此输入目标、受众、语言…",()->openLocalEditor(draftOwner,ChatMemory.generation(),"任务目标",draftOwner.task,10000,value->{draftOwner.task=value;WorkState.invalidate(draftOwner);return true;}));
  for(WorkState.Source source:new java.util.ArrayList<>(WorkState.current().sources)){
   LinearLayout item=Ui.card(this);item.addView(Ui.text(this,source.name,13,Ui.INK,true));item.addView(Ui.text(this,(source.file.length()/1024)+" KB · "+(source.id.isEmpty()?"仅保存在手机，尚未解析":"已由手机本地解析"),11,Ui.MUTED,false));
   if(source.meta!=null&&source.meta.has("width"))item.addView(Ui.text(this,source.meta.optInt("width")+" × "+source.meta.optInt("height")+" 像素",11,Ui.MUTED,false));
   Ui.addButton(item,"移除材料",false,()->WorkState.remove(this,source));body.addView(item);
  }
  Ui.addButton(body,"添加材料 · 系统文件选择器",false,()->{if(syncConversation()){render();return;}intentSession.disable();if(!SourcePickerActivity.begin(this,host())){toast("当前聊天或任务已变化，请重新打开材料选择");return;}startActivity(new Intent(this,SourcePickerActivity.class).putExtra("owner",ChatMemory.current().id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
  label("最多4份、单份8MB。材料保留在手机；下方确认后，仅任务文字和可提取文字发送给DeepSeek；图片像素在手机本地处理，文件在本机生成。时区："+java.util.TimeZone.getDefault().getID());
  if(WorkState.current().plan==null){Ui.addButton(body,"确认提交目标和材料 · 整理执行计划",true,()->{if(syncConversation()){render();return;}WorkState.prepare(this);});}
  else{
   JSONObject plan=WorkState.current().plan;LinearLayout card=Ui.card(this);card.addView(Ui.text(this,"执行前，请核对",17,Ui.INK,true));card.addView(Ui.text(this,plan.optString("preview"),13,Ui.INK,false));card.addView(Ui.text(this,"交付格式："+plan.optJSONArray("formats"),11,Ui.MUTED,false));body.addView(card);
   JSONArray missing=plan.optJSONArray("missing");if(missing!=null&&missing.length()>0){label("还缺少这些信息，补充上方目标后重新核对：");for(int i=0;i<missing.length();i++)body.addView(Ui.text(this,"• "+missing.optString(i),13,Ui.ERROR,false));}
   else if("ready".equals(plan.optString("status")))Ui.addButton(body,"确认计划 · 开始真实执行",true,()->{if(syncConversation()){render();return;}if(WorkState.current().plan!=plan){toast("目标已变化，请重新核对计划");render();return;}editTask=false;preview=false;TaskState.startPrepared(this,plan.optString("id"));});
   Ui.addButton(body,"重新核对修改后的要求",false,()->{if(syncConversation()){render();return;}WorkState.prepare(this);});
  }
  if(!WorkState.current().workError.isEmpty())body.addView(Ui.text(this,WorkState.current().workError,13,Ui.ERROR,false));if(!TaskState.current().error.isEmpty())body.addView(Ui.text(this,TaskState.current().error,13,Ui.ERROR,false));
  Ui.addButton(body,"清空这个任务和材料",false,()->{WorkState.reset(this);manualTask=false;TaskState.current().error="";intentSession.reset();scheduleTaskScan();render();});
 }
 private void renderArtifacts(){
  JSONObject result=displayedResult();body.addView(Ui.text(this,"结果已就绪，先核对再交付",21,Ui.INK,true));label(result.optString("title"));
  Ui.addButton(body,preview?"收起内容预览":"预览生成内容",false,()->{preview=!preview;render();});if(preview){TextView text=Ui.text(this,result.optString("preview"),13,Ui.INK,false);text.setTextIsSelectable(true);body.addView(text);}
  JSONArray warnings=result.optJSONArray("warnings");if(warnings!=null)for(int i=0;i<warnings.length();i++)label("核对："+warnings.optString(i));
  JSONArray artifacts=result.optJSONArray("artifacts");if(artifacts!=null)for(int i=0;i<artifacts.length();i++){
   JSONObject a=artifacts.optJSONObject(i);if(a==null)continue;LinearLayout card=Ui.card(this);card.addView(Ui.text(this,a.optString("fileName"),15,Ui.INK,true));card.addView(Ui.text(this,a.optLong("bytes")/1024+" KB · 真实 "+a.optString("ext").toUpperCase(java.util.Locale.ROOT),11,Ui.MUTED,false));
   if(a.optString("mime").startsWith("image/")){
    Ui.addButton(card,"预览真实图片",false,()->previewImage(a));
    if(a.optString("fileId").equals(artifactImageId)&&artifactImage!=null){ImageView image=new ImageView(this);image.setImageBitmap(artifactImage);image.setAdjustViewBounds(true);image.setContentDescription("已下载并校验的真实处理结果");card.addView(image,new LinearLayout.LayoutParams(-1,-2));}
   }
   deliveryActions(card,a);body.addView(card);
  }
  if(historyDetail!=null){historyFooter();return;}
  JSONArray events=result.optJSONArray("events");if(events!=null&&events.length()>0)Ui.addButton(body,"写入手机日历… · 另行确认",false,()->startActivity(new Intent(this,CalendarActivity.class).putExtra("events",events.toString()).putExtra("job",result.optString("id")).putExtra("timezone",result.optString("timezone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
  label("生成不等于发送。点击一键发送后，只交付选中的文件到显示的当前聊天；不会发送其他草稿。");
  Ui.addButton(body,"修改要求 · 重新核对",false,()->{TaskState.clearResult(this);WorkState.current().open=true;WorkState.invalidate();preview=false;render();});
  Ui.addButton(body,"完成 · 开始新任务",false,()->{TaskState.clearResult(this);WorkState.reset(this);artifactImage=null;artifactImageId="";preview=false;manualTask=false;intentSession.reset();scheduleTaskScan();render();});
 }
 private void previewImage(JSONObject artifact){
  if(downloadBusy)return;final ConversationState owner=ChatMemory.current();final int generation=ChatMemory.generation();downloadBusy=true;render();AgentApi.IO.execute(()->{try{
   File f=AgentApi.download(this,artifact);android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(),opts);opts.inSampleSize=Math.max(1,Math.max(opts.outWidth,opts.outHeight)/1400);opts.inJustDecodeBounds=false;android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
   if(image==null)throw new Exception();AgentApi.MAIN.post(()->{if(!sameWorkspace(owner,generation))return;downloadBusy=false;artifactImage=image;artifactImageId=artifact.optString("fileId");render();});
  }catch(Exception e){AgentApi.MAIN.post(()->{if(!sameWorkspace(owner,generation))return;downloadBusy=false;toast("本地图片读取或解码失败，请重新生成");render();});}});
 }
 private void renderResult(){
  JSONObject r=displayedResult(),deck=r.optJSONObject("deck");if(deck==null)return;
  LinearLayout card=Ui.card(this);card.addView(Ui.text(this,r.optString("fileName"),16,Ui.INK,true));JSONArray slides=deck.optJSONArray("slides");card.addView(Ui.text(this,(slides==null?0:slides.length())+" 页 · "+r.optLong("bytes")/1024+" KB · 真实 PPTX",12,Ui.MUTED,false));body.addView(card);
  if(historyDetail==null)smallRow(preview?"收起内容":"预览内容",()->{preview=!preview;render();},"继续修改",()->{revision=deck;TaskState.current().task="请保持其他内容不变，将这份 PPT 改为英文版。";TaskState.current().result=null;confirmDelivery=false;render();});
  if(preview&&slides!=null)for(int i=0;i<slides.length();i++){JSONObject s=slides.optJSONObject(i);if(s==null)continue;body.addView(Ui.text(this,(i+1)+" / "+s.optString("title"),14,Ui.INK,true));JSONArray bullets=s.optJSONArray("bullets");if(bullets!=null)for(int j=0;j<bullets.length();j++)body.addView(Ui.text(this,"• "+bullets.optString(j),12,Ui.MUTED,false));Ui.space(body,8);}
  label("初稿需人工核实。只在你点击后发送选中的文件。");deliveryActions(body,r);
  if(historyDetail!=null){historyFooter();return;}
  Ui.addButton(body,"新建任务 · 从聊天识别",false,()->{TaskState.clearResult(this);revision=null;preview=false;confirmDelivery=false;manualTask=false;intentSession.reset();taskIntent=null;render();});
 }
 private void deliveryActions(LinearLayout target,JSONObject file){
  if(historyArchive){target.addView(Ui.text(this,"归属待确认 · 请先确认聊天归属",12,Ui.MUTED,false));return;}
  if(!ChatMemory.ownsResult(file)){target.addView(Ui.text(this,"这份材料不属于当前聊天，已禁止交付",12,Ui.MUTED,false));return;}
  final ConversationState owner=ChatMemory.current();final int generation=ChatMemory.generation();
  String ext=file.optString("ext","pptx").toUpperCase(java.util.Locale.ROOT);
  DirectSend.Target recipient="com.whatsapp".equals(host())?DirectSend.target():null;
  boolean ready=recipient!=null&&DirectSend.compatible(this),enabled=DirectSend.consent(this);
  String note=DirectSend.message(file.optString("fileId"));
  if(!note.isEmpty())target.addView(Ui.text(this,note,12,Ui.MUTED,false));
  if(ready){
   target.addView(Ui.text(this,"发送到："+recipient.title,13,Ui.INK,true));
   if(!enabled)target.addView(Ui.text(this,"首次开启：点选文件后，禾语核对当前联系人的收件地址，直接打开 WhatsApp 文件预览并完成发送。无需重选文件或联系人；只交付这一个文件，可随时停止。是否送达以 WhatsApp 回执为准。",12,Ui.MUTED,false));
   if(DirectSend.used(this,file,recipient))target.addView(Ui.text(this,DirectSend.prior(this,file,recipient),12,Ui.MUTED,true));
   else {
    String title=DirectSend.busy()?"正在交付，请勿重复点击…":DeliveryBuild.DRY_RUN?"演练 "+ext+" · 不发送":enabled?"一键发送 "+ext:"同意并一键发送 "+ext;
    Button button=Ui.button(this,title,true,()->{if(!sameWorkspace(owner,generation)){toast("聊天已切换，请重新核对材料和接收人");render();return;}if(!DeliveryBuild.DRY_RUN&&!DirectSend.consent(this))DirectSend.setConsent(this,true);DirectSend.start(this,file,recipient);render();});button.setEnabled(!downloadBusy&&!DirectSend.busy());target.addView(button,new LinearLayout.LayoutParams(-1,Ui.dp(this,46)));
   }
  }else target.addView(Ui.text(this,"一键发送仅适配当前验证的 WhatsApp 版本。请在明确的聊天输入框开启禾语辅助；其他场景使用备用分享。",12,Ui.MUTED,false));
  Button share=Ui.button(this,"其他方式分享 "+ext+"…",false,()->{if(sameWorkspace(owner,generation))deliver(file);else{toast("聊天已切换，已取消分享");render();}});share.setEnabled(!downloadBusy&&!DirectSend.busy());target.addView(share,new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));
 }
 private void deliver(JSONObject result){if(downloadBusy)return;final ConversationState owner=ChatMemory.current();final int generation=ChatMemory.generation();downloadBusy=true;render();AgentApi.IO.execute(()->{try{File f=AgentApi.download(this,result);AgentApi.MAIN.post(()->{if(!sameWorkspace(owner,generation))return;downloadBusy=false;startActivity(new Intent(this,DeliveryActivity.class).putExtra("id",result.optString("fileId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));render();});}catch(Exception e){AgentApi.MAIN.post(()->{if(!sameWorkspace(owner,generation))return;downloadBusy=false;toast("本地文件已被清理；历史文字仍在，请重新生成");render();});}});}
}
