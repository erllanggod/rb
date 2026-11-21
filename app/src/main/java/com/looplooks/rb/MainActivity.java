package com.looplooks.rb;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    /** The Xposed hook replaces this to return true when the module is loaded. */
    public static boolean isModuleActive() { return false; }

    private SharedPreferences sp;
    private boolean prefsShared = true;
    private float dens;
    private final List<Object[]> sliders = new ArrayList<>();   // {SeekBar, TextView, key, def, min, tenths}
    private final List<Object[]> switches = new ArrayList<>();  // {M3Switch, defaultChecked}
    private LinearLayout hdContent;
    private OrbitView orbit;
    private View scrim;
    private IconSegment iconSeg;

    private int col(int id) { return getColor(id); }
    private int dp(float v) { return Math.round(v * dens); }
    private float spx(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private Drawable icon(int res, int color) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(col(R.color.card), 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private void divider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(col(R.color.divider));
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    /** Group label with the small accent bar in front, like the HTML. */
    private void groupLabel(LinearLayout parent, String s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(8));
        View bar = new View(this);
        bar.setBackground(round(col(R.color.accent), 2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(3), dp(16));
        blp.setMarginEnd(dp(10));
        row.addView(bar, blp);
        row.addView(text(s, 14, col(R.color.text_secondary)));
        parent.addView(row);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            // LSPosed makes this file readable by System UI only when opened in this mode.
            sp = getSharedPreferences(Cfg.FILE, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            sp = getSharedPreferences(Cfg.FILE, Context.MODE_PRIVATE);
            prefsShared = false;
        }
        dens = getResources().getDisplayMetrics().density;
        setupEdgeToEdge();

        // The rings live on a full-screen layer behind the page, so nothing but the screen edge
        // cuts them (the setting cards simply cover them).
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(col(R.color.bg));
        orbit = new OrbitView(this);
        frame.addView(orbit, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        frame.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Everything scrolls, banner header included, so nothing gets cut by a hard edge.
        // The rings behind drift up at half the scroll speed.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), 0, dp(14), dp(24));
        scroll.addView(root);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> orbit.setTranslationY(-y * 0.5f));

        // The header is full width: cancel the page padding with negative side margins.
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(-dp(14), 0, -dp(14), 0);
        root.addView(buildHeader(), hlp);

        // Soft fade under the status bar: content slides out of sight instead of being cut.
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{col(R.color.bg), col(R.color.bg), col(R.color.bg) & 0x00FFFFFF}));
        frame.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP));

        int iconStyle = sp.getInt(Cfg.K_ICON, Cfg.D_ICON);

        groupLabel(root, "Icon");
        LinearLayout segCard = card();
        iconSeg = new IconSegment(this, iconStyle);
        LinearLayout.LayoutParams sglp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        sglp.setMargins(dp(10), dp(10), dp(10), dp(10));
        segCard.addView(iconSeg, sglp);
        root.addView(segCard);

        // Ring settings only apply to the ring icon, so they dim out when Default is picked.
        final DimGroup ringWrap = new DimGroup(this);
        groupLabel(ringWrap, "Ring");
        LinearLayout ringCard = card();
        sliderRow(ringCard, "Size", Cfg.K_SIZE, Cfg.D_SIZE, 60, 260, true, false);
        sliderRow(ringCard, "Thickness", Cfg.K_STROKE, Cfg.D_STROKE, 10, 80, true, true);
        sliderRow(ringCard, "Gap", Cfg.K_GAP, Cfg.D_GAP, 0, 100, true, true);
        sliderRow(ringCard, "Track opacity", Cfg.K_TRACK, Cfg.D_TRACK, 0, 100, false, true);
        ringWrap.addView(ringCard);
        root.addView(ringWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        groupLabel(root, "Options");
        LinearLayout optCard = card();
        optionRow(optCard, R.drawable.ic_opt_swap, "Percent on the left", null,
                sp.getBoolean(Cfg.K_PCT_LEFT, Cfg.D_PCT_LEFT), Cfg.D_PCT_LEFT, false,
                on -> sp.edit().putBoolean(Cfg.K_PCT_LEFT, on).apply());
        final LinearLayout roundRow = optionRow(optCard, R.drawable.ic_opt_round, "Round line ends", null,
                sp.getBoolean(Cfg.K_ROUND, Cfg.D_ROUND), Cfg.D_ROUND, true,
                on -> sp.edit().putBoolean(Cfg.K_ROUND, on).apply());
        optionRow(optCard, R.drawable.ic_opt_lock, "Rotation lock icon", null,
                sp.getBoolean(Cfg.K_ROTATE, Cfg.D_ROTATE), Cfg.D_ROTATE, true,
                on -> sp.edit().putBoolean(Cfg.K_ROTATE, on).apply());
        optionRow(optCard, R.drawable.ic_opt_notif, "Monochrome notification icons", null,
                sp.getBoolean(Cfg.K_MONO, Cfg.D_MONO), Cfg.D_MONO, true,
                on -> sp.edit().putBoolean(Cfg.K_MONO, on).apply());
        root.addView(optCard);

        ringWrap.setDim(iconStyle == 1, false);
        dimRow(roundRow, iconStyle == 1, false);
        iconSeg.listener = i -> {
            sp.edit().putInt(Cfg.K_ICON, i).apply();
            ringWrap.setDim(i == 1, true);
            dimRow(roundRow, i == 1, true);
        };

        TextView apply = text("Apply and restart System UI", 15, col(R.color.on_accent));
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setGravity(Gravity.CENTER);
        apply.setBackground(round(col(R.color.accent), 26));
        apply.setOnClickListener(v -> restartSystemUi());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        alp.topMargin = dp(2);
        root.addView(apply, alp);

        TextView reset = text("Reset to default", 14, col(R.color.text_secondary));
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(20), dp(12), dp(20), dp(12));
        reset.setClickable(true);
        // Pill-shaped ripple (the default one is a square box on this theme).
        reset.setBackground(new RippleDrawable(
                ColorStateList.valueOf((col(R.color.accent) & 0x00FFFFFF) | 0x33000000),
                null, round(Color.WHITE, 24)));
        reset.setOnClickListener(v -> resetAll());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(6);
        root.addView(reset, rlp);

        // Edge-to-edge: the system bars sit on top of the page, so pad around them here.
        frame.setOnApplyWindowInsetsListener((v, ins) -> {
            @SuppressWarnings("deprecation") int top = ins.getSystemWindowInsetTop();
            @SuppressWarnings("deprecation") int bot = ins.getSystemWindowInsetBottom();
            hdContent.setPadding(dp(22), top + dp(38), dp(22), dp(16));
            orbit.setCenterY(top + dp(20) + dp(57));
            root.setPadding(dp(14), 0, dp(14), dp(24) + bot);
            ViewGroup.LayoutParams slp = scrim.getLayoutParams();
            slp.height = top + dp(28);
            scrim.setLayoutParams(slp);
            return ins;
        });
        setContentView(frame);
        frame.requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!night) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(f);
    }

    private FrameLayout buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(true);

        hdContent = new LinearLayout(this);
        hdContent.setOrientation(LinearLayout.VERTICAL);
        // Real top inset is applied by the insets listener in onCreate.
        hdContent.setPadding(dp(22), dp(38), dp(22), dp(16));
        hdContent.setClipChildren(false);
        hdContent.setClipToPadding(false);
        header.setClipChildren(false);

        String ver = "1.0";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        hdContent.addView(new Wordmark(this, "v" + ver), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Status is shown by the green satellite on the orbit (red and still when inactive).
        boolean active = isModuleActive() && prefsShared;
        orbit.setActive(active);
        orbit.setContentDescription(active ? "Module active" : "Module inactive");
        if (!active) {
            hdContent.setOnClickListener(v -> Toast.makeText(this,
                    "Enable the module in LSPosed for System UI and this app, then reopen.",
                    Toast.LENGTH_LONG).show());
        }

        header.addView(hdContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private void sliderRow(LinearLayout card, String label, String key, int def,
                           int min, int max, boolean tenthsDp, boolean dividerAbove) {
        if (dividerAbove) divider(card);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(6));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 15, col(R.color.text_primary));
        TextView value = text("", 15, col(R.color.text_secondary));
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(value);
        row.addView(head);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgressDrawable(getDrawable(R.drawable.seek_progress));
        sb.setThumb(getDrawable(R.drawable.seek_thumb));
        sb.setSplitTrack(false);
        sb.setBackground(null);
        sb.setPadding(dp(10), 0, dp(10), 0);
        int cur = sp.getInt(key, def);
        sb.setProgress(cur - min);
        value.setText(fmt(cur, tenthsDp));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int val = p + min;
                value.setText(fmt(val, tenthsDp));
                sp.edit().putInt(key, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        card.addView(row);
        sliders.add(new Object[]{sb, value, key, def, min, tenthsDp});
    }

    private static String fmt(int val, boolean tenthsDp) {
        return tenthsDp ? String.format(java.util.Locale.US, "%.1f dp", val / 10f) : val + "%";
    }

    private interface OnToggle { void on(boolean checked); }
    private interface OnPick { void pick(int index); }

    private static int blend(int base, int over, float a) {
        return Color.rgb(
                Math.round(Color.red(base) * (1f - a) + Color.red(over) * a),
                Math.round(Color.green(base) * (1f - a) + Color.green(over) * a),
                Math.round(Color.blue(base) * (1f - a) + Color.blue(over) * a));
    }

    private LinearLayout optionRow(LinearLayout card, int iconRes, String title, String sub,
                           boolean checked, boolean def, boolean dividerAbove, OnToggle l) {
        if (dividerAbove) divider(card);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        ImageView ic = new ImageView(this);
        ic.setImageDrawable(icon(iconRes, col(R.color.accent)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
        ilp.setMarginEnd(dp(14));
        row.addView(ic, ilp);

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 15, col(R.color.text_primary)));
        if (sub != null) {
            TextView b = text(sub, 12, col(R.color.text_secondary));
            b.setPadding(0, dp(1), 0, 0);
            tx.addView(b);
        }
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        M3Switch sw = new M3Switch(this, checked);
        sw.listener = l;
        sw.setContentDescription(title);
        row.addView(sw, new LinearLayout.LayoutParams(dp(52), dp(32)));
        row.setOnClickListener(v -> sw.performClick());
        card.addView(row);
        switches.add(new Object[]{sw, def});
        return row;
    }

    /** Greys a row out and makes it untouchable (its switch is always the last child). */
    private void dimRow(LinearLayout row, boolean dim, boolean animate) {
        row.setClickable(!dim);
        row.getChildAt(row.getChildCount() - 1).setClickable(!dim);
        float a = dim ? 0.35f : 1f;
        if (animate) row.animate().alpha(a).setDuration(200).start(); else row.setAlpha(a);
    }

    private void resetAll() {
        for (Object[] s : sliders) {
            SeekBar sb = (SeekBar) s[0];
            int def = (Integer) s[3], min = (Integer) s[4];
            sb.setProgress(def - min);
            ((TextView) s[1]).setText(fmt(def, (Boolean) s[5]));
        }
        iconSeg.setIndex(Cfg.D_ICON, true);
        // Switches write their own prefs through the listener.
        for (Object[] s : switches) ((M3Switch) s[0]).setChecked((Boolean) s[1], true);
    }

    private void restartSystemUi() {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
            pr.waitFor();
        } catch (Exception e) {
            Toast.makeText(this, "Root failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Vertical group that can be greyed out and blocks touches while dimmed. */
    private class DimGroup extends LinearLayout {
        private boolean blocked;

        DimGroup(Context c) {
            super(c);
            setOrientation(VERTICAL);
        }

        void setDim(boolean dim, boolean animate) {
            blocked = dim;
            float a = dim ? 0.35f : 1f;
            if (animate) animate().alpha(a).setDuration(200).start(); else setAlpha(a);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return blocked || super.onInterceptTouchEvent(e);
        }
    }

    /**
     * Ring / Default selector. One indicator slides between the two segments (no per-segment
     * background that could cross-fade and merge when switching fast).
     */
    private class IconSegment extends View {
        private final String[] labels = {"Ring", "Default"};
        private final float[] iconW = {16f, 22.6f};
        private final float[] iconH = {16f, 12.2f};
        private final Drawable[] icons = new Drawable[2];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final ArgbEvaluator ev = new ArgbEvaluator();
        private int index;
        private float t;                 // 0 = Ring selected, 1 = Default selected
        private ValueAnimator anim;
        private OnPick listener;

        IconSegment(Context c, int idx) {
            super(c);
            index = idx;
            t = idx;
            icons[0] = c.getDrawable(R.drawable.ic_seg_ring).mutate();
            icons[1] = c.getDrawable(R.drawable.ic_seg_battery).mutate();
            p.setTextSize(spx(14));
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setClickable(true);
            setFocusable(true);
            setContentDescription("Icon style: " + labels[idx]);
        }

        void setIndex(int i, boolean notify) {
            if (i == index) return;
            index = i;
            setContentDescription("Icon style: " + labels[i]);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(t, i);
            anim.setDuration(240);
            anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
            anim.start();
            if (notify && listener != null) listener.pick(i);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), dp(48));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                setIndex(e.getX() < getWidth() / 2f ? 0 : 1, true);
                performClick();
                return true;
            }
            return super.onTouchEvent(e);
        }

        @Override
        public boolean performClick() { return super.performClick(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), pad = dp(4), gap = dp(4);
            p.setStyle(Paint.Style.FILL);
            p.setColor(col(R.color.bg));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, dp(16), dp(16), p);

            float segW = (w - 2 * pad - gap) / 2f;
            float left = pad + t * (segW + gap);
            p.setColor(blend(col(R.color.bg), col(R.color.accent), 0.28f));
            r.set(left, pad, left + segW, h - pad);
            c.drawRoundRect(r, dp(12), dp(12), p);

            float cy = h / 2f;
            Paint.FontMetrics fm = p.getFontMetrics();
            for (int i = 0; i < 2; i++) {
                float sel = Math.max(0f, Math.min(1f, i == 0 ? 1f - t : t));
                int color = (Integer) ev.evaluate(sel, col(R.color.text_secondary), col(R.color.accent));
                float iw = dp(iconW[i]), ih = dp(iconH[i]);
                float total = iw + dp(8) + p.measureText(labels[i]);
                float x0 = pad + i * (segW + gap) + (segW - total) / 2f;
                icons[i].setTint(color);
                icons[i].setBounds(Math.round(x0), Math.round(cy - ih / 2f),
                        Math.round(x0 + iw), Math.round(cy + ih / 2f));
                icons[i].draw(c);
                p.setColor(color);
                c.drawText(labels[i], x0 + iw + dp(8), cy - (fm.ascent + fm.descent) / 2f, p);
            }
        }
    }

    /**
     * Two-line "LoopLooks" in the system font. The stem of the p (line 1) runs down into the stem
     * of the k (line 2): the line gap is measured from the real glyph bounds so they always touch,
     * whatever the system font is. p and k use the accent color, v1.0 sits after "Looks".
     */
    private class Wordmark extends View {
        private final Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String ver;
        private float xp, xk, xs, xv, base1, base2;

        Wordmark(Context c, String ver) {
            super(c);
            this.ver = ver;
            main.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            main.setTextSize(spx(36));
            small.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            small.setTextSize(spx(12));
            small.setColor(col(R.color.text_muted));
            setContentDescription("LoopLooks");
        }

        @Override
        protected void onMeasure(int w, int h) {
            Rect rL = new Rect(), rp = new Rect(), rk = new Rect();
            main.getTextBounds("L", 0, 1, rL);
            main.getTextBounds("p", 0, 1, rp);
            main.getTextBounds("k", 0, 1, rk);
            base1 = -rL.top;
            // p descender bottom meets k ascender top, with 1dp of overlap so the join is solid.
            base2 = base1 + rp.bottom + (-rk.top) - dp(1);
            xp = main.measureText("Loo");
            xk = xp + (rp.left - rk.left);          // line the two stems up exactly
            xs = xk + main.measureText("k");
            xv = xs + main.measureText("s") + dp(6);
            float line1 = xp + main.measureText("p");
            float line2 = xv + small.measureText(ver);
            setMeasuredDimension((int) Math.ceil(Math.max(line1, line2)) + dp(2),
                    (int) Math.ceil(base2) + dp(3));
        }

        @Override
        protected void onDraw(Canvas c) {
            int primary = col(R.color.text_primary), accent = col(R.color.accent);
            main.setColor(primary);
            c.drawText("Loo", 0, base1, main);
            c.drawText("Loo", 0, base2, main);
            c.drawText("s", xs, base2, main);
            main.setColor(accent);
            c.drawText("p", xp, base1, main);
            c.drawText("k", xk, base2, main);
            c.drawText(ver, xv, base2, small);
        }
    }

    /**
     * Banner rings: dashed outer orbit, a track ring with an accent arc for the battery level and
     * a dot at the end of the arc, plus a faint inner ring. Centered near the right edge so the
     * rings bleed off the screen. Follows the real battery level.
     */
    private class OrbitView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float cy;
        private float level = 75f;
        private boolean active;
        private float satAngle = 120f;   // degrees, 0 = 3 o'clock, clockwise
        private float ping;             // 0..1 pulse progress
        private ValueAnimator spinAnim, pingAnim;
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (l >= 0 && s > 0) { level = l * 100f / s; invalidate(); }
            }
        };

        OrbitView(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            cy = dp(77);
        }

        void setCenterY(float v) { cy = v; invalidate(); }

        void setActive(boolean a) {
            active = a;
            if (isAttachedToWindow()) startAnims();
            invalidate();
        }

        private void startAnims() {
            stopAnims();
            if (!active || !ValueAnimator.areAnimatorsEnabled()) return;
            spinAnim = ValueAnimator.ofFloat(0f, 360f);
            spinAnim.setDuration(28000);
            spinAnim.setInterpolator(new LinearInterpolator());
            spinAnim.setRepeatCount(ValueAnimator.INFINITE);
            spinAnim.addUpdateListener(a -> { satAngle = 120f + (float) a.getAnimatedValue(); invalidate(); });
            spinAnim.start();
            pingAnim = ValueAnimator.ofFloat(0f, 1f);
            pingAnim.setDuration(2000);
            pingAnim.setInterpolator(new PathInterpolator(0f, 0f, 0.58f, 1f));
            pingAnim.setRepeatCount(ValueAnimator.INFINITE);
            pingAnim.addUpdateListener(a -> { ping = (float) a.getAnimatedValue(); invalidate(); });
            pingAnim.start();
        }

        private void stopAnims() {
            if (spinAnim != null) spinAnim.cancel();
            if (pingAnim != null) pingAnim.cancel();
            spinAnim = null;
            pingAnim = null;
        }

        // Don't let the rings decide the header height: take the width, and only take a height
        // when the parent already fixed one (the header height comes from the text next to it).
        @Override
        protected void onMeasure(int w, int h) {
            int hh = MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h) : 0;
            setMeasuredDimension(MeasureSpec.getSize(w), hh);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent st = Build.VERSION.SDK_INT >= 33
                    ? getContext().registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED)
                    : getContext().registerReceiver(rx, f);
            if (st != null) rx.onReceive(getContext(), st);
            startAnims();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnims();
            try { getContext().unregisterReceiver(rx); } catch (Exception ignored) {}
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() - dp(31);
            // dashed outer orbit
            p.setColor(col(R.color.divider));
            p.setPathEffect(new DashPathEffect(new float[]{dp(2), dp(8)}, 0));
            c.drawCircle(cx, cy, dp(106), p);
            p.setPathEffect(null);
            // inner faint ring
            p.setColor(col(R.color.card));
            c.drawCircle(cx, cy, dp(40), p);
            // track + level arc
            float r = dp(68);
            p.setColor(col(R.color.track));
            c.drawCircle(cx, cy, r, p);
            p.setColor(col(R.color.accent));
            oval.set(cx - r, cy - r, cx + r, cy + r);
            float sweep = 360f * Math.max(0f, Math.min(100f, level)) / 100f;
            c.drawArc(oval, -90f, sweep, false, p);
            // dot at the end of the arc
            double a = Math.toRadians(-90f + sweep);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle((float) (cx + r * Math.cos(a)), (float) (cy + r * Math.sin(a)), dp(7), p);
            // status satellite on the outer orbit: green + pulsing + slowly orbiting when active
            double sa = Math.toRadians(satAngle);
            float sx = (float) (cx + dp(106) * Math.cos(sa));
            float sy = (float) (cy + dp(106) * Math.sin(sa));
            int sc = col(active ? R.color.success : R.color.danger);
            if (active) {
                p.setColor(sc);
                p.setAlpha((int) (0.5f * 255f * (1f - ping)));
                c.drawCircle(sx, sy, dp(4) + dp(7) * ping, p);
            }
            p.setColor(sc);
            c.drawCircle(sx, sy, dp(4), p);
            p.setStyle(Paint.Style.STROKE);
        }
    }

    /**
     * Status dot (6dp) with the soft pulse ring from the preview: the ring grows 1x -> 3.2x while it
     * fades from 55% to 0, every 2s, ease-out. Only the active state pulses, and only when system
     * animations are on.
     */
    private class PulseDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final boolean pulse;
        private float t;
        private ValueAnimator anim;

        PulseDot(Context c, int color, boolean pulse) {
            super(c);
            this.color = color;
            this.pulse = pulse;
        }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(dp(20), dp(20)); }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (pulse && ValueAnimator.areAnimatorsEnabled()) {
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(2000);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setInterpolator(new PathInterpolator(0f, 0f, 0.58f, 1f));
                anim.addUpdateListener(an -> { t = (Float) an.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) { anim.cancel(); anim = null; }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = dp(3);
            if (anim != null) {
                p.setColor(color);
                p.setAlpha(Math.round(255 * 0.55f * (1f - t)));
                c.drawCircle(cx, cy, r * (1f + 2.2f * t), p);
            }
            p.setColor(color);
            c.drawCircle(cx, cy, r, p);
        }
    }

    /** 52x32 switch: track + thumb that grows when on (same geometry as the HTML). */
    private class M3Switch extends View {
        private boolean checked;
        private float t;
        private OnToggle listener;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final ArgbEvaluator ev = new ArgbEvaluator();

        M3Switch(Context c, boolean on) {
            super(c);
            checked = on;
            t = on ? 1f : 0f;
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> setChecked(!checked, true));
        }

        void setChecked(boolean on, boolean notify) {
            if (on == checked) return;
            checked = on;
            ValueAnimator a = ValueAnimator.ofFloat(t, on ? 1f : 0f);
            a.setDuration(150);
            a.addUpdateListener(an -> { t = (Float) an.getAnimatedValue(); invalidate(); });
            a.start();
            if (notify && listener != null) listener.on(on);
        }

        @Override
        protected void onMeasure(int w, int h) { setMeasuredDimension(dp(52), dp(32)); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor((Integer) ev.evaluate(t, col(R.color.track), col(R.color.accent)));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            p.setColor((Integer) ev.evaluate(t, col(R.color.text_secondary), col(R.color.on_accent)));
            c.drawCircle(dp(16) + dp(20) * t, h / 2f, dp(10) + dp(2) * t, p);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Switch");
            info.setCheckable(true);
            info.setChecked(checked);
        }
    }
}
