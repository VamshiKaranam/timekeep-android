package io.github.vamshikaranam.timekeep;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Notices which app is on screen and, if that app has a rule, shows the pause or
 * block screen. It only receives "a different window opened" events: the app's name,
 * never what is on the screen.
 */
public class WatchService extends AccessibilityService {
    /** After passing the pause screen, leaving the app briefly doesn't bring it back. */
    private static final long PASS_GRACE_MS = 60_000L;

    /** App -> last moment it was on screen with the pause already passed. */
    private static final Map<String, Long> PASS = new HashMap<>();

    static void grantPass(String pkg) {
        synchronized (PASS) {
            PASS.put(pkg, System.currentTimeMillis());
        }
    }

    private static boolean hasPass(String pkg) {
        synchronized (PASS) {
            Long t = PASS.get(pkg);
            return t != null && System.currentTimeMillis() - t < PASS_GRACE_MS;
        }
    }

    private static volatile WatchService instance;

    /**
     * Called by the block screen when it lets the user back into an app. Usually the app
     * announces itself when it reappears; this covers the cases where it doesn't.
     */
    static void returningTo(final String pkg) {
        final WatchService s = instance;
        if (s == null) return;
        s.main.postDelayed(() -> {
            if (s.getPackageName().equals(s.current)) s.onForeground(pkg);
        }, 900);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> activityCache = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Runnable tick = this::check;
    private ExecutorService bg;
    private BroadcastReceiver screenReceiver;
    /** The app currently on screen, or null when unknown (screen off). */
    private String current;
    /** Bumped whenever the app on screen changes, so stale answers are dropped. */
    private int generation;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (bg == null) bg = Executors.newSingleThreadExecutor();
        if (screenReceiver != null) return;
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    leaveCurrent();
                    current = null;
                    generation++;
                    main.removeCallbacks(tick);
                } else {
                    // Screen on or unlocked: the app underneath may come back without a window event.
                    main.postDelayed(WatchService.this::resync, 800);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (p == null || cls == null) return;
        String pkg = p.toString();
        // Keyboards, pop-ups and the notification shade also send this event. Only a real
        // app screen (an activity) counts as "this app is now in front".
        if (!isActivity(pkg, cls.toString())) return;
        onForeground(pkg);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        main.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            screenReceiver = null;
        }
        if (bg != null) bg.shutdownNow();
        super.onDestroy();
    }

    private boolean isActivity(String pkg, String cls) {
        String key = pkg + "/" + cls;
        Boolean known = activityCache.get(key);
        if (known != null) return known;
        boolean is;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            is = true;
        } catch (PackageManager.NameNotFoundException e) {
            is = false;
        }
        if (activityCache.size() > 400) activityCache.clear();
        activityCache.put(key, is);
        return is;
    }

    private void leaveCurrent() {
        // Remember when a passed app was last in front, so a quick return skips the pause.
        if (current != null && hasPass(current)) grantPass(current);
    }

    private void onForeground(String pkg) {
        if (pkg.equals(current)) return;
        leaveCurrent();
        current = pkg;
        generation++;
        main.removeCallbacks(tick);
        if (pkg.equals(getPackageName())) return;
        if (!Store.get(this).isManaged(pkg)) return;
        check();
    }

    private void resync() {
        if (current != null || bg == null) return;
        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                final String pkg = Usage.foregroundNow(app);
                if (pkg != null) {
                    main.post(() -> {
                        if (current == null) onForeground(pkg);
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    /** Works out, off the main thread, whether the app on screen should be interrupted. */
    private void check() {
        final String pkg = current;
        if (pkg == null || bg == null) return;
        final int gen = generation;
        final boolean passed = hasPass(pkg);
        final Context app = getApplicationContext();
        try {
            bg.execute(() -> {
                final Rules.Decision d = Rules.evaluate(app, pkg, passed);
                main.post(() -> apply(d, gen, passed));
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void apply(Rules.Decision d, int gen, boolean passed) {
        if (gen != generation || !d.pkg.equals(current)) return; // the user has moved on
        if (d.kind == Rules.NONE) {
            if (passed) grantPass(d.pkg);
            warnIfNearlyOut(d);
        } else {
            Intent i = new Intent(this, BlockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(BlockActivity.X_KIND, d.kind);
            i.putExtra(BlockActivity.X_PKG, d.pkg);
            i.putExtra(BlockActivity.X_USED, d.usedMs);
            i.putExtra(BlockActivity.X_OPENS, d.opens);
            i.putExtra(BlockActivity.X_LIMIT, d.limitMin);
            i.putExtra(BlockActivity.X_SCHEDULE, d.scheduleName);
            i.putExtra(BlockActivity.X_ENDS, d.scheduleEnds);
            try {
                startActivity(i);
            } catch (RuntimeException ignored) {
            }
        }
        main.removeCallbacks(tick);
        main.postDelayed(tick, d.nextCheckMs);
    }

    /** A heads-up shortly before a daily limit runs out. */
    private void warnIfNearlyOut(Rules.Decision d) {
        if (d.limitMin <= 0 || d.usedMs < 0) return;
        long left = d.limitMin * Usage.MINUTE - d.usedMs;
        int level;
        if (left <= Usage.MINUTE) level = 1;
        else if (left <= 5 * Usage.MINUTE && d.limitMin > 5) level = 5;
        else return;
        String key = d.pkg + ":" + Usage.startOfDay(System.currentTimeMillis(), 0) + ":" + level;
        if (!warned.add(key)) return;
        if (warned.size() > 200) {
            warned.clear();
            warned.add(key);
        }
        String msg = level == 1
                ? "Less than a minute left for " + Ui.label(this, d.pkg) + " today"
                : "About 5 minutes left for " + Ui.label(this, d.pkg) + " today";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
