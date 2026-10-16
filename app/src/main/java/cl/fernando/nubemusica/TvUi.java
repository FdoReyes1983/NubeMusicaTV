package cl.fernando.nubemusica;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

/** Small Android TV adaptations shared by every screen. */
final class TvUi {
    private TvUi() {}

    static boolean isTv(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    static void configure(Activity activity) {
        if (!isTv(activity)) return;
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    static void focus(View view) {
        if (!isTv(view.getContext())) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        if (Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(true);
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f).setDuration(90).start();
            v.setElevation(focused ? dp(v.getContext(), 10) : 0);
        });
    }

    static void row(View view) {
        if (!isTv(view.getContext())) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        if (view instanceof ViewGroup) ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
        view.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(v.getContext(), 14));
            bg.setColor(focused ? 0xff2b4653 : Color.TRANSPARENT);
            if (focused) bg.setStroke(dp(v.getContext(), 2), MusicUi.ACCENT);
            v.setBackground(bg);
            v.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f).setDuration(70).start();
        });
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
