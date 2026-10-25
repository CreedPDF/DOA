package com.creedpdf.doa.client.render;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.model.DoaMesh;
import com.creedpdf.doa.client.model.DoaModels;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The heavens of the Fate cutscene: a pocket of real space far above the armory. A living world below with
 * oceans, clouds, city lights on its night side and a thin atmosphere; its moon; a ringed gas giant; the sun's
 * glare; the Milky Way and thousands of stars - and the seven stars of the pillars drawing into a line.
 */
public final class SpaceScene {
    /** How far above the armory the pocket of space sits (above the build limit for most worlds). */
    public static final double HEIGHT = 300;
    public static final Vec3d EARTH = new Vec3d(0, -52, -10);
    public static final double EARTH_R = 40;
    public static final Vec3d MOON = new Vec3d(-28, 9, -46);
    public static final Vec3d GIANT = new Vec3d(30, 12, -50);
    public static final Vec3d SUN = new Vec3d(-0.62, 0.18, -0.76).normalize();
    /** Where the seven stars gather (direction from the camera's origin). */
    public static final Vec3d FATE = new Vec3d(0.18, 0.62, -0.76).normalize();
    private static final Vec3d LINE = new Vec3d(0.97, 0.1, 0.2).normalize();

    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final Identifier T_EARTH = DoaMod.id("textures/space/earth.png");
    private static final Identifier T_CLOUDS = DoaMod.id("textures/space/earth_clouds.png");
    private static final Identifier T_NIGHT = DoaMod.id("textures/space/earth_night.png");
    private static final Identifier T_MOON = DoaMod.id("textures/space/moon.png");
    private static final Identifier T_GIANT = DoaMod.id("textures/space/giant.png");
    private static final Identifier T_RING = DoaMod.id("textures/space/ring.png");
    private static final Identifier T_MILKY = DoaMod.id("textures/space/milkyway.png");
    private static final Identifier T_GLARE = DoaMod.id("textures/space/glare.png");
    private static final Identifier T_WHITE = DoaMod.id("textures/space/white.png");
    private static final Identifier T_STAR = DoaMod.id("textures/effect/add_star.png");

    private static final int STARS = 2600;
    private static final float[] STAR = new float[STARS * 7];

    static {
        Random r = new Random(77);
        for (int i = 0; i < STARS; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            float mag = (float) Math.pow(r.nextDouble(), 3.2);
            float temp = r.nextFloat();
            STAR[i * 7] = (float) (s * Math.cos(th));
            STAR[i * 7 + 1] = (float) u;
            STAR[i * 7 + 2] = (float) (s * Math.sin(th));
            STAR[i * 7 + 3] = 0.07f + mag * 0.32f;
            // blackbody-ish tint: warm red-orange to white to pale
            STAR[i * 7 + 4] = temp < 0.25f ? 1f : (temp < 0.8f ? 1f : 0.85f);
            STAR[i * 7 + 5] = temp < 0.25f ? 0.72f : (temp < 0.8f ? 0.96f : 0.9f);
            STAR[i * 7 + 6] = temp < 0.25f ? 0.5f : (temp < 0.8f ? 0.9f : 1f);
            if (mag < 0.02f) STAR[i * 7 + 3] = 0.05f;
        }
    }

    private static final float[][] FATE_SCATTER = new float[Pillars.COUNT][3];

    static {
        Random r = new Random(4);
        for (int i = 0; i < Pillars.COUNT; i++)
            for (int k = 0; k < 3; k++) FATE_SCATTER[i][k] = (float) ((r.nextDouble() - 0.5) * 30);
    }

    private SpaceScene() {
    }

    /** Position of fate star i (relative to the space origin). */
    public static Vec3d fateStar(int i, double a, double time) {
        float k = Rite.smooth((a - Abilities.FATE_ALIGN) / 40.0);
        Vec3d base = FATE.multiply(66);
        Vec3d scattered = base.add(FATE_SCATTER[i][0], FATE_SCATTER[i][1] * 0.7, FATE_SCATTER[i][2] * 0.4);
        Vec3d aligned = base.add(LINE.multiply((i - 3) * 6.0));
        Vec3d p = scattered.add(aligned.subtract(scattered).multiply(k));
        return p.add(0, Math.sin(time * 0.03 + i) * 0.2 * (1 - k), 0);
    }

