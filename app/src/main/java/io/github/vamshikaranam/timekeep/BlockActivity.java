package io.github.vamshikaranam.timekeep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The screen that appears over an app: a countdown before opening it (pause), or a
 * stop when its daily limit is used up or a schedule is blocking it.
 */
public class BlockActivity extends Activity {
    static final String X_KIND = "kind";
    static final String X_PKG = "pkg";
    static final String X_USED = "used";
    static final String X_OPENS = "opens";
    static final String X_LIMIT = "limit";
    static final String X_SCHEDULE = "schedule";
    static final String X_ENDS = "ends";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pkg;
    private int kind;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        build(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String p = intent.getStringExtra(X_PKG);
        if (p != null && p.equals(pkg) && intent.getIntExtra(X_KIND, 0) == kind) return; // already showing it
        setIntent(intent);
        build(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Left via Home or Recents: don't linger behind other apps.
        if (!isChangingConfigurations() && !isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
        }
        finish();
    }

    /** Closes this screen so the app underneath comes back. */
    private void letThrough() {
        WatchService.grantPass(pkg);
        WatchService.returningTo(pkg);
        finishAndRemoveTask();
    }

    private void build(Intent in) {
        handler.removeCallbacksAndMessages(null);
        pkg = in.getStringExtra(X_PKG);
        kind = in.getIntExtra(X_KIND, Rules.NONE);
        if (pkg == null || kind == Rules.NONE) {
            finish();
            return;
        }
        final Store store = Store.get(this);
        final String app = Ui.label(this, pkg);
        long used = in.getLongExtra(X_USED, -1);
        int opens = in.getIntExtra(X_OPENS, 0);

        LinearLayout root = Ui.col(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, pad, pad, pad);
        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView title;
        TextView body;
        TextView primary;
        TextView secondary;
        TextView note = Ui.text(this, "", 13, R.color.muted);
        note.setGravity(Gravity.CENTER);

        if (kind == Rules.PAUSE) {
            final int seconds = Math.max(1, store.pauseSeconds());
            final RingView ring = new RingView(this, seconds);
            root.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 168), Ui.dp(this, 168)));

            title = Ui.bold(this, "Take a breath", 28, R.color.text);
            StringBuilder sb = new StringBuilder("Do you want to open " + app + " right now?");
            if (used >= 0) {
                sb.append("\n\nToday: ").append(Ui.dur(used));
                if (opens > 0) sb.append(" · opened ").append(opens).append(opens == 1 ? " time" : " times");
            }
            body = Ui.text(this, sb, 16, R.color.muted);

            primary = Ui.button(this, "Not now", true);
            primary.setOnClickListener(v -> goHome());
            secondary = Ui.button(this, "Open " + app, false);
            final TextView open = secondary;
            open.setEnabled(false);
            open.setAlpha(0.35f);
            open.setOnClickListener(v -> letThrough());

            final long startedAt = SystemClock.elapsedRealtime();
            final long total = seconds * 1000L;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.elapsedRealtime() - startedAt;
                    if (elapsed >= total) {
                        ring.set(1f, 0);
                        open.setEnabled(true);
                        open.setAlpha(1f);
                        return;
                    }
                    ring.set(elapsed / (float) total, (int) Math.ceil((total - elapsed) / 1000.0));
                    handler.postDelayed(this, 50);
                }
            });
        } else {
            root.addView(Ui.appIcon(this, Ui.icon(this, pkg), 76));
            if (kind == Rules.LIMIT) {
                int limit = in.getIntExtra(X_LIMIT, 0);
                title = Ui.bold(this, "Time's up", 28, R.color.text);
                body = Ui.text(this, "You've used " + app + " for " + Ui.dur(Math.max(0, used))
                        + " today. Your limit is " + Ui.dur(limit * Usage.MINUTE) + ".", 16, R.color.muted);
            } else {
                String name = in.getStringExtra(X_SCHEDULE);
                long ends = in.getLongExtra(X_ENDS, 0);
                title = Ui.bold(this, "Not right now", 28, R.color.text);
                String during = name == null || name.trim().isEmpty() ? "a schedule" : "“" + name.trim() + "”";
                body = Ui.text(this, app + " is blocked during " + during
                        + (ends > 0 ? ", until " + Ui.clock(this, ends) + "." : "."), 16, R.color.muted);
            }
            primary = Ui.button(this, "Close " + app, true);
            primary.setOnClickListener(v -> goHome());
            final int extra = store.extraMinutes();
            secondary = Ui.button(this, extra + (extra == 1 ? " more minute" : " more minutes"), false);
            secondary.setOnClickListener(v -> {
                store.grantExtra(pkg);
                letThrough();
            });
            int n = store.overridesToday(pkg);
            if (n > 0) {
                note.setText("You've already added extra time " + (n == 1 ? "once" : n + " times") + " today.");
            }
        }

        title.setGravity(Gravity.CENTER);
        body.setGravity(Gravity.CENTER);
        Ui.add(root, title, 28);
        Ui.add(root, body, 12);
        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1.4f));
        Ui.add(root, primary, 0);
        Ui.add(root, secondary, 12);
        Ui.add(root, note, 14);
        setContentView(root);
    }

    /** A circle that fills as the countdown runs, with the seconds left in the middle. */
    @SuppressLint("ViewConstructor")
    static final class RingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final int track, accent;
        private final float stroke;
        private float fraction;
        private int secondsLeft;

        RingView(Context c, int seconds) {
            super(c);
            secondsLeft = seconds;
            track = c.getColor(R.color.track);
            accent = c.getColor(R.color.accent);
            stroke = Ui.dp(c, 10);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            number.setColor(c.getColor(R.color.text));
            number.setTextAlign(Paint.Align.CENTER);
            number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }

        void set(float fraction, int secondsLeft) {
            this.fraction = fraction;
            this.secondsLeft = secondsLeft;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float half = stroke / 2f;
            oval.set(half, half, w - half, h - half);
            paint.setColor(track);
            canvas.drawArc(oval, 0, 360, false, paint);
            paint.setColor(accent);
            canvas.drawArc(oval, -90, 360f * Math.min(1f, fraction), false, paint);
            number.setTextSize(h * 0.36f);
            String s = secondsLeft > 0 ? String.valueOf(secondsLeft) : "✓";
            float y = h / 2f - (number.descent() + number.ascent()) / 2f;
            canvas.drawText(s, w / 2f, y, number);
        }
    }
}
