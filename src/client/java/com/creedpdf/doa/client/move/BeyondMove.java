package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.render.Universe;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import static com.creedpdf.doa.Abilities.*;

/**
 * 運 BEYOND. One unbroken camera move through every scale of the universe: from just above the clouds, with the sun
 * rising over the curve of the world, the camera pulls back until the Earth and Moon are two lit spheres, then the
 * planets on their orbits around a burning sun, then through the local stars and out of the Milky Way - a 3D spiral of
 * thirty thousand stars, dust lanes and nebulae - and on until it is one light among thousands strung along the
 * cosmic web. There the seven stars of the pillars align, and the whole zoom runs backwards in seconds, every scale
 * flashing past, down into the world to strike with the light of the universe.
 */
public final class BeyondMove implements Move {
    private static final int I = FATE;
    static final double POCKET = 300;
    private static final Vec3d Y = new Vec3d(0, 1, 0);
    private static final Vec3d SUN_D = Universe.SUN_DIR;
    private static final Vec3d TERM = SUN_D.crossProduct(Y).normalize();
    /** Universe time is frozen for the journey (the moon and planets are where they look best). */
    private static final double T0 = 0;
    private static final Vec3d MOON_DIR = Universe.moonPos(T0).normalize();

    /** Keyframes of the journey: u, focus, log10 distance, direction from focus to camera. */
    private static final double[] KU = {0, 0.22, 0.44, 0.7, 1};
    private static final double[] KD = {Math.log10(1.035), Math.log10(210), Math.log10(2.4e6), Math.log10(2.3 * Universe.GAL_R), Math.log10(9e17)};
    private static final Vec3d[] KF = {Vec3d.ZERO, Vec3d.ZERO, Universe.SUN, Universe.GALAXY, Universe.GALAXY};
    private static final Vec3d[] KDIR = {
            TERM.multiply(-1).add(Y.multiply(0.25)).normalize(),
            SUN_D.multiply(0.55).add(MOON_DIR.multiply(-0.45)).add(Y.multiply(0.35)).add(TERM.multiply(-0.6)).normalize(),
            Y.multiply(0.85).add(SUN_D.multiply(-0.35)).add(TERM.multiply(0.3)).normalize(),
            Universe.GAL_N.multiply(0.72).add(GE2().multiply(0.66)).normalize(),
            Universe.GAL_N.multiply(0.35).add(GE2().multiply(0.4)).add(GE1().multiply(0.85)).normalize()};
    private static final int STREAKS = 320;
    private static final float[] ST = new float[STREAKS * 4];

    static {
        java.util.Random r = new java.util.Random(91);
        for (int i = 0; i < STREAKS; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = 0.08 + r.nextDouble() * 0.9;
            ST[i * 4] = (float) (Math.cos(ang) * rad);
            ST[i * 4 + 1] = (float) (Math.sin(ang) * rad);
            ST[i * 4 + 2] = r.nextFloat();
            ST[i * 4 + 3] = r.nextFloat();
        }
    }

    private static Vec3d GE1() {
        return Universe.GAL_N.crossProduct(new Vec3d(1, 0, 0)).normalize();
    }

    private static Vec3d GE2() {
        return Universe.GAL_N.crossProduct(GE1()).normalize();
    }

    /** Journey progress 0 (above the clouds) .. 1 (the cosmic web), going out and then rushing back. */
    static double journey(double a) {
        if (a < BYD_UP) return 0;
        if (a < BYD_SYSTEM) return seg(a, BYD_UP, BYD_SYSTEM, 0, 0.22);
        if (a < BYD_GALAXY) return seg(a, BYD_SYSTEM, BYD_GALAXY, 0.22, 0.44);
        if (a < BYD_WEB) return seg(a, BYD_GALAXY, BYD_WEB, 0.44, 0.7);
        if (a < BYD_ALIGN) return seg(a, BYD_WEB, BYD_ALIGN, 0.7, 1);
        if (a < BYD_RUSH) return 1;
        double t = MathHelper.clamp((a - BYD_RUSH) / (double) (BYD_DOWN - BYD_RUSH), 0, 1);
        return 1 - (1 - Math.pow(1 - t, 2.2)) * 1.0;
    }

