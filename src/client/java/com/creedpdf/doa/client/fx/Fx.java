package com.creedpdf.doa.client.fx;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.Map;

/** All of the rite's particle work, driven per client tick from the armory's synced timeline. */
public final class Fx {
    public enum Kind {MOTE, SPARK, PETAL, INK, SHARD}

    private static final Map<Kind, SpriteProvider> SPRITES = new EnumMap<>(Kind.class);
    private static final float[] INK_C = {0.02f, 0.015f, 0.02f};
    private static final float[] STONE_C = {0.16f, 0.15f, 0.15f};
    private static final float[] PAPER_C = {0.88f, 0.82f, 0.66f};

    private Fx() {
    }

    public static void register() {
        reg(DoaParticles.MOTE, Kind.MOTE, 0.25f, 30, true);
        reg(DoaParticles.SPARK, Kind.SPARK, 0.12f, 16, true);
        reg(DoaParticles.PETAL, Kind.PETAL, 0.18f, 60, false);
        reg(DoaParticles.INK, Kind.INK, 0.6f, 40, false);
        reg(DoaParticles.SHARD, Kind.SHARD, 0.2f, 40, false);
    }

    private static void reg(SimpleParticleType type, Kind kind, float size, int life, boolean glow) {
        ParticleFactoryRegistry.getInstance().register(type, provider -> {
            SPRITES.put(kind, provider);
            return (params, world, x, y, z, vx, vy, vz) -> new DoaParticle(world, x, y, z, vx, vy, vz, provider,
                    1, 1, 1, 1, size, life, 0, 0.96f, glow, 0, 0, 0);
        });
    }

