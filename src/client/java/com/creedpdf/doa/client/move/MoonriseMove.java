package com.creedpdf.doa.client.move;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.fx.Fx;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import static com.creedpdf.doa.Abilities.*;

/**
 * 静 MOONRISE. Night falls in moments; the stars come out; a colossal full moon heaves itself over the horizon
 * behind the sphere. A great bell tolls and everything stops - mobs mid-stride, petals and drops hanging in the air -
 * while the camera drifts through the stillness in pale moonlight.
 */
public final class MoonriseMove implements Move {
    private static final int I = SILENCE;
    private static final float[] IVORY = Pillars.COLOR[I];

    private static float night(double a) {
        return Kit.window(a, MOON_NIGHT, 50, MOON_SET, 60);
    }

    /** Direction of the moon from the viewer: rising out of the horizon to the north-east. */
    private static Vec3d moonDir(double a) {
        double rise = Rite.easeOut((a - MOON_NIGHT - 20) / 170.0);
        double set = Rite.smooth((a - MOON_SET) / 70.0);
        double el = Math.toRadians(-14 + 34 * rise - 30 * set);
        return Kit.dir(Math.toRadians(35), el);
    }

    @Override
    public void render(MoveCtx c) {
        double a = c.a;
        float n = night(a);
        Kit.skyTint(c, 88, 0.01f, 0.015f, 0.04f, n * 0.88f);
        Cosmos.stars(c.cosmos, 80, n);
        Cosmos.skybox(c.cosmos, Cosmos.MILKY, 86, n * 0.6f, new org.joml.Quaternionf().rotateY(1.4f));
        Vec3d d = moonDir(a);
        Vec3d m = c.cam.add(d.multiply(78));
        if (n > 0.01f) {
            Vector3f to = new Vector3f((float) -d.x, (float) -d.y, (float) -d.z);
            Kit.glow(c, m, 70, 0.22f * n, 0.21f * n, 0.19f * n);
            Cosmos.planet(c.cosmos, Cosmos.Body.MOON, new Vector3f((float) m.x, (float) m.y, (float) m.z), 21, Cosmos.facing(Cosmos.Body.MOON, to), n);
        }
        double t = a - MOON_TOLL;
        if (t > 0) {
            for (int w = 0; w < 3; w++) {
                double tw = t - w * 10;
                if (tw <= 0 || tw > 60) continue;
                float k = (float) (1 - tw / 60.0);
                Kit.shock(c, new Vec3d(0, 0.1 + w * 0.02, 0), Rite.ORB_RADIUS + tw * 1.6, k * k, IVORY);
            }
            float hold = Kit.window(a, MOON_TOLL + 4, 8, MOON_SET, 30);
            int k = 0;
            for (LivingEntity e : AbilityView.targets(c.core)) {
                Vec3d p = c.rel(e.getLerpedPos(c.td));
                DoaFont.world(c.ms, c.vcp, "静", p.x, p.y + e.getHeight() + 1.0, p.z, 0.9f, 0, IVORY[0] * hold, IVORY[1] * hold, IVORY[2] * hold);
                Kit.seal(c, I, p, 1.2, hold * 0.7f, k * 0.7);
                k++;
            }
        }
    }

    @Override
    public void tick(ArmoryCoreEntity core, int a, boolean crossed, Random rnd) {
        Vec3d c = core.getPos();
        if (a == MOON_TOLL) {
            Cutscene.impact(core, I, 1.4f);
            // time stops: petals and drops hang where they are
            for (int k = 0; k < 340; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2, r = Rite.ORB_RADIUS + 2 + rnd.nextDouble() * 34;
                double x = c.x + Math.sin(ang) * r, z = c.z - Math.cos(ang) * r, y = c.y + 0.5 + rnd.nextDouble() * 16;
                if (k % 3 == 0)
                    Fx.spawn(Fx.Kind.MOTE, x, y, z, 0, 0, 0, Kit.bright(IVORY, 0.2f), 0.8f, 0.1f, MOON_SET - MOON_TOLL, 0, 0, true, 0, 0, 0);
                else
                    Fx.spawn(Fx.Kind.PETAL, x, y, z, 0, 0, 0, Kit.bright(IVORY, 0.1f), 1f, 0.14f, MOON_SET - MOON_TOLL, 0, 0, false, 0, 0, 0);
            }
        }
        if (a > MOON_NIGHT && a < MOON_TOLL && rnd.nextInt(2) == 0) {
            double ang = rnd.nextDouble() * Math.PI * 2, r = Rite.ORB_RADIUS + rnd.nextDouble() * 30;
            Fx.spawn(Fx.Kind.PETAL, c.x + Math.sin(ang) * r, c.y + 18, c.z - Math.cos(ang) * r, 0.02, -0.05, 0.01, Kit.bright(IVORY, 0.1f), 1f, 0.14f,
                    200, 0.002f, 0.98f, false, 0.05f, 0, 0.8f);
        }
    }

    @Override
    public Vec3d[] camera(ArmoryCoreEntity core, double a, float playerYaw) {
        Vec3d c = core.getPos();
        double R = Rite.ORB_RADIUS;
        if (a < 30) return Kit.relicShot(core, I, a);
        // look toward the moon with the sphere between: the camera sits on the far side
        Vec3d md = moonDir(Math.max(a, MOON_NIGHT + 120)).multiply(1, 0, 1).normalize();
        if (a < MOON_TOLL) {
            Vec3d pos = c.subtract(md.multiply(R + 18)).add(0, 2.2, 0);
            return new Vec3d[]{pos, Kit.lerp(c.add(0, 8, 0), pos.add(moonDir(a).multiply(60)), Kit.s((a - 40) / 80.0))};
        }
        double th = Math.atan2(-md.x, md.z) + (a - MOON_TOLL) * 0.0035;
        Vec3d pos = Kit.orbit(c, th, R + 14 - (a - MOON_TOLL) * 0.01, 3.5 + Math.sin(a * 0.01));
        return new Vec3d[]{pos, c.add(0, 7, 0)};
    }

    @Override
    public void hud(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        Kit.title(ctx, I, TITLE[I], LINE[I], a, w, h);
        float k = Kit.window(a, MOON_TOLL, 6, MOON_SET, 30);
        if (k > 0.01f) {
            ctx.fill(0, 0, w, h, ((int) (k * 40) << 24) | 0xEDE6D2);
            int band = (int) (Math.min(w, h) * 0.14f);
            for (int s = 0; s < band; s += 2) {
                int al = (int) (k * 100 * (1 - s / (float) band)) << 24;
                ctx.fill(0, s, w, s + 2, al | 0xF2ECDC);
                ctx.fill(0, h - s - 2, w, h - s, al | 0xF2ECDC);
            }
        }
        Kit.caption(ctx, "時止 · TIME STANDS STILL", "the bell has rung", Pillars.rgb(I), Kit.window(a, MOON_TOLL + 14, 14, MOON_TOLL + 100, 14), w, h,
                h * 0.16f);
    }
}
