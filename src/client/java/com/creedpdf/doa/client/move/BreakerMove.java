package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.model.DoaMeshSet;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.render.Universe;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static com.creedpdf.doa.Abilities.*;

/**
 * 怒 PLANETBREAKER. Up into orbit above the world. The seven pillars' light rises from the planet below and forges a
 * spear between the worlds; it is hurled at the red planet, which cracks open with molten fissures, shudders, and
 * bursts into burning plates and a shockwave. Back on the ground its pieces fall as meteors onto every enemy.
 */
public final class BreakerMove implements Move {
    private static final int I = WRATH;
    static final double POCKET = 300;
    private static final Vec3d EARTH = new Vec3d(0, -52, -10);
    private static final float EARTH_R = 40;
    private static final Vec3d RED = new Vec3d(16, 6, -66);
    private static final float RED_R = 15;
    private static final Vec3d FORGE = new Vec3d(5, -2, -30);
    private static final int FRAGS = 28;
    private static final float[][] FRAG = new float[FRAGS][7];

    static {
        java.util.Random r = new java.util.Random(5);
        for (int k = 0; k < FRAGS; k++) {
            FRAG[k][0] = 0.55f + r.nextFloat() * 1.1f;          // speed
            Vector3f ax = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
            FRAG[k][1] = ax.x;
            FRAG[k][2] = ax.y;
            FRAG[k][3] = ax.z;
            FRAG[k][4] = (r.nextFloat() - 0.5f) * 0.08f;        // spin rate
            FRAG[k][5] = r.nextFloat();
            FRAG[k][6] = r.nextFloat();
        }
    }

    private static final float[] MAGMA = {1.0f, 0.42f, 0.1f};
    private static final int DEBRIS = 1400;
    private static final float[] DB = new float[DEBRIS * 5];

