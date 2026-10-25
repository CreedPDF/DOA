package com.creedpdf.doa.client.fx;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.List;

import static com.creedpdf.doa.client.fx.Fx.Kind.*;
import static com.creedpdf.doa.client.fx.Fx.spawn;

/** Particles, impact frames, shake and the private sounds of each ability. */
public final class AbilityFx {
    private static final float[] INK_C = {0.02f, 0.015f, 0.02f};
    private static final float[] STONE = {0.16f, 0.15f, 0.15f};

    private AbilityFx() {
    }

    private static float[] bright(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
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
        for (int k = core.fxAbilityTick + 1; k <= a; k++) events(core, ab, k, rnd);
        core.fxAbilityTick = a;
        continuous(core, ab, a, rnd);
    }

    private static boolean mine(ArmoryCoreEntity core) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && core.getOwnerNetId() == mc.player.getId();
    }

    private static void privateSound(ArmoryCoreEntity core, net.minecraft.sound.SoundEvent s, float vol) {
        if (mine(core)) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(s, 1f, vol));
    }

    private static void burst(Vec3d p, float[] c, int n, Random rnd, double speed, boolean ink) {
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = speed * (0.3 + rnd.nextDouble());
            spawn(MOTE, p.x, p.y, p.z, Math.cos(a) * s, rnd.nextDouble() * speed, Math.sin(a) * s, bright(c, rnd.nextFloat() * 0.4f), 1f,
                    0.14f + rnd.nextFloat() * 0.2f, 20 + rnd.nextInt(25), 0.004f, 0.92f, true, 0, -0.4f, 0);
            if (ink && k % 2 == 0)
                spawn(INK, p.x, p.y, p.z, Math.cos(a) * s * 0.6, 0.03, Math.sin(a) * s * 0.6, INK_C, 0.8f, 0.7f + rnd.nextFloat() * 0.6f,
                        30 + rnd.nextInt(20), -0.002f, 0.9f, false, 0.04f, 1.4f, 0);
        }
    }

    private static void events(ArmoryCoreEntity core, int ab, int a, Random rnd) {
        Vec3d c = core.getPos();
        float[] col = Pillars.COLOR[ab];
        if (a == 1) burst(core.relicPos(ab), col, 40, rnd, 0.25, false);
        switch (ab) {
            case Abilities.DEATH -> {
                if (a == Abilities.DEATH_STRIKE) {
                    for (LivingEntity t : AbilityView.targets(core)) burst(t.getPos().add(0, t.getHeight() / 2, 0), col, 30, rnd, 0.35, true);
                    Cutscene.impact(core, ab, 2.0f);
                }
            }
            case Abilities.REDEMPTION -> {
                if (a == Abilities.REDEEM_HEAL) {
                    burst(c.add(0, 1, 0), col, 90, rnd, 0.3, false);
                    Cutscene.impact(core, ab, 0.8f);
                }
            }
            case Abilities.WRATH -> {
                List<Vec3d> pts = AbilityView.points(core);
                for (int k = 0; k < pts.size(); k++) {
                    if (a != Abilities.wrathImpact(k)) continue;
                    Vec3d p = pts.get(k);
                    burst(p.add(0, 0.5, 0), col, 26, rnd, 0.45, true);
                    for (int n = 0; n < 24; n++) {
                        double an = rnd.nextDouble() * Math.PI * 2, sp = 0.2 + rnd.nextDouble() * 0.6;
                        spawn(SHARD, p.x, p.y + 0.3, p.z, Math.cos(an) * sp, 0.3 + rnd.nextDouble() * 0.5, Math.sin(an) * sp, STONE, 1f,
                                0.14f + rnd.nextFloat() * 0.12f, 30 + rnd.nextInt(20), 0.06f, 0.95f, false, (rnd.nextFloat() - 0.5f) * 0.6f, 0, 0);
                    }
                    if (k == 0) Cutscene.impact(core, ab, 1.4f);
                    else Cutscene.addShake(core, 0.9f, 20);
                }
            }
            case Abilities.SOUL -> {
                if (a == Abilities.SOUL_CLIMAX) {
                    burst(c.add(0, 1.2, 0), col, 80, rnd, 0.3, false);
                    Cutscene.impact(core, ab, 1.0f);
                }
            }
            case Abilities.FATE -> {
                if (a == 1) privateSound(core, DoaSounds.FATE_ASCEND, 1f);
                if (a == Abilities.FATE_SPACE_IN) privateSound(core, DoaSounds.FATE_SPACE, 1f);
                if (a == Abilities.FATE_FIRE + 10) Cutscene.impact(core, ab, 0.5f);
                if (a == Abilities.FATE_STRIKE) {
                    Vec3d p = AbilityView.point(core);
                    burst(p.add(0, 1, 0), col, 120, rnd, 0.7, true);
                    burst(p.add(0, 3, 0), Pillars.COLOR[1], 60, rnd, 0.5, false);
                    Cutscene.impact(core, ab, 2.6f);
                }
            }
            case Abilities.SILENCE -> {
                if (a == Abilities.SILENCE_TOLL) Cutscene.impact(core, ab, 1.2f);
            }
            case Abilities.VOID -> {
                if (a == Abilities.VOID_COLLAPSE) {
                    Vec3d p = AbilityView.point(core);
                    burst(p, col, 120, rnd, 0.8, true);
                    Cutscene.impact(core, ab, 2.2f);
                }
            }
            default -> {
            }
        }
    }

    private static void continuous(ArmoryCoreEntity core, int ab, int a, Random rnd) {
        Vec3d c = core.getPos();
        float[] col = Pillars.COLOR[ab];
        Vec3d r = core.relicPos(ab);
        if (a < 20) {
            for (int n = 0; n < 3; n++)
                spawn(SPARK, r.x + rnd.nextGaussian() * 0.3, r.y + rnd.nextGaussian() * 0.3, r.z + rnd.nextGaussian() * 0.3,
                        rnd.nextGaussian() * 0.08, 0.08, rnd.nextGaussian() * 0.08, bright(col, 0.5f), 1f, 0.07f, 12, 0, 0.9f, true, 0, 0, 0);
        }
        switch (ab) {
            case Abilities.DEATH -> {
                if (a > Abilities.DEATH_MARK && a < Abilities.DEATH_STRIKE)
                    for (LivingEntity t : AbilityView.targets(core))
                        if (rnd.nextInt(2) == 0)
                            spawn(MOTE, t.getX() + rnd.nextGaussian() * 0.3, t.getY() + rnd.nextDouble() * t.getHeight(), t.getZ() + rnd.nextGaussian() * 0.3,
                                    0, 0.04, 0, col, 0.9f, 0.12f, 24, -0.001f, 0.95f, true, 0, -0.5f, 0);
            }
            case Abilities.REDEMPTION -> {
                if (a > Abilities.REDEEM_OPEN && a < 90) {
                    for (int n = 0; n < 4; n++) {
                        double an = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 3.5;
                        spawn(PETAL, c.x + Math.cos(an) * rr, c.y + 10 + rnd.nextDouble() * 4, c.z + Math.sin(an) * rr, 0, -0.06, 0,
                                bright(col, 0.3f), 1f, 0.14f, 90, 0.002f, 0.97f, true, (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1f);
                        spawn(MOTE, c.x + Math.cos(an) * rr * 0.6, c.y + rnd.nextDouble() * 12, c.z + Math.sin(an) * rr * 0.6, 0, -0.05, 0,
                                bright(col, 0.5f), 1f, 0.12f, 30, 0, 0.96f, true, 0, -0.4f, 0);
                    }
                }
            }
            case Abilities.WRATH -> {
                List<Vec3d> pts = AbilityView.points(core);
                for (int k = 0; k < pts.size(); k++) {
                    int hit = Abilities.wrathImpact(k);
                    int u = a - (hit - Abilities.WRATH_FALL);
                    if (u < 0 || a >= hit) continue;
                    float f = Rite.clamp01(u / (double) Abilities.WRATH_FALL);
                    Vec3d p = pts.get(k);
                    double y = p.y + 62 * (1 - f * f);
                    for (int n = 0; n < 3; n++)
                        spawn(SPARK, p.x + rnd.nextGaussian() * 0.3, y + rnd.nextDouble() * 6, p.z + rnd.nextGaussian() * 0.3,
                                rnd.nextGaussian() * 0.04, 0.3, rnd.nextGaussian() * 0.04, bright(col, 0.4f), 1f, 0.09f, 10, 0, 0.9f, true, 0, 0, 0);
                }
            }
            case Abilities.SOUL -> {
                if (a >= Abilities.SOUL_DRAIN_START && a <= Abilities.SOUL_DRAIN_END) {
                    Vec3d apex = c.add(0, Rite.ORB_RADIUS + 1.5, 0);
                    for (LivingEntity t : AbilityView.targets(core)) {
                        if (rnd.nextInt(2) != 0) continue;
                        Vec3d p = t.getPos().add(0, t.getHeight() * 0.6, 0);
                        Vec3d d = apex.subtract(p).multiply(1 / 40.0);
                        spawn(MOTE, p.x, p.y, p.z, d.x, d.y + 0.05, d.z, bright(col, 0.3f), 1f, 0.16f, 40, 0, 1f, true, 0, -0.3f, 0.2f);
                    }
                    for (int n = 0; n < 2; n++)
                        spawn(MOTE, c.x + rnd.nextGaussian() * 0.3, c.y + Rite.ORB_RADIUS - 1, c.z + rnd.nextGaussian() * 0.3, 0, -0.32, 0,
                                bright(col, 0.4f), 1f, 0.18f, 40, 0, 1f, true, 0, -0.3f, 0);
                }
            }
            case Abilities.FATE -> {
                if (a < Abilities.FATE_SPACE_IN)
                    for (int n = 0; n < 6; n++)
                        spawn(MOTE, c.x + rnd.nextGaussian() * 0.5, c.y + rnd.nextDouble() * 3, c.z + rnd.nextGaussian() * 0.5, 0, 0.4 + rnd.nextDouble() * 0.4, 0,
                                bright(col, 0.5f), 1f, 0.12f, 40, 0, 1f, true, 0, -0.3f, 0);
                if (a > Abilities.FATE_STRIKE && a < Abilities.FATE_STRIKE + 40) {
                    Vec3d p = AbilityView.point(core);
                    for (int n = 0; n < 6; n++) {
                        double an = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 9;
                        spawn(PETAL, p.x + Math.cos(an) * rr, p.y + 8 + rnd.nextDouble() * 10, p.z + Math.sin(an) * rr, 0, -0.04, 0,
                                bright(n % 2 == 0 ? col : Pillars.COLOR[1], 0.2f), 1f, 0.16f, 80, 0.004f, 0.96f, true, (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1f);
                    }
                }
            }
            case Abilities.SILENCE -> {
                if (a > Abilities.SILENCE_TOLL + 4 && rnd.nextInt(3) == 0) {
                    for (LivingEntity t : AbilityView.targets(core)) {
                        if (rnd.nextInt(3) != 0) continue;
                        spawn(MOTE, t.getX() + rnd.nextGaussian() * 0.5, t.getY() + rnd.nextDouble() * t.getHeight(), t.getZ() + rnd.nextGaussian() * 0.5,
                                0, 0, 0, bright(col, 0.2f), 0.8f, 0.1f, 80, 0, 0f, true, 0, 0, 0);
                    }
                }
            }
            case Abilities.VOID -> {
                if (a > Abilities.VOID_OPEN && a < Abilities.VOID_COLLAPSE) {
                    Vec3d p = AbilityView.point(core);
                    for (int n = 0; n < 8; n++) {
                        Vec3d d = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian() * 0.5, rnd.nextGaussian()).normalize().multiply(8 + rnd.nextDouble() * 8);
                        Vec3d tang = d.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(0.25);
                        Vec3d v = d.multiply(-0.05).add(tang);
                        if (n % 2 == 0)
                            spawn(INK, p.x + d.x, p.y + d.y, p.z + d.z, v.x, v.y, v.z, INK_C, 0.8f, 0.6f, 30, 0, 1f, false, 0.05f, -0.6f, 0);
                        else
                            spawn(MOTE, p.x + d.x, p.y + d.y, p.z + d.z, v.x, v.y, v.z, bright(col, 0.3f), 1f, 0.16f, 30, 0, 1f, true, 0, -0.6f, 0);
                    }
                }
            }
            default -> {
            }
        }
    }
}
