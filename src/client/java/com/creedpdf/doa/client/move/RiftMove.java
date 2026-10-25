package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.List;

import static com.creedpdf.doa.Abilities.*;

/**
 * 虚 RIFT. The sky tears open in long burning wounds; through them is deep space - planets, nebulae, a distant
 * galaxy. The camera pushes right up to the largest tear and looks through. Then the enemies are lifted off the
 * ground and drawn spiralling up into the rifts, and the sky seals shut behind them.
 */
public final class RiftMove implements Move {
    private static final int I = VOID;
    private static final int STEPS = 36, ROWS = 4;

    private record Frame(Vec3d c, Vec3d a, Vec3d b, double len, double wid) {
    }

    private static Frame frame(ArmoryCoreEntity core, Vec3d centerWorld, int k, double t, Vec3d origin) {
        Vec3d c = centerWorld.subtract(origin);
        Vec3d toCore = core.getPos().subtract(centerWorld).multiply(1, 0, 1);
        Vec3d n = toCore.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : toCore.normalize();
        Vec3d right = n.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(n).normalize();
        double th = Math.toRadians(62 + k * 23 % 50);
        Vec3d A = right.multiply(Math.cos(th)).add(up.multiply(Math.sin(th)));
        Vec3d B = right.multiply(-Math.sin(th)).add(up.multiply(Math.cos(th)));
        double open = t - (RFT_OPEN + k * 14);
        double seal = Rite.smooth((t - RFT_SEAL - k * 4) / 30.0);
        double len = (12 + k % 3 * 4) * Rite.smooth(open / 26.0) * (1 - seal * 0.7);
        double wid = (4.5 + k % 2 * 2.5) * Rite.smooth((open - 14) / 40.0) * (1 - seal);
        if (k == 0) {
            len *= 1.4;
            wid *= 1.4;
        }
        return new Frame(c, A, B, len, wid);
    }

