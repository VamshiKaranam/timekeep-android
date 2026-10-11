package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Home screen: today's screen time, the last seven days, and each app used today. */
public class MainActivity extends Activity {
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private int loadId;

    private static final class Row {
        String pkg;
        String label;
        Drawable icon;
        long ms;
        int opens;
    }

    private static final class Data {
        boolean usage;
        boolean accessibility;
        long today;
        long yesterday;
        final long[] week = new long[7];
        final String[] days = new String[7];
        final List<Row> rows = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = Ui.page(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final int id = ++loadId;
        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                final Data d = load(app);
                runOnUiThread(() -> {
                    if (id == loadId && !isFinishing() && !isDestroyed()) render(d);
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    private static Data load(Context c) {
        Data d = new Data();
        d.usage = Perms.usage(c);
        d.accessibility = Perms.accessibility(c);
        long now = System.currentTimeMillis();
        SimpleDateFormat letter = new SimpleDateFormat("EEEEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            d.days[i] = letter.format(new Date(Usage.startOfDay(now, i - 6) + 12 * Usage.HOUR));
        }
        if (!d.usage) return d;

        List<Usage.Interval> list = Usage.intervals(c, Usage.startOfDay(now, -6), now);
        Map<String, Long> today = null;
        for (int i = 0; i < 7; i++) {
            long start = Usage.startOfDay(now, i - 6);
            long end = Usage.startOfDay(now, i - 5);
            Map<String, Long> totals = Usage.totals(c, list, start, end);
            d.week[i] = Usage.sum(totals);
            if (i == 6) today = totals;
        }
        d.today = d.week[6];
        d.yesterday = d.week[5];
        long day = Usage.startOfDay(now, 0);
        if (today != null) {
            for (Map.Entry<String, Long> en : today.entrySet()) {
                if (en.getValue() < 30_000L) continue; // skip apps that only flashed by
                Row r = new Row();
                r.pkg = en.getKey();
                r.ms = en.getValue();
                r.label = Ui.label(c, r.pkg);
                r.icon = Ui.icon(c, r.pkg);
                r.opens = Usage.opens(list, r.pkg, day, now);
                d.rows.add(r);
            }
        }
        Collections.sort(d.rows, (a, b) -> Long.compare(b.ms, a.ms));
        return d;
    }

    private void render(Data d) {
        final Store store = Store.get(this);
        root.removeAllViews();

        // Title bar
        LinearLayout bar = Ui.row(this);
        bar.addView(Ui.bold(this, "Timekeep", 18, R.color.text), Ui.fill());
        TextView settings = Ui.bold(this, "Settings", 15, R.color.accent);
        settings.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), 0, Ui.dp(this, 12));
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings);
        root.addView(bar);

        if (!d.usage || !d.accessibility) Ui.add(root, setupCard(d), 8);

        // Today
        Ui.add(root, Ui.heading(this, "Today"), 20);
        TextView big = Ui.text(this, d.usage ? Ui.dur(d.today) : "—", 52, R.color.text);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        big.setIncludeFontPadding(false);
        Ui.add(root, big, 4);
        if (d.usage) {
            String vs = d.yesterday > 0
                    ? "Yesterday: " + Ui.dur(d.yesterday)
                    : "Screen time across all apps";
            Ui.add(root, Ui.text(this, vs, 14, R.color.muted), 4);
        }

        // Last 7 days
        LinearLayout chart = Ui.card(this);
        long sum = 0;
        int counted = 0;
        for (long v : d.week) {
            if (v > 0) {
                sum += v;
                counted++;
            }
        }
        LinearLayout chartHead = Ui.row(this);
        chartHead.addView(Ui.heading(this, "Last 7 days"), Ui.fill());
        if (counted > 0) {
            chartHead.addView(Ui.text(this, "Daily average " + Ui.dur(sum / counted), 13, R.color.muted));
        }
        chart.addView(chartHead);
        chart.addView(new BarChartView(this, d.week, d.days, 6), Ui.lp(Ui.MATCH, Ui.dp(this, 150)));
        Ui.add(root, chart, 20);

        // Shortcuts
        LinearLayout actions = Ui.row(this);
        int scheduleCount = store.schedules().size();
        TextView schedules = Ui.button(this,
                scheduleCount == 0 ? "Schedules" : "Schedules (" + scheduleCount + ")", false);
        schedules.setOnClickListener(v -> startActivity(new Intent(this, SchedulesActivity.class)));
        TextView all = Ui.button(this, "All apps", false);
        all.setOnClickListener(v -> startActivity(new Intent(this, AppListActivity.class)));
        LinearLayout.LayoutParams left = Ui.fill();
        left.rightMargin = Ui.dp(this, 6);
        LinearLayout.LayoutParams right = Ui.fill();
        right.leftMargin = Ui.dp(this, 6);
        actions.addView(schedules, left);
        actions.addView(all, right);
        Ui.add(root, actions, 12);

        // Apps used today
        Ui.add(root, Ui.heading(this, "Used today"), 28);
        if (!d.usage) {
            Ui.add(root, Ui.text(this, "Turn on usage access above to see your screen time.", 15, R.color.muted), 10);
        } else if (d.rows.isEmpty()) {
            Ui.add(root, Ui.text(this, "Nothing yet today.", 15, R.color.muted), 10);
        } else {
            Ui.add(root, Ui.text(this, "Tap an app to set a daily limit or a pause.", 14, R.color.muted), 4);
            long max = d.rows.get(0).ms;
            LinearLayout list = Ui.card(this);
            list.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            for (Row r : d.rows) list.addView(appRow(store, r, max));
            Ui.add(root, list, 12);
        }

        TextView foot = Ui.text(this, "Everything stays on this phone. Timekeep has no internet access.",
                13, R.color.muted);
        foot.setGravity(Gravity.CENTER);
        Ui.add(root, foot, 28);
    }

