package io.github.vamshikaranam.timekeep;

import android.content.Context;

import java.util.List;

/** Decides what should happen when a given app is on screen. */
final class Rules {
    private Rules() {
    }

    static final int NONE = 0;
    static final int LIMIT = 1;
    static final int SCHEDULE = 2;
    static final int PAUSE = 3;

    static final class Decision {
        int kind = NONE;
        String pkg;
        /** Time used today; only filled in when the app has a limit or a pause. */
        long usedMs = -1;
        int opens;
        /** Daily limit in minutes, or 0 if none. */
        int limitMin;
        String scheduleName;
        long scheduleEnds;
        /** When to look again if the app stays on screen. */
        long nextCheckMs = 10_000L;
    }

    /**
     * Reads usage history, so call it from a background thread.
     *
     * @param pausePassed true if the user already sat through the pause for this visit
     */
    static Decision evaluate(Context c, String pkg, boolean pausePassed) {
        Store store = Store.get(c);
        long now = System.currentTimeMillis();
        Decision d = new Decision();
        d.pkg = pkg;

        long allow = store.allowUntil(pkg);
        if (allow > now) {
            d.nextCheckMs = Math.max(1000L, Math.min(d.nextCheckMs, allow - now + 300L));
            return d;
        }

        Schedule s = store.activeSchedule(pkg, now);
        if (s != null) {
            d.kind = SCHEDULE;
            d.scheduleName = s.name;
            d.scheduleEnds = s.endsAt(now);
            return d;
        }

        Integer limit = store.limit(pkg);
        boolean wantsPause = store.pauseOn(pkg) && !pausePassed;
        if (limit != null || wantsPause) {
            long day = Usage.startOfDay(now, 0);
            List<Usage.Interval> list = Usage.intervals(c, day, now);
            d.usedMs = Usage.total(list, pkg, day, now);
            d.opens = Usage.opens(list, pkg, day, now);
        }
        if (limit != null) {
            d.limitMin = limit;
            long left = limit * Usage.MINUTE - d.usedMs;
            if (left <= 0) {
                d.kind = LIMIT;
                return d;
            }
            d.nextCheckMs = Math.max(1000L, Math.min(d.nextCheckMs, left + 300L));
        }
        if (wantsPause) d.kind = PAUSE;
        return d;
    }
}
