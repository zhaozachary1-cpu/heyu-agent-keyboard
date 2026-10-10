package ai.heyu.agent;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Gboard-inspired proportions. One full keyboard for host and local editors. */
final class KeyboardSurface extends LinearLayout {
 interface Actions {void work();void polish();void polishSettings();void more(View anchor);void switchIme();}
 private final KeyboardController keys; private final Actions actions; private final boolean internal;
 private final LinearLayout strip,rows; private String layoutSignature="",candidateSignature="";
 private boolean polishBusy=false;
 private final int pitch,stripHeight; private final Handler handler=new Handler();
 KeyboardSurface(Context context,KeyboardController controller,Actions actions,boolean internal){
  super(context);keys=controller;this.actions=actions;this.internal=internal;setOrientation(VERTICAL);
  setContentDescription(internal?"禾语内部编辑键盘":"禾语输入键盘");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  pitch=context.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?40:58;stripHeight=pitch==40?40:48;
  strip=Ui.row(context);addView(strip,new LayoutParams(-1,Ui.dp(context,stripHeight)));
  rows=Ui.column(context);rows.setPadding(Ui.dp(context,2),0,Ui.dp(context,2),0);addView(rows,new LayoutParams(-1,-2));refresh();
 }
 void setPolishBusy(boolean value){if(polishBusy!=value){polishBusy=value;candidateSignature="";refresh();}}
 int contentHeight(){return stripHeight+pitch*4;}
 void refresh(){
  String signature=keys.symbols+":"+keys.secondSymbols+":"+keys.shift+":"+keys.caps+":"+keys.chinese+":"+keys.secure+":"+keys.numeric;
  if(!signature.equals(layoutSignature)){layoutSignature=signature;renderRows();}
  String next=signature+":"+keys.composition()+":"+java.util.Arrays.toString(keys.candidates());
  if(!next.equals(candidateSignature)){candidateSignature=next;renderStrip();}
 }
 private void renderStrip(){
  strip.removeAllViews();strip.setPadding(Ui.dp(getContext(),4),0,Ui.dp(getContext(),4),0);
  if(!keys.composition().isEmpty()){
   HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);LinearLayout choices=Ui.row(getContext());
   TextView raw=chip(keys.composition(),"保留拼音 "+keys.composition(),()->{keys.commitRaw();refresh();});raw.setTextSize(14);raw.setTextColor(Ui.MUTED);choices.addView(raw,new LayoutParams(-2,-1));
   String[] words=keys.candidates();for(int i=0;i<words.length;i++){final int index=i;TextView candidate=chip(words[i],"候选词 "+words[i],()->keys.choose(index));candidate.setTextSize(20);if(i==0){candidate.setTextColor(Ui.ACCENT);candidate.setTypeface(null,android.graphics.Typeface.BOLD);}choices.addView(candidate,new LayoutParams(-2,-1));}
   scroll.addView(choices,new HorizontalScrollView.LayoutParams(-2,-1));strip.addView(scroll,new LayoutParams(-1,-1));return;
  }
  if(internal){TextView note=Ui.text(getContext(),"仅编辑当前字段，不会发到聊天",12,Ui.MUTED,false);note.setPadding(Ui.dp(getContext(),12),0,0,0);strip.addView(note,new LayoutParams(0,-2,1));return;}
  strip.addView(iconButton(getContext(),"more","更多键盘选项",v->actions.more(v)),new LayoutParams(Ui.dp(getContext(),48),-1));
  if(!keys.secure)strip.addView(chip("帮我做","帮我做",actions::work),new LayoutParams(Ui.dp(getContext(),84),-1));
  else{TextView safe=Ui.text(getContext(),"安全输入 · AI 已关闭",12,Ui.MUTED,false);strip.addView(safe,new LayoutParams(-2,-2));}
  Space spacer=new Space(getContext());strip.addView(spacer,new LayoutParams(0,1,1));
  if(!keys.secure){TextView polish=chip(polishBusy?"润色中…":"AI 润色","AI 润色",actions::polish);polish.setTextColor(Ui.ACCENT);polish.setEnabled(!polishBusy);polish.setAlpha(polishBusy?.55f:1f);polish.setOnLongClickListener(v->{actions.polishSettings();return true;});strip.addView(polish,new LayoutParams(Ui.dp(getContext(),104),-1));}
 }
 private TextView chip(String text,String description,Runnable click){TextView v=Ui.text(getContext(),text,14,Ui.INK,false);v.setGravity(Gravity.CENTER);v.setPadding(Ui.dp(getContext(),12),0,Ui.dp(getContext(),12),0);v.setSingleLine(true);v.setContentDescription(description);v.setBackground(Ui.touch(getContext(),Ui.BG,18));v.setOnClickListener(x->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);click.run();});return v;}
 static View iconButton(Context c,String icon,String desc,View.OnClickListener click){ImageView v=new ImageView(c);v.setImageDrawable(new KeyboardIcon(icon,Ui.INK));v.setPadding(Ui.dp(c,12),Ui.dp(c,12),Ui.dp(c,12),Ui.dp(c,12));v.setBackground(Ui.touch(c,Ui.BG,24));v.setContentDescription(desc);v.setOnClickListener(click);return v;}
 private void renderRows(){
  rows.removeAllViews();
  if(keys.numeric){renderNumeric();return;}
  if(keys.symbols){
   letters(keys.secondSymbols?new String[]{"~","`","|","•","√","π","÷","×","¶","∆"}:new String[]{"1","2","3","4","5","6","7","8","9","0"},false);
   letters(keys.secondSymbols?new String[]{"£","¢","€","¥","^","°","=","{","}","\\"}:new String[]{"@","#","$","_","&","-","+","(",")","/"},false);
   LinearLayout third=row();add(third,key(keys.secondSymbols?"?123":"=\\<",null,"更多符号",keys::shift,true),1.5f);
   for(String letter:(keys.secondSymbols?new String[]{"%","©","®","™","[","]","§"}:new String[]{"*","\"","'",":",";","!","?"}))add(third,key(letter,null,"按键 "+letter,()->keys.punctuation(letter),false),1);
   add(third,deleteKey(),1.5f);
  }else{
   letters(new String[]{"q","w","e","r","t","y","u","i","o","p"},false);
   letters(new String[]{"a","s","d","f","g","h","j","k","l"},true);
   LinearLayout third=row();add(third,key(null,keys.caps?"caps":"shift",keys.caps?"关闭大写锁定":"切换大写",keys::shift,true),1.5f);
   for(String letter:new String[]{"z","x","c","v","b","n","m"})add(third,letter(letter),1);add(third,deleteKey(),1.5f);
  }
  LinearLayout bottom=row();add(bottom,key(keys.symbols?"ABC":"?123",null,"切换字母与符号",keys::toggleSymbols,true),1.5f);
  String comma=keys.chinese&&keys.canChinese()?"，":",";add(bottom,key(comma,null,"逗号",()->keys.punctuation(comma),false),1);
  add(bottom,key(keys.chinese&&keys.canChinese()?"中":"En",null,"切换中英文",keys::toggleLanguage,true),1);
  View space=key(keys.chinese&&keys.canChinese()?"拼音":"English",null,"空格",keys::space,false);space.setOnLongClickListener(v->{keys.finishBest();actions.switchIme();return true;});add(bottom,space,4);
  String period=keys.chinese&&keys.canChinese()?"。":".";add(bottom,key(period,null,"句号",()->keys.punctuation(period),false),1);
  add(bottom,key(null,internal?"done":"enter",internal?"完成编辑":"换行",keys::enter,true),1.5f);
 }
 private void letters(String[] letters,boolean inset){LinearLayout row=row();if(inset)add(row,new Space(getContext()),.5f);for(String value:letters)add(row,keys.symbols?key(value,null,"按键 "+value,()->keys.punctuation(value),false):letter(value),1);if(inset)add(row,new Space(getContext()),.5f);}
 private View letter(String value){return key(keys.shift?value.toUpperCase(Locale.ROOT):value,null,"按键 "+(keys.shift?value.toUpperCase(Locale.ROOT):value),()->keys.letter(value),false);}
 private LinearLayout row(){LinearLayout row=Ui.row(getContext());row.setWeightSum(10);rows.addView(row,new LayoutParams(-1,Ui.dp(getContext(),pitch)));return row;}
 private void add(LinearLayout row,View view,float weight){row.addView(view,new LayoutParams(0,-1,weight));}
 private void renderNumeric(){
  String[][] values={{"1","2","3","+"},{"4","5","6","-"},{"7","8","9","delete"},{".","0",",","enter"}};
  for(String[] line:values){LinearLayout row=row();for(String value:line){View key;if(value.equals("delete"))key=deleteKey();else if(value.equals("enter"))key=key(null,internal?"done":"enter",internal?"完成编辑":"换行",keys::enter,true);else key=key(value,null,"按键 "+value,()->keys.punctuation(value),false);add(row,key,2.5f);}}
 }
 private View key(String text,String icon,String description,Runnable click,boolean modifier){
  Context c=getContext();FrameLayout v=new FrameLayout(c);v.setContentDescription(description);v.setFocusable(true);
  boolean accent=description.equals("换行")||description.equals("完成编辑");int fill=accent?Ui.LIGHT:modifier?Ui.TONAL:Ui.SURFACE;
  boolean compact=pitch==40;int opticalOffset=compact?3:7;
  v.setBackground(new InsetDrawable(Ui.touch(c,fill,modifier?20:8),Ui.dp(c,3),Ui.dp(c,2),Ui.dp(c,3),Ui.dp(c,compact?5:9)));
  // Insets draw gaps only. Do not also shrink the child via Drawable's implicit padding.
  v.setPadding(0,0,0,0);
  View face;if(icon!=null){ImageView image=new ImageView(c);image.setImageDrawable(new KeyboardIcon(icon,accent?Ui.ACCENT:Ui.INK));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(c,compact?20:24),Ui.dp(c,compact?20:24),Gravity.CENTER);p.bottomMargin=Ui.dp(c,opticalOffset);image.setLayoutParams(p);face=image;}
  else{TextView label=Ui.text(c,text,description.equals("空格")?14:description.equals("切换中英文")?16:text.length()>2?12:compact?20:22,Ui.INK,false);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setIncludeFontPadding(false);label.setPadding(0,0,0,Ui.dp(c,opticalOffset));label.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));face=label;}
  face.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);v.addView(face);v.setOnClickListener(x->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);click.run();});return v;
 }
 private View deleteKey(){
  View delete=key(null,"delete","删除",keys::backspace,true);
  final Runnable repeat=new Runnable(){public void run(){if(delete.isPressed()&&delete.isAttachedToWindow()){keys.backspace();handler.postDelayed(this,65);}}};
  delete.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){handler.postDelayed(repeat,380);}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(repeat);}return false;});return delete;
 }
 @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
