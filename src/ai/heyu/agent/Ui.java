package ai.heyu.agent;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class Ui {
 static int BG=0xFFF2F3F1,INK=0xFF252823,MUTED=0xFF626861,ACCENT=0xFF93462E,LIGHT=0xFFF0DED5,GREEN=0xFF38634A,SURFACE=0xFFFFFFFF,TONAL=0xFFE5E8E2,OUTLINE=0xFFD5D9D2,ERROR=0xFFA53124,ON_ACCENT=0xFFFFFFFF;
 static void theme(Context c){boolean dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;BG=dark?0xFF20231F:0xFFF2F3F1;INK=dark?0xFFE8EAE3:0xFF252823;MUTED=dark?0xFFB1B7AC:0xFF626861;ACCENT=dark?0xFFFFB59B:0xFF93462E;LIGHT=dark?0xFF533729:0xFFF0DED5;SURFACE=dark?0xFF343831:0xFFFFFFFF;TONAL=dark?0xFF42473E:0xFFE5E8E2;OUTLINE=dark?0xFF51574C:0xFFD5D9D2;ERROR=dark?0xFFFFB4AB:0xFFA53124;ON_ACCENT=dark?0xFF44281C:0xFFFFFFFF;}
 static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
 static GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
 static android.graphics.drawable.Drawable touch(Context c,int color,float radius){return new RippleDrawable(ColorStateList.valueOf(0x22757575),bg(color,dp(c,radius)),bg(Color.WHITE,dp(c,radius)));}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 static TextView text(Context c,String s,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(c,2),1);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 static void space(LinearLayout p,int dp){Space s=new Space(p.getContext());p.addView(s,new LinearLayout.LayoutParams(1,dp(p.getContext(),dp)));}
 static Button button(Context c,String s,boolean primary,Runnable run){Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?ON_ACCENT:INK);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,12),dp(c,4),dp(c,12),dp(c,4));b.setStateListAnimator(null);b.setElevation(0);b.setBackground(touch(c,primary?ACCENT:TONAL,18));b.setOnClickListener(v->run.run());return b;}
 static void addButton(LinearLayout p,String s,boolean primary,Runnable run){Button b=button(p.getContext(),s,primary,run);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.height=dp(p.getContext(),48);lp.topMargin=dp(p.getContext(),8);p.addView(b,lp);}
 static LinearLayout card(Context c){LinearLayout v=column(c);v.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));v.setBackground(bg(SURFACE,dp(c,20)));return v;}
}
