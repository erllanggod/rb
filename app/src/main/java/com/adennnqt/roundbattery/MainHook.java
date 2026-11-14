package com.adennnqt.roundbattery;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hooks HyperOS SystemUI (checked against MiuiSystemUI 17.03.260226.r):
 *  - MiuiBatteryMeterIconView        (percent outside the icon)  -> ring
 *  - MiuiHollowBatteryMeterIconView  (percent inside the icon)   -> ring with number inside
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String ICON_VIEW = "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView";
    private static final String HOLLOW_VIEW = "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView";
    private static final String DATE_VIEW = "com.android.systemui.controlcenter.phone.widget.ControlCenterDateView";
    private static final String METER_VIEW = "com.android.systemui.statusbar.views.MiuiBatteryMeterView";

    private static XSharedPreferences prefs;
    private static float sizeDp = Cfg.D_SIZE / 10f;
    private static float strokeDp = Cfg.D_STROKE / 10f;
    private static float gapDp = Cfg.D_GAP / 10f;
    private static int trackAlpha = Cfg.D_TRACK;
    private static boolean rotateIcon = Cfg.D_ROTATE;
    private static boolean roundEnds = Cfg.D_ROUND;
    private static boolean warned = false;

    private static void loadPrefs() {
        try {
            if (prefs == null) {
                prefs = new XSharedPreferences(Cfg.PKG, Cfg.FILE);
            } else {
                prefs.reload();
            }
            if (!prefs.getFile().canRead()) {
                if (!warned) {
                    warned = true;
                    XposedBridge.log("RoundBattery: settings file not readable, using defaults. "
                            + "Enable the module for System UI AND for Round Battery in LSPosed, then reopen the app.");
                }
                return;
            }
            sizeDp = prefs.getInt(Cfg.K_SIZE, Cfg.D_SIZE) / 10f;
            strokeDp = prefs.getInt(Cfg.K_STROKE, Cfg.D_STROKE) / 10f;
            gapDp = prefs.getInt(Cfg.K_GAP, Cfg.D_GAP) / 10f;
            trackAlpha = prefs.getInt(Cfg.K_TRACK, Cfg.D_TRACK);
            rotateIcon = prefs.getBoolean(Cfg.K_ROTATE, Cfg.D_ROTATE);
            roundEnds = prefs.getBoolean(Cfg.K_ROUND, Cfg.D_ROUND);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: loadPrefs failed: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Cfg.PKG.equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(Cfg.PKG + ".MainActivity", lpparam.classLoader,
                        "isModuleActive", XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log("RoundBattery: active flag hook failed: " + t);
            }
            return;
        }
        if (!"com.android.systemui".equals(lpparam.packageName)) return;
        loadPrefs();
        try {
            hookIcon(XposedHelpers.findClass(ICON_VIEW, lpparam.classLoader), false);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: icon view hook failed: " + t);
        }
        try {
            hookIcon(XposedHelpers.findClass(HOLLOW_VIEW, lpparam.classLoader), true);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: hollow view hook failed: " + t);
        }
        try {
            hookDateView(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: date view hook failed: " + t);
        }
    }

    private static float dp(View v, float value) {
        return value * v.getResources().getDisplayMetrics().density;
    }

    /** Field names: MiuiBatteryMeterIconView uses mLevel, the Kotlin hollow view uses level. */
    private static String f(boolean hollow, String base) {
        if (!hollow) return "m" + base;
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    private void hookIcon(Class<?> icon, final boolean hollow) {
        XposedHelpers.findAndHookMethod(icon, "onMeasure", int.class, int.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam p) {
                loadPrefs();
                View v = (View) p.thisObject;
                // Non-hollow: 1dp left pad + ring + gap on the right (this is the gap to the percent text).
                // Hollow (percent inside the ring): ring + 1dp each side, no gap.
                int w = hollow
                        ? Math.round(dp(v, sizeDp + 2f))
                        : Math.round(dp(v, 1f + sizeDp + gapDp));
                int h = View.MeasureSpec.getSize((Integer) p.args[1]);
                XposedHelpers.callMethod(v, "setMeasuredDimension", w, h);
                return null;
            }
        });

        XposedHelpers.findAndHookMethod(icon, "onDraw", Canvas.class, new XC_MethodReplacement() {
            private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF box = new RectF();

            @Override
            protected Object replaceHookedMethod(MethodHookParam p) {
                try {
                    View v = (View) p.thisObject;
                    Canvas c = (Canvas) p.args[0];
                    int level = XposedHelpers.getIntField(v, f(hollow, "Level"));
                    int color = pickColor(v, hollow);

                    float d = dp(v, sizeDp);
                    float sw = dp(v, strokeDp);
                    float cx = hollow ? v.getWidth() / 2f : dp(v, 1f) + d / 2f;
                    float cy = v.getHeight() / 2f;
                    float r = (d - sw) / 2f;
                    box.set(cx - r, cy - r, cx + r, cy + r);

                    track.setStyle(Paint.Style.STROKE);
                    track.setStrokeWidth(sw);
                    track.setColor(color);
                    track.setAlpha(Math.round(255f * trackAlpha / 100f));

                    arc.setStyle(Paint.Style.STROKE);
                    arc.setStrokeWidth(sw);
                    arc.setColor(color);
                    arc.setStrokeCap(roundEnds ? Paint.Cap.ROUND : Paint.Cap.BUTT);

                    c.drawArc(box, 0, 360, false, track);
                    float sweep = 360f * Math.max(0, Math.min(100, level)) / 100f;
                    if (sweep > 0f) c.drawArc(box, -90, sweep, false, arc);

                    if (hollow) {
                        String s = String.valueOf(level);
                        float inner = d - 2f * sw;
                        txt.setColor(color);
                        txt.setTypeface(Typeface.DEFAULT_BOLD);
                        txt.setTextAlign(Paint.Align.CENTER);
                        float size = inner * 0.75f;
                        txt.setTextSize(size);
                        float maxW = inner * 0.9f;
                        float tw = txt.measureText(s);
                        if (tw > maxW) txt.setTextSize(size * maxW / tw);
                        Paint.FontMetrics fm = txt.getFontMetrics();
                        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, txt);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("RoundBattery: onDraw failed: " + t);
                }
                return null;
            }
        });
    }


    /** Same state -> color order as the stock icon: charging, power save, performance, low, tint. */
    private static int pickColor(View v, boolean hollow) {
        if (XposedHelpers.getBooleanField(v, f(hollow, "Charging")))
            return XposedHelpers.getIntField(v, f(hollow, "BatteryChargingColor"));
        if (XposedHelpers.getBooleanField(v, f(hollow, "PowerSave")))
            return XposedHelpers.getIntField(v, f(hollow, "BatteryPowerSaveColor"));
        if (XposedHelpers.getBooleanField(v, f(hollow, "PerformanceMode")))
            return XposedHelpers.getIntField(v, f(hollow, "BatteryPerformanceModeColor"));
        if (XposedHelpers.getBooleanField(v, f(hollow, "Low")))
            return XposedHelpers.getIntField(v, f(hollow, "BatteryLowColor"));
        if (XposedHelpers.getBooleanField(v, f(hollow, "UseTint")))
            return XposedHelpers.getIntField(v, f(hollow, "TintColor"));
        float intensity = XposedHelpers.getFloatField(v, f(hollow, "DarkIntensity"));
        if (!hollow) {
            return intensity > 0.5f
                    ? XposedHelpers.getIntField(v, "mDarkColor")
                    : XposedHelpers.getIntField(v, "mLightColor");
        }
        return intensity > 0.5f ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    /** Rotation-lock icon right after the date in the control center header (shown only while rotation is locked). */
    private void hookDateView(ClassLoader cl) {
        Class<?> date = XposedHelpers.findClass(DATE_VIEW, cl);
        XposedHelpers.findAndHookMethod(date, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    attachRotateIcon((TextView) p.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log("RoundBattery: attachRotateIcon failed: " + t);
                }
            }
        });
    }

    private static void attachRotateIcon(final TextView tv) {
        final Context ctx = tv.getContext();
        final RotateLockDrawable icon = new RotateLockDrawable(tv);

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                loadPrefs();
                boolean locked = Settings.System.getInt(ctx.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
                if (rotateIcon && locked) {
                    int size = Math.round(tv.getTextSize());
                    icon.setBounds(0, 0, size, size);
                    tv.setCompoundDrawablesRelative(null, null, icon, null);
                    tv.setCompoundDrawablePadding(Math.round(dp(tv, 4f)));
                } else {
                    tv.setCompoundDrawablesRelative(null, null, null, null);
                }
            }
        };

        final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                update.run();
            }
        };

        final Runnable register = new Runnable() {
            @Override
            public void run() {
                ctx.getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer);
                update.run();
            }
        };

        tv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                register.run();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                ctx.getContentResolver().unregisterContentObserver(observer);
            }
        });
        if (tv.isAttachedToWindow()) register.run();
    }
}
