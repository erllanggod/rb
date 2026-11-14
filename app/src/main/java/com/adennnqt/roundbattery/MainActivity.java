package com.adennnqt.roundbattery;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    /** The Xposed hook replaces this to return true when the module is loaded. */
    public static boolean isModuleActive() { return false; }

    private SharedPreferences sp;
    private float dens;
    private final List<RingText> rings = new ArrayList<>();

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

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(col(R.color.card), 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private void divider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(col(R.color.divider));
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void groupLabel(LinearLayout parent, String s) {
        TextView t = text(s, 13, col(R.color.text_secondary));
        t.setPadding(dp(8), dp(4), 0, dp(8));
        parent.addView(t);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(Cfg.FILE, Context.MODE_PRIVATE);
        dens = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(col(R.color.bg));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(20), dp(14), dp(24));
        scroll.addView(root);

        buildHeader(root);
        buildPreview(root);

        groupLabel(root, "Ring");
        LinearLayout ringCard = card();
        sliderRow(ringCard, "Size", Cfg.K_SIZE, Cfg.D_SIZE, 60, 260, true, false);
        sliderRow(ringCard, "Thickness", Cfg.K_STROKE, Cfg.D_STROKE, 10, 80, true, true);
        sliderRow(ringCard, "Gap to percent", Cfg.K_GAP, Cfg.D_GAP, 0, 100, true, true);
        sliderRow(ringCard, "Track opacity", Cfg.K_TRACK, Cfg.D_TRACK, 0, 100, false, true);
        root.addView(ringCard);

        groupLabel(root, "Style");
        LinearLayout styleCard = card();
        switchRow(styleCard, "Round line ends", Cfg.K_ROUND, Cfg.D_ROUND, false);
        switchRow(styleCard, "Show percent text", Cfg.K_SHOWPCT, Cfg.D_SHOWPCT, true);
        root.addView(styleCard);

        Button apply = new Button(this);
        apply.setText("Apply and restart System UI");
        apply.setAllCaps(false);
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setTextColor(0xFFFFFFFF);
        apply.setBackground(round(col(R.color.accent), 24));
        apply.setStateListAnimator(null);
        apply.setOnClickListener(v -> restartSystemUi());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        alp.topMargin = dp(4);
        root.addView(apply, alp);

        TextView hint = text("Needs LSPosed and root", 12, col(R.color.text_muted));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        setContentView(scroll);
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(6), 0, dp(6), dp(14));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Round Battery", 28, col(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(title);

        boolean active = isModuleActive();
        TextView status = text(
                active ? "Module active in System UI"
                       : "Not active. Enable it in LSPosed for System UI and this app.",
                13, col(active ? R.color.success : R.color.danger));
        status.setPadding(0, dp(4), dp(8), 0);
        left.addView(status);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = text("Reset", 13, col(R.color.text_primary));
        reset.setPadding(dp(14), dp(7), dp(14), dp(7));
        reset.setBackground(round(col(R.color.card), 20));
        reset.setOnClickListener(v -> resetAll());
        row.addView(reset);

        root.addView(row);
    }

    private void buildPreview(LinearLayout root) {
        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(14), dp(16), dp(14), dp(16));
        strip.setBackground(round(col(R.color.strip), 14));
        strip.addView(text("04:49", 15, col(R.color.text_primary)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        strip.addView(new RingText(this, 71, R.color.text_primary, 1.7f, 15));
        hero.addView(strip);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(10);
        hero.addView(tiles, tlp);
        addTile(tiles, 71, R.color.text_primary, "Normal", false);
        addTile(tiles, 45, R.color.success, "Charging", true);
        addTile(tiles, 12, R.color.danger, "Low", true);

        root.addView(hero);
    }

    private void addTile(LinearLayout tiles, int pct, int colorRes, String label, boolean margin) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(12), dp(4), dp(10));
        t.setBackground(round(col(R.color.strip), 12));
        t.addView(new RingText(this, pct, colorRes, 1.2f, 12));
        TextView l = text(label, 11, col(R.color.text_secondary));
        l.setPadding(0, dp(6), 0, 0);
        t.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (margin) lp.setMarginStart(dp(8));
        tiles.addView(t, lp);
    }

    private void sliderRow(LinearLayout card, String label, String key, int def,
                           int min, int max, boolean tenthsDp, boolean dividerAbove) {
        if (dividerAbove) divider(card);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 15, col(R.color.text_primary));
        TextView value = text("", 15, col(R.color.accent));
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(value);
        row.addView(head);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int cur = sp.getInt(key, def);
        sb.setProgress(cur - min);
        ColorStateList accent = ColorStateList.valueOf(col(R.color.accent));
        sb.setProgressTintList(accent);
        sb.setThumbTintList(accent);
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(col(R.color.divider)));
        value.setText(fmt(cur, tenthsDp));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int val = p + min;
                value.setText(fmt(val, tenthsDp));
                sp.edit().putInt(key, val).apply();
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(sb);
        card.addView(row);
        sliders.add(new Object[]{sb, value, key, def, min, tenthsDp});
    }

    private final List<Object[]> sliders = new ArrayList<>();
    private final List<Object[]> switches = new ArrayList<>();

    private static String fmt(int val, boolean tenthsDp) {
        return tenthsDp ? String.format(java.util.Locale.US, "%.1f dp", val / 10f) : val + "%";
    }

    private void switchRow(LinearLayout card, String label, String key, boolean def, boolean dividerAbove) {
        if (dividerAbove) divider(card);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.addView(text(label, 15, col(R.color.text_primary)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setTrackTintList(new ColorStateList(states,
                new int[]{col(R.color.accent), col(R.color.divider)}));
        sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sw.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean(key, on).apply();
            refresh();
        });
        row.addView(sw);
        card.addView(row);
        switches.add(new Object[]{sw, key, def});
    }

    private void resetAll() {
        SharedPreferences.Editor e = sp.edit();
        for (Object[] s : sliders) e.putInt((String) s[2], (Integer) s[3]);
        for (Object[] s : switches) e.putBoolean((String) s[1], (Boolean) s[2]);
        e.apply();
        for (Object[] s : sliders) {
            SeekBar sb = (SeekBar) s[0];
            int def = (Integer) s[3], min = (Integer) s[4];
            sb.setProgress(def - min);
            ((TextView) s[1]).setText(fmt(def, (Boolean) s[5]));
        }
        for (Object[] s : switches) ((Switch) s[0]).setChecked((Boolean) s[2]);
        refresh();
    }

    private void refresh() {
        for (RingText r : rings) {
            r.requestLayout();
            r.invalidate();
        }
    }

    private void restartSystemUi() {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
            pr.waitFor();
        } catch (Exception e) {
            Toast.makeText(this, "Root failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Ring + percent text drawn with the same rules as the hook. */
    private class RingText extends View {
        private final int pct;
        private final int colorRes;
        private final float k;
        private final float textSp;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        RingText(Context c, int pct, int colorRes, float k, float textSp) {
            super(c);
            this.pct = pct;
            this.colorRes = colorRes;
            this.k = k;
            this.textSp = textSp;
            rings.add(this);
        }

        private float size() { return sp.getInt(Cfg.K_SIZE, Cfg.D_SIZE) / 10f * dens * k; }
        private float stroke() { return sp.getInt(Cfg.K_STROKE, Cfg.D_STROKE) / 10f * dens * k; }
        private float gap() { return sp.getInt(Cfg.K_GAP, Cfg.D_GAP) / 10f * dens * k; }
        private boolean showPct() { return sp.getBoolean(Cfg.K_SHOWPCT, Cfg.D_SHOWPCT); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            txt.setTextSize(textSp * dens);
            float tw = showPct() ? txt.measureText(pct + "%") : 0f;
            float w = size() + (showPct() ? gap() + tw : 0f);
            Paint.FontMetrics fm = txt.getFontMetrics();
            float h = Math.max(size(), fm.descent - fm.ascent);
            setMeasuredDimension((int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1);
        }

        @Override
        protected void onDraw(Canvas c) {
            int color = col(colorRes);
            float size = size(), sw = stroke();
            float cy = getHeight() / 2f;
            float r = (size - sw) / 2f;
            box.set(sw / 2f, cy - r, sw / 2f + 2 * r, cy + r);

            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(sw);
            track.setColor(color);
            track.setAlpha(Math.round(255f * sp.getInt(Cfg.K_TRACK, Cfg.D_TRACK) / 100f));

            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(sw);
            arc.setColor(color);
            arc.setStrokeCap(sp.getBoolean(Cfg.K_ROUND, Cfg.D_ROUND) ? Paint.Cap.ROUND : Paint.Cap.BUTT);

            c.drawArc(box, 0, 360, false, track);
            c.drawArc(box, -90, 360f * pct / 100f, false, arc);

            if (showPct()) {
                txt.setTextSize(textSp * dens);
                txt.setColor(color);
                Paint.FontMetrics fm = txt.getFontMetrics();
                c.drawText(pct + "%", size + gap(), cy - (fm.ascent + fm.descent) / 2f, txt);
            }
        }
    }
}
