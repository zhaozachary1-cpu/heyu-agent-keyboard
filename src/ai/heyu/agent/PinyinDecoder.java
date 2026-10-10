package ai.heyu.agent;
import android.content.Context;
import java.io.*;
/** On-device AOSP dictionary search. Never calls a network, learns, or logs typed text. */
final class PinyinDecoder {
 private static boolean loaded;
 private final File scratch; private boolean ready;
 static { try { System.loadLibrary("heyu_pinyin");loaded=true; } catch(UnsatisfiedLinkError ignored) {loaded=false;} }
 PinyinDecoder(Context context) {
  scratch=new File(context.getCacheDir(),"pinyin-no-learning.tmp");
  if(!loaded)return;
  try {
   File dict=new File(context.getFilesDir(),"dict-pinyin-aosp.dat");
   if(!dict.exists()||dict.length()!=1068442L){File tmp=new File(context.getFilesDir(),"dict-pinyin.tmp");try(InputStream in=context.getAssets().open("dict_pinyin.dat");OutputStream out=new FileOutputStream(tmp)){in.transferTo(out);}if(!tmp.renameTo(dict))throw new IOException("dictionary install");}
   scratch.delete(); ready=open(dict.getAbsolutePath(),scratch.getAbsolutePath());
  } catch(Exception ignored){ready=false;}
 }
 boolean ready(){return ready;}
 String[] candidates(String value){return ready&&!value.isEmpty()?search(value):new String[0];}
 int consumedChars(int hanzi){return ready?consumed(hanzi):0;}
 void dispose(){if(ready){close();ready=false;}scratch.delete();}
 private static native boolean open(String dict,String user);
 private static native String[] search(String value);
 private static native int consumed(int chars);
 private static native void close();
}
