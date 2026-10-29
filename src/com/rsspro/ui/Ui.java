package com.rsspro.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rsspro.R;
import com.rsspro.store.Store;

public final class Ui {
    private Ui() {
    }

    public static void applyTheme(Activity activity) {
        int mode = Store.readThemeMode(activity);
        if (mode == 1) {
            activity.setTheme(R.style.AppThemeLight);
        } else if (mode == 2) {
            activity.setTheme(R.style.AppThemeDark);
        }
    }

    public static void applySystemBars(Activity activity) {
        Window window = activity.getWindow();
        int background = color(activity, R.attr.rssBg);
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        boolean light = isColorLight(background);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (light && Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    public static int color(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            if (value.resourceId != 0) {
                return context.getResources().getColor(value.resourceId, context.getTheme());
            }
        }
        return 0xFF000000;
    }

    public static int bg(Context context) {
        return color(context, R.attr.rssBg);
    }

    public static int surface(Context context) {
        return color(context, R.attr.rssSurface);
    }

    public static int surfaceAlt(Context context) {
        return color(context, R.attr.rssSurfaceAlt);
    }

    public static int textColor(Context context) {
        return color(context, R.attr.rssText);
    }

    public static int secondaryColor(Context context) {
        return color(context, R.attr.rssTextSecondary);
    }

    public static int accent(Context context) {
        return color(context, R.attr.rssAccent);
    }

    public static int onAccent(Context context) {
        return color(context, R.attr.rssOnAccent);
    }

    public static int danger(Context context) {
        return color(context, R.attr.rssDanger);
    }

    public static int dividerColor(Context context) {
        return color(context, R.attr.rssDivider);
    }

    public static boolean isDark(Context context) {
        return !isColorLight(bg(context));
    }

    public static boolean isColorLight(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        double luminance = 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
        return luminance > 0.48;
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    public static TextView text(Context context, CharSequence value, float sizeSp,
                                int textColor, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        view.setLineSpacing(dp(context, 2), 1.0f);
        return view;
    }

    public static TextView action(Context context, String label, boolean primary) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(android.view.Gravity.CENTER);
        view.setMinHeight(dp(context, 40));
        view.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        view.setClickable(true);
        view.setFocusable(true);
        if (primary) {
            view.setTextColor(onAccent(context));
            view.setBackground(ripple(context, rounded(accent(context), dp(context, 12)), onAccent(context)));
        } else {
            view.setTextColor(accent(context));
            view.setBackground(ripple(context, rounded(0x00000000, dp(context, 12)), accent(context)));
        }
        return view;
    }

    public static TextView chip(Context context, String label, int dotColor) {
        TextView view = text(context, label, 11, dotColor, true);
        view.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        view.setBackground(rounded(blend(surfaceAlt(context), dotColor, 0.16f), dp(context, 20)));
        return view;
    }

    public static GradientDrawable rounded(int fillColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    public static Drawable card(Context context, boolean clickable) {
        GradientDrawable base = rounded(surface(context), dp(context, 16));
        base.setStroke(dp(context, 1), dividerColor(context));
        if (!clickable) {
            return base;
        }
        int rippleColor = accent(context);
        rippleColor = Color.argb(38, Color.red(rippleColor), Color.green(rippleColor),
                Color.blue(rippleColor));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null);
    }

    public static Drawable ripple(Context context, Drawable content, int rippleColor) {
        int color = Color.argb(44, Color.red(rippleColor), Color.green(rippleColor),
                Color.blue(rippleColor));
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static int blend(int base, int overlay, float amount) {
        float inverse = 1.0f - amount;
        int r = Math.round(Color.red(base) * inverse + Color.red(overlay) * amount);
        int g = Math.round(Color.green(base) * inverse + Color.green(overlay) * amount);
        int b = Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount);
        return Color.rgb(r, g, b);
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(dividerColor(context));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        return view;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.setMargins(left, top, right, bottom);
            view.setLayoutParams(margins);
        }
    }

    public static void padding(View view, int all) {
        view.setPadding(all, all, all, all);
    }

    public static void clipRounded(final View view, final float radiusPx) {
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radiusPx);
            }
        });
    }
}
