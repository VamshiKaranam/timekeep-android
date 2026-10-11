package io.github.vamshikaranam.timekeep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Seven bars, one per day, with today picked out. */
@SuppressLint("ViewConstructor")
public class BarChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final long[] values;
    private final String[] labels;
    private final int highlight;
    private final int accent, soft, track, text, muted;
    private final float density, scaled;

    public BarChartView(Context c, long[] values, String[] labels, int highlight) {
        super(c);
        this.values = values;
        this.labels = labels;
        this.highlight = highlight;
        accent = c.getColor(R.color.accent);
        soft = c.getColor(R.color.accent_soft);
        track = c.getColor(R.color.track);
        text = c.getColor(R.color.text);
        muted = c.getColor(R.color.muted);
        density = c.getResources().getDisplayMetrics().density;
        scaled = c.getResources().getDisplayMetrics().scaledDensity;
        label.setTextAlign(Paint.Align.CENTER);
    }

    /** Short form that fits above a bar: "45m", "2.5h". */
    private static String compact(long ms) {
        long m = ms / 60000L;
        if (m < 1) return "";
        if (m < 60) return m + "m";
        long tenths = Math.round(m / 6.0);
        return tenths % 10 == 0 ? (tenths / 10) + "h" : (tenths / 10) + "." + (tenths % 10) + "h";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();
        float valueSize = 11 * scaled;
        float daySize = 12 * scaled;
        float top = valueSize + 8 * density;
        float bottom = h - (daySize + 10 * density);
        float room = Math.max(1f, bottom - top);
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        float slot = w / n;
        float barW = Math.min(slot * 0.56f, 34 * density);
        float radius = 7 * density;

        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            boolean on = i == highlight;
            float bh = values[i] > 0 ? Math.max(4 * density, room * values[i] / max) : 3 * density;
            rect.set(cx - barW / 2f, bottom - bh, cx + barW / 2f, bottom);
            bar.setColor(values[i] > 0 ? (on ? accent : soft) : track);
            float r = Math.min(radius, bh / 2f);
            canvas.drawRoundRect(rect, r, r, bar);

            label.setTypeface(on ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
            label.setColor(on ? text : muted);
            label.setTextSize(valueSize);
            canvas.drawText(compact(values[i]), cx, bottom - bh - 5 * density, label);
            label.setTextSize(daySize);
            canvas.drawText(labels[i], cx, h - 3 * density, label);
        }
    }
}
