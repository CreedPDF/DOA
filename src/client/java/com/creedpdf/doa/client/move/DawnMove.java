package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.model.DoaMeshSet;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.DoaMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.Arrays;
import java.util.Comparator;

import static com.creedpdf.doa.Abilities.*;

/**
 * 贖 DAWN. The dome's crown opens and the caller rises through it, up past the clouds into a sea of cloud at
 * sunrise. A colossal halo gate climbs out of the clouds, turns, and pours its light straight down through the sky
 * into the sphere: the caller is made whole and the undead burn.
 */
public final class DawnMove implements Move {
    private static final int I = REDEMPTION;
    static final double POCKET = 300;
    private static final Identifier GATE = DoaMod.id("textures/entity/gate.png");
    private static final Identifier GATE_GLOW = DoaMod.id("textures/entity/gate_glow.png");
    private static final int CLOUDS = 280;
    private static final float[] CL = new float[CLOUDS * 5];

    static {
        java.util.Random r = new java.util.Random(31);
        for (int i = 0; i < CLOUDS; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 6 + Math.sqrt(r.nextDouble()) * 82;
            CL[i * 5] = (float) (Math.sin(a) * d);
            CL[i * 5 + 1] = (float) (-14 + r.nextGaussian() * 1.6);
            CL[i * 5 + 2] = (float) (-Math.cos(a) * d);
            CL[i * 5 + 3] = 6 + r.nextFloat() * 9;
            CL[i * 5 + 4] = r.nextInt(4);
        }
    }

    private static final Vec3d SUN = new Vec3d(0, 0.05, -1).normalize();
    private static final Vec3d GATE_AT = new Vec3d(0, 0, -58);

    @Override
    public double oculus(double a) {
        return 30 * Kit.window(a, DAWN_RISE, 30, LENGTH[I] - 44, 30);
    }