    /**
     * @param s   the space origin relative to the matrix origin
     * @param cam the camera relative to the matrix origin
     */
    public static void render(MatrixStack ms, VertexConsumerProvider vcp, Vec3d s, Vec3d cam, double a, double time, float[] right, float[] up) {
        DoaMesh sphere = DoaModels.PLANET, ring = DoaModels.RING;
        if (sphere == null) return;
        Vector3f S = new Vector3f((float) s.x, (float) s.y, (float) s.z);
        Vector3f camv = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
        Quaternionf none = new Quaternionf();
        float sx = (float) SUN.x, sy = (float) SUN.y, sz = (float) SUN.z;

        // the void itself, then the galaxy painted on the inside of the sky
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(T_WHITE)), new Vector3f(camv), 92, none, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> set(o, 0, 0, 0, 1));
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEyes(T_MILKY)), new Vector3f(camv), 88, new Quaternionf().rotateY(0.6f), FULL, 0, true,
                (x, y, z, nx, ny, nz, u, v, o) -> set(o, 0.9f, 0.9f, 0.9f, 1));

        // stars
        VertexConsumer st = vcp.getBuffer(RenderLayer.getEyes(T_STAR));
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        for (int i = 0; i < STARS; i++) {
            float twinkle = 0.75f + 0.25f * MathHelper.sin((float) (time * 0.15 + i * 1.7));
            float size = STAR[i * 7 + 3];
            float b = Math.min(1, size * 3.2f) * twinkle;
            quad(st, m, en, camv.x + STAR[i * 7] * 80, camv.y + STAR[i * 7 + 1] * 80, camv.z + STAR[i * 7 + 2] * 80, size, right, up,
                    STAR[i * 7 + 4] * b, STAR[i * 7 + 5] * b, STAR[i * 7 + 6] * b);
        }

        // the sun
        VertexConsumer gl = vcp.getBuffer(RenderLayer.getEyes(T_GLARE));
        quad(gl, m, en, camv.x + sx * 74, camv.y + sy * 74, camv.z + sz * 74, 26, right, up, 1f, 0.97f, 0.9f);
        quad(gl, m, en, camv.x + sx * 74, camv.y + sy * 74, camv.z + sz * 74, 7, right, up, 1f, 1f, 1f);

        // the world below
        Vector3f ec = new Vector3f((float) EARTH.x, (float) EARTH.y, (float) EARTH.z).add(S);
        Quaternionf erot = new Quaternionf().rotateZ(0.41f).rotateY((float) (time * 0.0006 + 1.9));
        float er = (float) EARTH_R;
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(T_EARTH)), ec, er, erot, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float d = nx * sx + ny * sy + nz * sz;
                    float lit = MathHelper.clamp(d * 1.15f + 0.06f, 0.025f, 1f);
                    float dusk = Math.max(0, 1 - Math.abs(d) * 5) * 0.35f;
                    set(o, lit + dusk * 0.9f, lit * 0.97f + dusk * 0.45f, lit * 0.93f + dusk * 0.15f, 1);
                });
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEyes(T_NIGHT)), ec, er * 1.001f, erot, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float d = nx * sx + ny * sy + nz * sz;
                    float k = MathHelper.clamp(-d * 3f - 0.05f, 0, 1);
                    set(o, k, k, k, 1);
                });
        Quaternionf crot = new Quaternionf().rotateZ(0.41f).rotateY((float) (time * 0.0009 + 1.9));
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(T_CLOUDS)), ec, er * 1.012f, crot, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float d = nx * sx + ny * sy + nz * sz;
                    float lit = MathHelper.clamp(d * 1.2f + 0.08f, 0.02f, 1f);
                    float dusk = Math.max(0, 1 - Math.abs(d) * 5) * 0.4f;
                    set(o, lit + dusk, lit * 0.95f + dusk * 0.5f, lit * 0.92f + dusk * 0.2f, 0.95f);
                });
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEyes(T_WHITE)), ec, er * 1.035f, erot, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float vx = x - camv.x, vy = y - camv.y, vz = z - camv.z;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float f = 1 - Math.abs((nx * vx + ny * vy + nz * vz) / len);
                    float d = nx * sx + ny * sy + nz * sz;
                    float day = MathHelper.clamp((d + 0.25f) * 1.6f, 0, 1);
                    float k = f * f * f * (0.15f + 0.85f * day);
                    set(o, k * 0.42f, k * 0.66f, k * 1.0f, 1);
                });

        // its moon
        Vector3f mc = new Vector3f((float) MOON.x, (float) MOON.y, (float) MOON.z).add(S);
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(T_MOON)), mc, 4f, new Quaternionf().rotateY(2.2f), FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float lit = MathHelper.clamp((nx * sx + ny * sy + nz * sz) * 1.1f, 0.012f, 1f);
                    set(o, lit, lit, lit, 1);
                });

        // the ringed giant
        Vector3f gc = new Vector3f((float) GIANT.x, (float) GIANT.y, (float) GIANT.z).add(S);
        Quaternionf grot = new Quaternionf().rotateZ(0.38f).rotateX(0.22f);
        Quaternionf gspin = new Quaternionf(grot).rotateY((float) (time * 0.0015));
        sphere.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(T_GIANT)), gc, 10f, gspin, FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float d = nx * sx + ny * sy + nz * sz;
                    float lit = MathHelper.clamp(d * 1.1f + 0.04f, 0.015f, 1f);
                    set(o, lit, lit * 0.97f, lit * 0.92f, 1);
                });
        if (ring != null) {
            Vector3f gcc = new Vector3f(gc);
            ring.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(T_RING)), gc, 10f, grot, FULL, 0, false,
                    (x, y, z, nx, ny, nz, u, v, o) -> {
                        // the giant's shadow falls across the ring behind it
                        float rx = x - gcc.x, ry = y - gcc.y, rz = z - gcc.z;
                        float along = rx * sx + ry * sy + rz * sz;
                        float px = rx - sx * along, py = ry - sy * along, pz = rz - sz * along;
                        boolean shadow = along < 0 && px * px + py * py + pz * pz < 100f;
                        float lit = shadow ? 0.06f : 0.55f + 0.45f * Math.abs(nx * sx + ny * sy + nz * sz);
                        set(o, lit, lit * 0.96f, lit * 0.9f, 1);
                    });
        }

        // the seven stars of the pillars
        VertexConsumer fs = vcp.getBuffer(RenderLayer.getEyes(T_GLARE));
        float appear = Rite.smooth((a - Abilities.FATE_SPACE_IN - 30) / 30.0);
        float fire = Rite.smooth((a - Abilities.FATE_FIRE) / 8.0);
        Vec3d[] pos = new Vec3d[Pillars.COUNT];
        for (int i = 0; i < Pillars.COUNT; i++) {
            pos[i] = fateStar(i, a, time).add(s);
            float[] c = Pillars.COLOR[i];
            float pulse = (0.8f + 0.2f * MathHelper.sin((float) (time * 0.2 + i))) * appear * (1 + fire * 1.5f);
            quad(fs, m, en, (float) pos[i].x, (float) pos[i].y, (float) pos[i].z, 3.2f + fire * 2, right, up, c[0] * pulse, c[1] * pulse, c[2] * pulse);
            quad(fs, m, en, (float) pos[i].x, (float) pos[i].y, (float) pos[i].z, 1.1f, right, up, pulse, pulse, pulse);
        }
        // threads of fate drawn between them once they align
        float thread = Rite.smooth((a - Abilities.FATE_ALIGN - 34) / 18.0);
        if (thread > 0) {
            VertexConsumer th = vcp.getBuffer(RenderLayer.getEyes(T_WHITE));
            for (int i = 0; i < Pillars.COUNT - 1; i++) {
                float seg = MathHelper.clamp(thread * (Pillars.COUNT - 1) - i, 0, 1);
                if (seg <= 0) break;
                Vec3d b = pos[i].add(pos[i + 1].subtract(pos[i]).multiply(seg));
                float[] c = Pillars.colorAt(i * Math.PI * 2 / 7);
                float k = 0.7f + fire;
                ArmoryCoreRenderer.strip(th, m, en, pos[i], b, cam, 0.16 + fire * 0.25, c[0] * k, c[1] * k, c[2] * k);
            }
        }
        // the strike: the aligned stars fire down through the atmosphere
        if (a >= Abilities.FATE_FIRE) {
            Vec3d mid = pos[3];
            Vec3d hit = new Vec3d(ec.x, ec.y, ec.z).add(mid.subtract(new Vec3d(ec.x, ec.y, ec.z)).normalize().multiply(EARTH_R * 1.01));
            float k = Rite.smooth((a - Abilities.FATE_FIRE) / 6.0);
            Vec3d end = mid.add(hit.subtract(mid).multiply(Rite.easeOut((a - Abilities.FATE_FIRE) / 12.0)));
            VertexConsumer bm = vcp.getBuffer(RenderLayer.getEyes(T_WHITE));
            float[] v = Pillars.COLOR[4];
            ArmoryCoreRenderer.strip(bm, m, en, mid, end, cam, 1.6 * k, v[0] * k, v[1] * k, v[2] * k);
            ArmoryCoreRenderer.strip(bm, m, en, mid, end, cam, 0.5 * k, k, k, k);
            if (a >= Abilities.FATE_FIRE + 12) {
                float f = Rite.smooth((a - Abilities.FATE_FIRE - 12) / 10.0);
                quad(fs, m, en, (float) hit.x, (float) hit.y, (float) hit.z, 6 + f * 10, right, up, v[0] * 1.4f, v[1] * 1.4f, v[2] * 1.4f);
            }
        }
    }

    private static void set(float[] o, float r, float g, float b, float a) {
        o[0] = r;
        o[1] = g;
        o[2] = b;
        o[3] = a;
    }

    /** Camera-facing quad from precomputed right/up vectors. */
    static void quad(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, float x, float y, float z, float s, float[] r, float[] u,
                     float cr, float cg, float cb) {
        float rx = r[0] * s, ry = r[1] * s, rz = r[2] * s, ux = u[0] * s, uy = u[1] * s, uz = u[2] * s;
        ArmoryCoreRenderer.vtx(vc, m, en, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, cr, cg, cb, 1, FULL, 0, 1, 0);
        ArmoryCoreRenderer.vtx(vc, m, en, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, cr, cg, cb, 1, FULL, 0, 1, 0);
    }
}
