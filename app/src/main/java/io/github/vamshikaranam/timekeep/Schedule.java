package io.github.vamshikaranam.timekeep;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A block of time on chosen weekdays during which a set of apps is blocked. */
final class Schedule {
    static final String[] DAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    static final String[] DAY_LETTER = {"M", "T", "W", "T", "F", "S", "S"};

    String id = UUID.randomUUID().toString();
    String name = "";
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    int days = 0b0011111;
    /** Minutes after midnight. If end is not after start, the block runs past midnight. */
    int start = 9 * 60;
    int end = 17 * 60;
    boolean enabled = true;
    final Set<String> apps = new HashSet<>();

    private static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
    }

    private boolean onDay(int index) {
        return (days & (1 << index)) != 0;
    }

    boolean activeAt(long now) {
        if (!enabled) return false;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int today = dayIndex(c);
        int yesterday = (today + 6) % 7;
        if (start < end) {
            return onDay(today) && minute >= start && minute < end;
        }
        return (onDay(today) && minute >= start) || (onDay(yesterday) && minute < end);
    }

    /** When the block that is active at {@code now} ends. */
    long endsAt(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean tomorrow = start >= end && minute >= start;
        c.set(Calendar.HOUR_OF_DAY, end / 60);
        c.set(Calendar.MINUTE, end % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (tomorrow) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    String daysLabel() {
        if (days == 0b1111111) return "Every day";
        if (days == 0b0011111) return "Weekdays";
        if (days == 0b1100000) return "Weekends";
        if (days == 0) return "No days";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (!onDay(i)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(DAY_SHORT[i]);
        }
        return sb.toString();
    }

    String timeLabel(Context c) {
        return Ui.clock(c, start) + " – " + Ui.clock(c, end);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("days", days);
        o.put("start", start);
        o.put("end", end);
        o.put("enabled", enabled);
        JSONArray a = new JSONArray();
        for (String p : apps) a.put(p);
        o.put("apps", a);
        return o;
    }

    static Schedule fromJson(JSONObject o) {
        Schedule s = new Schedule();
        s.id = o.optString("id", s.id);
        s.name = o.optString("name", "");
        s.days = o.optInt("days", s.days);
        s.start = o.optInt("start", s.start);
        s.end = o.optInt("end", s.end);
        s.enabled = o.optBoolean("enabled", true);
        JSONArray a = o.optJSONArray("apps");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String p = a.optString(i, null);
                if (p != null) s.apps.add(p);
            }
        }
        return s;
    }

    Schedule copy() {
        Schedule s = new Schedule();
        s.id = id;
        s.name = name;
        s.days = days;
        s.start = start;
        s.end = end;
        s.enabled = enabled;
        s.apps.addAll(apps);
        return s;
    }
}
