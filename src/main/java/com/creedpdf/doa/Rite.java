package com.creedpdf.doa;

/**
 * The timeline of the summoning rite, shared by the server (sounds, collision, events) and the client
 * (models, VFX, camera). Everything is a pure function of the rite's tick counter so every viewer sees the same thing.
 */
public final class Rite {
    public static final double PILLAR_RADIUS = 8.0;
    public static final double ORB_RADIUS = 15.0;
    public static final double FALL_HEIGHT = 110.0;

    public static final int LEAD = 40;
    public static final int SLOT = 40;
    public static final int FALL = 26;

    public static final int LAST_LAND = land(Pillars.COUNT - 1);
    public static final int SEAL = LAST_LAND + 24;
    public static final int CORE_START = SEAL + 10;
    public static final int CORE_END = CORE_START + 30;
    public static final int CORRUPT_START = CORE_START + 15;
    public static final int CORRUPT_END = CORRUPT_START + 50;
    public static final int RISE_START = CORRUPT_END - 5;
    public static final int RISE_END = RISE_START + 120;
    public static final int SEALED = RISE_END;
    public static final int CUT_END = SEALED + 40;

    public static final int REL_SINK = 70;
    public static final int REL_ASCEND_START = 50;
    public static final int REL_ASCEND = 70;
    public static final int REL_END = REL_ASCEND_START + REL_ASCEND + 10;

    private Rite() {
    }

    public static int spawn(int i) {
        return LEAD + i * SLOT;
    }

    public static int land(int i) {
        return spawn(i) + FALL;
    }

    public static int relicAppear(int i) {
        return SEALED + 12 + i * 6;
    }

    public static double angle(int i) {
        return i * Math.PI * 2 / Pillars.COUNT;
    }

    public static double dx(int i) {
        return Math.sin(angle(i)) * PILLAR_RADIUS;
    }

    public static double dz(int i) {
        return -Math.cos(angle(i)) * PILLAR_RADIUS;
    }

    public static float clamp01(double x) {
        return (float) Math.max(0, Math.min(1, x));
    }

    public static float smooth(double x) {
        float k = clamp01(x);
        return k * k * (3 - 2 * k);
    }

    public static float easeOut(double x) {
        float k = 1 - clamp01(x);
        return 1 - k * k * k;
    }

    /** Height of pillar i above its landing point (accelerating plunge). */
    public static double fallHeight(int i, double t) {
        float p = clamp01((t - spawn(i)) / FALL);
        return FALL_HEIGHT * (1 - p * p);
    }

    public static boolean visible(int i, double t) {
        return t >= spawn(i);
    }

    public static boolean falling(int i, double t) {
        return t >= spawn(i) && t < land(i);
    }

    public static boolean landed(int i, double t) {
        return t >= land(i);
    }

    /** 0..1 how far the dome has risen (1 = closed). */
    public static float dome(double t, double rel) {
        if (rel >= 0) return 1 - smooth(rel / REL_SINK);
        return smooth((t - RISE_START) / (RISE_END - RISE_START));
    }

    /** 0..1 how far the ink corruption has spread over the ground. */
    public static float corrupt(double t, double rel) {
        if (rel >= 0) return 1 - smooth((rel - 20) / 60.0);
        return smooth((t - CORRUPT_START) / (CORRUPT_END - CORRUPT_START));
    }

    /** 0..1 size of the black core orb that the corruption pours from. */
    public static float core(double t, double rel) {
        if (rel >= 0) return 0;
        float grow = easeOut((t - CORE_START) / (CORE_END - CORE_START));
        float fade = 1 - smooth((t - (RISE_END - 30)) / 30.0);
        return grow * fade;
    }

    /** How far the pillars have risen back into the sky on release. */
    public static double ascend(double rel) {
        if (rel < REL_ASCEND_START) return 0;
        float k = clamp01((rel - REL_ASCEND_START) / REL_ASCEND);
        return k * k * 150;
    }

    /** Ragged edge noise (0..1) around the circle. */
    public static double edgeNoise(double theta, double t) {
        return 0.5 + 0.22 * Math.sin(theta * 7 + t * 0.031) + 0.16 * Math.sin(theta * 13 - t * 0.047 + 1.3)
                + 0.08 * Math.sin(theta * 31 + t * 0.11 + 0.4) + 0.04 * Math.sin(theta * 57 - t * 0.19);
    }

    /** Elevation (degrees) of the dome's corrupted rim at angle theta. */
    public static double domeEdge(double theta, float p, double t) {
        double jag = 16;
        double e = p * (90 + jag) - jag * edgeNoise(theta, t);
        return Math.max(0, Math.min(90, e));
    }

    /** Radius of the spreading ground corruption at angle theta. */
    public static double corruptEdge(double theta, float c, double t) {
        double r = c * (ORB_RADIUS + 3.5) * (0.82 + 0.18 * edgeNoise(theta * 1.7 + 2, t * 1.3));
        return Math.max(0, Math.min(ORB_RADIUS, r));
    }
}