    private View appRow(Store store, final Row r, long max) {
        LinearLayout row = Ui.row(this);
        Ui.ripple(row);
        int p = Ui.dp(this, 12);
        row.setPadding(p, p, p, p);
        row.addView(Ui.appIcon(this, r.icon, 40));

        LinearLayout mid = Ui.col(this);
        LinearLayout.LayoutParams midLp = Ui.fill();
        midLp.leftMargin = Ui.dp(this, 14);

        LinearLayout top = Ui.row(this);
        TextView name = Ui.bold(this, r.label, 16, R.color.text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, Ui.fill());

        Integer limit = store.limit(r.pkg);
        boolean over = limit != null && r.ms >= limit * Usage.MINUTE;
        TextView time = Ui.bold(this, Ui.dur(r.ms), 15, over ? R.color.warn : R.color.text);
        time.setPadding(Ui.dp(this, 10), 0, 0, 0);
        top.addView(time);
        mid.addView(top);

        float fraction = limit != null
                ? r.ms / (float) (limit * Usage.MINUTE)
                : (max > 0 ? r.ms / (float) max : 0f);
        int color = limit == null ? R.color.muted : (over ? R.color.warn : R.color.accent);
        LinearLayout.LayoutParams meterLp = Ui.lp(Ui.MATCH, Ui.dp(this, 5));
        meterLp.topMargin = Ui.dp(this, 7);
        mid.addView(new MeterView(this, fraction, color), meterLp);

        StringBuilder sub = new StringBuilder();
        sub.append(r.opens).append(r.opens == 1 ? " open" : " opens");
        String rules = store.ruleSummary(r.pkg);
        if (!rules.isEmpty()) sub.append(" · ").append(rules);
        TextView subText = Ui.text(this, sub, 13, R.color.muted);
        subText.setPadding(0, Ui.dp(this, 5), 0, 0);
        mid.addView(subText);

        row.addView(mid, midLp);
        row.setOnClickListener(v ->
                startActivity(new Intent(this, AppRulesActivity.class).putExtra("pkg", r.pkg)));
        return row;
    }

    private View setupCard(Data d) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.bold(this, "Finish setup", 18, R.color.text));
        Ui.add(card, Ui.text(this, "Timekeep needs two switches turned on in Android's settings.",
                14, R.color.muted), 4);

        Ui.add(card, setupStep("1. Usage access", "Lets Timekeep see how long each app is used.",
                d.usage, "Open usage access", v -> Perms.openUsageSettings(this)), 16);
        Ui.add(card, setupStep("2. Accessibility", "Lets Timekeep notice when an app opens, so it can show "
                        + "the pause or block screen. It sees the app's name only, never what's on screen.",
                d.accessibility, "Open accessibility", v -> Perms.openAccessibilitySettings(this)), 16);

        Ui.add(card, Ui.text(this, "If Android says “Restricted setting” or a switch is greyed out: "
                + "open App info below, tap the ⋮ menu at the top right, choose “Allow restricted "
                + "settings”, then try the switch again.", 13, R.color.muted), 18);
        TextView info = Ui.bold(this, "Open App info", 14, R.color.accent);
        info.setPadding(0, Ui.dp(this, 10), 0, 0);
        info.setOnClickListener(v -> Perms.openAppInfo(this));
        card.addView(info);
        return card;
    }

    private View setupStep(String title, String why, boolean done, String action, View.OnClickListener go) {
        LinearLayout col = Ui.col(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.bold(this, title, 16, R.color.text), Ui.fill());
        if (done) head.addView(Ui.bold(this, "✓ On", 14, R.color.accent));
        col.addView(head);
        Ui.add(col, Ui.text(this, why, 14, R.color.muted), 2);
        if (!done) {
            TextView b = Ui.button(this, action, true);
            b.setOnClickListener(go);
            Ui.add(col, b, 10);
        }
        return col;
    }

    private void showSettings() {
        final Store store = Store.get(this);
        String[] items = {
                "Pause length: " + store.pauseSeconds() + " seconds",
                "Extra time on the block screen: " + store.extraMinutes()
                        + (store.extraMinutes() == 1 ? " minute" : " minutes"),
                "Android usage access settings",
                "Android accessibility settings"
        };
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        choose("Pause length", new int[]{5, 10, 15, 20, 30}, " seconds",
                                store.pauseSeconds(), store::setPauseSeconds);
                    } else if (which == 1) {
                        choose("Extra time", new int[]{1, 2, 5, 10, 15}, " min",
                                store.extraMinutes(), store::setExtraMinutes);
                    } else if (which == 2) {
                        Perms.openUsageSettings(this);
                    } else {
                        Perms.openAccessibilitySettings(this);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private interface IntSetter {
        void set(int value);
    }

    private void choose(String title, final int[] options, String unit, int current, final IntSetter setter) {
        String[] labels = new String[options.length];
        int checked = -1;
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i] + unit;
            if (options[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setter.set(options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
