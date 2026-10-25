package com.creedpdf.doa.client.render;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

import static com.creedpdf.doa.client.render.ArmoryCoreRenderer.*;

/** World-space VFX of the seven abilities. */
public final class AbilityRenderer {
    private AbilityRenderer() {
    }

    public static void render(ArmoryCoreEntity e, float td, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin, Vec3d cam,
                              EntityRenderDispatcher dispatcher) {
        int ab = e.getAbility();
        if (ab < 0) return;
        double a = e.getAbilityTicks() + td;
        double time = e.age + td;
        switch (ab) {
            case Abilities.DEATH -> death(e, a, td, ms, vcp, origin, cam, dispatcher);
            case Abilities.REDEMPTION -> redemption(e, a, time, ms, vcp, cam);
            case Abilities.WRATH -> wrath(e, a, time, ms, vcp, origin, cam);
            case Abilities.SOUL -> soul(e, a, td, time, ms, vcp, origin, cam, dispatcher);
            case Abilities.FATE -> fate(e, a, time, ms, vcp, origin, cam, dispatcher);
            case Abilities.SILENCE -> silence(e, a, td, time, ms, vcp, origin, dispatcher);
            case Abilities.VOID -> voidMaw(e, a, time, ms, vcp, origin, dispatcher);
            default -> {
            }
        }
    }

    /** Degrees of the dome's crown opened by Redemption's light. */
    public static double oculus(ArmoryCoreEntity e, float td) {
        if (e.getAbility() != Abilities.REDEMPTION) return 0;
        double a = e.getAbilityTicks() + td;
        return 24 * Rite.smooth((a - Abilities.REDEEM_OPEN) / 14.0) * (1 - Rite.smooth((a - 84) / 16.0));
    }

    private static void labelAbove(MatrixStack ms, VertexConsumerProvider vcp, EntityRenderDispatcher d, Vec3d p, int i, float s, float k) {
        float[] c = Pillars.COLOR[i];
        ms.push();
        ms.translate(p.x, p.y, p.z);
        ms.multiply(d.getRotation());
        billboard(vcp.getBuffer(RenderLayer.getEyes(LABEL[i])), ms, s, c[0] * k, c[1] * k, c[2] * k);
        ms.pop();
    }

    private static void groundSeal(MatrixStack ms, VertexConsumerProvider vcp, Vec3d p, int i, double r, double spin, float k) {
        float[] c = Pillars.COLOR[i];
        ms.push();
        ms.translate(p.x, p.y + 0.06, p.z);
        ms.multiply(new Quaternionf().rotateY((float) spin));
        flat(vcp.getBuffer(RenderLayer.getEyes(SEAL[i])), ms, r, c[0] * k, c[1] * k, c[2] * k);
        ms.pop();
    }

    private static void ring(MatrixStack ms, VertexConsumerProvider vcp, Vec3d p, double r, float[] c, float k, boolean ink) {
        ms.push();
        ms.translate(p.x, p.y + 0.1, p.z);
        if (ink) flatColor(vcp.getBuffer(RenderLayer.getEntityTranslucent(RING_INK)), ms, r * 0.93, 0.02f, 0.015f, 0.02f, k * 0.85f);
        ms.translate(0, 0.03, 0);
        flat(vcp.getBuffer(RenderLayer.getEyes(RING_ADD)), ms, r, c[0] * k * 1.4f, c[1] * k * 1.4f, c[2] * k * 1.4f);
        ms.pop();
    }

    // ------------------------------------------------------------------------------------------------ 死

