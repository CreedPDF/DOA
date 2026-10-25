package com.creedpdf.doa;

/**
 * The seven relics' abilities. Each runs on its own timeline (ability ticks "a", 0 = awakened) shared by the
 * server (effects, sounds) and the client (VFX, camera).
 */
public final class Abilities {
    public static final int DEATH = 0, REDEMPTION = 1, WRATH = 2, SOUL = 3, FATE = 4, SILENCE = 5, VOID = 6;

    public static final String[] TITLE = {
            "VERDICT OF DEATH", "ABSOLUTION", "HEAVEN'S WRATH", "SOUL HARVEST", "ALIGNMENT OF FATE", "BELL OF SILENCE", "THE VOID MAW"};
    public static final String[] LINE = {
            "every enemy is branded, then judged",
            "heaven's light pours down and makes you whole",
            "spears of heavenly fire rain on the battlefield",
            "the souls of your enemies flow into you",
            "the stars of the seven align and strike",
            "a bell tolls and the world falls still",
            "a hungering void tears open and devours"};
    /** Length of each ability (and its cutscene), in ticks. */
    public static final int[] LENGTH = {112, 104, 150, 124, 236, 124, 156};
    /** Cooldown after use, in ticks. */
    public static final int[] COOLDOWN = {900, 1200, 900, 900, 2400, 1200, 1200};

    public static final double RANGE = 64;

    // --- Death
    public static final int DEATH_MARK = 22, DEATH_STRIKE = 72;
    // --- Redemption
    public static final int REDEEM_OPEN = 12, REDEEM_HEAL = 44;
    // --- Wrath
    public static final int WRATH_SPEARS = 30, WRATH_FIRST = 26, WRATH_GAP = 3, WRATH_FALL = 14;
    // --- Soul
    public static final int SOUL_DRAIN_START = 16, SOUL_DRAIN_END = 84, SOUL_CLIMAX = 88;
    // --- Fate
    public static final int FATE_SPACE_IN = 30, FATE_ALIGN = 96, FATE_FIRE = 150, FATE_SPACE_OUT = 184, FATE_STRIKE = 188;
    // --- Silence
    public static final int SILENCE_TOLL = 22, SILENCE_FREEZE = 200;
    // --- Void
    public static final int VOID_OPEN = 10, VOID_COLLAPSE = 118;

    private Abilities() {
    }

    public static int wrathImpact(int k) {
        return WRATH_FIRST + k * WRATH_GAP + WRATH_FALL;
    }
}
