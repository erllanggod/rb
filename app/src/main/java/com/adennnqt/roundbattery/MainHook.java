package com.adennnqt.roundbattery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hooks HyperOS SystemUI (tested against dex of MiuiSystemUI 17.03.260226.r):
 *  - MiuiBatteryMeterIconView.onMeasure / onDraw  -> draw a ring of our own size
 *  - MiuiBatteryMeterView.updateAll / updateChargeAndText -> gap between ring and percent text
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String ICON_VIEW = "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView";
    private static final String METER_VIEW = "com.android.systemui.statusbar.views.MiuiBatteryMeterView";

    private static XSharedPreferences prefs;
    private static float sizeDp = Cfg.D_SIZE / 10f;
    private static float strokeDp = Cfg.D_STROKE / 10f;
    private static float gapDp = Cfg.D_GAP / 10f;
    private static int trackAlpha = Cfg.D_TRACK;
    private static boolean roundCap = Cfg.D_ROUND;
    private static boolean showPercent = Cfg.D_SHOWPCT;

    private static void loadPrefs() {
        try {
            if (prefs == null) {
                prefs = new XSharedPreferences(Cfg.PKG, Cfg.FILE);
                prefs.makeWorldReadable();
            } else {
                prefs.reload();
            }
            sizeDp = prefs.getInt(Cfg.K_SIZE, Cfg.D_SIZE) / 10f;
            strokeDp = prefs.getInt(Cfg.K_STROKE, Cfg.D_STROKE) / 10f;
            gapDp = prefs.getInt(Cfg.K_GAP, Cfg.D_GAP) / 10f;
            trackAlpha = prefs.getInt(Cfg.K_TRACK, Cfg.D_TRACK);
            roundCap = prefs.getBoolean(Cfg.K_ROUND, Cfg.D_ROUND);
            showPercent = prefs.getBoolean(Cfg.K_SHOWPCT, Cfg.D_SHOWPCT);
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
            hookIconView(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: hookIconView failed: " + t);
        }
        try {
            hookMeterView(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("RoundBattery: hookMeterView failed: " + t);
        }
    }

    private static float dp(View v, float value) {
        return value * v.getResources().getDisplayMetrics().density;
    }

    private void hookIconView(ClassLoader cl) {
        Class<?> icon = XposedHelpers.findClass(ICON_VIEW, cl);

        // Width = ring + small padding, height = whatever the parent gives (same as stock).
        XposedHelpers.findAndHookMethod(icon, "onMeasure", int.class, int.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam p) {
                loadPrefs();
                View v = (View) p.thisObject;
                int w = Math.round(dp(v, sizeDp + 2f));
                int h = View.MeasureSpec.getSize((Integer) p.args[1]);
                XposedHelpers.callMethod(v, "setMeasuredDimension", w, h);
                return null;
            }
        });

        XposedHelpers.findAndHookMethod(icon, "onDraw", Canvas.class, new XC_MethodReplacement() {
            private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF box = new RectF();

            @Override
            protected Object replaceHookedMethod(MethodHookParam p) {
                try {
                    View v = (View) p.thisObject;
                    Canvas c = (Canvas) p.args[0];
                    int level = XposedHelpers.getIntField(v, "mLevel");
                    int color = pickColor(v);

                    float d = dp(v, sizeDp);
                    float sw = dp(v, strokeDp);
                    float cx = v.getWidth() / 2f;
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
                    arc.setStrokeCap(roundCap ? Paint.Cap.ROUND : Paint.Cap.BUTT);

                    c.drawArc(box, 0, 360, false, track);
                    float sweep = 360f * Math.max(0, Math.min(100, level)) / 100f;
                    if (sweep > 0f) c.drawArc(box, -90, sweep, false, arc);
                } catch (Throwable t) {
                    XposedBridge.log("RoundBattery: onDraw failed: " + t);
                }
                return null;
            }
        });
    }

    /** Same state -> color order as the stock icon: charging, power save, performance, low, tint. */
    private static int pickColor(View v) {
        boolean charging = XposedHelpers.getBooleanField(v, "mCharging");
        if (charging) return XposedHelpers.getIntField(v, "mBatteryChargingColor");
        if (XposedHelpers.getBooleanField(v, "mPowerSave"))
            return XposedHelpers.getIntField(v, "mBatteryPowerSaveColor");
        if (XposedHelpers.getBooleanField(v, "mPerformanceMode"))
            return XposedHelpers.getIntField(v, "mBatteryPerformanceModeColor");
        if (XposedHelpers.getBooleanField(v, "mLow"))
            return XposedHelpers.getIntField(v, "mBatteryLowColor");
        if (XposedHelpers.getBooleanField(v, "mUseTint"))
            return XposedHelpers.getIntField(v, "mTintColor");
        float intensity = XposedHelpers.getFloatField(v, "mDarkIntensity");
        return intensity > 0.5f
                ? XposedHelpers.getIntField(v, "mDarkColor")
                : XposedHelpers.getIntField(v, "mLightColor");
    }

    private void hookMeterView(ClassLoader cl) {
        Class<?> meter = XposedHelpers.findClass(METER_VIEW, cl);
        XC_MethodHook applyGap = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View self = (View) p.thisObject;
                    Object container = XposedHelpers.getObjectField(p.thisObject, "mBatteryPercentContainer");
                    if (!(container instanceof LinearLayout)) return;
                    LinearLayout ll = (LinearLayout) container;
                    ViewGroup.LayoutParams lp = ll.getLayoutParams();
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.round(dp(self, gapDp)));
                        ll.setLayoutParams(lp);
                    }
                    if (!showPercent) ll.setVisibility(View.GONE);
                } catch (Throwable t) {
                    XposedBridge.log("RoundBattery: applyGap failed: " + t);
                }
            }
        };
        XposedHelpers.findAndHookMethod(meter, "updateAll", applyGap);
        XposedHelpers.findAndHookMethod(meter, "updateChargeAndText", applyGap);
    }
}
