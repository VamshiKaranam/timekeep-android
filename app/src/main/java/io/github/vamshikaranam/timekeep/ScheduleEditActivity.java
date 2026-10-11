package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Create or change one schedule: name, days, hours and which apps it blocks. */
public class ScheduleEditActivity extends Activity {
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Store store;
    private Schedule schedule;
    private boolean isNew;
    private List<String> installed;
    private EditText name;
    private TextView startView;
    private TextView endView;
    private TextView overnight;
    private TextView appsButton;
    private TextView appsList;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = Store.get(this);
        Intent in = getIntent();
        String id = in.getStringExtra("id");
        schedule = id == null ? null : store.schedule(id);
        if (schedule == null) {
            isNew = true;
            schedule = new Schedule();
            schedule.name = in.getStringExtra("name") == null ? "" : in.getStringExtra("name");
            schedule.days = in.getIntExtra("days", schedule.days);
            schedule.start = in.getIntExtra("start", schedule.start);
            schedule.end = in.getIntExtra("end", schedule.end);
        }

        LinearLayout root = Ui.page(this);
        root.addView(Ui.back(this));
        Ui.add(root, Ui.bold(this, isNew ? "New schedule" : "Edit schedule", 24, R.color.text), 8);

        // Name
        LinearLayout nameCard = Ui.card(this);
        nameCard.addView(Ui.heading(this, "Name"));
        name = new EditText(this);
        name.setHint("Work hours");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(schedule.name);
        nameCard.addView(name);
        Ui.add(root, nameCard, 16);

        // Days
        LinearLayout daysCard = Ui.card(this);
        daysCard.addView(Ui.heading(this, "Days"));
        LinearLayout days = Ui.row(this);
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            final TextView chip = Ui.chip(this, Schedule.DAY_LETTER[i]);
            chip.setPadding(0, 0, 0, 0);
            chip.setSelected((schedule.days & bit) != 0);
            chip.setOnClickListener(v -> {
                schedule.days ^= bit;
                chip.setSelected((schedule.days & bit) != 0);
            });
            LinearLayout.LayoutParams lp = Ui.fill();
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            days.addView(chip, lp);
        }
        Ui.add(daysCard, days, 12);
        Ui.add(root, daysCard, 12);

        // Hours
        LinearLayout hoursCard = Ui.card(this);
        hoursCard.addView(Ui.heading(this, "Blocked from"));
        LinearLayout hours = Ui.row(this);
        startView = Ui.button(this, "", false);
        endView = Ui.button(this, "", false);
        TextView to = Ui.text(this, "to", 15, R.color.muted);
        to.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        hours.addView(startView, Ui.fill());
        hours.addView(to);
        hours.addView(endView, Ui.fill());
        Ui.add(hoursCard, hours, 12);
        overnight = Ui.text(this, "", 13, R.color.muted);
        Ui.add(hoursCard, overnight, 10);
        startView.setOnClickListener(v -> pickTime(true));
        endView.setOnClickListener(v -> pickTime(false));
        Ui.add(root, hoursCard, 12);
        renderHours();

        // Apps
        LinearLayout appsCard = Ui.card(this);
        appsCard.addView(Ui.heading(this, "Apps to block"));
        appsList = Ui.text(this, "", 15, R.color.text);
        Ui.add(appsCard, appsList, 10);
        appsButton = Ui.button(this, "Loading apps…", false);
        appsButton.setEnabled(false);
        appsButton.setOnClickListener(v -> pickApps());
        Ui.add(appsCard, appsButton, 12);
        Ui.add(root, appsCard, 12);
        renderApps();

        TextView save = Ui.button(this, "Save", true);
        save.setOnClickListener(v -> save());
        Ui.add(root, save, 24);

        if (!isNew) {
            TextView delete = Ui.bold(this, "Delete schedule", 15, R.color.warn);
            delete.setGravity(android.view.Gravity.CENTER);
            delete.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Delete this schedule?")
                    .setPositiveButton("Delete", (d, w) -> {
                        store.deleteSchedule(schedule.id);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            Ui.add(root, delete, 8);
        }

        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                final List<String> apps = Ui.launchableApps(app);
                runOnUiThread(() -> {
                    installed = apps;
                    appsButton.setEnabled(true);
                    renderApps();
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

    private void renderHours() {
        startView.setText(Ui.clock(this, schedule.start));
        endView.setText(Ui.clock(this, schedule.end));
        if (schedule.start == schedule.end) {
            overnight.setText("Same start and end: blocks a full 24 hours from the start time.");
        } else if (schedule.start > schedule.end) {
            overnight.setText("Runs overnight and ends the next morning.");
        } else {
            overnight.setText("");
        }
    }

    private void pickTime(final boolean start) {
        int t = start ? schedule.start : schedule.end;
        new TimePickerDialog(this, (view, hour, minute) -> {
            if (start) schedule.start = hour * 60 + minute;
            else schedule.end = hour * 60 + minute;
            renderHours();
        }, t / 60, t % 60, DateFormat.is24HourFormat(this)).show();
    }

    private void renderApps() {
        int n = schedule.apps.size();
        if (installed != null) {
            appsButton.setText(n == 0 ? "Choose apps" : "Change apps (" + n + ")");
        }
        if (n == 0) {
            appsList.setText("None chosen yet.");
            appsList.setTextColor(getColor(R.color.muted));
            return;
        }
        List<String> names = new ArrayList<>();
        for (String p : schedule.apps) names.add(Ui.label(this, p));
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        StringBuilder sb = new StringBuilder();
        for (String s : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        appsList.setText(sb);
        appsList.setTextColor(getColor(R.color.text));
    }

    private void pickApps() {
        if (installed == null) return;
        // Already-chosen apps first, then the rest alphabetically.
        final List<String> order = new ArrayList<>();
        for (String p : installed) if (schedule.apps.contains(p)) order.add(p);
        for (String p : installed) if (!schedule.apps.contains(p)) order.add(p);
        final String[] labels = new String[order.size()];
        final boolean[] checked = new boolean[order.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Ui.label(this, order.get(i));
            checked[i] = schedule.apps.contains(order.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("Apps to block")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Done", (dialog, which) -> {
                    // Rebuilding from the list also drops apps that have since been uninstalled.
                    schedule.apps.clear();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) schedule.apps.add(order.get(i));
                    }
                    renderApps();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save() {
        if (schedule.days == 0) {
            Toast.makeText(this, "Pick at least one day.", Toast.LENGTH_SHORT).show();
            return;
        }
        schedule.name = name.getText().toString().trim();
        if (schedule.name.isEmpty()) schedule.name = "Schedule";
        store.saveSchedule(schedule);
        if (schedule.apps.isEmpty()) {
            Toast.makeText(this, "Saved. It won't block anything until you choose apps.",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
