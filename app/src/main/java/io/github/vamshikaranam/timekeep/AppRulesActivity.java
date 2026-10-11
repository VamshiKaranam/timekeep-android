package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** One app: its recent use, and its daily limit and pause switch. */
public class AppRulesActivity extends Activity {
    private static final int[] QUICK = {15, 30, 60, 120};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private String pkg;
    private Store store;
    private LinearLayout stats;
    private LinearLayout limitBody;
    private LinearLayout scheduleBody;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) {
            finish();
            return;
        }
        store = Store.get(this);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.back(this));

        LinearLayout head = Ui.row(this);
        head.addView(Ui.appIcon(this, Ui.icon(this, pkg), 52));
        TextView name = Ui.bold(this, Ui.label(this, pkg), 24, R.color.text);
        name.setPadding(Ui.dp(this, 14), 0, 0, 0);
        head.addView(name, Ui.fill());
        Ui.add(root, head, 8);

        stats = Ui.card(this);
        stats.addView(Ui.text(this, "Usage for this app will show here once usage access is on.", 14, R.color.muted));
        Ui.add(root, stats, 20);

        // Daily limit
        LinearLayout limitCard = Ui.card(this);
        Switch limitSwitch = new Switch(this);
        limitSwitch.setChecked(store.limit(pkg) != null);
        limitCard.addView(switchRow("Daily limit",
                "Block this app once it has been used this long in a day.", limitSwitch));
        limitBody = Ui.col(this);
        limitCard.addView(limitBody);
        limitSwitch.setOnCheckedChangeListener((b, on) -> {
            store.setLimit(pkg, on ? Integer.valueOf(30) : null);
            renderLimit();
        });
        Ui.add(root, limitCard, 12);
        renderLimit();

        // Pause before opening
        LinearLayout pauseCard = Ui.card(this);
        Switch pauseSwitch = new Switch(this);
        pauseSwitch.setChecked(store.pauseOn(pkg));
        pauseCard.addView(switchRow("Pause before opening",
                "Show a " + store.pauseSeconds() + "-second countdown each time you open this app, "
                        + "so opening it is a choice rather than a reflex.", pauseSwitch));
        pauseSwitch.setOnCheckedChangeListener((b, on) -> store.setPause(pkg, on));
        Ui.add(root, pauseCard, 12);

        // Schedules
        LinearLayout scheduleCard = Ui.card(this);
        scheduleCard.addView(Ui.bold(this, "Schedules", 17, R.color.text));
        scheduleBody = Ui.col(this);
        scheduleCard.addView(scheduleBody);
        TextView manage = Ui.bold(this, "Manage schedules", 14, R.color.accent);
        manage.setPadding(0, Ui.dp(this, 12), 0, 0);
        manage.setOnClickListener(v -> startActivity(new Intent(this, SchedulesActivity.class)));
        scheduleCard.addView(manage);
        Ui.add(root, scheduleCard, 12);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pkg == null) return;
        renderSchedules();
        loadStats();
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    private View switchRow(String title, String why, Switch sw) {
        LinearLayout row = Ui.row(this);
        LinearLayout text = Ui.col(this);
        text.addView(Ui.bold(this, title, 17, R.color.text));
        Ui.add(text, Ui.text(this, why, 14, R.color.muted), 2);
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.rightMargin = Ui.dp(this, 12);
        row.addView(text, lp);
        row.addView(sw);
        return row;
    }

    private void renderLimit() {
        limitBody.removeAllViews();
        final Integer limit = store.limit(pkg);
        if (limit == null) return;

        LinearLayout stepper = Ui.row(this);
        stepper.setGravity(Gravity.CENTER);
        TextView minus = Ui.button(this, "−", false);
        TextView plus = Ui.button(this, "+", false);
        TextView value = Ui.bold(this, Ui.dur(limit * Usage.MINUTE), 26, R.color.text);
        value.setGravity(Gravity.CENTER);
        int side = Ui.dp(this, 56);
        stepper.addView(minus, Ui.lp(side, side));
        stepper.addView(value, Ui.fill());
        stepper.addView(plus, Ui.lp(side, side));
        minus.setOnClickListener(v -> setLimit(limit - (limit > 60 ? 15 : 5)));
        plus.setOnClickListener(v -> setLimit(limit + (limit >= 60 ? 15 : 5)));
        Ui.add(limitBody, stepper, 16);

        LinearLayout quick = Ui.row(this);
        for (int i = 0; i < QUICK.length; i++) {
            final int minutes = QUICK[i];
            TextView chip = Ui.chip(this, Ui.dur(minutes * Usage.MINUTE));
            chip.setSelected(limit == minutes);
            chip.setOnClickListener(v -> setLimit(minutes));
            LinearLayout.LayoutParams lp = Ui.fill();
            if (i > 0) lp.leftMargin = Ui.dp(this, 8);
            quick.addView(chip, lp);
        }
        Ui.add(limitBody, quick, 14);
        Ui.add(limitBody, Ui.text(this, "Resets at midnight. The block screen offers "
                + store.extraMinutes() + " more " + (store.extraMinutes() == 1 ? "minute" : "minutes")
                + " if you need them.", 13, R.color.muted), 12);
    }

    private void setLimit(int minutes) {
        store.setLimit(pkg, Math.max(5, Math.min(12 * 60, minutes)));
        renderLimit();
    }

    private void renderSchedules() {
        scheduleBody.removeAllViews();
        List<Schedule> list = store.schedulesFor(pkg);
        if (list.isEmpty()) {
            Ui.add(scheduleBody, Ui.text(this, "This app isn't in any schedule. Schedules block a group of "
                    + "apps at set hours, like work time or bedtime.", 14, R.color.muted), 4);
            return;
        }
        for (Schedule s : list) {
            String name = s.name.trim().isEmpty() ? "Schedule" : s.name.trim();
            String line = name + " — " + s.daysLabel() + ", " + s.timeLabel(this)
                    + (s.enabled ? "" : " (off)");
            Ui.add(scheduleBody, Ui.text(this, line, 14, R.color.muted), 6);
        }
    }

    private void loadStats() {
        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                if (!Perms.usage(app)) return;
                long now = System.currentTimeMillis();
                List<Usage.Interval> list = Usage.intervals(app, Usage.startOfDay(now, -6), now);
                final long[] week = new long[7];
                final String[] days = new String[7];
                SimpleDateFormat letter = new SimpleDateFormat("EEEEE", Locale.getDefault());
                for (int i = 0; i < 7; i++) {
                    long start = Usage.startOfDay(now, i - 6);
                    week[i] = Usage.total(list, pkg, start, Usage.startOfDay(now, i - 5));
                    days[i] = letter.format(new Date(start + 12 * Usage.HOUR));
                }
                final int opens = Usage.opens(list, pkg, Usage.startOfDay(now, 0), now);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    stats.removeAllViews();
                    LinearLayout head = Ui.row(this);
                    head.addView(Ui.heading(this, "Today"), Ui.fill());
                    head.addView(Ui.text(this, opens + (opens == 1 ? " open" : " opens"), 13, R.color.muted));
                    stats.addView(head);
                    Ui.add(stats, Ui.bold(this, Ui.dur(week[6]), 30, R.color.text), 2);
                    stats.addView(new BarChartView(this, week, days, 6), Ui.lp(Ui.MATCH, Ui.dp(this, 130)));
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }
}