    public static void spawn(Kind k, double x, double y, double z, double vx, double vy, double vz, float[] c, float a,
                             float size, int life, float gravity, float drag, boolean glow, float spin, float growth, float sway) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpriteProvider sp = SPRITES.get(k);
        if (sp == null || mc.world == null) return;
        mc.particleManager.addParticle(new DoaParticle(mc.world, x, y, z, vx, vy, vz, sp, c[0], c[1], c[2], a, size, life,
                gravity, drag, glow, spin, growth, sway));
    }

    private static float[] bright(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    // ------------------------------------------------------------------------------------------------ per tick

    public static void tick(ArmoryCoreEntity core) {
        ClientWorld world = (ClientWorld) core.getWorld();
        Random rnd = world.random;
        int t = core.getRiteTicks();
        int rel = core.getReleaseTicks();
        if (core.fxTick < 0 || t - core.fxTick > 40) core.fxTick = t - 1;
        for (int k = core.fxTick + 1; k <= t; k++) events(core, k, rnd);
        core.fxTick = t;
        if (rel >= 0) releaseFx(core, rel, rnd);
        else continuous(core, t, rnd);
    }

    private static void events(ArmoryCoreEntity core, int t, Random rnd) {
        for (int i = 0; i < Pillars.COUNT; i++) {
            if (t == Rite.land(i)) impact(core, i, rnd);
            if (t == Rite.land(i) + 6) reveal(core, i, rnd);
            if (t == Rite.relicAppear(i)) relicBurst(core, i, rnd);
        }
        if (t == Rite.SEAL) {
            for (int i = 0; i < Pillars.COUNT; i++) {
                Vec3d b = core.pillarBase(i);
                float[] c = Pillars.COLOR[i];
                for (int n = 0; n < 24; n++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    spawn(Kind.MOTE, b.x + Math.cos(a) * 1.4, b.y + 0.2, b.z + Math.sin(a) * 1.4, 0, 0.05 + rnd.nextDouble() * 0.1, 0,
                            bright(c, 0.2f), 0.9f, 0.18f, 40, -0.002f, 0.97f, true, 0, -0.5f, 0.6f);
                }
            }
        }
        if (t == Rite.SEALED) {
            Vec3d c = core.getPos();
            for (int n = 0; n < 160; n++) {
                double a = rnd.nextDouble() * Math.PI * 2, e = rnd.nextDouble() * Math.PI / 2;
                double R = Rite.ORB_RADIUS - 0.4;
                double x = Math.cos(a) * Math.cos(e) * R, y = Math.sin(e) * R, z = Math.sin(a) * Math.cos(e) * R;
                float[] col = Pillars.colorAt(Math.atan2(x, -z));
                spawn(Kind.MOTE, c.x + x, c.y + y, c.z + z, -x * 0.012, -y * 0.012, -z * 0.012, bright(col, 0.3f), 1f, 0.22f,
                        50 + rnd.nextInt(30), 0, 0.98f, true, 0, -0.6f, 0);
            }
            Cutscene.addShake(core, 1.2f, 16);
        }
    }

    private static void impact(ArmoryCoreEntity core, int i, Random rnd) {
        Vec3d b = core.pillarBase(i);
        float[] c = Pillars.COLOR[i];
        for (int n = 0; n < 70; n++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.2 + rnd.nextDouble() * 0.7;
            spawn(Kind.SHARD, b.x, b.y + 0.4, b.z, Math.cos(a) * s, 0.35 + rnd.nextDouble() * 0.6, Math.sin(a) * s,
                    rnd.nextFloat() < 0.6f ? STONE_C : PAPER_C, 1f, 0.12f + rnd.nextFloat() * 0.14f, 30 + rnd.nextInt(30),
                    0.06f, 0.95f, false, (rnd.nextFloat() - 0.5f) * 0.6f, 0, 0);
        }
        for (int n = 0; n < 46; n++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.15 + rnd.nextDouble() * 0.45;
            spawn(Kind.INK, b.x + Math.cos(a) * 0.6, b.y + 0.3 + rnd.nextDouble(), b.z + Math.sin(a) * 0.6, Math.cos(a) * s,
                    0.02 + rnd.nextDouble() * 0.08, Math.sin(a) * s, INK_C, 0.8f, 0.8f + rnd.nextFloat() * 0.8f, 40 + rnd.nextInt(30),
                    -0.002f, 0.9f, false, (rnd.nextFloat() - 0.5f) * 0.05f, 1.6f, 0);
        }
        for (int n = 0; n < 60; n++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.1 + rnd.nextDouble() * 0.5;
            spawn(Kind.MOTE, b.x, b.y + 1 + rnd.nextDouble() * 3, b.z, Math.cos(a) * s, 0.05 + rnd.nextDouble() * 0.35, Math.sin(a) * s,
                    bright(c, rnd.nextFloat() * 0.4f), 1f, 0.15f + rnd.nextFloat() * 0.2f, 25 + rnd.nextInt(30), 0.004f, 0.93f, true, 0, -0.4f, 0);
        }
        for (int n = 0; n < 30; n++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            spawn(Kind.PETAL, b.x + Math.cos(a) * 2, b.y + 2 + rnd.nextDouble() * 5, b.z + Math.sin(a) * 2,
                    Math.cos(a) * 0.15, 0.1 + rnd.nextDouble() * 0.15, Math.sin(a) * 0.15, bright(c, 0.15f), 1f,
                    0.14f + rnd.nextFloat() * 0.08f, 80 + rnd.nextInt(60), 0.006f, 0.96f, false, (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1.2f);
        }
        for (int n = 0; n < 40; n++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.6 + rnd.nextDouble() * 0.9;
            spawn(Kind.SPARK, b.x, b.y + 0.3, b.z, Math.cos(a) * s, 0.05 + rnd.nextDouble() * 0.2, Math.sin(a) * s, bright(c, 0.5f),
                    1f, 0.07f, 10 + rnd.nextInt(10), 0.02f, 0.86f, true, 0, 0, 0);
        }
        Cutscene.onImpact(core, i);
    }

    private static void reveal(ArmoryCoreEntity core, int i, Random rnd) {
        Vec3d b = core.pillarBase(i);
        float[] c = Pillars.COLOR[i];
        for (int n = 0; n < 50; n++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = 0.6 + rnd.nextDouble() * 0.7;
            spawn(Kind.MOTE, b.x + Math.cos(a) * rr, b.y + 10.2 + (rnd.nextDouble() - 0.5) * 1.4, b.z + Math.sin(a) * rr,
                    Math.cos(a) * 0.04, 0.02, Math.sin(a) * 0.04, bright(c, 0.35f), 1f, 0.12f, 30 + rnd.nextInt(20), -0.001f, 0.95f, true, 0, -0.5f, 0);
        }
    }

    private static void relicBurst(ArmoryCoreEntity core, int i, Random rnd) {
        Vec3d b = core.pillarBase(i);
        Vec3d in = new Vec3d(core.getX() - b.x, 0, core.getZ() - b.z).normalize();
        Vec3d p = b.add(in.multiply(1.2)).add(0, 4, 0);
        float[] c = Pillars.COLOR[i];
        for (int n = 0; n < 40; n++) {
            spawn(Kind.SPARK, p.x, p.y, p.z, in.x * 0.2 + rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12, in.z * 0.2 + rnd.nextGaussian() * 0.12,
                    bright(c, 0.5f), 1f, 0.06f, 12 + rnd.nextInt(8), 0, 0.88f, true, 0, 0, 0);
        }
    }

    private static void continuous(ArmoryCoreEntity core, int t, Random rnd) {
        Vec3d c = core.getPos();
        // the call: a thin rain of light motes from the sky during the opening
        if (t < Rite.spawn(0) + 10) {
            for (int n = 0; n < 4; n++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * Rite.PILLAR_RADIUS * 1.3;
                spawn(Kind.MOTE, c.x + Math.cos(a) * r, c.y + 18 + rnd.nextDouble() * 20, c.z + Math.sin(a) * r, 0, -0.25, 0,
                        new float[]{1f, 0.93f, 0.8f}, 0.8f, 0.12f, 70, 0, 1f, true, 0, 0, 0);
            }
        }
        for (int i = 0; i < Pillars.COUNT; i++) {
            Vec3d b = core.pillarBase(i);
            float[] col = Pillars.COLOR[i];
            if (Rite.falling(i, t)) {
                double h = Rite.fallHeight(i, t);
                for (int n = 0; n < 6; n++) {
                    spawn(Kind.SPARK, b.x + rnd.nextGaussian() * 0.6, b.y + h + 1 + rnd.nextDouble() * 9, b.z + rnd.nextGaussian() * 0.6,
                            rnd.nextGaussian() * 0.05, 0.3 + rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.05, bright(col, 0.4f), 1f, 0.09f,
                            10 + rnd.nextInt(8), 0, 0.9f, true, 0, 0, 0);
                    spawn(Kind.MOTE, b.x + rnd.nextGaussian() * 0.9, b.y + h + rnd.nextDouble() * 12, b.z + rnd.nextGaussian() * 0.9,
                            0, 0.1, 0, bright(col, 0.2f), 0.8f, 0.25f, 18 + rnd.nextInt(10), 0, 0.92f, true, 0, -0.6f, 0);
                }
                if (rnd.nextInt(2) == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = 2.6 + rnd.nextDouble() * 0.6;
                    spawn(Kind.MOTE, b.x + Math.cos(a) * rr, b.y + 0.1, b.z + Math.sin(a) * rr, 0, 0.06, 0, bright(col, 0.3f), 0.9f, 0.14f,
                            30, -0.001f, 0.96f, true, 0, -0.5f, 0);
                }
            } else if (Rite.landed(i, t) && rnd.nextInt(3) == 0) {
                double a = rnd.nextDouble() * Math.PI * 2;
                spawn(Kind.MOTE, b.x + Math.cos(a) * 0.95, b.y + 1 + rnd.nextDouble() * 6, b.z + Math.sin(a) * 0.95,
                        Math.cos(a) * 0.01, 0.025, Math.sin(a) * 0.01, bright(col, 0.25f), 0.8f, 0.1f, 40, -0.0005f, 0.98f, true, 0, -0.4f, 0.3f);
            }
        }
        double core3 = Rite.core(t, -1);
        Vec3d orb = c.add(0, 3.6, 0);
        if (core3 > 0.02) {
            for (int n = 0; n < 5; n++) {
                Vec3d d = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian()).normalize().multiply(3.5 + rnd.nextDouble() * 3);
                spawn(Kind.INK, orb.x + d.x, orb.y + d.y, orb.z + d.z, -d.x * 0.045, -d.y * 0.045, -d.z * 0.045, INK_C, 0.75f,
                        0.4f + rnd.nextFloat() * 0.4f, 22, 0, 1f, false, 0.04f, -0.7f, 0);
            }
        }
        float corr = Rite.corrupt(t, -1);
        if (corr > 0.01 && corr < 0.999) {
            for (int n = 0; n < 12; n++) {
                double th = rnd.nextDouble() * Math.PI * 2;
                double r = Rite.corruptEdge(th, corr, t);
                double x = Math.sin(th) * r, z = -Math.cos(th) * r;
                spawn(Kind.INK, c.x + x, c.y + 0.15, c.z + z, x * 0.004, 0.03 + rnd.nextDouble() * 0.04, z * 0.004, INK_C, 0.8f,
                        0.5f + rnd.nextFloat() * 0.5f, 30, -0.001f, 0.95f, false, 0.03f, 1.2f, 0);
            }
        }
        float dome = Rite.dome(t, -1);
        if (dome > 0.001 && dome < 0.999) {
            for (int n = 0; n < 16; n++) {
                double th = rnd.nextDouble() * Math.PI * 2;
                double e = Math.toRadians(Rite.domeEdge(th, dome, t));
                double R = Rite.ORB_RADIUS;
                double x = Math.sin(th) * Math.cos(e) * R, y = Math.sin(e) * R, z = -Math.cos(th) * Math.cos(e) * R;
                float[] col = Pillars.colorAt(th);
                if (rnd.nextInt(3) == 0)
                    spawn(Kind.MOTE, c.x + x, c.y + y, c.z + z, x * 0.003, 0.04, z * 0.003, bright(col, 0.3f), 1f, 0.2f, 24, -0.001f, 0.95f, true, 0, -0.5f, 0);
                else
                    spawn(Kind.INK, c.x + x * 1.02, c.y + y + 0.2, c.z + z * 1.02, x * 0.004, 0.05, z * 0.004, INK_C, 0.85f, 0.55f, 26, -0.002f, 0.95f, false, 0.03f, 1.1f, 0);
            }
        }
        if (t > Rite.SEALED && rnd.nextInt(2) == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * (Rite.ORB_RADIUS - 2);
            int i = rnd.nextInt(Pillars.COUNT);
            spawn(Kind.MOTE, c.x + Math.cos(a) * r, c.y + 0.2 + rnd.nextDouble() * 2, c.z + Math.sin(a) * r, 0, 0.02, 0,
                    bright(Pillars.COLOR[i], 0.3f), 0.7f, 0.1f, 90, -0.0004f, 0.99f, true, 0, 0, 0.4f);
        }
    }

    private static void releaseFx(ArmoryCoreEntity core, int rel, Random rnd) {
        Vec3d c = core.getPos();
        float dome = Rite.dome(0, rel);
        if (dome > 0.001) {
            for (int n = 0; n < 14; n++) {
                double th = rnd.nextDouble() * Math.PI * 2;
                double e = Math.toRadians(Rite.domeEdge(th, dome, core.age));
                double R = Rite.ORB_RADIUS;
                double x = Math.sin(th) * Math.cos(e) * R, y = Math.sin(e) * R, z = -Math.cos(th) * Math.cos(e) * R;
                float[] col = Pillars.colorAt(th);
                spawn(Kind.PETAL, c.x + x, c.y + y, c.z + z, rnd.nextGaussian() * 0.05, 0.05, rnd.nextGaussian() * 0.05,
                        bright(col, 0.2f), 1f, 0.13f, 80, 0.004f, 0.96f, false, (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1f);
            }
        }
        if (rel >= Rite.REL_ASCEND_START) {
            double up = Rite.ascend(rel);
            for (int i = 0; i < Pillars.COUNT; i++) {
                Vec3d b = core.pillarBase(i);
                for (int n = 0; n < 3; n++)
                    spawn(Kind.MOTE, b.x + rnd.nextGaussian() * 0.6, b.y + up + rnd.nextDouble() * 2, b.z + rnd.nextGaussian() * 0.6, 0, -0.1, 0,
                            bright(Pillars.COLOR[i], 0.3f), 1f, 0.2f, 20, 0, 0.95f, true, 0, -0.5f, 0);
            }
        }
    }

    @SuppressWarnings("unused")
    private static double wrap(double a) {
        return MathHelper.wrapDegrees(a);
    }
}
