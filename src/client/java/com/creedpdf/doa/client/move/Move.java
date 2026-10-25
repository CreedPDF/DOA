package com.creedpdf.doa.client.move;

import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** One relic's cinematic move on the client: its world/scene rendering, particles, camera and overlay. */
public interface Move {
    /** World and pocket-scene rendering for everyone (pocket scenes check {@link MoveCtx#mine}). */
    void render(MoveCtx c);

    /** Particles and one-shot events; called for every move tick crossed. */
    default void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
    }

    /** The caller's camera: {position, look-at} in world coordinates, or null to stay on the player. */
    Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw);

    /** Screen overlay (fades, captions) for the caller. */
    default void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
    }

    /** Degrees of the dome's crown opened (Dawn). */
    default double oculus(double a) {
        return 0;
    }
}
