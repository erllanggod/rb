package com.adennnqt.roundbattery;

/** Shared keys + defaults (used by both the settings app and the hook). */
public final class Cfg {
    public static final String PKG = "com.adennnqt.roundbattery";
    public static final String FILE = "settings";

    public static final String K_SIZE = "size_tenth_dp";      // ring diameter, 0.1 dp units
    public static final String K_STROKE = "stroke_tenth_dp";  // ring thickness, 0.1 dp units
    public static final String K_GAP = "gap_tenth_dp";        // gap ring -> percent text, 0.1 dp units
    public static final String K_TRACK = "track_alpha";       // 0..100
    public static final String K_STYLE = "icon_style";        // 0 = ring, 1 = vertical battery
    public static final String K_ROUND = "round_ends";        // boolean: round line ends on the ring
    public static final String K_ROTATE = "rotate_icon";      // boolean: rotation-lock icon next to the date

    public static final int D_SIZE = 135;
    public static final int D_STROKE = 20;
    public static final int D_GAP = 20;
    public static final int D_TRACK = 30;
    public static final boolean D_ROTATE = true;
    public static final boolean D_ROUND = true;
    public static final int D_STYLE = 0;

    // vertical battery proportions (height = Size x BAT_H, width = height x BAT_W)
    public static final float BAT_H = 0.85f;
    public static final float BAT_W = 0.66f;

    private Cfg() {}
}
