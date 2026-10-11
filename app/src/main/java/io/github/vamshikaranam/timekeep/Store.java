package io.github.vamshikaranam.timekeep;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All rules and settings, kept in memory and saved to this phone only. */
final class Store {
    private static Store instance;

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences prefs;
    private final Map<String, Integer> limits = new HashMap<>();
    private final Set<String> pause = new HashSet<>();
    private final List<Schedule> schedules = new ArrayList<>();
    private final Map<String, Long> allowUntil = new HashMap<>();
    private final Map<String, Integer> overrides = new HashMap<>();
    private long overridesDay;
    private int pauseSeconds;
    private int extraMinutes;

    private Store(Context c) {
        prefs = c.getSharedPreferences("timekeep", Context.MODE_PRIVATE);
        pauseSeconds = prefs.getInt("pauseSeconds", 10);
        extraMinutes = prefs.getInt("extraMinutes", 5);
        try {
            JSONObject l = new JSONObject(prefs.getString("limits", "{}"));
            for (Iterator<String> it = l.keys(); it.hasNext(); ) {
                String k = it.next();
                limits.put(k, l.getInt(k));
            }
        } catch (JSONException ignored) {
        }
        try {
            JSONArray a = new JSONArray(prefs.getString("pause", "[]"));
            for (int i = 0; i < a.length(); i++) pause.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        try {
            JSONArray a = new JSONArray(prefs.getString("schedules", "[]"));
            for (int i = 0; i < a.length(); i++) schedules.add(Schedule.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        try {
            JSONObject o = new JSONObject(prefs.getString("allowUntil", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                allowUntil.put(k, o.getLong(k));
            }
        } catch (JSONException ignored) {
        }
        try {
            JSONObject o = new JSONObject(prefs.getString("overrides", "{}"));
            overridesDay = o.optLong("day", 0);
            JSONObject counts = o.optJSONObject("counts");
            if (counts != null) {
                for (Iterator<String> it = counts.keys(); it.hasNext(); ) {
                    String k = it.next();
                    overrides.put(k, counts.getInt(k));
                }
            }
        } catch (JSONException ignored) {
        }
    }

    // ---- daily limits ----

    /** Daily limit in minutes, or null if the app has none. */
    synchronized Integer limit(String pkg) {
        return limits.get(pkg);
    }

    synchronized void setLimit(String pkg, Integer minutes) {
        if (minutes == null) limits.remove(pkg);
        else limits.put(pkg, minutes);
        prefs.edit().putString("limits", new JSONObject(limits).toString()).apply();
    }

    // ---- pause before opening ----

    synchronized boolean pauseOn(String pkg) {
        return pause.contains(pkg);
    }

    synchronized void setPause(String pkg, boolean on) {
        if (on) pause.add(pkg);
        else pause.remove(pkg);
        prefs.edit().putString("pause", new JSONArray(pause).toString()).apply();
    }

    synchronized int pauseSeconds() {
        return pauseSeconds;
    }

    synchronized void setPauseSeconds(int s) {
        pauseSeconds = s;
        prefs.edit().putInt("pauseSeconds", s).apply();
    }

    // ---- schedules ----

    synchronized List<Schedule> schedules() {
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : schedules) out.add(s.copy());
        return out;
    }

    synchronized Schedule schedule(String id) {
        for (Schedule s : schedules) if (s.id.equals(id)) return s.copy();
        return null;
    }

    synchronized void saveSchedule(Schedule s) {
        Schedule c = s.copy();
        boolean replaced = false;
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).id.equals(c.id)) {
                schedules.set(i, c);
                replaced = true;
                break;
            }
        }
        if (!replaced) schedules.add(c);
        persistSchedules();
    }

    synchronized void deleteSchedule(String id) {
        for (Iterator<Schedule> it = schedules.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) it.remove();
        }
        persistSchedules();
    }

    private void persistSchedules() {
        JSONArray a = new JSONArray();
        try {
            for (Schedule s : schedules) a.put(s.toJson());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString("schedules", a.toString()).apply();
    }

    /** The schedule blocking this app right now, or null. */
    synchronized Schedule activeSchedule(String pkg, long now) {
        for (Schedule s : schedules) {
            if (s.apps.contains(pkg) && s.activeAt(now)) return s.copy();
        }
        return null;
    }

    synchronized List<Schedule> schedulesFor(String pkg) {
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : schedules) if (s.apps.contains(pkg)) out.add(s.copy());
        return out;
    }

    /** True if any rule at all applies to this app. */
    synchronized boolean isManaged(String pkg) {
        if (limits.containsKey(pkg) || pause.contains(pkg)) return true;
        for (Schedule s : schedules) if (s.enabled && s.apps.contains(pkg)) return true;
        return false;
    }

    /** Short description of an app's rules, e.g. "Limit 30m · Pause · 1 schedule". Empty if none. */
    synchronized String ruleSummary(String pkg) {
        StringBuilder sb = new StringBuilder();
        Integer l = limits.get(pkg);
        if (l != null) sb.append("Limit ").append(Ui.dur(l * 60000L));
        if (pause.contains(pkg)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Pause");
        }
        int n = 0;
        for (Schedule s : schedules) if (s.enabled && s.apps.contains(pkg)) n++;
        if (n > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(n).append(n == 1 ? " schedule" : " schedules");
        }
        return sb.toString();
    }

    // ---- "a few more minutes" override ----

    synchronized int extraMinutes() {
        return extraMinutes;
    }

    synchronized void setExtraMinutes(int m) {
        extraMinutes = m;
        prefs.edit().putInt("extraMinutes", m).apply();
    }

    /** Time until which the app is let through regardless of limits and schedules. */
    synchronized long allowUntil(String pkg) {
        Long t = allowUntil.get(pkg);
        return t == null ? 0 : t;
    }

    /** Lets the app through for the configured extra minutes and counts the extension. */
    synchronized void grantExtra(String pkg) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Long>> it = allowUntil.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() < now) it.remove();
        }
        allowUntil.put(pkg, now + extraMinutes * 60000L);
        rollOverrides(now);
        Integer n = overrides.get(pkg);
        overrides.put(pkg, n == null ? 1 : n + 1);
        JSONObject o = new JSONObject();
        try {
            o.put("day", overridesDay);
            o.put("counts", new JSONObject(overrides));
        } catch (JSONException ignored) {
        }
        prefs.edit()
                .putString("allowUntil", new JSONObject(allowUntil).toString())
                .putString("overrides", o.toString())
                .apply();
    }

    /** How many times extra time was taken for this app today. */
    synchronized int overridesToday(String pkg) {
        rollOverrides(System.currentTimeMillis());
        Integer n = overrides.get(pkg);
        return n == null ? 0 : n;
    }

    private void rollOverrides(long now) {
        long day = Usage.startOfDay(now, 0);
        if (day != overridesDay) {
            overridesDay = day;
            overrides.clear();
        }
    }
}
