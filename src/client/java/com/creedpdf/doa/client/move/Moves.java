package com.creedpdf.doa.client.move;

import static com.creedpdf.doa.Abilities.*;

/** The seven moves, by pillar. */
public final class Moves {
    private static final Move[] MOVES = {new EclipseMove(), new DawnMove(), new BreakerMove(), new HeartMove(), new BeyondMove(), new MoonriseMove(),
            new RiftMove()};

    private Moves() {
    }

    public static Move get(int i) {
        return MOVES[Math.max(0, Math.min(MOVES.length - 1, i))];
    }

    /** True while the caller's camera is inside a private pocket scene (the world is not drawn there). */
    public static boolean inPocket(int i, double a) {
        return switch (i) {
            case REDEMPTION -> a >= DAWN_UP && a < DAWN_DOWN;
            case WRATH -> a >= BRK_UP && a < BRK_DOWN;
            case SOUL -> a >= HRT_DOWN && a < HRT_UP;
            case FATE -> a >= BYD_UP && a < BYD_DOWN;
            default -> false;
        };
    }
}
