package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.render.Universe;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.List;

import static com.creedpdf.doa.Abilities.*;

/**
 * 死 ECLIPSE. The sky darkens to a bloody dusk while the moon slides across the sun; at totality the corona flares
 * around a black sun, the diamond ring flashes, and the black sun weeps crimson light down onto every enemy in turn.
 * Midway the camera tears up out of the sky into orbit and pulls back to watch the moon's shadow - a black spot ringed
 * by dusk, city lights blinking on inside it - sweep across the face of the turning world.
 */
public final class EclipseMove implements Move {
    private static final int I = DEATH;
    /** The sun's place in the sky (direction from the viewer). */
    static final Vec3d SUN_DIR = new Vec3d(0.35, 0.6, -0.72).normalize();
    private static final Vec3d SIDE = SUN_DIR.crossProduct(new Vec3d(0, 1, 0)).normalize();
    private static final Vec3d UP = SIDE.crossProduct(SUN_DIR).normalize();
    /** The orbital interlude (a private pocket scene). */
    public static final int ECL_SPACE = 150, ECL_BACK = 250;
    static final double POCKET = 300;
    private static final Vec3d EY = new Vec3d(0, 1, 0);
    private static final Vec3d U_SUN = Universe.SUN_DIR;
    private static final Vec3d U_TERM = U_SUN.crossProduct(EY).normalize();

    private static Vec3d umbraAt(double k) {
        return U_SUN.add(U_TERM.multiply(0.3 - 0.6 * k)).add(EY.multiply(0.1 - 0.2 * k)).normalize();
    }

    /** {universe camera, look direction} for the interlude. */
    private static Vec3d[] spacePath(double a) {
        double k = Rite.clamp01((a - ECL_SPACE) / (double) (ECL_BACK - ECL_SPACE));
        double e = 1 - Math.pow(1 - k, 2.4);
        double D = 1.045 * Math.pow(4.8 / 1.045, e);
        Vec3d d0 = U_SUN.add(U_TERM.multiply(0.62)).add(EY.multiply(0.2)).normalize();
        Vec3d d1 = U_SUN.multiply(0.55).add(U_TERM.multiply(0.8)).add(EY.multiply(0.32)).normalize();
        Vec3d dir = Kit.lerp(d0, d1, Kit.s(k)).normalize();
        Vec3d cam = dir.multiply(D);
        Vec3d tgt = Kit.lerp(umbraAt(k), Vec3d.ZERO, Kit.s(k * 1.4));
        return new Vec3d[]{cam, tgt.subtract(cam).normalize()};
    }

    public static boolean pocket(double a) {
        return a >= ECL_SPACE && a < ECL_BACK;
    }

    private void space(MoveCtx c, double a) {
        double k = Rite.clamp01((a - ECL_SPACE) / (double) (ECL_BACK - ECL_SPACE));
        Vec3d[] p = spacePath(a);
        double time = a * 0.6;
        Universe.View v = new Universe.View(p[0], Vec3d.ZERO, time, c.cam);
        Cosmos.blackout(c.cosmos, 94);
        Cosmos.skybox(c.cosmos, Cosmos.MILKY, 90, 0.8f, new org.joml.Quaternionf().rotateY(0.6f));
        Cosmos.stars(c.cosmos, 86, 1);
        Vec3d sp = c.cam.add(U_SUN.multiply(84));
        Universe.sunAt(c.cosmos, (float) sp.x, (float) sp.y, (float) sp.z, 2.4f, c.time);
        Vec3d mU = U_SUN.multiply(Universe.MOON_DIST).add(U_TERM.multiply(-0.3 + 0.6 * k));
        double[] qm = v.place(mU);
        float rm = (float) (Universe.MOON_R * v.scale * qm[3]);
        Universe.body(c.cosmos, Cosmos.MOON, (float) qm[0], (float) qm[1], (float) qm[2], rm, Cosmos.MOON_Q, U_SUN, new float[]{1, 1, 1}, 0.85f, null, 0);
        double[] qe = v.place(Vec3d.ZERO);
        Universe.umbra = umbraAt(k);
        Universe.umbraR = 0.075f;
        Universe.earth(c.cosmos, (float) qe[0], (float) qe[1], (float) qe[2], (float) (v.scale * qe[3]), Universe.earthRot(time), U_SUN, time);
        Universe.umbra = null;
    }

    /** Angular offset of the moon from the sun (0 at totality). */
    private static double offset(double a) {
        if (a < ECL_DARK) return 0.5;
        if (a < ECL_TOTAL) return 0.5 * (1 - Rite.smooth((a - ECL_DARK) / (ECL_TOTAL - ECL_DARK)));
        if (a < ECL_LIGHT) return 0;
        return -0.5 * Rite.smooth((a - ECL_LIGHT) / 90.0);
    }

