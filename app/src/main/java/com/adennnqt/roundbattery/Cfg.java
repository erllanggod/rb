package com.adennnqt.roundbattery;

/** Shared keys + defaults (used by both the settings app and the hook). */
public final class Cfg {
    public static final String PKG = "com.adennnqt.roundbattery";
    public static final String FILE = "settings";

    public static final String K_SIZE = "size_tenth_dp";      // ring diameter, 0.1 dp units
    public static final String K_STROKE = "stroke_tenth_dp";  // ring thickness, 0.1 dp units
    public static final String K_GAP = "gap_tenth_dp";        // gap ring -> percent text, 0.1 dp units
    public static final String K_TRACK = "track_alpha";       // 0..100
    public static final String K_ROUND = "round_cap";         // boolean
    public static final String K_SHOWPCT = "show_percent";    // boolean

    public static final int D_SIZE = 120;
    public static final int D_STROKE = 24;
    public static final int D_GAP = 20;
    public static final int D_TRACK = 30;
    public static final boolean D_ROUND = true;
    public static final boolean D_SHOWPCT = true;

    private Cfg() {}
}