    static {
        java.util.Random r = new java.util.Random(55);
        for (int k = 0; k < DEBRIS; k++) {
            Vector3f d = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian() * 0.7f, (float) r.nextGaussian()).normalize();
            DB[k * 5] = d.x;
            DB[k * 5 + 1] = d.y;
            DB[k * 5 + 2] = d.z;
            DB[k * 5 + 3] = (float) (0.3 + Math.pow(r.nextDouble(), 0.6) * 1.6);
            DB[k * 5 + 4] = r.nextFloat();
        }
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float[] ember = Pillars.COLOR[I];
        if (c.mine && a >= BRK_UP && a < BRK_DOWN) {
            space(c, a, ember);
            return;
        }
        // ---- meteors raining on the battlefield
        List<Vec3d> pts = AbilityView.points(c.core);
        DoaMeshSet frags = DoaModels.FRAGMENTS;
        for (int k = 0; k < pts.size(); k++) {
            int st = meteorStart(k), hit = meteorHit(k);
            Vec3d p = c.rel(pts.get(k));
            if (a >= st - 4 && a < hit) {
                double f = Rite.clamp01((a - st) / BRK_FALL);
                Vec3d from = p.add(new Vec3d(-46, 150, 30).add(k % 5 * 6, 0, k % 3 * 7));
                Vec3d pos = Kit.lerp(from, p, f * f);
                Vec3d dir = p.subtract(from).normalize();
                // fire trail and glow
                VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Kit.TRAIL));
                Vec3d tail = pos.subtract(dir.multiply(34));
                Bill.ribbon(vc, c.ms, tail.x, tail.y, tail.z, pos.x, pos.y, pos.z, c.cam.x, c.cam.y, c.cam.z, 2.2f, ember[0] * 1.4f, ember[1] * 1.2f,
                        ember[2], 1, 0, 1);
                Bill.ribbon(vc, c.ms, tail.x, tail.y, tail.z, pos.x, pos.y, pos.z, c.cam.x, c.cam.y, c.cam.z, 0.7f, 1.2f, 1.0f, 0.7f, 1, 0.3f, 1);
                Kit.glow(c, pos, 4.5f, MAGMA[0], MAGMA[1], MAGMA[2]);
                if (frags != null) drawFragment(c, frags, k % FRAGS, pos, 2.2f, (float) (a * 0.15 + k));
                Kit.seal(c, I, p, 3.0 * f, (float) f, a * 0.06);
            } else if (a >= hit && a < hit + 30) {
                double h = a - hit;
                float kk = (float) Math.exp(-h / 7);
                Kit.shock(c, p, 1 + h * 0.55, kk, ember);
                Kit.glow(c, p.add(0, 2, 0), (float) (6 + h * 0.4), MAGMA[0] * kk * 1.6f, MAGMA[1] * kk * 1.4f, MAGMA[2] * kk);
                Kit.column(c, Kit.COLUMN, p, 1.5 * kk + 0.3, 0, 30, ember[0] * kk * 1.5f, ember[1] * kk * 1.3f, ember[2] * kk);
            }
        }
    }

    private static void drawFragment(MoveCtx c, DoaMeshSet frags, int k, Vec3d pos, float scale, float spin) {
        DoaMeshSet.Part part = frags.list.get(k);
        Vector3f cen = part.centroid();
        c.ms.push();
        c.ms.translate(pos.x, pos.y, pos.z);
        c.ms.multiply(new Quaternionf().rotateAxis(spin, FRAG[k][1], FRAG[k][2], FRAG[k][3]));
        c.ms.scale(scale, scale, scale);
        c.ms.translate(-cen.x, -cen.y, -cen.z);
        frags.renderPart(part.name, c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmos.RED)), Bill.FULL,
                (p, f, nx, ny, nz, o) -> set(o, f == 1 ? 0.3f : 1, f == 1 ? 0.1f : 1, f == 1 ? 0.04f : 1, 1));
        frags.renderPart(part.name, c.ms, c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.WHITE)), Bill.FULL,
                (p, f, nx, ny, nz, o) -> set(o, f == 1 ? MAGMA[0] : 0, f == 1 ? MAGMA[1] : 0, f == 1 ? MAGMA[2] : 0, 1));
        c.ms.pop();
    }

    private static void set(float[] o, float r, float g, float b, float a) {
        o[0] = r;
        o[1] = g;
        o[2] = b;
        o[3] = a;
    }

    private void space(MoveCtx c, double a, float[] ember) {
        Vec3d s = new Vec3d(0, POCKET, 0);
        Cosmos.blackout(c.cosmos, 92);
        Cosmos.skybox(c.cosmos, Cosmos.MILKY, 88, 0.9f, new Quaternionf().rotateY(0.6f));
        Cosmos.stars(c.cosmos, 80, 1);
        Vec3d sunP = c.cam.add(new Vec3d(Cosmos.SUN.x, Cosmos.SUN.y, Cosmos.SUN.z).multiply(82));
        Universe.sunAt(c.cosmos, (float) sunP.x, (float) sunP.y, (float) sunP.z, 2.6f, c.time);
        Cosmos.planet(c.cosmos, Cosmos.Body.EARTH, v(s.add(EARTH)), EARTH_R, new Quaternionf(Cosmos.EARTH_Q).rotateY((float) (a * 0.0008)), 1);
        Vec3d red = s.add(RED);
        // ---- the spear: seven lights rise off the world and forge together
        Vec3d forge = s.add(FORGE);
        float gather = Kit.window(a, BRK_GATHER, 30, BRK_LAUNCH, 10);
        for (int i = 0; i < Pillars.COUNT && gather > 0.01f; i++) {
            float[] col = Pillars.COLOR[i];
            double ang = i / 7.0 * Math.PI * 2;
            Vec3d from = s.add(EARTH).add(new Vec3d(Math.sin(ang) * 18, EARTH_R - 3, -Math.cos(ang) * 18 + 8).normalize().multiply(EARTH_R));
            double grow = Rite.clamp01((a - BRK_GATHER - i * 4) / 40.0);
            Vec3d tip = Kit.lerp(from, forge, grow);
            Kit.beam(c, from, tip, 0.5 * gather, col[0] * gather * 1.3f, col[1] * gather * 1.3f, col[2] * gather * 1.3f);
            Kit.glow(c, tip, 1.6f, col[0] * gather, col[1] * gather, col[2] * gather);
        }
        Vec3d toRed = red.subtract(forge).normalize();
        double fly = Rite.clamp01((a - BRK_LAUNCH) / (BRK_HIT - BRK_LAUNCH));
        if (a > BRK_GATHER + 30 && a < BRK_HIT) {
            float sk = Kit.s((a - BRK_GATHER - 30) / 40.0);
            Vec3d head = Kit.lerp(forge, red.subtract(toRed.multiply(RED_R)), fly * fly);
            Vec3d tail = head.subtract(toRed.multiply(9 + fly * 20));
            Kit.beam(c, tail, head, 1.2 * sk, ember[0] * 1.6f * sk, ember[1] * 1.3f * sk, ember[2] * sk);
            Kit.beam(c, tail, head, 0.35 * sk, sk, sk, sk * 0.9f);
            Cosmos.sun(c.cosmos, head.x, head.y, head.z, 5 * sk, sk);
        }
        // ---- the red planet: struck, cracking, broken
        if (a < BRK_SHATTER) {
            double hit = a - BRK_HIT;
            float shake = hit > 0 ? (float) (0.25 * Math.sin(a * 2.3) * Math.min(1, hit / 30)) : 0;
            Vector3f rc = v(red.add(shake, shake * 0.6, 0));
            Cosmos.planet(c.cosmos, Cosmos.Body.RED, rc, RED_R, Cosmos.RED_Q, 1);
            if (hit > 0) {
                float cr = Kit.s(hit / 50.0) * (0.75f + 0.25f * (float) Math.sin(a * 0.9));
                var mesh = DoaModels.PLANET;
                if (mesh != null) mesh.render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.CRACKS)), rc, RED_R * 1.003f, Cosmos.RED_Q, Bill.FULL, 0, false,
                        (x, y, z, nx, ny, nz, u, vv, o) -> set(o, cr * 1.4f, cr * 1.2f, cr, 1));
                Kit.glow(c, red, RED_R * (1.4f + cr), MAGMA[0] * cr * 0.4f, MAGMA[1] * cr * 0.3f, MAGMA[2] * cr * 0.2f);
                if (hit < 16) Cosmos.sun(c.cosmos, red.x - toRed.x * RED_R, red.y - toRed.y * RED_R, red.z - toRed.z * RED_R, 30 * (float) Math.exp(-hit / 6), 1.4f);
            }
        } else {
            double t = (a - BRK_SHATTER) / 110.0;
            float burst = (float) Math.exp(-(a - BRK_SHATTER) / 10.0);
            DoaMeshSet frags = DoaModels.FRAGMENTS;
            if (frags != null) {
                for (int k = 0; k < frags.list.size() && k < FRAGS; k++) {
                    DoaMeshSet.Part part = frags.list.get(k);
                    Vector3f cen = part.centroid();
                    Vector3f dir = new Vector3f(cen).normalize();
                    double travel = (1 - Math.exp(-t * 3)) * FRAG[k][0] * 2.2 + t * FRAG[k][0] * 0.8;
                    double ang = (a - BRK_SHATTER) * FRAG[k][4];
                    float heat = (float) Math.max(0.15, 1 - t * 0.7);
                    c.ms.push();
                    c.ms.translate(red.x, red.y, red.z);
                    c.ms.scale(RED_R, RED_R, RED_R);
                    c.ms.translate(dir.x * travel, dir.y * travel, dir.z * travel);
                    c.ms.translate(cen.x, cen.y, cen.z);
                    c.ms.multiply(new Quaternionf().rotateAxis((float) ang, FRAG[k][1], FRAG[k][2], FRAG[k][3]));
                    c.ms.translate(-cen.x, -cen.y, -cen.z);
                    frags.renderPart(part.name, c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmos.RED)), Bill.FULL,
                            (p, f, nx, ny, nz, o) -> set(o, f == 1 ? 0.25f : 1, f == 1 ? 0.08f : 1, f == 1 ? 0.03f : 1, 1));
                    frags.renderPart(part.name, c.ms, c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.WHITE)), Bill.FULL,
                            (p, f, nx, ny, nz, o) -> set(o, f == 1 ? MAGMA[0] * heat : 0, f == 1 ? MAGMA[1] * heat : 0, f == 1 ? MAGMA[2] * heat : 0, 1));
                    c.ms.pop();
                }
            }
            // a storm of glowing debris and dust thrown out in every direction
            VertexConsumer ev = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.STAR));
            VertexConsumer dv = c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO));
            for (int k = 0; k < DEBRIS; k++) {
                Vec3d d = new Vec3d(DB[k * 5], DB[k * 5 + 1], DB[k * 5 + 2]);
                double travel = RED_R * (1 + (1 - Math.exp(-t * 4)) * DB[k * 5 + 3] * 2.4 + t * DB[k * 5 + 3] * 1.2);
                Vec3d q = red.add(d.multiply(travel));
                float heat = (float) Math.max(0, 1 - t * (0.6 + DB[k * 5 + 4]));
                float sz = 0.18f + DB[k * 5 + 4] * 0.5f;
                Bill.sprite(ev, c.ms, q.x, q.y, q.z, sz, 0, MAGMA[0] * heat * 1.4f, MAGMA[1] * heat * 1.2f, MAGMA[2] * heat + 0.02f);
                if (k % 6 == 0) Bill.sprite(dv, c.ms, q.x, q.y, q.z, sz * 7, 0, 0.25f * heat, 0.08f * heat, 0.03f * heat);
            }
            // the molten heart, the flash and the shockwave
            float core = (float) Math.exp(-(a - BRK_SHATTER) / 30.0);
            Kit.glow(c, red, RED_R * (0.9f + (1 - core) * 1.5f), MAGMA[0] * core * 1.5f, MAGMA[1] * core * 1.2f, MAGMA[2] * core);
            Cosmos.sun(c.cosmos, red.x, red.y, red.z, 70 * burst + 4, Math.min(1.5f, burst * 2));
            float ring = (float) Math.max(0, 1 - t);
            VertexConsumer rv = c.vcp.getBuffer(RenderLayer.getEyes(Kit.RING));
            float tilt = 0.45f;
            Bill.plane(rv, c.ms, red.x, red.y, red.z, new float[]{1, 0, 0}, new float[]{0, MathHelper.sin(tilt), MathHelper.cos(tilt)},
                    (float) (RED_R * (1.2 + t * 6)), ember[0] * ring * 1.5f, ember[1] * ring * 1.2f, ember[2] * ring, 1);
            Bill.plane(rv, c.ms, red.x, red.y, red.z, new float[]{1, 0, 0}, new float[]{0, MathHelper.sin(tilt + 0.4f), MathHelper.cos(tilt + 0.4f)},
                    (float) (RED_R * (1.0 + t * 4)), ring, ring * 0.8f, ring * 0.6f, 1);
        }
    }

    private static Vector3f v(Vec3d p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] ember = Pillars.COLOR[I];
        if (a == BRK_UP) Kit.privateSound(core, DoaSounds.FATE_SPACE, 0.85f);
        if (a == BRK_HIT - 12) Kit.privateSound(core, DoaSounds.SPEAR, 0.6f);
        if (a == BRK_HIT) {
            Kit.privateSound(core, DoaSounds.IMPACT, 0.6f);
            Cutscene.addShake(core, 1.4f, 40);
        }
        if (a == BRK_SHATTER) {
            Kit.privateSound(core, DoaSounds.VOID_COLLAPSE, 0.75f);
            Cutscene.impact(core, I, 2.6f);
        }
        if (a == BRK_SHATTER + 22) Kit.privateSound(core, DoaSounds.FATE_STRIKE, 0.7f);
        List<Vec3d> pts = AbilityView.points(core);
        for (int k = 0; k < pts.size(); k++) {
            if (a == meteorHit(k)) {
                Vec3d p = pts.get(k);
                Kit.burst(p.add(0, 0.5, 0), ember, 40, rnd, 0.5, true, true);
                for (int n = 0; n < 16; n++)
                    com.creedpdf.doa.client.fx.Fx.spawn(com.creedpdf.doa.client.fx.Fx.Kind.SPARK, p.x, p.y + 0.5, p.z, rnd.nextGaussian() * 0.5,
                            0.4 + rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.5, MAGMA, 1f, 0.1f, 20 + rnd.nextInt(10), 0.04f, 0.9f, true, 0, 0, 0);
                if (k == 0) Cutscene.impact(core, I, 1.8f);
                else Cutscene.addShake(core, 1.0f, 24);
            }
        }
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        float base = Kit.baseYaw(playerYaw);
        if (a < 30) return Kit.relicShot(core, I, a);
        if (a < BRK_UP) return Kit.ascent(c, (a - 30) / (BRK_UP - 30));
        if (a < BRK_DOWN) {
            Vec3d s = c.add(0, POCKET, 0);
            Vec3d pos = s.add(-2, 1, 4 - (a - BRK_UP) * 0.018);
            Vec3d limb = s.add(4, -22, -40), red = s.add(RED), forge = s.add(FORGE);
            // the pull-back: from skimming the clouds to high orbit, the world turning into a sphere below
            double pk = Rite.clamp01((a - BRK_UP) / 80.0);
            double ease = 1 - Math.pow(1 - pk, 3);
            Vec3d skim = s.add(EARTH).add(new Vec3d(0.05, 1, 0.12).normalize().multiply(EARTH_R + 0.9));
            pos = Kit.lerp(skim, pos, ease);
            Vec3d look;
            if (a < BRK_UP + 80) look = Kit.lerp(skim.add(0, -0.5, -30), limb, Kit.s(pk * 1.2));
            else if (a < BRK_UP + 110) look = Kit.lerp(limb, forge, Kit.s((a - BRK_UP - 80) / 30.0));
            else if (a < BRK_LAUNCH) look = Kit.lerp(forge, Kit.lerp(forge, red, 0.5), Kit.s((a - BRK_UP - 110) / (BRK_LAUNCH - BRK_UP - 110.0)));
            else look = Kit.lerp(Kit.lerp(forge, red, 0.5), red, Kit.s((a - BRK_LAUNCH) / 40.0));
            return new Vec3d[]{pos, look};
        }
        if (a < 600) return new Vec3d[]{Kit.orbit(c, base + (a - BRK_DOWN) * 0.003, 46, 34), c.add(0, 2, 0)};
        return new Vec3d[]{Kit.orbit(c, base + 0.8 + (a - 600) * 0.004, 32, 15), c.add(0, 4, 0)};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        Kit.fade(ctx, w, h, Kit.window(a, BRK_UP - 8, 8, BRK_UP, 10), 0xFFF0E0);
        Kit.fade(ctx, w, h, Kit.window(a, BRK_DOWN - 8, 8, BRK_DOWN, 10), 0xFFF0E0);
        Kit.caption(ctx, "砕 · THE WORLD BREAKS", "its burning pieces will fall", Pillars.rgb(I), Kit.window(a, BRK_SHATTER + 20, 14, BRK_SHATTER + 90, 14), w, h,
                h * 0.16f);
        float rk = (float) Math.exp(-Math.max(0, a - BRK_SHATTER) / 8.0) * (a >= BRK_SHATTER ? 1 : 0);
        Kit.fade(ctx, w, h, rk * 0.8f, 0xFFD8A0);
    }
}