    private static double halfWidth(Frame f, double s, double time, int k) {
        double base = Math.pow(Math.max(0, 1 - s * s), 0.75);
        double jag = 1 + 0.22 * Math.sin(s * 11 + time * 0.05 + k) + 0.12 * Math.sin(s * 29 - time * 0.09);
        return f.wid * base * jag;
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float[] mag = Pillars.COLOR[I];
        List<Vec3d> pts = AbilityView.points(c.core);
        for (int k = 0; k < pts.size(); k++) {
            Frame f = frame(c.core, pts.get(k), k, a, c.origin);
            if (f.len < 0.05) continue;
            // the view into space: each corner looks along its own line of sight into the panorama
            if (f.wid > 0.02) {
                VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmos.PANORAMA));
                MatrixStack.Entry en = c.ms.peek();
                Matrix4f m = en.getPositionMatrix();
                for (int i = 0; i < STEPS; i++) {
                    double s0 = -1 + 2.0 * i / STEPS, s1 = -1 + 2.0 * (i + 1) / STEPS;
                    double w0 = halfWidth(f, s0, c.time, k), w1 = halfWidth(f, s1, c.time, k);
                    for (int r = 0; r < ROWS; r++) {
                        double r0 = -1 + 2.0 * r / ROWS, r1 = -1 + 2.0 * (r + 1) / ROWS;
                        Vec3d[] q = {pt(f, s0, w0 * r0), pt(f, s1, w1 * r0), pt(f, s1, w1 * r1), pt(f, s0, w0 * r1)};
                        float[][] uv = new float[4][];
                        for (int j = 0; j < 4; j++) uv[j] = pano(q[j].subtract(c.cam));
                        for (int j = 1; j < 4; j++) {
                            if (uv[j][0] - uv[0][0] > 0.5f) uv[j][0] -= 1;
                            if (uv[j][0] - uv[0][0] < -0.5f) uv[j][0] += 1;
                        }
                        for (int j = 0; j < 4; j++) Bill.vtx(vc, m, en, (float) q[j].x, (float) q[j].y, (float) q[j].z, uv[j][0], uv[j][1], 1.3f, 1.3f, 1.3f, 1);
                    }
                }
            }
            // the burning lips of the tear
            VertexConsumer ev = c.vcp.getBuffer(RenderLayer.getEyes(Kit.RIFT_EDGE));
            float heat = 1.2f + 0.3f * (float) Math.sin(c.time * 0.3 + k);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < STEPS; i++) {
                    double s0 = -1 + 2.0 * i / STEPS, s1 = -1 + 2.0 * (i + 1) / STEPS;
                    Vec3d p0 = pt(f, s0, side * halfWidth(f, s0, c.time, k)), p1 = pt(f, s1, side * halfWidth(f, s1, c.time, k));
                    Bill.ribbon(ev, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 0.7f, mag[0] * heat, mag[1] * heat, mag[2] * heat, 1, 0, 1);
                    Bill.ribbon(ev, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 0.18f, 1.2f, 1.1f, 1.15f, 1, 0, 1);
                }
            }
            float g = (float) Math.min(1, f.len / 10);
            Kit.glow(c, f.c, (float) (f.len * 1.4), mag[0] * 0.35f * g, mag[1] * 0.2f * g, mag[2] * 0.3f * g);
        }
        // the taken: lifted and drawn up toward the tears
        float lift = Kit.window(a, RFT_LIFT, 20, RFT_TAKE, 6);
        if (lift > 0.01f && !pts.isEmpty()) {
            for (LivingEntity e : AbilityView.targets(c.core)) {
                Vec3d p = c.rel(e.getLerpedPos(c.td)).add(0, e.getHeight() / 2, 0);
                Vec3d best = pts.get(0);
                for (Vec3d q : pts) if (q.squaredDistanceTo(e.getPos()) < best.squaredDistanceTo(e.getPos())) best = q;
                Vec3d r = c.rel(best);
                Kit.beam(c, p, Kit.lerp(p, r, 0.85), 0.25 * lift, mag[0] * lift * 0.7f, mag[1] * lift * 0.5f, mag[2] * lift * 0.7f);
                Kit.glow(c, p, 1.6f * lift, mag[0] * lift * 0.8f, mag[1] * lift * 0.6f, mag[2] * lift * 0.8f);
                DoaFont.world(c.ms, c.vcp, "虚", p.x, p.y + e.getHeight() * 0.5 + 1, p.z, 0.8f, 0, mag[0] * lift, mag[1] * lift, mag[2] * lift);
            }
        }
    }

    private static Vec3d pt(Frame f, double s, double w) {
        return f.c.add(f.a.multiply(s * f.len)).add(f.b.multiply(w));
    }

    private static float[] pano(Vec3d d) {
        Vec3d n = d.normalize();
        float u = (float) (Math.atan2(n.x, n.z) / (Math.PI * 2) + 0.25);
        float v = (float) (0.5 - Math.asin(MathHelper.clamp(n.y, -1, 1)) / Math.PI);
        return new float[]{u, v};
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] mag = Pillars.COLOR[I];
        List<Vec3d> pts = AbilityView.points(core);
        for (int k = 0; k < pts.size(); k++) {
            if (a == RFT_OPEN + k * 14) Cutscene.addShake(core, 1.0f, 40);
            if (a > RFT_OPEN + k * 14 && a < RFT_SEAL + 20 && rnd.nextInt(2) == 0) {
                Vec3d p = pts.get(k);
                Kit.mote(p.x + rnd.nextGaussian() * 4, p.y + rnd.nextGaussian() * 6, p.z + rnd.nextGaussian() * 4, rnd.nextGaussian() * 0.05,
                        -0.05, rnd.nextGaussian() * 0.05, Kit.bright(mag, 0.3f), 0.2f, 40);
            }
            if (a == RFT_SEAL + 26) Kit.burst(pts.get(k), mag, 50, rnd, 0.6, true, false);
        }
        if (a > RFT_LIFT && a < RFT_TAKE)
            for (LivingEntity e : AbilityView.targets(core)) {
                double ang = a * 0.3 + e.getId();
                Kit.mote(e.getX() + Math.cos(ang) * 1.2, e.getY() + rnd.nextDouble() * e.getHeight(), e.getZ() + Math.sin(ang) * 1.2, 0, 0.1, 0,
                        Kit.bright(mag, 0.3f), 0.14f, 20);
                if (rnd.nextInt(3) == 0) Kit.ink(e.getX(), e.getY(), e.getZ(), rnd.nextGaussian() * 0.05, 0.12, rnd.nextGaussian() * 0.05, 0.6f, 30);
            }
        if (a == RFT_TAKE) Cutscene.impact(core, I, 1.6f);
        if (a == RFT_SEAL + 26) Cutscene.impact(core, I, 2.0f);
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        double R = Rite.ORB_RADIUS;
        float base = Kit.baseYaw(playerYaw);
        if (a < 30) return Kit.relicShot(core, I, a);
        List<Vec3d> pts = AbilityView.points(core);
        Vec3d main = pts.isEmpty() ? c.add(0, 40, -R - 12) : pts.get(0);
        Vec3d n = c.subtract(main).multiply(1, 0, 1).normalize();
        Vec3d wide = c.subtract(n.multiply(-(R + 8))).add(0, 4, 0);
        wide = c.add(n.multiply(R + 10)).add(0, 4, 0);
        if (a < 150) return new Vec3d[]{wide, Kit.lerp(c.add(0, 10, 0), main, Kit.s((a - 30) / 60.0))};
        if (a < RFT_LIFT) {
            Vec3d close = main.add(n.multiply(7)).add(0, -1, 0);
            double k = Kit.s((a - 150) / 50.0) * (1 - Kit.s((a - 226) / 34.0));
            return new Vec3d[]{Kit.lerp(wide, close, k), main};
        }
        if (a < RFT_TAKE + 30) return new Vec3d[]{Kit.orbit(c, base + (a - RFT_LIFT) * 0.004, 46, 22), Kit.lerp(c, main, 0.5).add(0, 6, 0)};
        return new Vec3d[]{wide.add(0, -1, 0), Kit.lerp(main, c.add(0, 10, 0), Kit.s((a - RFT_SEAL - 20) / 50.0))};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        Kit.caption(ctx, "裂 · THE SKY TEARS", "and behind it, the void", Pillars.rgb(I), Kit.window(a, 160, 14, 240, 14), w, h, h * 0.16f);
        Kit.caption(ctx, "淵 · THE VOID TAKES", null, Pillars.rgb(I), Kit.window(a, RFT_TAKE - 10, 10, RFT_TAKE + 50, 14), w, h, h * 0.16f);
    }
}
