package com.looplooks.rb;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * HyperOS rotation-lock icon (path data taken from the stock rotation.xml vector).
 * Filled with the host TextView's current text color, scaled to fit the drawable bounds.
 */
public final class RotateLockDrawable extends Drawable {
    private static final String LOCK =
            "M113.8,99.27c0.15,-2.06 -1.41,-3.85 -3.46,-3.99 -0.14,0 -0.28,0 -0.42,0h0v-0.72c0.18,-3.51 0,-7.02 -0.52,-10.49 -0.64,-2.75 -2.11,-5.23 -4.2,-7.13 -1.24,-1.19 -2.67,-2.15 -4.23,-2.88 -0.93,-0.4 -1.9,-0.72 -2.88,-0.95 -2.55,-0.49 -5.17,-0.33 -7.65,0.46 -2.37,0.76 -4.51,2.09 -6.24,3.88 -0.89,0.9 -1.65,1.92 -2.28,3.02 -0.71,1.27 -1.21,2.66 -1.47,4.09 -0.45,3.32 -0.58,6.66 -0.4,10.01v0.63h0c-2.25,0.18 -3.92,2.16 -3.73,4.41 0,0.08 0,0.15 0.02,0.22v17.55c-0.19,2.05 1.31,3.88 3.37,4.08 0.22,0.02 0.44,0.02 0.66,0h29.42c2.07,0.14 3.87,-1.44 4.01,-3.51 0,-0.19 0,-0.38 0,-0.57v-18.09h0ZM87.58,87.51c0.13,-4.07 3.53,-7.26 7.6,-7.12 0.6,0.02 1.2,0.12 1.78,0.28s1.16,0.39 1.69,0.66c2.21,1.27 3.61,3.6 3.68,6.16v7.76h-14.81c0.06,-2.67 0.06,-5.2 0.06,-7.73h0Z";
    private static final String ARROW =
            "M152.15,88.2h-7.53c-5.33,-22.61 -25.64,-39.44 -49.87,-39.44 -28.3,0 -51.24,22.94 -51.24,51.24s22.94,51.24 51.24,51.24c14.94,0 28.39,-6.39 37.75,-16.6 0.05,-0.33 0.08,-0.66 0.08,-1 0,-3.53 -2.86,-6.4 -6.4,-6.4 -0.82,0 -1.61,0.16 -2.33,0.44 -7.32,7.69 -17.65,12.48 -29.1,12.48 -22.18,0 -40.17,-17.98 -40.17,-40.17s17.98,-40.17 40.17,-40.17c18.08,0 33.36,11.94 38.4,28.37h-7c-3.51,0 -5.57,3.94 -3.57,6.83l13,18.72c1.73,2.49 5.41,2.49 7.14,0l13,-18.72c2,-2.88 -0.06,-6.83 -3.57,-6.83Z";

    private static final Path P_LOCK = parse(LOCK);
    private static final Path P_ARROW = parse(ARROW);
    private static final RectF SRC = new RectF();

    static {
        RectF a = new RectF();
        RectF b = new RectF();
        P_LOCK.computeBounds(a, true);
        P_ARROW.computeBounds(b, true);
        SRC.set(a);
        SRC.union(b);
    }

    private final TextView host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF dst = new RectF();
    private int alpha = 255;

    public RotateLockDrawable(TextView host) {
        this.host = host;
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        dst.set(b);
        m.setRectToRect(SRC, dst, Matrix.ScaleToFit.CENTER);
        p.setColor(host.getCurrentTextColor());
        p.setAlpha(alpha);
        c.save();
        c.concat(m);
        c.drawPath(P_ARROW, p);
        c.drawPath(P_LOCK, p);
        c.restore();
    }

    @Override public void setAlpha(int a) { alpha = a; }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    // ---- minimal SVG path parser: M m L l H h V v C c S s Z z ----
    private static Path parse(String d) {
        Path path = new Path();
        int[] pos = {0};
        int n = d.length();
        float x = 0, y = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
        boolean lastCubic = false;
        char cmd = 'M';
        while (true) {
            skip(d, pos);
            if (pos[0] >= n) break;
            char ch = d.charAt(pos[0]);
            if (Character.isLetter(ch)) {
                cmd = ch;
                pos[0]++;
                if (cmd == 'z' || cmd == 'Z') {
                    path.close();
                    x = sx;
                    y = sy;
                    lastCubic = false;
                    continue;
                }
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    float nx = num(d, pos), ny = num(d, pos);
                    if (rel) { nx += x; ny += y; }
                    path.moveTo(nx, ny);
                    x = nx; y = ny; sx = nx; sy = ny;
                    cmd = rel ? 'l' : 'L';
                    lastCubic = false;
                    break;
                }
                case 'L': {
                    float nx = num(d, pos), ny = num(d, pos);
                    if (rel) { nx += x; ny += y; }
                    path.lineTo(nx, ny);
                    x = nx; y = ny;
                    lastCubic = false;
                    break;
                }
                case 'H': {
                    float nx = num(d, pos);
                    if (rel) nx += x;
                    path.lineTo(nx, y);
                    x = nx;
                    lastCubic = false;
                    break;
                }
                case 'V': {
                    float ny = num(d, pos);
                    if (rel) ny += y;
                    path.lineTo(x, ny);
                    y = ny;
                    lastCubic = false;
                    break;
                }
                case 'C': {
                    float x1 = num(d, pos), y1 = num(d, pos);
                    float x2 = num(d, pos), y2 = num(d, pos);
                    float x3 = num(d, pos), y3 = num(d, pos);
                    if (rel) { x1 += x; y1 += y; x2 += x; y2 += y; x3 += x; y3 += y; }
                    path.cubicTo(x1, y1, x2, y2, x3, y3);
                    lcx = x2; lcy = y2; x = x3; y = y3;
                    lastCubic = true;
                    break;
                }
                case 'S': {
                    float x2 = num(d, pos), y2 = num(d, pos);
                    float x3 = num(d, pos), y3 = num(d, pos);
                    if (rel) { x2 += x; y2 += y; x3 += x; y3 += y; }
                    float x1 = lastCubic ? 2 * x - lcx : x;
                    float y1 = lastCubic ? 2 * y - lcy : y;
                    path.cubicTo(x1, y1, x2, y2, x3, y3);
                    lcx = x2; lcy = y2; x = x3; y = y3;
                    lastCubic = true;
                    break;
                }
                default:
                    pos[0] = n;
            }
        }
        return path;
    }

    private static void skip(String d, int[] pos) {
        while (pos[0] < d.length()) {
            char c = d.charAt(pos[0]);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t') pos[0]++;
            else break;
        }
    }

    private static float num(String d, int[] pos) {
        skip(d, pos);
        int s = pos[0];
        int i = s;
        int n = d.length();
        if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
        boolean dot = false;
        while (i < n) {
            char c = d.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
            } else if (c == '.' && !dot) {
                dot = true;
                i++;
            } else {
                break;
            }
        }
        pos[0] = i;
        return Float.parseFloat(d.substring(s, i));
    }
}
