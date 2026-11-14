package com.adennnqt.roundbattery;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/** Circular arrow with a small padlock inside (rotation lock). Color follows the host TextView. */
public final class RotateLockDrawable extends Drawable {
    private final TextView host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final RectF body = new RectF();
    private final RectF shackle = new RectF();
    private final Path head = new Path();
    private int alpha = 255;

    public RotateLockDrawable(TextView host) {
        this.host = host;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        p.setColor(host.getCurrentTextColor());
        p.setAlpha(alpha);

        // circular arrow: 300 degree arc, clockwise, gap at the top
        float r = s * 0.40f;
        float sw = s * 0.09f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        float start = -60f;
        float sweep = 300f;
        c.drawArc(oval, start, sweep, false, p);

        // arrow head at the end of the arc, pointing clockwise
        double th = Math.toRadians(start + sweep);
        float px = cx + (float) Math.cos(th) * r;
        float py = cy + (float) Math.sin(th) * r;
        float tx = (float) -Math.sin(th);
        float ty = (float) Math.cos(th);
        float nx = (float) Math.cos(th);
        float ny = (float) Math.sin(th);
        float hl = s * 0.16f;
        float hw = s * 0.12f;
        head.reset();
        head.moveTo(px + tx * hl, py + ty * hl);
        head.lineTo(px + nx * hw, py + ny * hw);
        head.lineTo(px - nx * hw, py - ny * hw);
        head.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(head, p);

        // padlock
        float bw = s * 0.30f;
        float bh = s * 0.22f;
        body.set(cx - bw / 2f, cy + s * 0.03f, cx + bw / 2f, cy + s * 0.03f + bh);
        c.drawRoundRect(body, s * 0.04f, s * 0.04f, p);
        float sr = s * 0.08f;
        shackle.set(cx - sr, body.top - sr * 1.6f, cx + sr, body.top + sr * 0.4f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.05f);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.drawArc(shackle, 180f, 180f, false, p);
    }

    @Override public void setAlpha(int a) { alpha = a; }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
