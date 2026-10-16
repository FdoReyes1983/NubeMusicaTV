package cl.fernando.nubemusica;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import androidx.core.view.*;
import androidx.core.graphics.Insets;
import com.google.android.material.button.MaterialButton;

/** Shared native controls; no network resources or artwork placeholders disguised as covers. */
final class MusicUi {
    static final int BG=0xff0c1322,CARD=0xff182335,INK=0xffeef4f8,MUTED=0xffa8b7c9,ACCENT=0xff69e1bf;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout col(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static LinearLayout.LayoutParams fill(int height){return new LinearLayout.LayoutParams(-1,height);}
    static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static GradientDrawable round(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static MaterialButton button(Context c,String label,boolean primary){
        MaterialButton v=new MaterialButton(c);v.setText(label);v.setAllCaps(false);v.setTextSize(13);
        v.setMinWidth(0);v.setMinimumWidth(0);v.setMinimumHeight(0);v.setCornerRadius(dp(c,16));
        v.setPadding(dp(c,10),0,dp(c,10),0);v.setTextColor(primary?BG:ACCENT);
        v.setBackgroundTintList(ColorStateList.valueOf(primary?ACCENT:Color.TRANSPARENT));TvUi.focus(v);return v;
    }
    static ImageButton icon(Context c,int resource,String description){ImageButton v=new ImageButton(c);v.setImageResource(resource);v.setImageTintList(ColorStateList.valueOf(INK));v.setBackgroundColor(Color.TRANSPARENT);v.setContentDescription(description);TvUi.focus(v);return v;}
    static LinearLayout screen(Activity a){
        WindowCompat.setDecorFitsSystemWindows(a.getWindow(),false);
        LinearLayout root=col(a);root.setBackgroundColor(BG);a.setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets b=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            int side=TvUi.isTv(a)?dp(a,32):dp(a,16);int top=TvUi.isTv(a)?dp(a,18):dp(a,6);int bottom=TvUi.isTv(a)?dp(a,18):dp(a,8);v.setPadding(side+b.left,top+b.top,side+b.right,bottom+b.bottom);return insets;
        });return root;
    }
    static LinearLayout header(Activity a,LinearLayout root,String title){
        LinearLayout bar=row(a);MaterialButton back=button(a,"‹",false);back.setTextSize(30);back.setContentDescription("Volver");back.setOnClickListener(v->a.finish());
        bar.addView(back,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
        TextView name=text(a,title,19,INK);name.setTypeface(null,Typeface.BOLD);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(name,new LinearLayout.LayoutParams(0,dp(a,48),1));root.addView(bar);return bar;
    }
}
