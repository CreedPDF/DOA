package com.creedpdf.doa;

/**
 * The seven relics' moves. Each is a long cinematic with its own timeline (move ticks "a", 0 = awakened) shared by
 * the server (who is struck, when) and the client (scenes, VFX, camera, HUD).
 */
public final class Abilities {
    public static final int DEATH = 0, REDEMPTION = 1, WRATH = 2, SOUL = 3, FATE = 4, SILENCE = 5, VOID = 6;

    public static final String[] TITLE = {"ECLIPSE", "DAWN", "PLANETBREAKER", "HEART OF THE WORLD", "BEYOND", "MOONRISE", "RIFT"};
    public static final String[] LINE = {
            "the moon devours the sun, and the black sun passes judgement",
            "above the clouds a gate of heaven opens and pours down its light",
            "a world beyond the sky is broken, and its burning pieces fall",
            "down through every layer of the earth to the engine at its core",
            "past the stars, past the galaxy, to the edge of everything - and back",
            "night falls, a giant moon rises, and time itself stands still",
            "the sky tears open into space, and the void takes what it is given"};
    /** Length of each move (and its cutscene), in ticks. */
    public static final int[] LENGTH = {600, 520, 760, 760, 880, 560, 640};
    /** Rest after use, in ticks. */
    public static final int[] COOLDOWN = {1200, 1200, 1600, 1600, 2400, 1600, 1600};

    public static final double RANGE = 64;

    // 死 Eclipse
    public static final int ECL_DARK = 40, ECL_TOTAL = 290, ECL_MARK = 300, ECL_STRIKE = 340, ECL_GAP = 4, ECL_LIGHT = 470;
    // 贖 Dawn
    public static final int DAWN_RISE = 30, DAWN_UP = 110, DAWN_GATE = 200, DAWN_POUR = 330, DAWN_DOWN = 386, DAWN_HEAL = 420;
    // 怒 Planetbreaker
    public static final int BRK_UP = 90, BRK_GATHER = 150, BRK_LAUNCH = 250, BRK_HIT = 300, BRK_SHATTER = 360, BRK_DOWN = 476,
            BRK_METEOR = 500, BRK_GAP = 7, BRK_FALL = 22, BRK_METEORS = 24;
    // 魂 Heart of the World
    public static final int HRT_DIVE = 30, HRT_DOWN = 84, HRT_CORE = 400, HRT_WAKE = 420, HRT_UP = 526, HRT_DRAIN = 540, HRT_DRAIN_END = 670,
            HRT_CLIMAX = 680;
    public static final double HRT_LAYER = 50;
    // 運 Beyond
    public static final int BYD_UP = 100, BYD_ORBIT = 100, BYD_SYSTEM = 260, BYD_GALAXY = 380, BYD_WEB = 520, BYD_ALIGN = 640, BYD_RUSH = 700,
            BYD_DOWN = 766, BYD_STRIKE = 776;
    // 静 Moonrise
    public static final int MOON_NIGHT = 30, MOON_TOLL = 220, MOON_FREEZE = 600, MOON_SET = 480;
    // 虚 Rift
    public static final int RFT_OPEN = 30, RFT_LIFT = 260, RFT_TAKE = 500, RFT_SEAL = 540, RFT_RIFTS = 5;

    private Abilities() {
    }

    public static int eclipseStrike(int k) {
        return ECL_STRIKE + k * ECL_GAP;
    }

    public static int meteorStart(int k) {
        return BRK_METEOR + k * BRK_GAP;
    }

    public static int meteorHit(int k) {
        return meteorStart(k) + BRK_FALL;
    }
}
