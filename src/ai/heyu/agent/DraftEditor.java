package ai.heyu.agent;
import android.text.Spanned;
import android.view.inputmethod.*;
/** Fail-closed, full-draft snapshots. No clipboard, chat history, network or sending. */
final class DraftEditor {
 static final int MAX_INPUT=4000,MAX_OUTPUT=6000;
 interface Guard {boolean valid();}
 static final class Snapshot {
  final String text;final int start,end;
  Snapshot(String text,int start,int end){this.text=text;this.start=start;this.end=end;}
  boolean sameText(Snapshot other){return other!=null&&text.equals(other.text);}
  boolean sameState(Snapshot other){return sameText(other)&&start==other.start&&end==other.end;}
 }
 enum Result {APPLIED,STALE,FAILED,UNCERTAIN}
 static boolean validText(String text,int limit){
  if(text==null||text.length()>limit||text.indexOf('\ufffc')>=0)return false;
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);
   if(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')return false;
   if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))return false;}
   else if(Character.isLowSurrogate(c))return false;
  }
  return true;
 }
 static Snapshot read(InputConnection ic,int limit){
  if(ic==null)return null;
  try{
   ExtractedTextRequest req=new ExtractedTextRequest();req.hintMaxChars=limit+1;req.hintMaxLines=1000;req.flags=InputConnection.GET_TEXT_WITH_STYLES;
   ExtractedText e=ic.getExtractedText(req,0);
   // A slice/partial extract is never an entire draft. No fallback to selected text.
   if(e==null||e.text==null||e.startOffset!=0||e.partialStartOffset!=-1||e.partialEndOffset!=-1)return null;
   if(e.text instanceof Spanned){Spanned spans=(Spanned)e.text;for(Object span:spans.getSpans(0,spans.length(),Object.class))if((spans.getSpanFlags(span)&Spanned.SPAN_COMPOSING)!=0)return null;}
   String text=e.text.toString();int a=e.selectionStart,b=e.selectionEnd;
   if(!validText(text,limit)||a<0||b<0||a>text.length()||b>text.length())return null;
   int low=Math.min(a,b),high=Math.max(a,b);
   CharSequence before=ic.getTextBeforeCursor(limit+1,0),after=ic.getTextAfterCursor(limit+1,0),selected=ic.getSelectedText(0);
   if(before==null||after==null||!text.substring(0,low).contentEquals(before)||!text.substring(high).contentEquals(after))return null;
   if(low!=high&&(selected==null||!text.substring(low,high).contentEquals(selected)))return null;
   if(low==high&&selected!=null&&selected.length()!=0)return null;
   return new Snapshot(text,a,b);
  }catch(RuntimeException e){return null;}
 }
 private static void restoreCursor(InputConnection ic,Snapshot old,Guard guard){
  try{if(guard.valid()&&old.sameText(read(ic,MAX_OUTPUT)))ic.setSelection(old.start,old.end);}catch(RuntimeException ignored){}
 }
 static Result replace(InputConnection ic,Snapshot expected,String value,Guard guard){
  if(expected==null||!validText(value,MAX_OUTPUT)||value.isBlank())return Result.FAILED;
  if(!guard.valid()||!expected.sameState(read(ic,MAX_OUTPUT)))return Result.STALE;
  boolean began=false,submitted=false;
  try{
   ic.beginBatchEdit();began=true;
   if(!guard.valid()||!expected.sameState(read(ic,MAX_OUTPUT)))return Result.STALE;
   if(!ic.setSelection(0,expected.text.length())){restoreCursor(ic,expected,guard);return Result.FAILED;}
   Snapshot selected=read(ic,MAX_OUTPUT);
   // The editor may ignore setSelection. Never append a polished draft accidentally.
   if(!guard.valid()||!expected.sameText(selected)||selected.start!=0||selected.end!=expected.text.length()){
    restoreCursor(ic,expected,guard);return Result.STALE;
   }
   submitted=true;boolean accepted=ic.commitText(value,1);
   ic.finishComposingText();
   Snapshot actual=read(ic,MAX_OUTPUT);
   if(guard.valid()&&actual!=null&&value.equals(actual.text))return Result.APPLIED;
   if(!accepted&&expected.sameText(actual)){restoreCursor(ic,expected,guard);return Result.FAILED;}
   // Do not blindly roll back: an unusual host or concurrent edit may own this text.
   return Result.UNCERTAIN;
  }catch(RuntimeException e){if(!submitted)restoreCursor(ic,expected,guard);return submitted?Result.UNCERTAIN:Result.FAILED;}
  finally{if(began)try{ic.endBatchEdit();}catch(RuntimeException ignored){}}
 }
}
