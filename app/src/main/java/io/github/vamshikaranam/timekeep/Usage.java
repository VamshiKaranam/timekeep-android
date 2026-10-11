package io.github.vamshikaranam.timekeep;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Screen time, worked out from Android's own record of when each app came to the
 * front and left again. Call from a background thread.
 */
final class Usage {
    private Usage() {
    }

    static final long MINUTE = 60_000L;
    static final long HOUR = 3_600_000L;

    // UsageEvents.Event type numbers (several only have names on newer Android versions).
    private static final int RESUMED = 1;
    private static final int PAUSED = 2;
    private static final int SCREEN_OFF = 16;
    private static final int STOPPED = 23;
    private static final int SHUTDOWN = 26;
    private static final int STARTUP = 27;

    /** One stretch of an app being on screen. */
    static final class Interval {
        final String pkg;
        final long start;
        final long end;

        Interval(String pkg, long start, long end) {
            this.pkg = pkg;
            this.start = start;
            this.end = end;
        }
    }

    /** Midnight of the day containing {@code t}, shifted by whole days. */
    static long startOfDay(long t, int offsetDays) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (offsetDays != 0) c.add(Calendar.DAY_OF_YEAR, offsetDays);
        return c.getTimeInMillis();
    }

    /**
     * Every stretch of app use that overlaps [from, to]. Looks a few hours further back
     * so that an app opened before {@code from} and still in use is counted.
     */
    static List<Interval> intervals(Context c, long from, long to) {
        List<Interval> out = new ArrayList<>();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return out;
        long end = Math.min(to, System.currentTimeMillis());
        UsageEvents events;
        try {
            events = usm.queryEvents(from - 3 * HOUR, end);
        } catch (RuntimeException e) {
            return out;
        }
        if (events == null) return out;

        HashMap<String, Long> open = new HashMap<>(); // "package/activity" -> time it came to the front
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            long ts = e.getTimeStamp();
            if (type == RESUMED) {
                String pkg = e.getPackageName();
                if (pkg == null) continue;
                String key = pkg + "/" + e.getClassName();
                if (!open.containsKey(key)) open.put(key, ts);
            } else if (type == PAUSED || type == STOPPED) {
                String pkg = e.getPackageName();
                if (pkg == null) continue;
                Long s = open.remove(pkg + "/" + e.getClassName());
                if (s != null && ts > s) out.add(new Interval(pkg, s, ts));
            } else if (type == SCREEN_OFF || type == SHUTDOWN) {
                closeAll(open, ts, out);
            } else if (type == STARTUP) {
                open.clear();
            }
        }
        closeAll(open, end, out);
        return out;
    }

    private static void closeAll(HashMap<String, Long> open, long ts, List<Interval> out) {
        for (Map.Entry<String, Long> en : open.entrySet()) {
            String key = en.getKey();
            String pkg = key.substring(0, key.indexOf('/'));
            if (ts > en.getValue()) out.add(new Interval(pkg, en.getValue(), ts));
        }
        open.clear();
    }

    /** Time per app within [start, end], leaving out the home screen and this app. */
    static Map<String, Long> totals(Context c, List<Interval> list, long start, long end) {
        Map<String, Long> m = new HashMap<>();
        for (Interval i : list) {
            long s = Math.max(i.start, start);
            long e = Math.min(i.end, end);
            if (e <= s || !visible(c, i.pkg)) continue;
            Long cur = m.get(i.pkg);
            m.put(i.pkg, (cur == null ? 0L : cur) + (e - s));
        }
        return m;
    }

    static long sum(Map<String, Long> totals) {
        long t = 0;
        for (Long v : totals.values()) t += v;
        return t;
    }

    static long total(List<Interval> list, String pkg, long start, long end) {
        long t = 0;
        for (Interval i : list) {
            if (!i.pkg.equals(pkg)) continue;
            long s = Math.max(i.start, start);
            long e = Math.min(i.end, end);
            if (e > s) t += e - s;
        }
        return t;
    }

    /** How many separate times the app was opened within [start, end]. */
    static int opens(List<Interval> list, String pkg, long start, long end) {
        int n = 0;
        long lastEnd = 0;
        for (Interval i : list) {
            if (!i.pkg.equals(pkg)) continue;
            if (i.start >= start && i.start < end && i.start - lastEnd > 3000) n++;
            if (i.end > lastEnd) lastEnd = i.end;
        }
        return n;
    }

    static long usedToday(Context c, String pkg) {
        long now = System.currentTimeMillis();
        long day = startOfDay(now, 0);
        return total(intervals(c, day, now), pkg, day, now);
    }

    /** The app on screen right now according to the usage record, or null. */
    static String foregroundNow(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = usm.queryEvents(now - 10 * MINUTE, now);
        } catch (RuntimeException e) {
            return null;
        }
        if (events == null) return null;
        HashMap<String, Long> open = new HashMap<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            String pkg = e.getPackageName();
            if (type == RESUMED && pkg != null) {
                open.put(pkg + "/" + e.getClassName(), e.getTimeStamp());
            } else if ((type == PAUSED || type == STOPPED) && pkg != null) {
                open.remove(pkg + "/" + e.getClassName());
            } else if (type == SCREEN_OFF || type == SHUTDOWN || type == STARTUP) {
                open.clear();
            }
        }
        String best = null;
        long bestTs = 0;
        for (Map.Entry<String, Long> en : open.entrySet()) {
            if (en.getValue() >= bestTs) {
                bestTs = en.getValue();
                best = en.getKey().substring(0, en.getKey().indexOf('/'));
            }
        }
        return best;
    }

    // ---- which apps are worth showing ----

    private static final Map<String, Boolean> VISIBLE = new ConcurrentHashMap<>();
    private static volatile String homePackage;

    /** False for the home screen, this app, and background pieces of the system with no app icon. */
    static boolean visible(Context c, String pkg) {
        Boolean cached = VISIBLE.get(pkg);
        if (cached != null) return cached;
        PackageManager pm = c.getPackageManager();
        if (homePackage == null) {
            ResolveInfo home = pm.resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            homePackage = home != null && home.activityInfo != null ? home.activityInfo.packageName : "";
        }
        boolean v = !pkg.equals(c.getPackageName())
                && !pkg.equals(homePackage)
                && pm.getLaunchIntentForPackage(pkg) != null;
        VISIBLE.put(pkg, v);
        return v;
    }
}
