package ai.heyu.agent;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
/** One input path for local and host editors. A missing local target never falls back to the host. */
final class KeyboardController {
 interface Target { InputConnection connection(); boolean internal(); void done(); void changed(); }
 private final PinyinDecoder decoder; private Target target;
 private String composing=""; private String[] candidates=new String[0];
 boolean chinese=true,shift=false,caps=false,symbols=false,secondSymbols=false,numeric=false,secure=false;
 private long lastShift=0;
 KeyboardController(PinyinDecoder decoder){this.decoder=decoder;}
 void bind(Target next,boolean number,boolean password){cancelComposition();target=next;numeric=number;secure=password;symbols=number;shift=false;caps=false;secondSymbols=false;}
 void detach(){cancelComposition();target=null;}
 boolean isBound(){return target!=null;}
 private InputConnection ic(){return target==null?null:target.connection();}
 String composition(){return composing;}
 String[] candidates(){return candidates;}
 boolean canChinese(){return !secure&&decoder.ready();}
 private void notifyChanged(){if(target!=null)target.changed();}
 /** Suggestions are committed text, never preedit; keep subsequent typing on the host. */
 boolean insertSuggestion(String value){
  if(target==null||target.internal()||secure||!composing.isEmpty()||value==null||value.isEmpty())return false;
  InputConnection connection=ic();if(connection==null)return false;
  boolean committed;
  connection.beginBatchEdit();
  try{
   connection.finishComposingText();
   committed=connection.commitText(value,1);
   connection.finishComposingText();
  }finally{connection.endBatchEdit();}
  if(committed){composing="";candidates=new String[0];notifyChanged();}
  return committed;
 }
 void letter(String raw){
  InputConnection ic=ic();if(ic==null)return;
  if(chinese&&canChinese()&&!symbols&&!shift&&raw.matches("[a-z']")){
   if(composing.length()>=48)return;composing+=raw;candidates=decoder.candidates(composing);ic.setComposingText(composing,1);
  }else{finishBest();ic.commitText(shift?raw.toUpperCase(java.util.Locale.ROOT):raw,1);if(shift&&!caps)shift=false;}
  notifyChanged();
 }
 void choose(int index){
  InputConnection ic=ic();if(ic==null||index<0||index>=candidates.length)return;
  String word=candidates[index];int used=decoder.consumedChars(word.length());
  // Candidate 0 may decode only a prefix: keep undecoded spelling, never discard it.
  if(used<=0||used>composing.length())return;
  String rest=composing.substring(used);while(rest.startsWith("'"))rest=rest.substring(1);
  ic.commitText(word,1);composing=rest;candidates=decoder.candidates(rest);if(!rest.isEmpty())ic.setComposingText(rest,1);else ic.finishComposingText();notifyChanged();
 }
 void finishBest(){
  if(composing.isEmpty())return;
  if(candidates.length>0){String before=composing;choose(0);if(composing.equals(before)){commitRaw();return;}}
  // Preserve undecoded tail as typed; no silent truncation.
  if(!composing.isEmpty())commitRaw();
 }
 void commitRaw(){InputConnection ic=ic();if(ic!=null&&!composing.isEmpty())ic.commitText(composing,1);composing="";candidates=new String[0];if(ic!=null)ic.finishComposingText();}
 void cancelComposition(){InputConnection ic=ic();if(ic!=null)ic.finishComposingText();composing="";candidates=new String[0];}
 void backspace(){
  InputConnection ic=ic();if(ic==null)return;
  if(!composing.isEmpty()){composing=composing.substring(0,composing.length()-1);candidates=decoder.candidates(composing);ic.setComposingText(composing,1);if(composing.isEmpty())ic.finishComposingText();}
  else{CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0)ic.commitText("",1);else ic.deleteSurroundingTextInCodePoints(1,0);}
  notifyChanged();
 }
 void space(){if(!composing.isEmpty()){finishBest();notifyChanged();}else{InputConnection ic=ic();if(ic!=null)ic.commitText(" ",1);notifyChanged();}}
 void punctuation(String value){finishBest();InputConnection ic=ic();if(ic!=null)ic.commitText(value,1);notifyChanged();}
 void enter(){finishBest();if(target==null)return;if(target.internal())target.done();else{InputConnection ic=ic();if(ic!=null)ic.commitText("\n",1);}notifyChanged();}
 void toggleLanguage(){finishBest();chinese=!chinese;symbols=false;shift=false;caps=false;notifyChanged();}
 void toggleSymbols(){finishBest();symbols=!symbols;secondSymbols=false;notifyChanged();}
 void shift(){if(symbols){secondSymbols=!secondSymbols;}else{long now=android.os.SystemClock.uptimeMillis();if(shift&&!caps&&now-lastShift<450)caps=true;else{shift=!shift;caps=false;}lastShift=now;}notifyChanged();}
 /** Cursor moves outside the composing span terminate its ownership. */
 void selectionChanged(int start,int end,int composingStart,int composingEnd){if(!composing.isEmpty()&&(start!=end||start!=composingEnd)){cancelComposition();notifyChanged();}}
}
