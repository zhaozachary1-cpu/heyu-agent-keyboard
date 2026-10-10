package ai.heyu.agent;
import android.content.Context;
/** One global style preference. Drafts/results never go into preferences. */
final class PolishStyle {
 static final String[] IDS={"formal","practical","natural","warm","concise"};
 static final String[] LABELS={"正式","务实","自然","亲和","简洁"};
 static final String[] DETAILS={"措辞规范、礼貌得体，适合正式沟通","直说重点和下一步，不堆砌客套话","保留你的口吻，让表达通顺自然","语气温和、有分寸，不刻意讨好","去掉重复表达，保留必要信息"};
 static int index(Context c){String id=c.getSharedPreferences("polish",0).getString("style","natural");for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 2;}
 static String id(Context c){return IDS[index(c)];}
 static String label(Context c){return LABELS[index(c)];}
 static void select(Context c,int index){if(index>=0&&index<IDS.length)c.getSharedPreferences("polish",0).edit().putString("style",IDS[index]).apply();}
}
