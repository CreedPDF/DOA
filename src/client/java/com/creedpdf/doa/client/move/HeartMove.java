package com.creedpdf.doa.client.move;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.model.DoaMeshSet;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.creedpdf.doa.Abilities.*;

/**
 * 魂 HEART OF THE WORLD. The caller plunges through the ink floor and falls down a shaft through every layer of the
 * world - topsoil and roots, sediment, granite crust, deep slate, crystal caverns, basalt veined with magma, the
 * molten mantle, the white-hot outer core - into the chamber at its centre, where an ancient engine wakes and draws
 * the souls of the enemies above down into itself, then returns their strength to the caller.
 */
public final class HeartMove implements Move {
    private static final int I = SOUL;
    static final double POCKET = 300;
    private static final double SHAFT_R = 7;
    private static final double DEPTH = 8 * HRT_LAYER;
    private static final Vec3d ENGINE = new Vec3d(0, -DEPTH - 44, 0);
    private static final Identifier ENG = DoaMod.id("textures/entity/engine.png");
    private static final Identifier ENG_GLOW = DoaMod.id("textures/entity/engine_glow.png");
    static final String[] LAYER = {"TOPSOIL", "SEDIMENT", "GRANITE CRUST", "DEEP SLATE", "CRYSTAL CAVERNS", "BASALT AND MAGMA", "THE MANTLE", "THE OUTER CORE"};
    static final String[] KM = {"0.5 KM", "4 KM", "35 KM", "120 KM", "410 KM", "660 KM", "2,900 KM", "5,150 KM"};

    static double depth(double a) {
        double k = Rite.clamp01((a - HRT_DOWN) / (HRT_CORE - HRT_DOWN));
        return DEPTH * (k * k * (3 - 2 * k) * 0.5 + k * 0.5);
    }