    private static double seg(double a, double a0, double a1, double u0, double u1) {
        double k = (a - a0) / (a1 - a0);
        // ease in at the very start of the journey, otherwise near-linear so the zoom never stops
        double e = a0 == BYD_UP ? Rite.smooth(k) * 0.6 + k * 0.4 : k * 0.85 + Rite.smooth(k) * 0.15;
        return u0 + (u1 - u0) * e;
    }

    /** {universe camera, universe focus, look direction}. */
    static Vec3d[] path(double u, double a) {
        int i = 0;
        while (i < KU.length - 2 && u > KU[i + 1]) i++;
        double k = MathHelper.clamp((u - KU[i]) / (KU[i + 1] - KU[i]), 0, 1);
        double ld = KD[i] + (KD[i + 1] - KD[i]) * k;
        double D = Math.pow(10, ld);
        double Da = Math.pow(10, KD[i]), Db = Math.pow(10, KD[i + 1]);
        double w = MathHelper.clamp((D - Da) / (Db - Da), 0, 1);
        w = Math.pow(w, 0.8);
        Vec3d focus = Kit.lerp(KF[i], KF[i + 1], w);
        double dk = Rite.smooth(k);
        Vec3d dir = Kit.lerp(KDIR[i], KDIR[i + 1], dk).normalize();
        // a slow drift so even the still moments breathe
        dir = dir.add(TERM.multiply(Math.sin(a * 0.011) * 0.03)).add(Y.multiply(Math.cos(a * 0.008) * 0.02)).normalize();
        Vec3d cam = focus.add(dir.multiply(D));
        // near the world, look toward the horizon (the sun rising over the limb) rather than straight down
        double hz = 1 - Rite.smooth(ld / 1.1);
        Vec3d side = SUN_D.subtract(dir.multiply(SUN_D.dotProduct(dir))).normalize();
        Vec3d look = dir.multiply(-1).add(side.multiply(1.25 * hz)).normalize();
        return new Vec3d[]{cam, focus, look};
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float[] vio = Pillars.COLOR[I];
        if (c.mine && a >= BYD_UP && a < BYD_DOWN) {
            heavens(c, a, vio);
            return;
        }
        // the caller's spirit rising as a thread of light
        float rise = Kit.window(a, 30, 10, BYD_UP - 10, 10);
        if (rise > 0.01f) {
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 1.0, 0, 260, vio[0] * rise, vio[1] * rise, vio[2] * rise);
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 0.3, 0, 260, rise, rise, rise);
        }
        // the light of the universe comes down
        Vec3d p = c.rel(AbilityView.point(c.core));
        double st = a - BYD_STRIKE;
        if (st > -12) {
            float k = (float) (st < 0 ? Rite.smooth((st + 12) / 12.0) * 0.6 : Math.exp(-st / 22.0) * 2.0 + 0.2);
            k *= 1 - Rite.smooth((a - (LENGTH[I] - 24)) / 22.0);
            double w = st < 0 ? 1 + (st + 12) * 0.1 : 9 * Math.exp(-st / 14.0) + 1.6;
            float[] gold = Pillars.COLOR[1];
            Kit.beam(c, p.add(0, 300, 0), p, w, vio[0] * k, vio[1] * k, vio[2] * k);
            Kit.beam(c, p.add(0, 300, 0), p, w * 0.3, k, k, k);
            Kit.beam(c, p.add(0, 160, 0), p, w * 1.7, gold[0] * k * 0.3f, gold[1] * k * 0.3f, gold[2] * k * 0.3f);
            Kit.seal(c, I, p, 14 * Math.min(1, k + 0.3), k, c.time * 0.01);
            if (st > 0 && st < 40) Kit.shock(c, p, 2 + st * 1.1, (float) Math.exp(-st / 12), vio);
        }
    }

    private void heavens(MoveCtx c, double a, float[] vio) {
        double u = journey(a);
        Vec3d[] p = path(u, a);
        Universe.View v = new Universe.View(p[0], p[1], T0 + a * 0.5, c.cam);
        Universe.render(c, v);
        boolean out = a < BYD_ALIGN;
        // markers, as on a star chart
        float lab = out ? 1 : 0;
        double ld = Math.log10(p[0].distanceTo(p[1]));
        float earthL = Kit.window(ld, 1.3, 0.4, 3.6, 0.5) * lab;
        if (earthL > 0.01f) {
            double[] q = v.place(Vec3d.ZERO);
            float sz = (float) Math.max(0.3, 0.03 * q[4] * q[3] / 32 * 6);
            DoaFont.world(c.ms, c.vcp, "地  EARTH", q[0], q[1] + sz * 2.2, q[2], sz, 0.25f, vio[0] * earthL, vio[1] * earthL, vio[2] * earthL);
            double[] m = v.place(Universe.moonPos(v.time));
            DoaFont.world(c.ms, c.vcp, "月  MOON", m[0], m[1] + sz * 1.3, m[2], sz * 0.7f, 0.25f, 0.8f * earthL, 0.8f * earthL, 0.85f * earthL);
        }
        float sysL = Kit.window(ld, 5.0, 0.4, 7.6, 0.4) * lab;
        if (sysL > 0.01f) {
            Universe.orbits(c, v, sysL, vio);
            double[] q = v.place(Universe.SUN);
            DoaFont.world(c.ms, c.vcp, "日  THE SUN", q[0], q[1] + 3.2, q[2], 1.1f, 0.25f, sysL, 0.85f * sysL, 0.6f * sysL);
        }
        float hereL = Kit.window(ld, 11.5, 0.6, 15.6, 0.6) * lab;
        if (hereL > 0.01f) {
            double[] q = v.place(Universe.SUN);
            float pulse = 0.7f + 0.3f * (float) Math.sin(a * 0.3);
            Kit.glow(c, new Vec3d(q[0], q[1], q[2]), 0.9f * pulse, vio[0] * 1.6f * hereL, vio[1] * 1.6f * hereL, vio[2] * 1.6f * hereL);
            DoaFont.world(c.ms, c.vcp, "YOU ARE HERE", q[0], q[1] + 2.0, q[2], 1.0f, 0.25f, vio[0] * 1.3f * hereL, vio[1] * 1.3f * hereL, vio[2] * 1.3f * hereL);
        }
        // the seven stars of the pillars align at the edge of everything
        float appear = Kit.window(a, BYD_ALIGN - 30, 20, BYD_RUSH - 6, 8);
        if (appear > 0.01f) {
            Vec3d look = p[2];
            Vec3d side = look.crossProduct(Y).normalize();
            Vec3d center = c.cam.add(look.multiply(46));
            double al = Kit.s((a - BYD_ALIGN + 4) / 40.0);
            Universe.fateStars(c, center, side, al, 7 * appear, c.time);
        }
        // the rush: the zoom in reverse, streaking
        if (a >= BYD_RUSH) {
            double t = (a - BYD_RUSH) / (BYD_DOWN - BYD_RUSH);
            float sk = (float) (Math.sin(Math.PI * Math.min(1, t * 1.15)));
            VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Kit.STREAK));
            Vec3d fwd = p[2];
            Vec3d r1 = fwd.crossProduct(Y).normalize(), u1 = r1.crossProduct(fwd).normalize();
            for (int i = 0; i < STREAKS; i++) {
                double ph = (ST[i * 4 + 2] + t * (3 + ST[i * 4 + 3] * 4)) % 1.0;
                double d0 = 80 * (1 - ph), len = 6 + 26 * ph;
                Vec3d off = r1.multiply(ST[i * 4] * 30).add(u1.multiply(ST[i * 4 + 1] * 30));
                Vec3d p0 = c.cam.add(fwd.multiply(d0)).add(off.multiply(d0 / 80));
                double d1 = Math.max(1.5, d0 - len);
                Vec3d p1 = c.cam.add(fwd.multiply(d1)).add(off.multiply(d1 / 80));
                float b = (float) (0.3 + ph * 0.9) * (i % 3 == 0 ? 1 : 0.55f) * sk;
                float[] col = i % 4 == 0 ? vio : i % 7 == 0 ? Pillars.COLOR[1] : new float[]{0.85f, 0.9f, 1};
                Bill.ribbon(vc, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 0.06f + (float) ph * 0.1f, col[0] * b, col[1] * b,
                        col[2] * b, 1, 0, 1);
            }
        }
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] vio = Pillars.COLOR[I];
        if (a == 30) Kit.privateSound(core, DoaSounds.FATE_ASCEND, 1f);
        if (a == BYD_UP) Kit.privateSound(core, DoaSounds.FATE_SPACE, 1f);
        if (a == BYD_GALAXY) Kit.privateSound(core, DoaSounds.FATE_SPACE, 0.8f);
        if (a == BYD_WEB) Kit.privateSound(core, DoaSounds.FATE_SPACE, 0.6f);
        if (a == BYD_ALIGN + 40) Kit.privateSound(core, DoaSounds.REVEAL, 0.7f);
        if (a == BYD_RUSH) Kit.privateSound(core, DoaSounds.DESCEND, 0.6f);
        if (a == BYD_ALIGN + 56) Cutscene.impact(core, I, 0.6f);
        if (a == BYD_STRIKE) {
            Vec3d p = AbilityView.point(core);
            Kit.burst(p.add(0, 1, 0), vio, 160, rnd, 0.8, true, true);
            Kit.burst(p.add(0, 3, 0), Pillars.COLOR[1], 80, rnd, 0.6, false, false);
            Cutscene.impact(core, I, 3.0f);
        }
        if (a > BYD_STRIKE && a < BYD_STRIKE + 50) {
            Vec3d p = AbilityView.point(core);
            for (int n = 0; n < 6; n++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 12;
                com.creedpdf.doa.client.fx.Fx.spawn(com.creedpdf.doa.client.fx.Fx.Kind.PETAL, p.x + Math.cos(ang) * rr, p.y + 8 + rnd.nextDouble() * 12,
                        p.z + Math.sin(ang) * rr, 0, -0.05, 0, Kit.bright(n % 2 == 0 ? vio : Pillars.COLOR[1], 0.2f), 1f, 0.17f, 90, 0.004f, 0.96f, true,
                        (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1f);
            }
        }
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        if (a < 30) return Kit.relicShot(core, I, a);
        if (a < BYD_UP) return Kit.ascent(c, (a - 30) / (BYD_UP - 30));
        Vec3d s = c.add(0, POCKET, 0);
        if (a < BYD_DOWN) {
            Vec3d look = path(journey(a), a)[2];
            return new Vec3d[]{s, s.add(look.multiply(10))};
        }
        Vec3d p = AbilityView.point(core);
        Vec3d d = p.subtract(c).multiply(1, 0, 1);
        double th = Math.atan2(d.x, -d.z) + 0.7;
        return new Vec3d[]{Kit.orbit(p, th + (a - BYD_DOWN) * 0.004, 36, 24), p.add(0, 6, 0)};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        int col = Pillars.rgb(I);
        Kit.fade(ctx, w, h, Kit.window(a, BYD_UP - 8, 8, BYD_UP, 10), 0xFFF8EC);
        Kit.fade(ctx, w, h, Kit.window(a, BYD_DOWN - 10, 10, BYD_DOWN, 10), 0xFFF8EC);
        float y = h * 0.16f;
        Kit.caption(ctx, "地 · THE WORLD", "a small blue light", col, Kit.window(a, BYD_ORBIT + 70, 14, BYD_SYSTEM - 10, 12), w, h, y);
        Kit.caption(ctx, "日 · THE SUN AND ITS CHILDREN", "eight worlds turning", col, Kit.window(a, BYD_SYSTEM + 14, 14, BYD_GALAXY - 20, 12), w, h, y);
        Kit.caption(ctx, "星 · THE MILKY WAY", "four hundred billion stars", col, Kit.window(a, BYD_GALAXY + 14, 14, BYD_WEB - 20, 12), w, h, y);
        Kit.caption(ctx, "界 · THE COSMIC WEB", "and every light a galaxy", col, Kit.window(a, BYD_WEB + 14, 14, BYD_ALIGN - 10, 12), w, h, y);
        Kit.caption(ctx, "運 · THE SEVEN ALIGN", null, col, Kit.window(a, BYD_ALIGN + 30, 10, BYD_RUSH - 6, 6), w, h, y);
    }
}
