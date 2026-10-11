package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** The list of blocking schedules. */
public class SchedulesActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = Ui.page(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        final Store store = Store.get(this);
        root.removeAllViews();
        root.addView(Ui.back(this));
        Ui.add(root, Ui.bold(this, "Schedules", 24, R.color.text), 8);
        Ui.add(root, Ui.text(this, "Block a group of apps at set hours.", 15, R.color.muted), 4);

        List<Schedule> list = store.schedules();
        for (final Schedule s : list) Ui.add(root, scheduleCard(store, s), 12);

        TextView add = Ui.button(this, "New schedule", true);
        add.setOnClickListener(v -> startActivity(new Intent(this, ScheduleEditActivity.class)));
        Ui.add(root, add, 20);

        if (list.isEmpty()) {
            Ui.add(root, Ui.heading(this, "Or start from"), 28);
            Ui.add(root, preset("Work hours", "Weekdays, 9 to 5", 0b0011111, 9 * 60, 17 * 60), 10);
            Ui.add(root, preset("Bedtime", "Every night, 10 pm to 7 am", 0b1111111, 22 * 60, 7 * 60), 10);
        }
    }

    private View scheduleCard(final Store store, final Schedule s) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        LinearLayout text = Ui.col(this);
        text.addView(Ui.bold(this, s.name.trim().isEmpty() ? "Schedule" : s.name.trim(), 17, R.color.text));
        Ui.add(text, Ui.text(this, s.daysLabel() + " · " + s.timeLabel(this), 14, R.color.muted), 2);
        int n = s.apps.size();
        Ui.add(text, Ui.text(this, n == 0 ? "No apps chosen yet" : n + (n == 1 ? " app" : " apps"),
                14, n == 0 ? R.color.warn : R.color.muted), 2);
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.rightMargin = Ui.dp(this, 12);
        row.addView(text, lp);

        Switch sw = new Switch(this);
        sw.setChecked(s.enabled);
        sw.setOnCheckedChangeListener((b, on) -> {
            s.enabled = on;
            store.saveSchedule(s);
        });
        row.addView(sw);
        card.addView(row);

        TextView edit = Ui.bold(this, "Edit", 14, R.color.accent);
        edit.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 24), 0);
        edit.setLayoutParams(Ui.lp(Ui.WRAP, Ui.WRAP));
        View.OnClickListener open = v ->
                startActivity(new Intent(this, ScheduleEditActivity.class).putExtra("id", s.id));
        edit.setOnClickListener(open);
        text.setOnClickListener(open);
        card.addView(edit);
        return card;
    }

    private View preset(final String name, String detail, final int days, final int start, final int end) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.bold(this, name, 16, R.color.text));
        Ui.add(card, Ui.text(this, detail, 14, R.color.muted), 2);
        card.setClickable(true);
        card.setOnClickListener(v -> startActivity(new Intent(this, ScheduleEditActivity.class)
                .putExtra("name", name)
                .putExtra("days", days)
                .putExtra("start", start)
                .putExtra("end", end)));
        return card;
    }
}