    private static float wake(double a) {
        return Rite.smooth((a - HRT_WAKE) / 50.0);
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float[] jade = Pillars.COLOR[I];
        if (c.mine && a >= HRT_DOWN && a < HRT_UP) {
            underworld(c, a, jade);
            return;
        }
        // ---- the world: the floor opens to swallow the caller, later the souls are dragged down into it
        float dive = Kit.window(a, HRT_DIVE, 20, HRT_DOWN, 30);
        if (dive > 0.01f) {
            Kit.seal(c, I, new Vec3d(0, 0.08, 0), 9 * dive, dive * 1.3f, c.time * 0.02);
            Kit.seal(c, I, new Vec3d(0, 0.1, 0), 4 * dive, dive, -c.time * 0.04);
            Kit.glow(c, new Vec3d(0, 0.5, 0), 6 * dive, jade[0] * dive, jade[1] * dive, jade[2] * dive);
        }
        float drain = Kit.window(a, HRT_DRAIN - 10, 14, HRT_DRAIN_END, 12);
        if (drain > 0.01f) {
            Vec3d sink = new Vec3d(0, -1.5, 0);
            VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO));
            int n = 0;
            for (LivingEntity t : AbilityView.targets(c.core)) {
                Vec3d p = c.rel(t.getLerpedPos(c.td)).add(0, t.getHeight() * 0.6, 0);
                Vec3d ctrl = p.add(sink).multiply(0.5).add(0, 14, 0);
                for (int b = 0; b < 18; b++) {
                    double s = ((b / 18.0) + a * 0.02 + n * 0.37) % 1.0;
                    double u = 1 - s;
                    Vec3d q = p.multiply(u * u).add(ctrl.multiply(2 * u * s)).add(sink.multiply(s * s));
                    float bk = drain * (float) Math.sin(s * Math.PI);
                    Bill.sprite(vc, c.ms, q.x, q.y, q.z, 0.4f, 0, jade[0] * bk, jade[1] * bk, jade[2] * bk);
                }
                Bill.sprite(vc, c.ms, p.x, p.y, p.z, 1.3f, 0, jade[0] * drain * 0.7f, jade[1] * drain * 0.7f, jade[2] * drain * 0.7f);
                n++;
            }
            Kit.seal(c, I, new Vec3d(0, 0.08, 0), 7, drain, c.time * 0.03);
        }
        double cl = a - HRT_CLIMAX;
        if (cl > -6 && cl < 50) {
            float f = (float) (cl < 0 ? Rite.smooth((cl + 6) / 6.0) : Math.exp(-cl / 14));
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 5 * f + 1, 0, 240, jade[0] * f * 1.5f, jade[1] * f * 1.5f, jade[2] * f * 1.5f);
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 1.6 * f, 0, 240, f, f, f);
            if (cl > 0) Kit.shock(c, Vec3d.ZERO, 2 + cl * 1.2, f, jade);
        }
    }

    private void underworld(MoveCtx c, double a, float[] jade) {
        Vec3d s = new Vec3d(0, POCKET, 0);
        double d = depth(a);
        MatrixStack ms = c.ms;
        // the shaft walls near the camera, layer by layer
        int segs = 40;
        double y0 = Math.max(0, d - 46), y1 = Math.min(DEPTH, d + 34);
        for (int L = 0; L < 8; L++) {
            double ly0 = Math.max(y0, L * HRT_LAYER), ly1 = Math.min(y1, (L + 1) * HRT_LAYER);
            if (ly1 <= ly0) continue;
            float heat = (float) (L / 7.0);
            for (int pass = 0; pass < (L >= 3 ? 2 : 1); pass++) {
                VertexConsumer out = pass == 0 ? c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Kit.STRATA[L]))
                        : c.vcp.getBuffer(RenderLayer.getEyes(Kit.STRATA_GLOW[L]));
                MatrixStack.Entry en = ms.peek();
                Matrix4f m = en.getPositionMatrix();
                for (double y = ly0; y < ly1; y += 2) {
                    double yb = Math.min(ly1, y + 2);
                    for (int j = 0; j < segs; j++) {
                        double t0 = j * Math.PI * 2 / segs, t1 = (j + 1) * Math.PI * 2 / segs;
                        float x0 = (float) (s.x + Math.cos(t0) * SHAFT_R), z0 = (float) (s.z + Math.sin(t0) * SHAFT_R);
                        float x1 = (float) (s.x + Math.cos(t1) * SHAFT_R), z1 = (float) (s.z + Math.sin(t1) * SHAFT_R);
                        float ya = (float) (s.y - y), yb2 = (float) (s.y - yb);
                        float u0 = j / (float) segs * 5, u1 = (j + 1) / (float) segs * 5, va = (float) (y / 9.0), vb = (float) (yb / 9.0);
                        // light grows warmer and brighter with depth; glow throbs in the deep layers
                        float lit = pass == 0 ? (float) (0.42 + 0.35 * heat) : (float) ((0.45 + 0.55 * heat) * (0.85 + 0.15 * Math.sin(c.time * 0.08 + y * 0.2)));
                        float r = lit, g = lit * (pass == 0 ? 0.95f - heat * 0.25f : 1), b = lit * (pass == 0 ? 0.92f - heat * 0.45f : 1);
                        Bill.vtx(out, m, en, x0, ya, z0, u0, va, r, g, b, 1);
                        Bill.vtx(out, m, en, x1, ya, z1, u1, va, r, g, b, 1);
                        Bill.vtx(out, m, en, x1, yb2, z1, u1, vb, r, g, b, 1);
                        Bill.vtx(out, m, en, x0, yb2, z0, u0, vb, r, g, b, 1);
                    }
                }
            }
            // a seam of light where one layer gives way to the next
            double seam = L * HRT_LAYER;
            if (L > 0 && seam > y0 && seam < y1) {
                float[] col = L >= 5 ? new float[]{1f, 0.45f, 0.12f} : Kit.bright(jade, 0.2f);
                VertexConsumer rv = c.vcp.getBuffer(RenderLayer.getEyes(Kit.EDGE));
                MatrixStack.Entry en = ms.peek();
                Matrix4f m = en.getPositionMatrix();
                for (int j = 0; j < segs; j++) {
                    double t0 = j * Math.PI * 2 / segs, t1 = (j + 1) * Math.PI * 2 / segs;
                    float rr = (float) (SHAFT_R - 0.05);
                    float x0 = (float) (s.x + Math.cos(t0) * rr), z0 = (float) (s.z + Math.sin(t0) * rr);
                    float x1 = (float) (s.x + Math.cos(t1) * rr), z1 = (float) (s.z + Math.sin(t1) * rr);
                    float ya = (float) (s.y - seam + 0.5), yb = (float) (s.y - seam - 0.5);
                    Bill.vtx(rv, m, en, x0, ya, z0, j / 8f, 0, col[0], col[1], col[2], 1);
                    Bill.vtx(rv, m, en, x1, ya, z1, (j + 1) / 8f, 0, col[0], col[1], col[2], 1);
                    Bill.vtx(rv, m, en, x1, yb, z1, (j + 1) / 8f, 1, col[0], col[1], col[2], 1);
                    Bill.vtx(rv, m, en, x0, yb, z0, j / 8f, 1, col[0], col[1], col[2], 1);
                }
            }
        }
        // the ink floor sealing the shaft above
        Kit.seal(c, I, s.add(0, 0.5, 0), SHAFT_R, 0.8f, c.time * 0.02);
        // the chamber at the heart of the world
        Vec3d e = s.add(ENGINE);
        if (d > DEPTH - 60) {
            var sphere = DoaModels.PLANET;
            Vector3f ec = new Vector3f((float) e.x, (float) e.y, (float) e.z);
            if (sphere != null) {
                sphere.render(ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Kit.STRATA[7])), ec, 46, new Quaternionf(), Bill.FULL, 0, false,
                        (x, y, z, nx, ny, nz, u, v, o) -> {
                            o[0] = 0.75f;
                            o[1] = 0.55f;
                            o[2] = 0.35f;
                            o[3] = 1;
                        });
                float th = 0.55f + 0.15f * (float) Math.sin(c.time * 0.05);
                sphere.render(ms, c.vcp.getBuffer(RenderLayer.getEyes(Kit.STRATA_GLOW[7])), ec, 45.5f, new Quaternionf().rotateY((float) (c.time * 0.001)), Bill.FULL,
                        (float) (c.time * 0.0005), true, (x, y, z, nx, ny, nz, u, v, o) -> {
                            o[0] = th;
                            o[1] = th * 0.7f;
                            o[2] = th * 0.4f;
                            o[3] = 1;
                        });
            }
            engine(c, e, a, jade);
            // the souls of the enemies above pour down the shaft into its heart
            float souls = Kit.window(a, HRT_WAKE + 40, 20, HRT_UP - 10, 10);
            if (souls > 0.01f) {
                VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO));
                for (int k = 0; k < 10; k++) {
                    double ang = k / 10.0 * Math.PI * 2;
                    Vec3d top = s.add(Math.cos(ang) * 4, -DEPTH + 2, Math.sin(ang) * 4);
                    for (int b = 0; b < 16; b++) {
                        double t = ((b / 16.0) + a * 0.03 + k * 0.13) % 1.0;
                        Vec3d q = Kit.lerp(top, e, t).add(Math.sin(t * 9 + k) * 2 * (1 - t), 0, Math.cos(t * 9 + k) * 2 * (1 - t));
                        float bk = souls * (float) Math.sin(t * Math.PI);
                        Bill.sprite(vc, ms, q.x, q.y, q.z, 0.9f, 0, jade[0] * bk, jade[1] * bk, jade[2] * bk);
                    }
                }
            }
        }
    }

    private void engine(MoveCtx c, Vec3d e, double a, float[] jade) {
        DoaMeshSet eng = DoaModels.ENGINE;
        if (eng == null) return;
        float wk = wake(a);
        double t = c.time;
        double sp = 0.004 + wk * 0.03;
        MatrixStack ms = c.ms;
        ms.push();
        ms.translate(e.x, e.y, e.z);
        ms.scale(1.3f, 1.3f, 1.3f);
        DoaMeshSet.Pose pose = (n, m) -> {
            switch (n) {
                case "ring1" -> m.multiply(RotationAxis.POSITIVE_X.rotation((float) (t * sp * 1.3)));
                case "ring2" -> m.multiply(RotationAxis.POSITIVE_Z.rotation((float) (t * sp)));
                case "ring3" -> m.multiply(RotationAxis.POSITIVE_Y.rotation((float) (-t * sp * 0.8)));
                case "core" -> m.multiply(RotationAxis.POSITIVE_Y.rotation((float) (t * 0.01)));
                default -> {
                }
            }
            return true;
        };
        eng.render(ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ENG)), Bill.FULL, pose, (p, f, nx, ny, nz, o) -> {
            float k = p.equals("frame") ? 0.55f : 0.9f;
            o[0] = k;
            o[1] = k;
            o[2] = k;
            o[3] = 1;
        });
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * (0.08 + wk * 0.25));
        eng.render(ms, c.vcp.getBuffer(RenderLayer.getEyes(ENG_GLOW)), Bill.FULL, pose, (p, f, nx, ny, nz, o) -> {
            float k = p.equals("core") ? (0.4f + wk * 1.2f) * pulse : (0.15f + wk * 0.9f);
            o[0] = jade[0] * k;
            o[1] = jade[1] * k;
            o[2] = jade[2] * k;
            o[3] = 1;
        });
        ms.pop();
        Kit.glow(c, e, 10 + wk * 16, jade[0] * (0.2f + wk * 0.8f), jade[1] * (0.2f + wk * 0.8f), jade[2] * (0.2f + wk * 0.8f));
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] jade = Pillars.COLOR[I];
        if (a == HRT_DOWN) Kit.privateSound(core, DoaSounds.ORB_RISE, 0.7f);
        if (a == HRT_WAKE) {
            Kit.privateSound(core, DoaSounds.ORB_CLOSE, 0.8f);
            Cutscene.impact(core, I, 1.6f);
        }
        Vec3d c = core.getPos();
        if (a > HRT_DRAIN && a < HRT_DRAIN_END)
            for (LivingEntity t : AbilityView.targets(core))
                if (rnd.nextInt(3) == 0)
                    Kit.mote(t.getX() + rnd.nextGaussian() * 0.3, t.getY() + rnd.nextDouble() * t.getHeight(), t.getZ() + rnd.nextGaussian() * 0.3,
                            0, 0.06, 0, Kit.bright(jade, 0.3f), 0.15f, 30);
        if (a == HRT_CLIMAX) {
            Kit.burst(c.add(0, 1, 0), jade, 140, rnd, 0.5, true, true);
            Cutscene.impact(core, I, 2.0f);
        }
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        float base = Kit.baseYaw(playerYaw);
        if (a < HRT_DIVE) return Kit.relicShot(core, I, a);
        if (a < HRT_DOWN) {
            double k = Kit.s((a - HRT_DIVE) / 50.0);
            Vec3d pos = c.add(0.4, 16 - 15 * k, 0.4);
            return new Vec3d[]{pos, c.add(0.5, -8, 0.6)};
        }
        Vec3d s = c.add(0, POCKET, 0);
        if (a < HRT_CORE) {
            double d = depth(a);
            double th = a * 0.008;
            Vec3d pos = s.add(Math.sin(a * 0.01) * 1.5, -d, Math.cos(a * 0.01) * 1.5);
            return new Vec3d[]{pos, pos.add(Math.sin(th) * 3, -12, Math.cos(th) * 3)};
        }
        if (a < HRT_UP) {
            Vec3d e = s.add(ENGINE);
            double k = Kit.s((a - HRT_CORE) / 60.0);
            double th = base + (a - HRT_CORE) * 0.01;
            Vec3d pos = Kit.lerp(s.add(0, -DEPTH + 2, 0), Kit.orbit(e, th, 30, 8), k);
            return new Vec3d[]{pos, e};
        }
        if (a < 620) return new Vec3d[]{Kit.orbit(c, base + (a - HRT_UP) * 0.004, 34, 15), c.add(0, 3, 0)};
        return new Vec3d[]{Kit.orbit(c, base + 0.6 + (a - 620) * 0.004, 44, 34), c.add(0, 6, 0)};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        Kit.fade(ctx, w, h, Kit.window(a, HRT_DOWN - 10, 10, HRT_DOWN, 10), 0x0A2016);
        Kit.fade(ctx, w, h, Kit.window(a, HRT_UP - 8, 8, HRT_UP, 10), 0xE8FFF4);
        if (a > HRT_DOWN && a < HRT_CORE + 20) {
            double d = depth(a);
            int L = (int) Math.min(7, d / HRT_LAYER);
            double into = d - L * HRT_LAYER;
            float al = Kit.s(into / 6.0) * (1 - Kit.s((into - 30) / 10.0));
            if (L == 7) al = Kit.s(into / 6.0);
            Kit.caption(ctx, LAYER[L], KM[L], Pillars.rgb(I), al * (1 - Kit.s((a - HRT_CORE) / 10.0)), w, h, h * 0.16f);
        }
        Kit.caption(ctx, "核 · THE HEART OF THE WORLD", "6,371 KM", Pillars.rgb(I), Kit.window(a, HRT_CORE + 10, 14, HRT_UP - 30, 14), w, h, h * 0.16f);
    }
}
