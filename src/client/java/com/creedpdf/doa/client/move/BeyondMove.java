package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.creedpdf.doa.Abilities.*;

/**
 * 運 BEYOND. The caller rises into orbit and keeps going: the world falls away with its moon; the solar system turns
 * beneath; the whole Milky Way is seen from outside, the caller's place marked on one arm; it shrinks to one light
 * among thousands strung along the cosmic web. There, at the edge of everything, the seven stars of the pillars
 * align - and the caller rushes all the way back down to strike with the light of the universe.
 */
public final class BeyondMove implements Move {
    private static final int I = FATE;
    static final double POCKET = 300;
    private static final Vec3d EARTH_DIR = new Vec3d(0, -0.8, -0.6).normalize();
    private static final Vec3d SYS = new Vec3d(0, -8, -56);
    private static final Vec3d GAL = new Vec3d(0, -10, -60);
    private static final double[] ORB = {5, 7.5, 10, 13, 19, 25, 30, 34};
    private static final float[] PR = {0.3f, 0.5f, 0.55f, 0.4f, 1.7f, 1.4f, 0.9f, 0.9f};
    private static final Cosmos.Body[] PB = {Cosmos.Body.MOON, Cosmos.Body.MOON, Cosmos.Body.EARTH, Cosmos.Body.RED, Cosmos.Body.GIANT,
            Cosmos.Body.GIANT, Cosmos.Body.MOON, Cosmos.Body.MOON};
    private static final int GALS = 170;
    private static final float[] G = new float[GALS * 8];
    private static final int STREAKS = 240;
    private static final float[] ST = new float[STREAKS * 4];
    private static final Vec3d FATE_DIR = new Vec3d(0.2, 0.35, -0.9).normalize();
    private static final Vec3d LINE = new Vec3d(0.97, 0.12, 0.2).normalize();
    private static final float[][] SCATTER = new float[7][3];

