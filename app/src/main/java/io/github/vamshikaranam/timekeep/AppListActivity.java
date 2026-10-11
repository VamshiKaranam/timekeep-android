package io.github.vamshikaranam.timekeep;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Every installed app, searchable, to set rules for ones not used today. */
public class AppListActivity extends Activity {
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private String query = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.col(this);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 12), p, 0);
        root.addView(Ui.back(this));
        Ui.add(root, Ui.bold(this, "All apps", 24, R.color.text), 8);

        EditText search = new EditText(this);
        search.setHint("Search");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                filter();
            }
        });
        Ui.add(root, search, 8);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, AppRulesActivity.class).putExtra("pkg", shown.get(position))));
        root.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        setContentView(root);

        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                final List<String> apps = Ui.launchableApps(app);
                runOnUiThread(() -> {
                    all.clear();
                    all.addAll(apps);
                    filter();
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged(); // rule summaries may have changed
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    private void filter() {
        shown.clear();
        for (String pkg : all) {
            if (query.isEmpty() || Ui.label(this, pkg).toLowerCase(Locale.getDefault()).contains(query)) {
                shown.add(pkg);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static final class Holder {
        ImageView icon;
        TextView name;
        TextView rules;
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Context c = AppListActivity.this;
            Holder h;
            if (convert == null) {
                LinearLayout row = Ui.row(c);
                int v = Ui.dp(c, 10);
                row.setPadding(0, v, 0, v);
                h = new Holder();
                h.icon = Ui.appIcon(c, null, 40);
                row.addView(h.icon);
                LinearLayout text = Ui.col(c);
                h.name = Ui.bold(c, "", 16, R.color.text);
                h.rules = Ui.text(c, "", 13, R.color.accent);
                text.addView(h.name);
                text.addView(h.rules);
                LinearLayout.LayoutParams lp = Ui.fill();
                lp.leftMargin = Ui.dp(c, 14);
                row.addView(text, lp);
                row.setTag(h);
                convert = row;
            } else {
                h = (Holder) convert.getTag();
            }
            String pkg = shown.get(position);
            h.icon.setImageDrawable(Ui.icon(c, pkg));
            h.name.setText(Ui.label(c, pkg));
            String rules = Store.get(c).ruleSummary(pkg);
            h.rules.setText(rules);
            h.rules.setVisibility(rules.isEmpty() ? View.GONE : View.VISIBLE);
            return convert;
        }
    }
}