    /** 0..1 how dark the world has grown. */
    private static float dark(double a) {
        double off = Math.abs(offset(a));
        float cover = (float) Math.max(0, 1 - off / 0.5);
        return Rite.smooth((a - 20) / 40.0) * (0.25f + 0.7f * cover * cover) * (1 - Rite.smooth((a - (LENGTH[I] - 50)) / 40.0));
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        if (c.mine && pocket(a)) {
            space(c, a);
            return;
        }
        float dk = dark(a);
        Kit.skyTint(c, 88, 0.06f, 0.0f, 0.012f, dk * 0.86f);
        // sun and moon hang at infinity: drawn around the viewer's camera
        double off = offset(a);
        Vec3d sun = c.cam.add(SUN_DIR.multiply(80));
        Vec3d mdir = SUN_DIR.add(SIDE.multiply(off)).add(UP.multiply(off * 0.18)).normalize();
        Vec3d moon = c.cam.add(mdir.multiply(76));
        float cover = (float) Math.max(0, 1 - Math.abs(off) / 0.075);
        float glare = 1 - cover * 0.97f;
        Cosmos.sun(c.cosmos, sun.x, sun.y, sun.z, 16 * glare + 2, glare);
        Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GLARE)), c.ms, sun.x, sun.y, sun.z, 4.2f, 0, 1, 1, 1);
        float corona = Rite.smooth((cover - 0.6) / 0.4) * (1 - Rite.smooth((a - (ECL_LIGHT + 30)) / 20.0));
        if (corona > 0.01f) {
            float flick = 0.92f + 0.08f * (float) Math.sin(c.time * 0.7);
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.CORONA)), c.ms, sun.x, sun.y, sun.z, 19, (float) (c.time * 0.002),
                    corona * flick, corona * 0.93f * flick, corona * 0.95f * flick);
            // crimson tears of the black sun
            float[] cr = Pillars.COLOR[I];
            float tears = Kit.window(a, ECL_TOTAL + 10, 20, ECL_LIGHT, 30);
            for (int k = 0; k < 7 && tears > 0.01f; k++) {
                double ang = k / 7.0 * Math.PI * 2 + c.time * 0.003;
                Vec3d dirOut = SIDE.multiply(Math.cos(ang)).add(UP.multiply(Math.sin(ang)));
                Vec3d p0 = sun.add(dirOut.multiply(5.2)), p1 = sun.add(dirOut.multiply(5.2 + 8 * tears)).add(0, -10 * tears, 0);
                Kit.beam(c, p0, p1, 0.6, cr[0] * tears, cr[1] * tears, cr[2] * tears);
            }
        }
        // diamond ring: the last bead of sunlight at the moon's edge just before totality
        float ring = Kit.window(a, ECL_TOTAL - 26, 8, ECL_TOTAL - 8, 8);
        if (ring > 0.01f) {
            Vec3d bead = sun.add(SIDE.multiply(-4.6)).add(UP.multiply(1.2));
            Cosmos.sun(c.cosmos, bead.x, bead.y, bead.z, 14 * ring, ring * 1.4f);
        }
        // the moon, seen from its night side: a black disc with the faintest earthshine
        Vector3f mc = new Vector3f((float) moon.x, (float) moon.y, (float) moon.z);
        Cosmos.planet(c.cosmos, Cosmos.Body.MOON, mc, 4.6f, Cosmos.MOON_Q, 1f, SUN_DIR);

        // judgement
        float[] col = Pillars.COLOR[I];
        List<LivingEntity> ts = AbilityView.targets(c.core);
        for (int k = 0; k < ts.size(); k++) {
            LivingEntity t = ts.get(k);
            Vec3d p = c.rel(t.getLerpedPos(c.td));
            int hit = eclipseStrike(k);
            float mark = Kit.window(a, ECL_MARK + k, 10, hit, 4);
            if (mark > 0.01f) {
                float pulse = 0.8f + 0.2f * (float) Math.sin(a * 0.6 + k);
                DoaFont.world(c.ms, c.vcp, "死", p.x, p.y + t.getHeight() + 1.4, p.z, 1.2f, 0, col[0] * mark * pulse * 1.4f, col[1] * mark * pulse,
                        col[2] * mark * pulse);
                Kit.seal(c, I, p, 1.6 * mark, mark, a * 0.05 + k);
            }
            double h = a - hit;
            if (h > -8 && h < 26) {
                float kb = (float) (h < 0 ? Rite.smooth((h + 8) / 8.0) : Math.exp(-h / 6.0)) * 1.6f;
                Vec3d top = p.add(SUN_DIR.multiply(160));
                Kit.beam(c, top, p, 1.1 + (h > 0 ? 1.2 * Math.exp(-h / 4) : 0), col[0] * kb, col[1] * kb * 0.6f, col[2] * kb * 0.6f);
                Kit.beam(c, top, p, 0.3, kb, kb * 0.85f, kb * 0.85f);
                if (h > 0) Kit.shock(c, p, 0.8 + h * 0.5, (float) Math.exp(-h / 7), col);
            }
        }
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] col = Pillars.COLOR[I];
        List<LivingEntity> ts = AbilityView.targets(core);
        for (int k = 0; k < ts.size(); k++) {
            LivingEntity t = ts.get(k);
            if (a == eclipseStrike(k)) {
                Kit.burst(t.getPos().add(0, t.getHeight() / 2, 0), col, 40, rnd, 0.35, true, true);
                for (int n = 0; n < 30; n++)
                    Kit.ink(t.getX() + rnd.nextGaussian() * 0.4, t.getY() + rnd.nextDouble() * t.getHeight(), t.getZ() + rnd.nextGaussian() * 0.4,
                            rnd.nextGaussian() * 0.05, 0.08 + rnd.nextDouble() * 0.1, rnd.nextGaussian() * 0.05, 0.6f + rnd.nextFloat() * 0.6f, 50);
                if (k == 0) Cutscene.impact(core, I, 2.2f);
                else Cutscene.addShake(core, 0.8f, 20);
            }
            if (a > ECL_MARK && a < eclipseStrike(k) && rnd.nextInt(2) == 0)
                Kit.mote(t.getX() + rnd.nextGaussian() * 0.3, t.getY() + rnd.nextDouble() * t.getHeight(), t.getZ() + rnd.nextGaussian() * 0.3,
                        0, 0.05, 0, col, 0.12f, 24);
        }
        if (a == ECL_TOTAL) Cutscene.addShake(core, 1.2f, 40);
        if (a == ECL_SPACE) Kit.privateSound(core, com.creedpdf.doa.registry.DoaSounds.FATE_SPACE, 0.7f);
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        double R = Rite.ORB_RADIUS;
        float base = Kit.baseYaw(playerYaw);
        if (a < 40) return Kit.relicShot(core, I, a);
        if (a >= ECL_SPACE - 30 && a < ECL_SPACE) return Kit.ascent(c, (a - ECL_SPACE + 30) / 30.0);
        if (pocket(a)) {
            Vec3d s = c.add(0, POCKET, 0);
            return new Vec3d[]{s, s.add(spacePath(a)[1].multiply(10))};
        }
        if (a < 150) {
            Vec3d pos = Kit.orbit(c, base, R + 16, 2.6);
            return new Vec3d[]{pos, Kit.lerp(c.add(0, 8, 0), pos.add(SUN_DIR.multiply(50)), Kit.s((a - 50) / 70.0))};
        }
        if (a < ECL_MARK) {
            Vec3d pos = Kit.orbit(c, base + 0.6, R + 8, 3 + (a - 150) * 0.02);
            return new Vec3d[]{pos, pos.add(SUN_DIR.multiply(60))};
        }
        if (a < ECL_LIGHT) {
            Vec3d pos = Kit.orbit(c, base + 1.2 + (a - ECL_MARK) * 0.003, 42, 32);
            return new Vec3d[]{pos, c.add(0, 2, 0)};
        }
        Vec3d pos = Kit.orbit(c, base + 2.2, R + 14, 3);
        return new Vec3d[]{pos, Kit.lerp(pos.add(SUN_DIR.multiply(50)), c.add(0, 6, 0), Kit.s((a - ECL_LIGHT) / 60.0))};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        Kit.fade(ctx, w, h, Kit.window(a, ECL_SPACE - 8, 8, ECL_SPACE, 10), 0xFFF4F0);
        Kit.fade(ctx, w, h, Kit.window(a, ECL_BACK - 10, 10, ECL_BACK, 12), 0x000000);
        Kit.caption(ctx, "影 · THE SHADOW OF THE MOON", "it falls across the world", Pillars.rgb(I), Kit.window(a, ECL_SPACE + 20, 14, ECL_BACK - 24, 12), w, h,
                h * 0.16f);
        float tot = Kit.window(a, ECL_TOTAL - 6, 14, ECL_TOTAL + 50, 14);
        Kit.caption(ctx, "日蝕 · THE BLACK SUN", "and the black sun weeps", Pillars.rgb(I), tot, w, h, h * 0.18f);
        float dk = dark(a);
        if (dk > 0.4f) {
            int band = (int) (Math.min(w, h) * 0.18f);
            for (int s = 0; s < band; s += 2) {
                int al = (int) ((dk - 0.4f) * 160 * (1 - s / (float) band)) << 24;
                ctx.fill(0, s, w, s + 2, al | 0x300008);
                ctx.fill(0, h - s - 2, w, h - s, al | 0x300008);
            }
        }
    }
}
