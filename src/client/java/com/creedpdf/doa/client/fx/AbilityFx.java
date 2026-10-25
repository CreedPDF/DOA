package com.creedpdf.doa.client.fx;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.client.move.Kit;
import com.creedpdf.doa.client.move.Moves;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Drives the running move's particles and one-shot events from its synced clock. */
public final class AbilityFx {
    private AbilityFx() {
    }

    public static void tick(ArmoryCoreEntity core) {
        int ab = core.getAbility();
        if (ab < 0) {
            core.fxAbility = -1;
            return;
        }
        int a = core.getAbilityTicks();
        if (core.fxAbility != ab) {
            core.fxAbility = ab;
            core.fxAbilityTick = a - 1;
        }
        Random rnd = core.getWorld().random;
        if (a - core.fxAbilityTick > 40) core.fxAbilityTick = a - 1;
        for (int k = core.fxAbilityTick + 1; k <= a; k++) {
            if (k == 1) {
                Vec3d r = core.relicPos(ab);
                Kit.burst(r, Pillars.COLOR[ab], 40, rnd, 0.25, false, false);
            }
            Moves.get(ab).tick(core, k, true, rnd);
        }
        core.fxAbilityTick = a;
    }
}