    private static double gateRise(double a) {
        return -46 + 58 * Rite.easeOut((a - DAWN_GATE) / 110.0);
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float[] gold = Pillars.COLOR[I];
        // ---- the pocket of sky above the clouds, seen only by the caller
        if (c.mine && a >= DAWN_UP && a < DAWN_DOWN) {
            Vec3d s = new Vec3d(0, POCKET, 0);
            Cosmos.backdrop(c.cosmos, 92, new float[]{0.09f, 0.07f, 0.2f}, new float[]{1.0f, 0.6f, 0.32f}, new float[]{0.4f, 0.28f, 0.3f});
            Vec3d sunP = c.cam.add(SUN.multiply(84));
            Cosmos.sun(c.cosmos, sunP.x, sunP.y, sunP.z, 34, 1.0f);
            com.creedpdf.doa.client.render.Universe.sunAt(c.cosmos, (float) sunP.x, (float) sunP.y, (float) sunP.z, 4.2f, c.time);
            // crepuscular rays fanning out of the sun across the sky
            VertexConsumer rays = c.vcp.getBuffer(RenderLayer.getEyes(Kit.COLUMN));
            Vec3d rs = SUN.crossProduct(new Vec3d(0, 1, 0)).normalize(), ru = rs.crossProduct(SUN).normalize();
            for (int k = 0; k < 18; k++) {
                double ang = k / 18.0 * Math.PI * 2 + Math.sin(c.time * 0.004 + k) * 0.05;
                Vec3d dir = rs.multiply(Math.cos(ang)).add(ru.multiply(Math.sin(ang)));
                float rk = (0.12f + 0.1f * (float) Math.sin(c.time * 0.03 + k * 2.1)) * (k % 3 == 0 ? 1.6f : 1f);
                Vec3d p0 = sunP.add(dir.multiply(5)), p1 = sunP.add(dir.multiply(70)).subtract(SUN.multiply(20));
                Bill.ribbon(rays, c.ms, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.cam.x, c.cam.y, c.cam.z, 2.5f + k % 4, gold[0] * rk, gold[1] * rk * 0.85f,
                        gold[2] * rk * 0.6f, 1, 0, 1);
            }
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO)), c.ms, sunP.x, sunP.y, sunP.z, 60, 0, 0.5f, 0.28f, 0.1f);
            // sea of clouds: sorted far to near so the soft edges layer properly
            Integer[] order = new Integer[CLOUDS];
            for (int i = 0; i < CLOUDS; i++) order[i] = i;
            final Vec3d cam = c.cam;
            Arrays.sort(order, Comparator.comparingDouble(i -> -cam.squaredDistanceTo(s.x + CL[i * 5], s.y + CL[i * 5 + 1], s.z + CL[i * 5 + 2])));
            for (int i : order) {
                double x = s.x + CL[i * 5], y = s.y + CL[i * 5 + 1], z = s.z + CL[i * 5 + 2];
                Vec3d d = new Vec3d(x, y, z).subtract(cam);
                double toward = d.normalize().dotProduct(SUN);
                float warm = (float) Math.max(0, toward);
                float r = 0.62f + 0.4f * warm, g = 0.52f + 0.22f * warm, b = 0.62f - 0.12f * warm;
                VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEntityTranslucent(Kit.CLOUD[(int) CL[i * 5 + 4]]));
                float sz = CL[i * 5 + 3];
                Bill.quad(vc, c.ms, x, y, z, sz, sz * 0.6f, 0, 0, 0, 1, 1, r, g, b, 0.95f);
            }
            // the halo gate
            DoaMeshSet gate = DoaModels.GATE;
            float up = Kit.s((a - DAWN_GATE) / 30.0);
            if (gate != null && up > 0) {
                Vec3d g = s.add(GATE_AT).add(0, gateRise(a), 0);
                double spin = (a - DAWN_GATE) * 0.004 + (a > DAWN_POUR ? (a - DAWN_POUR) * 0.02 : 0);
                c.ms.push();
                c.ms.translate(g.x, g.y, g.z);
                c.ms.multiply(RotationAxis.POSITIVE_X.rotation((float) (Math.PI / 2)));
                c.ms.scale(0.9f, 0.9f, 0.9f);
                DoaMeshSet.Pose pose = (n, ms) -> {
                    ms.multiply(RotationAxis.POSITIVE_Y.rotation((float) (n.equals("inner") ? -spin * 2 : spin)));
                    return true;
                };
                gate.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GATE)), Bill.FULL, pose, null);
                float gk = 0.8f + (a > DAWN_POUR ? 1.2f * Kit.s((a - DAWN_POUR) / 20.0) : 0);
                gate.render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(GATE_GLOW)), Bill.FULL, pose,
                        (p, f, nx, ny, nz, o) -> {
                            o[0] = gold[0] * gk;
                            o[1] = gold[1] * gk;
                            o[2] = gold[2] * gk;
                            o[3] = 1;
                        });
                c.ms.pop();
                // its heart flares and pours down
                float pour = Kit.s((a - DAWN_POUR) / 16.0);
                Kit.glow(c, g, 22 + pour * 30, gold[0] * (0.4f + pour), gold[1] * (0.4f + pour), gold[2] * (0.4f + pour));
                if (pour > 0.01f) {
                    Kit.beam(c, g, g.add(0, -120, 0), 6 * pour, gold[0] * pour * 1.4f, gold[1] * pour * 1.3f, gold[2] * pour);
                    Kit.beam(c, g, g.add(0, -120, 0), 1.8 * pour, pour, pour, pour * 0.9f);
                }
                // rays streaming through the gate
                for (int k = 0; k < 12; k++) {
                    double ang = k / 12.0 * Math.PI * 2 + a * 0.003;
                    Vec3d o = g.add(Math.cos(ang) * 22, Math.sin(ang) * 22, 0);
                    Kit.beam(c, o, o.add(0, 0, 60), 1.2, gold[0] * 0.25f * up, gold[1] * 0.22f * up, gold[2] * 0.15f * up);
                }
            }
            return;
        }
        // ---- the world: heaven's light poured through the open crown
        double pourW = Kit.window(a, DAWN_DOWN - 6, 10, LENGTH[I] - 50, 30);
        if (pourW > 0.01) {
            float k = (float) pourW;
            double heal = a - DAWN_HEAL;
            float flash = heal > 0 ? (float) Math.exp(-heal / 6) : 0;
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 3.6 * k + flash * 2, 0, 220, gold[0] * k * (1 + flash), gold[1] * k * (1 + flash), gold[2] * k);
            Kit.column(c, Kit.COLUMN, Vec3d.ZERO, 1.0 * k, 0, 220, k * (1 + flash), k * (1 + flash), k);
            Kit.seal(c, I, Vec3d.ZERO, 6 * k, k * 1.2f, c.time * 0.02);
            Kit.seal(c, I, new Vec3d(0, 0.04, 0), 3 * k, k, -c.time * 0.035);
            if (heal > 0 && heal < 40) Kit.shock(c, Vec3d.ZERO, 1 + heal * 0.6, (float) Math.exp(-heal / 10), gold);
            // the dawn finds the undead
            for (LivingEntity t : AbilityView.targets(c.core)) {
                Vec3d p = c.rel(t.getLerpedPos(c.td));
                float u = Kit.window(a, DAWN_HEAL - 10, 10, DAWN_HEAL + 6, 16);
                if (u > 0.01f) {
                    Kit.beam(c, p.add(0, 120, 0), p, 1.2 * u, gold[0] * u * 1.5f, gold[1] * u * 1.3f, gold[2] * u);
                    if (a > DAWN_HEAL) Kit.shock(c, p, 1 + (a - DAWN_HEAL) * 0.3, u, gold);
                }
            }
        }
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        float[] gold = Pillars.COLOR[I];
        Vec3d c = core.getPos();
        if (a == DAWN_UP) Kit.privateSound(core, com.creedpdf.doa.registry.DoaSounds.FATE_SPACE, 1.3f);
        if (a > DAWN_DOWN && a < LENGTH[I] - 40) {
            for (int n = 0; n < 4; n++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 4;
                com.creedpdf.doa.client.fx.Fx.spawn(com.creedpdf.doa.client.fx.Fx.Kind.PETAL, c.x + Math.cos(ang) * rr, c.y + 14 + rnd.nextDouble() * 6,
                        c.z + Math.sin(ang) * rr, 0, -0.07, 0, Kit.bright(gold, 0.3f), 1f, 0.15f, 100, 0.002f, 0.97f, true, (rnd.nextFloat() - 0.5f) * 0.3f, 0, 1f);
                Kit.mote(c.x + Math.cos(ang) * rr * 0.6, c.y + rnd.nextDouble() * 14, c.z + Math.sin(ang) * rr * 0.6, 0, -0.06, 0, Kit.bright(gold, 0.5f), 0.12f, 30);
            }
        }
        if (a == DAWN_HEAL) {
            Kit.burst(c.add(0, 1, 0), gold, 120, rnd, 0.35, false, false);
            for (LivingEntity t : AbilityView.targets(core)) Kit.burst(t.getPos().add(0, 1, 0), gold, 30, rnd, 0.3, true, false);
            Cutscene.impact(core, I, 1.0f);
        }
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        float base = Kit.baseYaw(playerYaw);
        if (a < DAWN_RISE) return Kit.relicShot(core, I, a);
        if (a < DAWN_UP) {
            double k = Kit.s((a - DAWN_RISE - 20) / 60.0);
            Vec3d pos = c.add(2.2 * (1 - k), 1.8 + k * k * 30, 2.2 * (1 - k));
            return new Vec3d[]{pos, pos.add(0.2, 10, -0.4 - 3 * (1 - k))};
        }
        if (a < DAWN_DOWN) {
            Vec3d s = c.add(0, POCKET, 0);
            Vec3d pos = s.add(0, 2 + Math.sin(a * 0.02) * 0.3, -(a - DAWN_UP) * 0.08);
            Vec3d lookSun = pos.add(SUN.multiply(40));
            Vec3d g = s.add(GATE_AT).add(0, gateRise(a), 0);
            Vec3d look = a < DAWN_GATE ? lookSun : Kit.lerp(lookSun, g, Kit.s((a - DAWN_GATE) / 50.0));
            if (a > DAWN_POUR) look = Kit.lerp(g, g.add(0, -60, 0), Kit.s((a - DAWN_POUR) / 40.0));
            return new Vec3d[]{pos, look};
        }
        double th = base + (a - DAWN_DOWN) * 0.012;
        return new Vec3d[]{Kit.orbit(c, th, 6.5, 1.3), c.add(0, 2 + Kit.s((a - DAWN_DOWN) / 40.0) * 12, 0)};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        Kit.fade(ctx, w, h, Kit.window(a, DAWN_UP - 8, 8, DAWN_UP, 10), 0xFFF4E0);
        Kit.fade(ctx, w, h, Kit.window(a, DAWN_DOWN - 8, 8, DAWN_DOWN, 10), 0xFFF4E0);
        Kit.caption(ctx, "暁 · ABOVE THE CLOUDS", "the gate of heaven", Pillars.rgb(I), Kit.window(a, DAWN_GATE + 20, 14, DAWN_GATE + 90, 14), w, h, h * 0.16f);
    }
}
