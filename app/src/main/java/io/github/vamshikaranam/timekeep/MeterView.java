package io.github.vamshikaranam.timekeep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A thin rounded progress bar. */
@SuppressLint("ViewConstructor")
public class MeterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int track;
    private int fill;
    private float fraction;

    public MeterView(Context c, float fraction, int fillColorRes) {
        super(c);
        track = c.getColor(R.color.track);
        fill = c.getColor(fillColorRes);
        this.fraction = fraction;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        paint.setColor(track);
        canvas.drawRoundRect(rect, r, r, paint);
        if (fraction > 0f) {
            rect.right = Math.max(h, w * Math.min(1f, fraction));
            paint.setColor(fill);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