    static {
        java.util.Random r = new java.util.Random(91);
        Vec3d[][] fil = new Vec3d[12][2];
        for (int f = 0; f < 12; f++) {
            fil[f][0] = new Vec3d(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize().multiply(30 + r.nextDouble() * 50);
            fil[f][1] = new Vec3d(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize().multiply(30 + r.nextDouble() * 50);
        }
        for (int i = 0; i < GALS; i++) {
            Vec3d[] f = fil[i % 12];
            Vec3d p = Kit.lerp(f[0], f[1], r.nextDouble()).add(r.nextGaussian() * 4, r.nextGaussian() * 4, r.nextGaussian() * 4);
            if (p.length() < 12) p = p.normalize().multiply(14);
            G[i * 8] = (float) p.x;
            G[i * 8 + 1] = (float) p.y;
            G[i * 8 + 2] = (float) p.z;
            G[i * 8 + 3] = 0.6f + r.nextFloat() * 2.6f;
            Vector3f n = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
            G[i * 8 + 4] = n.x;
            G[i * 8 + 5] = n.y;
            G[i * 8 + 6] = n.z;
            G[i * 8 + 7] = 0.4f + r.nextFloat() * 0.6f;
        }
        for (int i = 0; i < STREAKS; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = 0.05 + r.nextDouble() * 0.9;
            ST[i * 4] = (float) (Math.cos(ang) * rad);
            ST[i * 4 + 1] = (float) (Math.sin(ang) * rad);
            ST[i * 4 + 2] = r.nextFloat();
            ST[i * 4 + 3] = r.nextFloat();
        }
        for (int i = 0; i < 7; i++) for (int k = 0; k < 3; k++) SCATTER[i][k] = (float) ((r.nextDouble() - 0.5) * 40);
    }

    private static Vector3f v(Vec3d p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    private static double earthR(double a) {
        double k = Rite.smooth((a - BYD_ORBIT) / (BYD_SYSTEM - BYD_ORBIT));
        return 40 * Math.pow(0.05, k);
    }

    private static Vec3d earthC(double a) {
        double k = Rite.smooth((a - BYD_ORBIT) / (BYD_SYSTEM - BYD_ORBIT));
        double re = earthR(a);
        return EARTH_DIR.multiply(re + 12 + 40 * k);
    }

    private static Vec3d fateStar(int i, double a) {
        float k = Rite.smooth((a - BYD_ALIGN - 10) / 40.0);
        Vec3d base = FATE_DIR.multiply(62);
        Vec3d sc = base.add(SCATTER[i][0], SCATTER[i][1] * 0.6, SCATTER[i][2] * 0.3);
        Vec3d al = base.add(LINE.multiply((i - 3) * 7.0));
        return Kit.lerp(sc, al, k);
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
        Vec3d s = new Vec3d(0, POCKET, 0);
        Cosmos.blackout(c.cosmos, 92);
        float inGal = Kit.s((a - BYD_GALAXY) / 30.0);
        Cosmos.skybox(c.cosmos, Cosmos.MILKY, 88, (1 - inGal) * 0.9f, new Quaternionf().rotateY(0.6f));
        Cosmos.skybox(c.cosmos, Cosmos.DEEP, 87, Kit.s((a - BYD_WEB + 20) / 40.0) * 0.9f, new Quaternionf());
        Cosmos.stars(c.cosmos, 80, 1 - Kit.s((a - BYD_WEB) / 40.0) * 0.6f);

        if (a < BYD_SYSTEM + 6) {
            // ---- orbit: the world and its moon fall away
            Vec3d sunP = c.cam.add(new Vec3d(Cosmos.SUN.x, Cosmos.SUN.y, Cosmos.SUN.z).multiply(80));
            Cosmos.sun(c.cosmos, sunP.x, sunP.y, sunP.z, 24, 1);
            double re = earthR(a);
            Vec3d ec = s.add(earthC(a));
            Cosmos.planet(c.cosmos, Cosmos.Body.EARTH, v(ec), (float) re, Cosmos.EARTH_Q, 1);
            double ma = a * 0.004 + 1.2;
            Vec3d mc = ec.add(Math.cos(ma) * re * 7.5, re * 1.2, Math.sin(ma) * re * 7.5);
            Cosmos.planet(c.cosmos, Cosmos.Body.MOON, v(mc), (float) (re * 0.27), Cosmos.MOON_Q, 1);
        } else if (a < BYD_GALAXY + 6) {
            // ---- the solar system
            double z = 1 - 0.5 * Kit.s((a - BYD_SYSTEM) / (BYD_GALAXY - BYD_SYSTEM));
            Vec3d sun = s.add(SYS);
            Cosmos.sun(c.cosmos, sun.x, sun.y, sun.z, (float) (14 * z), 1.2f);
            Kit.glow(c, sun, (float) (5 * z), 1f, 0.9f, 0.7f);
            float tilt = 0.42f;
            float[] e1 = {1, 0, 0}, e2 = {0, MathHelper.sin(tilt), MathHelper.cos(tilt)};
            VertexConsumer ring = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.WHITE));
            for (int i = 0; i < ORB.length; i++) {
                double R = ORB[i] * z;
                for (int k = 0; k < 64; k++) {
                    double t0 = k / 64.0 * Math.PI * 2, t1 = (k + 1) / 64.0 * Math.PI * 2;
                    Vec3d p0 = sun.add(e1[0] * Math.cos(t0) * R + e2[0] * Math.sin(t0) * R, e1[1] * Math.cos(t0) * R + e2[1] * Math.sin(t0) * R,
                            e1[2] * Math.cos(t0) * R + e2[2] * Math.sin(t0) * R);
                    Vec3d p1 = sun.add(e1[0] * Math.cos(t1) * R + e2[0] * Math.sin(t1) * R, e1[1] * Math.cos(t1) * R + e2[1] * Math.sin(t1) * R,
                            e1[2] * Math.cos(t1) * R + e2[2] * Math.sin(t1) * R);
                    float b = i == 2 ? 0.3f : 0.1f;
                    Bill.ribbon(ring, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 0.03f, b * (i == 2 ? vio[0] * 2 : 1),
                            b * (i == 2 ? vio[1] * 2 : 1), b * (i == 2 ? vio[2] * 2 : 1), 1, 0, 1);
                }
            }
            for (int i = 0; i < ORB.length; i++) {
                double R = ORB[i] * z;
                double ang = a * 0.02 / Math.pow(i + 1, 0.8) + i * 1.7;
                Vec3d p = sun.add(e1[0] * Math.cos(ang) * R + e2[0] * Math.sin(ang) * R, e1[1] * Math.cos(ang) * R + e2[1] * Math.sin(ang) * R,
                        e1[2] * Math.cos(ang) * R + e2[2] * Math.sin(ang) * R);
                Cosmos.planet(c.cosmos, PB[i], v(p), (float) (PR[i] * z), Cosmos.baseRot(PB[i]), 1);
                if (PB[i] == Cosmos.Body.GIANT && i == 5)
                    Cosmos.rings(c.cosmos, v(p), (float) (PR[i] * z), Cosmos.GIANT_Q, 1);
                if (i == 2) {
                    Kit.glow(c, p, (float) (1.6 * z), vio[0], vio[1], vio[2]);
                    DoaFont.world(c.ms, c.vcp, "EARTH", p.x, p.y + 1.6 * z, p.z, (float) (0.6 * z), 0.2f, vio[0], vio[1], vio[2]);
                }
            }
        } else if (a < BYD_WEB + 6) {
            // ---- the galaxy, seen from outside
            double z = 1 - 0.75 * Kit.s((a - BYD_GALAXY) / (BYD_WEB - BYD_GALAXY));
            Vec3d g = s.add(GAL);
            double rot = a * 0.0015;
            float tilt = 1.0f;
            float[] e1 = {(float) Math.cos(rot), 0, (float) Math.sin(rot)};
            float[] e2 = {(float) (-Math.sin(rot) * Math.cos(tilt)), MathHelper.sin(tilt), (float) (Math.cos(rot) * Math.cos(tilt))};
            float rg = (float) (58 * z);
            Cosmos.galaxy(c.cosmos, g.x, g.y, g.z, e1, e2, rg, 1.2f);
            Vec3d here = g.add(e1[0] * 0.52 * rg + e2[0] * 0.18 * rg, e1[1] * 0.52 * rg + e2[1] * 0.18 * rg, e1[2] * 0.52 * rg + e2[2] * 0.18 * rg);
            float pulse = 0.7f + 0.3f * (float) Math.sin(a * 0.3);
            Kit.glow(c, here, 1.4f * pulse, vio[0] * 1.5f, vio[1] * 1.5f, vio[2] * 1.5f);
            DoaFont.world(c.ms, c.vcp, "YOU ARE HERE", here.x, here.y + 2.2, here.z, 1.1f, 0.25f, vio[0] * 1.3f, vio[1] * 1.3f, vio[2] * 1.3f);
        } else if (a < BYD_RUSH) {
            // ---- the cosmic web
            double z = 1 - 0.6 * Kit.s((a - BYD_WEB) / (BYD_RUSH - BYD_WEB));
            Vec3d ours = s.add(GAL.multiply(0.6 * z));
            Cosmos.galaxy(c.cosmos, ours.x, ours.y, ours.z, new float[]{1, 0, 0}, new float[]{0, 0.84f, 0.54f}, (float) (14 * z * 0.3 + 2), 1.2f);
            Kit.glow(c, ours, 1.2f, vio[0], vio[1], vio[2]);
            for (int i = 0; i < GALS; i++) {
                Vec3d p = s.add(G[i * 8] * z, G[i * 8 + 1] * z, G[i * 8 + 2] * z);
                Vector3f n = new Vector3f(G[i * 8 + 4], G[i * 8 + 5], G[i * 8 + 6]);
                Vector3f e1 = new Vector3f(n).cross(0, 1, 0);
                if (e1.lengthSquared() < 1e-4) e1.set(1, 0, 0);
                e1.normalize();
                Vector3f e2 = new Vector3f(n).cross(e1).normalize();
                Cosmos.galaxy(c.cosmos, p.x, p.y, p.z, new float[]{e1.x, e1.y, e1.z}, new float[]{e2.x, e2.y, e2.z}, (float) (G[i * 8 + 3] * z), G[i * 8 + 7]);
            }
            // the seven stars of the pillars, aligning at the edge of everything
            float appear = Kit.s((a - BYD_ALIGN + 20) / 20.0);
            Vec3d[] pos = new Vec3d[7];
            for (int i = 0; i < 7; i++) {
                pos[i] = s.add(fateStar(i, a));
                float[] col = Pillars.COLOR[i];
                float pk = appear * (0.85f + 0.15f * (float) Math.sin(c.time * 0.2 + i));
                Cosmos.sun(c.cosmos, pos[i].x, pos[i].y, pos[i].z, 4.5f * pk, pk);
                Kit.glow(c, pos[i], 3 * pk, col[0] * pk, col[1] * pk, col[2] * pk);
            }
            float thread = Kit.s((a - BYD_ALIGN - 40) / 16.0);
            for (int i = 0; i < 6 && thread > 0.01f; i++) {
                float seg = MathHelper.clamp(thread * 6 - i, 0, 1);
                if (seg <= 0) break;
                Vec3d b = Kit.lerp(pos[i], pos[i + 1], seg);
                float[] col = Pillars.colorAt(i * Math.PI * 2 / 7);
                Kit.beam(c, pos[i], b, 0.35, col[0] * 1.4f, col[1] * 1.4f, col[2] * 1.4f);
            }
        } else {
            // ---- the rush back down through every scale
            double t = (a - BYD_RUSH) / (BYD_DOWN - BYD_RUSH);
            VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Kit.STREAK));
            Vec3d fwd = new Vec3d(0, 0, -1);
            for (int i = 0; i < STREAKS; i++) {
                double ph = (ST[i * 4 + 2] + t * (2 + ST[i * 4 + 3] * 3)) % 1.0;
                double d0 = 90 * (1 - ph), d1 = d0 + 12 + 30 * ph;
                Vec3d off = new Vec3d(ST[i * 4] * 40, ST[i * 4 + 1] * 40, 0);
                Vec3d p0 = c.cam.add(fwd.multiply(d0)).add(off.multiply(d0 / 90));
                Vec3d p1 = c.cam.add(fwd.multiply(Math.max(1, d0 - d1 * 0.4))).add(off.multiply(Math.max(1, d0 - d1 * 0.4) / 90));
                float b = (float) (0.4 + ph * 0.8) * (i % 3 == 0 ? 1 : 0.6f);
                float[] col = i % 4 == 0 ? vio : new float[]{1, 1, 1};
                Bill.ribbon(vc, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 0.12f, col[0] * b, col[1] * b, col[2] * b, 1, 0, 1);
            }
            // flashes of the scales passing: the galaxy, the sun, the world
            float g1 = Kit.window(a, BYD_RUSH + 6, 4, BYD_RUSH + 16, 6), g2 = Kit.window(a, BYD_RUSH + 24, 4, BYD_RUSH + 34, 6),
                    g3 = Kit.window(a, BYD_RUSH + 42, 4, BYD_RUSH + 56, 8);
            Vec3d ahead = c.cam.add(0, -6, -46);
            if (g1 > 0.01f) Cosmos.galaxy(c.cosmos, ahead.x, ahead.y, ahead.z, new float[]{1, 0, 0}, new float[]{0, 0.5f, 0.87f}, 70 * g1, g1 * 1.3f);
            if (g2 > 0.01f) Cosmos.sun(c.cosmos, ahead.x, ahead.y, ahead.z, 60 * g2, g2 * 1.5f);
            if (g3 > 0.01f) Cosmos.planet(c.cosmos, Cosmos.Body.EARTH, v(c.cam.add(0, -20 - 60 * (1 - g3), -40)), (float) (8 + 30 * g3), Cosmos.EARTH_Q, 1);
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
        float base = Kit.baseYaw(playerYaw);
        if (a < 30) return Kit.relicShot(core, I, a);
        if (a < BYD_UP) return Kit.ascent(c, (a - 30) / (BYD_UP - 30));
        Vec3d s = c.add(0, POCKET, 0);
        if (a < BYD_SYSTEM) return new Vec3d[]{s, s.add(earthC(a))};
        if (a < BYD_GALAXY) {
            Vec3d pos = s.add(Math.sin(a * 0.004) * 5, 6 + Math.cos(a * 0.003) * 2, 2);
            return new Vec3d[]{pos, s.add(SYS)};
        }
        if (a < BYD_WEB) return new Vec3d[]{s.add(Math.sin(a * 0.003) * 4, 4, 0), s.add(GAL)};
        if (a < BYD_ALIGN) return new Vec3d[]{s, s.add(0, -4, -50).add(Math.sin(a * 0.005) * 8, 0, 0)};
        if (a < BYD_RUSH) return new Vec3d[]{s, Kit.lerp(s.add(0, -4, -50), s.add(FATE_DIR.multiply(62)), Kit.s((a - BYD_ALIGN) / 30.0))};
        if (a < BYD_DOWN) return new Vec3d[]{s, s.add(0, -6, -50)};
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
        Kit.fade(ctx, w, h, Kit.window(a, BYD_SYSTEM - 6, 6, BYD_SYSTEM, 8) * 0.9f, 0x000000);
        Kit.fade(ctx, w, h, Kit.window(a, BYD_GALAXY - 6, 6, BYD_GALAXY, 8) * 0.9f, 0x000000);
        Kit.fade(ctx, w, h, Kit.window(a, BYD_WEB - 6, 6, BYD_WEB, 8) * 0.9f, 0x000000);
        Kit.fade(ctx, w, h, Kit.window(a, BYD_DOWN - 10, 10, BYD_DOWN, 10), 0xFFF8EC);
        float y = h * 0.16f;
        Kit.caption(ctx, "地 · THE WORLD", "a small blue light", col, Kit.window(a, BYD_ORBIT + 60, 14, BYD_SYSTEM - 20, 12), w, h, y);
        Kit.caption(ctx, "日 · THE SUN AND ITS CHILDREN", "eight worlds turning", col, Kit.window(a, BYD_SYSTEM + 14, 14, BYD_GALAXY - 20, 12), w, h, y);
        Kit.caption(ctx, "星 · THE MILKY WAY", "four hundred billion stars", col, Kit.window(a, BYD_GALAXY + 14, 14, BYD_WEB - 20, 12), w, h, y);
        Kit.caption(ctx, "界 · THE COSMIC WEB", "and every light a galaxy", col, Kit.window(a, BYD_WEB + 14, 14, BYD_ALIGN - 10, 12), w, h, y);
        Kit.caption(ctx, "運 · THE SEVEN ALIGN", null, col, Kit.window(a, BYD_ALIGN + 30, 10, BYD_RUSH - 6, 6), w, h, y);
    }
}