    private static void death(ArmoryCoreEntity e, double a, float td, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin, Vec3d cam,
                              EntityRenderDispatcher d) {
        float[] c = Pillars.COLOR[0];
        float mark = Rite.smooth((a - Abilities.DEATH_MARK) / 8.0) * (1 - Rite.smooth((a - Abilities.DEATH_STRIKE - 4) / 6.0));
        List<LivingEntity> ts = AbilityView.targets(e);
        int n = 0;
        for (LivingEntity t : ts) {
            Vec3d p = t.getLerpedPos(td).subtract(origin);
            if (mark > 0.01f) {
                float pulse = 0.8f + 0.2f * (float) Math.sin(a * 0.5 + n);
                labelAbove(ms, vcp, d, p.add(0, t.getHeight() + 1.1, 0), 0, 0.55f, mark * pulse * 1.3f);
                groundSeal(ms, vcp, p, 0, 1.3 * mark, a * 0.05 + n, mark);
            }
            double s = a - Abilities.DEATH_STRIKE;
            if (s > -6 && s < 12) {
                float k = (float) (s < 0 ? Rite.smooth((s + 6) / 6.0) : Math.exp(-s / 4.0)) * 1.6f;
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TRAIL));
                column(vc, ms, new Vec3d(p.x, p.y, p.z), cam, 0.5 + (s > 0 ? 0.4 : 0), 0, 46, c[0] * k, c[1] * k, c[2] * k, 1, 0);
                column(vc, ms, new Vec3d(p.x, p.y, p.z), cam, 0.15, 0, 46, k, k * 0.8f, k * 0.8f, 1, 0);
                if (s > 0) ring(ms, vcp, p, 0.5 + s * 0.45, c, (float) Math.exp(-s / 5.0), true);
            }
            n++;
        }
        // a crimson seal of judgement across the sky
        float sky = Rite.smooth((a - 10) / 14.0) * (1 - Rite.smooth((a - Abilities.DEATH_STRIKE - 10) / 16.0));
        if (sky > 0.01f) groundSeal(ms, vcp, new Vec3d(0, 46, 0), 0, 26, -a * 0.01, sky * 0.9f);
    }

    // ------------------------------------------------------------------------------------------------ 贖

    private static void redemption(ArmoryCoreEntity e, double a, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d cam) {
        float[] c = Pillars.COLOR[1];
        float open = Rite.smooth((a - Abilities.REDEEM_OPEN) / 14.0) * (1 - Rite.smooth((a - 84) / 16.0));
        if (open <= 0.01f) return;
        double heal = a - Abilities.REDEEM_HEAL;
        float flash = heal > 0 ? (float) Math.exp(-heal / 6) : 0;
        float k = open * (1 + flash * 1.5f);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
        Vec3d base = Vec3d.ZERO;
        column(vc, ms, base, cam, 2.6 * open + flash, 0, 140, c[0] * k, c[1] * k, c[2] * k, 1, 0);
        column(vc, ms, base, cam, 0.8 * open, 0, 140, k, k, k * 0.9f, 1, 0);
        groundSeal(ms, vcp, base, 1, 4.5 * open, time * 0.02, open * 1.2f);
        groundSeal(ms, vcp, base.add(0, 0.05, 0), 1, 2.2 * open, -time * 0.035, open);
        if (heal > 0 && heal < 30) ring(ms, vcp, base, 1 + heal * 0.45, c, (float) Math.exp(-heal / 9), false);
    }

    // ------------------------------------------------------------------------------------------------ 怒

    private static void wrath(ArmoryCoreEntity e, double a, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin, Vec3d cam) {
        float[] c = Pillars.COLOR[2];
        List<Vec3d> pts = AbilityView.points(e);
        // the burning gate in the heavens the spears pour from
        float gate = Rite.smooth((a - 6) / 16.0) * (1 - Rite.smooth((a - (Abilities.LENGTH[2] - 22)) / 16.0));
        if (gate > 0.01f) {
            groundSeal(ms, vcp, new Vec3d(0, 62, 0), 2, 34 * gate, time * 0.008, gate);
            groundSeal(ms, vcp, new Vec3d(0, 62.2, 0), 2, 18 * gate, -time * 0.015, gate * 0.8f);
        }
        for (int k = 0; k < pts.size(); k++) {
            Vec3d p = pts.get(k).subtract(origin);
            int hit = Abilities.wrathImpact(k);
            double u = a - (hit - Abilities.WRATH_FALL);
            if (u < -8) continue;
            if (u < 0) {
                groundSeal(ms, vcp, p, 2, 2.2 * Rite.smooth((u + 8) / 8.0), time * 0.05, 0.6f);
                continue;
            }
            double h = a - hit;
            if (h < 0) {
                float f = Rite.clamp01(u / Abilities.WRATH_FALL);
                double y = 62 * (1 - f * f);
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TRAIL));
                column(vc, ms, p, cam, 0.7, y, y + 20, c[0] * 1.5f, c[1] * 1.5f, c[2] * 1.5f, 1, 0);
                column(vc, ms, p, cam, 0.22, y, y + 14, 1.3f, 1.1f, 0.8f, 1, 0);
                groundSeal(ms, vcp, p, 2, 2.2, time * 0.05, 0.6f + f);
            } else if (h < 20) {
                float k2 = (float) Math.exp(-h / 5);
                ring(ms, vcp, p, 0.8 + h * 0.35, c, (float) Math.exp(-h / 6), true);
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
                column(vc, ms, p, cam, 1.2, 0, 26, c[0] * k2 * 1.5f, c[1] * k2 * 1.5f, c[2] * k2 * 1.5f, 1, 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ 魂

    private static Vec3d bezier(Vec3d a, Vec3d b, Vec3d c, double t) {
        double u = 1 - t;
        return a.multiply(u * u).add(b.multiply(2 * u * t)).add(c.multiply(t * t));
    }

    private static void soul(ArmoryCoreEntity e, double a, float td, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin,
                             Vec3d cam, EntityRenderDispatcher d) {
        float[] c = Pillars.COLOR[3];
        float k = Rite.smooth((a - Abilities.SOUL_DRAIN_START + 6) / 10.0) * (1 - Rite.smooth((a - Abilities.SOUL_DRAIN_END) / 8.0));
        Vec3d apex = new Vec3d(0, Rite.ORB_RADIUS + 1.5, 0);
        VertexConsumer halo = vcp.getBuffer(RenderLayer.getEyes(HALO));
        if (k > 0.01f) {
            int n = 0;
            for (LivingEntity t : AbilityView.targets(e)) {
                Vec3d p = t.getLerpedPos(td).subtract(origin).add(0, t.getHeight() * 0.6, 0);
                Vec3d ctrl = p.add(apex).multiply(0.5).add(0, 10, 0);
                Matrix4f m = ms.peek().getPositionMatrix();
                for (int b = 0; b < 14; b++) {
                    double s = ((b / 14.0) + a * 0.025 + n * 0.37) % 1.0;
                    Vec3d q = bezier(p, ctrl, apex, s);
                    float bk = k * (float) Math.sin(s * Math.PI) * 0.9f;
                    ms.push();
                    ms.translate(q.x, q.y, q.z);
                    ms.multiply(d.getRotation());
                    billboard(halo, ms, 0.35f, c[0] * bk, c[1] * bk, c[2] * bk);
                    ms.pop();
                }
                ms.push();
                ms.translate(p.x, p.y, p.z);
                ms.multiply(d.getRotation());
                float pk = k * (0.6f + 0.4f * (float) Math.sin(a * 0.4 + n));
                billboard(halo, ms, 1.2f, c[0] * pk, c[1] * pk, c[2] * pk);
                ms.pop();
                n++;
            }
            // the gathering above the sphere and the stream pouring down inside to the caller
            ms.push();
            ms.translate(apex.x, apex.y, apex.z);
            ms.multiply(d.getRotation());
            billboard(halo, ms, 3.2f * k, c[0] * k, c[1] * k, c[2] * k);
            ms.pop();
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
            column(vc, ms, Vec3d.ZERO, cam, 0.6 * k, 0, Rite.ORB_RADIUS, c[0] * k, c[1] * k, c[2] * k, 1, 0);
        }
        double cl = a - Abilities.SOUL_CLIMAX;
        if (cl > -4 && cl < 24) {
            float f = (float) (cl < 0 ? Rite.smooth((cl + 4) / 4.0) : Math.exp(-cl / 6));
            ms.push();
            ms.translate(0, 1.2, 0);
            ms.multiply(d.getRotation());
            billboard(halo, ms, 2.8f * (1 + (float) Math.max(0, cl) * 0.05f), c[0] * f * 1.5f, c[1] * f * 1.5f, c[2] * f * 1.5f);
            ms.pop();
            if (cl > 0) ring(ms, vcp, Vec3d.ZERO, 1 + cl * 0.5, c, f, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ 運

    private static void fate(ArmoryCoreEntity e, double a, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin, Vec3d cam,
                             EntityRenderDispatcher d) {
        float[] c = Pillars.COLOR[4];
        // the caller's spirit rising as a thread of light
        float rise = Rite.smooth(a / 8.0) * (1 - Rite.smooth((a - Abilities.FATE_SPACE_IN) / 10.0));
        if (rise > 0.01f) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
            column(vc, ms, Vec3d.ZERO, cam, 0.9, 0, 200, c[0] * rise, c[1] * rise, c[2] * rise, 1, 0);
            column(vc, ms, Vec3d.ZERO, cam, 0.3, 0, 200, rise, rise, rise, 1, 0);
        }
        // the pocket of the heavens, seen only by the caller
        if (Cutscene.isAbilityCutscene(e) && a >= Abilities.FATE_SPACE_IN && a < Abilities.FATE_SPACE_OUT) {
            Vec3d s = new Vec3d(0, SpaceScene.HEIGHT, 0);
            Quaternionf rot = d.getRotation();
            Vector3f r = rot.transform(new Vector3f(1, 0, 0)), u = rot.transform(new Vector3f(0, 1, 0));
            SpaceScene.render(ms, vcp, s, cam, a, time, new float[]{r.x, r.y, r.z}, new float[]{u.x, u.y, u.z});
        }
        // the stars' answer: a pillar of light from the heavens onto the battlefield
        Vec3d p = AbilityView.point(e).subtract(origin);
        double st = a - Abilities.FATE_STRIKE;
        if (st > -16) {
            float k = (float) (st < 0 ? Rite.smooth((st + 16) / 16.0) * 0.5 : Math.exp(-st / 18.0) * 1.8 + 0.2);
            if (a > Abilities.LENGTH[4] - 20) k *= 1 - Rite.smooth((a - (Abilities.LENGTH[4] - 20)) / 18.0);
            double w = st < 0 ? 0.6 + (st + 16) * 0.04 : 5.5 * Math.exp(-st / 12.0) + 1.2;
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
            column(vc, ms, p, cam, w, 0, 260, c[0] * k, c[1] * k, c[2] * k, 1, 0);
            column(vc, ms, p, cam, w * 0.35, 0, 260, k, k, k, 1, 0);
            float[] gold = Pillars.COLOR[1];
            column(vc, ms, p, cam, w * 1.6, 0, 120, gold[0] * k * 0.35f, gold[1] * k * 0.35f, gold[2] * k * 0.35f, 1, 0);
            groundSeal(ms, vcp, p, 4, 10 * Math.min(1, k + 0.3), time * 0.01, k);
            if (st > 0 && st < 30) ring(ms, vcp, p, 1 + st * 0.8, c, (float) Math.exp(-st / 10), true);
        }
    }

    // ------------------------------------------------------------------------------------------------ 静

    private static void silence(ArmoryCoreEntity e, double a, float td, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin,
                                EntityRenderDispatcher d) {
        float[] c = Pillars.COLOR[5];
        double t = a - Abilities.SILENCE_TOLL;
        if (t < 0) return;
        for (int w = 0; w < 3; w++) {
            double tw = t - w * 9;
            if (tw <= 0 || tw > 50) continue;
            float k = (float) (1 - tw / 50.0);
            ring(ms, vcp, new Vec3d(0, 0.1 + w * 0.02, 0), 2 + tw * 1.4, c, k * k, false);
        }
        float hold = Rite.smooth((t - 4) / 8.0) * (1 - Rite.smooth((a - (Abilities.LENGTH[5] - 14)) / 12.0));
        int n = 0;
        for (LivingEntity m : AbilityView.targets(e)) {
            Vec3d p = m.getLerpedPos(td).subtract(origin);
            labelAbove(ms, vcp, d, p.add(0, m.getHeight() + 0.9, 0), 5, 0.42f, hold * 0.8f);
            groundSeal(ms, vcp, p, 5, 1.0, n * 0.7, hold * 0.6f);
            n++;
        }
    }

    // ------------------------------------------------------------------------------------------------ 虚

    private static void voidMaw(ArmoryCoreEntity e, double a, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin,
                                EntityRenderDispatcher d) {
        float[] c = Pillars.COLOR[6];
        Vec3d p = AbilityView.point(e).subtract(origin);
        float grow = Rite.easeOut((a - Abilities.VOID_OPEN) / 30.0);
        float shrink = 1 - Rite.smooth((a - (Abilities.VOID_COLLAPSE - 8)) / 8.0);
        float g = grow * shrink;
        if (g > 0.01f) {
            double r = 2.8 * g * (1 + 0.04 * Math.sin(time * 0.4));
            // accretion: two burning discs whirling around the hole, tilted
            for (int k = 0; k < 2; k++) {
                ms.push();
                ms.translate(p.x, p.y, p.z);
                ms.multiply(new Quaternionf().rotateX(0.35f + k * 0.2f).rotateZ(0.15f));
                ms.multiply(new Quaternionf().rotateY((float) (time * (0.12 + k * 0.07))));
                float b = g * (k == 0 ? 1.4f : 0.9f);
                flat(vcp.getBuffer(RenderLayer.getEyes(RING_ADD)), ms, r * (k == 0 ? 3.2 : 2.1), c[0] * b, c[1] * b, c[2] * b);
                flat(vcp.getBuffer(RenderLayer.getEyes(EDGE)), ms, r * (k == 0 ? 2.4 : 1.6), c[0] * b * 0.5f, c[1] * b * 0.4f, c[2] * b * 0.5f);
                ms.pop();
            }
            // the darkening ring of bent light and the hole itself
            ms.push();
            ms.translate(p.x, p.y, p.z);
            ms.multiply(d.getRotation());
            billboard(vcp.getBuffer(RenderLayer.getEyes(HALO)), ms, (float) (r * 2.6), c[0] * g * 0.6f, c[1] * g * 0.5f, c[2] * g * 0.6f);
            ms.pop();
            ms.push();
            ms.translate(p.x, p.y, p.z);
            sphere(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SKIN)), ms, r, 16, 24, time);
            ms.pop();
        }
        double col = a - Abilities.VOID_COLLAPSE;
        if (col > 0 && col < 30) {
            float f = (float) Math.exp(-col / 7);
            ms.push();
            ms.translate(p.x, p.y, p.z);
            ms.multiply(d.getRotation());
            billboard(vcp.getBuffer(RenderLayer.getEyes(HALO)), ms, (float) (4 + col * 1.2), c[0] * f * 1.6f, c[1] * f * 1.6f, c[2] * f * 1.6f);
            ms.pop();
            ring(ms, vcp, p.add(0, -6, 0), 1 + col * 0.9, c, f, true);
        }
    }
}
