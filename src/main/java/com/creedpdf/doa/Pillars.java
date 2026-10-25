package com.creedpdf.doa;

/** The seven pillars of the Orbital Armory, in the order they fall (clockwise from north). */
public final class Pillars {
    public static final int COUNT = 7;

    public static final String[] ID = {"death", "redemption", "wrath", "soul", "fate", "silence", "void"};
    public static final String[] KANJI = {"死", "贖", "怒", "魂", "運", "静", "虚"};
    public static final String[] NAME = {"DEATH", "REDEMPTION", "WRATH", "SOUL", "FATE", "SILENCE", "VOID"};
    /** Theme colours (linear-ish 0..1 RGB). */
    public static final float[][] COLOR = {
            {0.86f, 0.06f, 0.12f},  // crimson
            {0.95f, 0.72f, 0.22f},  // gold
            {1.00f, 0.36f, 0.10f},  // ember
            {0.26f, 0.80f, 0.55f},  // jade
            {0.62f, 0.38f, 1.00f},  // violet
            {0.93f, 0.90f, 0.82f},  // ivory
            {1.00f, 0.32f, 0.64f},  // sakura
    };
    /** Hirajoshi steps (semitones) used to pitch each pillar's koto reveal. */
    public static final int[] STEP = {-5, -4, 0, 2, 3, 7, 8};

    private Pillars() {
    }

    public static int rgb(int i) {
        float[] c = COLOR[i];
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    public static float pitch(int i) {
        return (float) Math.pow(2, STEP[i] / 12.0);
    }

    /** Colour of the seam at an angle around the circle (radians, 0 = north, clockwise): blends neighbouring pillars. */
    public static float[] colorAt(double angle) {
        double f = angle / (Math.PI * 2) * COUNT;
        f = ((f % COUNT) + COUNT) % COUNT;
        int a = (int) Math.floor(f);
        int b = (a + 1) % COUNT;
        float k = (float) (f - a);
        k = k * k * (3 - 2 * k);
        float[] ca = COLOR[a], cb = COLOR[b];
        return new float[]{ca[0] + (cb[0] - ca[0]) * k, ca[1] + (cb[1] - ca[1]) * k, ca[2] + (cb[2] - ca[2]) * k};
    }
}
