package com.looplooks.rb;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
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

    private int col(int id) { return getColor(id); }
    private int dp(float v) { return Math.round(v * dens); }

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

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(col(R.color.bg));

        // Fixed top: header + live preview
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(20), dp(14), dp(4));
        buildHeader(top);
        page.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scrolling settings
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(4), dp(14), dp(24));
        scroll.addView(root);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        groupLabel(root, "Ring");
        LinearLayout ringCard = card();
        sliderRow(ringCard, "Size", Cfg.K_SIZE, Cfg.D_SIZE, 60, 260, true, false);
        sliderRow(ringCard, "Thickness", Cfg.K_STROKE, Cfg.D_STROKE, 10, 80, true, true);
        sliderRow(ringCard, "Gap", Cfg.K_GAP, Cfg.D_GAP, 0, 100, true, true);
        sliderRow(ringCard, "Track opacity", Cfg.K_TRACK, Cfg.D_TRACK, 0, 100, false, true);
        root.addView(ringCard);

        groupLabel(root, "Options");
        LinearLayout optCard = card();
        optionRow(optCard, R.drawable.ic_opt_round, "Round line ends", null,
                sp.getBoolean(Cfg.K_ROUND, Cfg.D_ROUND), Cfg.D_ROUND, false,
                on -> sp.edit().putBoolean(Cfg.K_ROUND, on).apply());
        optionRow(optCard, R.drawable.ic_opt_lock, "Rotation lock icon", null,
                sp.getBoolean(Cfg.K_ROTATE, Cfg.D_ROTATE), Cfg.D_ROTATE, true,
                on -> sp.edit().putBoolean(Cfg.K_ROTATE, on).apply());
        root.addView(optCard);

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
        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            reset.setBackgroundResource(ripple.resourceId);
        }
        reset.setOnClickListener(v -> resetAll());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(6);
        root.addView(reset, rlp);

        setContentView(page);
    }

    private void buildHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), dp(18));
        // The status dot pulses past its own bounds, so none of its ancestors may clip it.
        row.setClipChildren(false);
        row.setClipToPadding(false);
        parent.setClipChildren(false);
        parent.setClipToPadding(false);

        ImageView title = new ImageView(this);
        title.setImageResource(R.drawable.ic_title);
        title.setImageTintList(android.content.res.ColorStateList.valueOf(col(R.color.text_primary)));
        title.setAdjustViewBounds(true);
        title.setContentDescription("LL");
        LinearLayout holder = new LinearLayout(this);
        holder.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(64)));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        left.addView(holder);

        boolean active = isModuleActive() && prefsShared;
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setClipChildren(false);
        // 20dp canvas for the pulse, laid out as the 6dp dot: -7dp margins on every side.
        PulseDot dot = new PulseDot(this, col(active ? R.color.success : R.color.danger), active);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(20), dp(20));
        dlp.setMargins(dp(-7), dp(-7), 0, dp(-7));
        status.addView(dot, dlp);
        TextView st = text(active ? "Active" : "Inactive", 10, col(R.color.text_secondary));
        st.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        st.setLetterSpacing(0.01f);
        status.addView(st);
        if (!active) {
            status.setOnClickListener(v -> Toast.makeText(this,
                    "Enable the module in LSPosed for System UI and this app, then reopen.",
                    Toast.LENGTH_LONG).show());
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(2), dp(6), 0, 0);
        left.addView(status, clp);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parent.addView(row);
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

    private void optionRow(LinearLayout card, int iconRes, String title, String sub,
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
    }

    private void resetAll() {
        for (Object[] s : sliders) {
            SeekBar sb = (SeekBar) s[0];
            int def = (Integer) s[3], min = (Integer) s[4];
            sb.setProgress(def - min);
            ((TextView) s[1]).setText(fmt(def, (Boolean) s[5]));
        }
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
