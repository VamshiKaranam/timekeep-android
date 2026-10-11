package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Small helpers for building screens in code and formatting what they show. */
final class Ui {
    private Ui() {
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final Map<String, String> LABELS = new ConcurrentHashMap<>();
    private static final Map<String, Drawable> ICONS = new ConcurrentHashMap<>();

    // ---- formatting ----

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** "45m", "2h 5m", "<1m". */
    static String dur(long ms) {
        long m = ms / 60000L;
        if (m == 0) return ms > 0 ? "<1m" : "0m";
        if (m < 60) return m + "m";
        long h = m / 60;
        m = m % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /** Clock time for a minute of the day, in the phone's 12/24-hour style. */
    static String clock(Context c, int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        return DateFormat.getTimeFormat(c).format(cal.getTime());
    }

    static String clock(Context c, long timestamp) {
        return DateFormat.getTimeFormat(c).format(new Date(timestamp));
    }

    // ---- app names and icons ----

    static String label(Context c, String pkg) {
        String l = LABELS.get(pkg);
        if (l != null) return l;
        PackageManager pm = c.getPackageManager();
        try {
            l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            l = pkg;
        }
        LABELS.put(pkg, l);
        return l;
    }

    static Drawable icon(Context c, String pkg) {
        Drawable d = ICONS.get(pkg);
        if (d != null) return d;
        PackageManager pm = c.getPackageManager();
        try {
            d = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            d = pm.getDefaultActivityIcon();
        }
        ICONS.put(pkg, d);
        return d;
    }

    /** Every app with a home-screen icon except this one, sorted by name. Slow; use off the main thread. */
    static List<String> launchableApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            if (r.activityInfo == null) continue;
            String p = r.activityInfo.packageName;
            if (!p.equals(c.getPackageName())) seen.add(p);
        }
        final Context app = c.getApplicationContext();
        List<String> out = new ArrayList<>(seen);
        for (String p : out) label(app, p);
        Collections.sort(out, (a, b) -> label(app, a).compareToIgnoreCase(label(app, b)));
        return out;
    }

    // ---- views ----

    static TextView text(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(c.getColor(colorRes));
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    static TextView bold(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = text(c, s, sp, colorRes);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Small all-caps section heading. */
    static TextView heading(Context c, CharSequence s) {
        TextView t = bold(c, s.toString().toUpperCase(), 12, R.color.muted);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackgroundResource(R.drawable.card_bg);
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        return l;
    }

    static TextView button(Context c, CharSequence s, boolean primary) {
        TextView t = bold(c, s, 16, primary ? R.color.on_accent : R.color.accent);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary);
        t.setMinHeight(dp(c, 52));
        int p = dp(c, 18);
        t.setPadding(p, dp(c, 10), p, dp(c, 10));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** A pill that shows selected / not selected. */
    static TextView chip(Context c, CharSequence s) {
        TextView t = bold(c, s, 14, R.color.text);
        t.setTextColor(c.getColorStateList(R.color.chip_text));
        t.setBackgroundResource(R.drawable.chip_bg);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 40));
        t.setMinWidth(dp(c, 40));
        int p = dp(c, 12);
        t.setPadding(p, 0, p, 0);
        t.setClickable(true);
        return t;
    }

    static ImageView appIcon(Context c, Drawable d, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    /** Takes the leftover width in a row. */
    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(0, WRAP, 1f);
    }

    /** Adds a view below the previous one with a top gap. */
    static <T extends View> T add(LinearLayout parent, T v, int topDp) {
        LinearLayout.LayoutParams p = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) v.getLayoutParams()
                : new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(parent.getContext(), topDp);
        parent.addView(v, p);
        return v;
    }

    static void ripple(View v) {
        TypedValue tv = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setBackgroundResource(tv.resourceId);
        v.setClickable(true);
        v.setFocusable(true);
    }

    /** Sets a scrolling page as the activity's content and returns the column to fill. */
    static LinearLayout page(Activity a) {
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout col = col(a);
        int p = dp(a, 20);
        col.setPadding(p, dp(a, 12), p, dp(a, 40));
        sv.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));
        a.setContentView(sv);
        return col;
    }

    /** "‹ Back" link that closes the screen. */
    static TextView back(final Activity a) {
        TextView t = bold(a, "‹  Back", 16, R.color.accent);
        t.setPadding(0, dp(a, 12), dp(a, 24), dp(a, 12));
        t.setOnClickListener(v -> a.finish());
        t.setLayoutParams(new LinearLayout.LayoutParams(WRAP, WRAP));
        return t;
    }
}
