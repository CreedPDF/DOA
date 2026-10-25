package com.creedpdf.doa.client.render;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.client.model.DoaMesh;
import com.creedpdf.doa.client.model.DoaModels;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Building blocks of DOA's space: an opaque void, the Milky Way and deep-field skies, thousands of stars, the sun,
 * the baked-lit planets (their lighting matches SUN and each body's orientation, see tools/textures/space_textures.py),
 * a ringed giant, spiral galaxies. Positions are relative to the current matrix origin.
 */
public final class Cosmos {
    public static final Vector3f SUN = new Vector3f(-0.62f, 0.18f, -0.76f).normalize();
    public static final Quaternionf EARTH_Q = new Quaternionf().rotateX(-1.2f).rotateZ(0.41f).rotateY(1.9f);
    public static final Quaternionf MOON_Q = new Quaternionf().rotateY(2.2f);
    public static final Quaternionf GIANT_Q = new Quaternionf().rotateZ(0.38f).rotateX(0.22f);
    public static final Quaternionf RED_Q = new Quaternionf().rotateZ(-0.2f).rotateY(0.7f);

    public static final Identifier EARTH = tex("earth"), CLOUDS = tex("earth_clouds"), MOON = tex("moon"), GIANT = tex("giant"), RING = tex("ring"),
            RED = tex("red"), CRACKS = tex("red_cracks"), MILKY = tex("milkyway"), DEEP = tex("deepfield"), GALAXY = tex("galaxy"),
            GLARE = tex("glare"), CORONA = tex("corona"), WHITE = tex("white"), PANORAMA = tex("panorama");
    public static final Identifier STAR = DoaMod.id("textures/effect/add_star.png");

    private static Identifier tex(String n) {
        return DoaMod.id("textures/space/" + n + ".png");
    }

    private static final int STARS = 3000;
    private static final float[] STAR_DATA = new float[STARS * 7];

    static {
        Random r = new Random(77);
        for (int i = 0; i < STARS; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            float mag = (float) Math.pow(r.nextDouble(), 3.4);
            float temp = r.nextFloat();
            STAR_DATA[i * 7] = (float) (s * Math.cos(th));
            STAR_DATA[i * 7 + 1] = (float) u;
            STAR_DATA[i * 7 + 2] = (float) (s * Math.sin(th));
            STAR_DATA[i * 7 + 3] = 0.06f + mag * 0.34f;
            STAR_DATA[i * 7 + 4] = temp < 0.2f ? 1f : (temp < 0.8f ? 1f : 0.82f);
            STAR_DATA[i * 7 + 5] = temp < 0.2f ? 0.7f : (temp < 0.8f ? 0.96f : 0.88f);
            STAR_DATA[i * 7 + 6] = temp < 0.2f ? 0.48f : (temp < 0.8f ? 0.9f : 1f);
        }
    }

    public static final class Ctx {
        public MatrixStack ms;
        public VertexConsumerProvider vcp;
        public Vec3d cam;
        public double time;

        public Ctx(MatrixStack ms, VertexConsumerProvider vcp, Vec3d cam, double time) {
            this.ms = ms;
            this.vcp = vcp;
            this.cam = cam;
            this.time = time;
        }

        public Vector3f camv() {
            return new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
        }
    }

    private Cosmos() {
    }

    private static DoaMesh sphere() {
        return DoaModels.PLANET;
    }

    private static void set(float[] o, float r, float g, float b, float a) {
        o[0] = r;
        o[1] = g;
        o[2] = b;
        o[3] = a;
    }

    // ------------------------------------------------------------------------------------------------ skies

    /** An opaque sphere around the camera that hides the world (optionally with a vertical colour gradient). */
    public static void backdrop(Ctx c, float radius, float[] zenith, float[] horizon, float[] nadir) {
        DoaMesh s = sphere();
        if (s == null) return;
        Vector3f cv = c.camv();
        s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE)), cv, radius, new Quaternionf(), Bill.FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float h = (y - cv.y) / radius;
                    float[] a = h >= 0 ? horizon : horizon, b = h >= 0 ? zenith : nadir;
                    float k = (float) Math.pow(Math.abs(h), 0.6);
                    set(o, a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, 1);
                });
    }

    public static void blackout(Ctx c, float radius) {
        float[] k = {0, 0, 0};
        backdrop(c, radius, k, k, k);
    }

    /** A sky texture painted on the inside of a sphere around the camera (additive). */
    public static void skybox(Ctx c, Identifier tex, float radius, float k, Quaternionf rot) {
        DoaMesh s = sphere();
        if (s == null || k <= 0.01f) return;
        s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(tex)), c.camv(), radius, rot, Bill.FULL, 0, true,
                (x, y, z, nx, ny, nz, u, v, o) -> set(o, k, k, k, 1));
    }

    public static void stars(Ctx c, float radius, float k) {
        if (k <= 0.01f) return;
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(STAR));
        Vector3f cv = c.camv();
        float scale = radius / 80f;
        for (int i = 0; i < STARS; i++) {
            float tw = 0.75f + 0.25f * MathHelper.sin((float) (c.time * 0.15 + i * 1.7));
            float size = STAR_DATA[i * 7 + 3] * scale;
            float b = Math.min(1, STAR_DATA[i * 7 + 3] * 3.2f) * tw * k;
            Bill.sprite(vc, c.ms, cv.x + STAR_DATA[i * 7] * radius, cv.y + STAR_DATA[i * 7 + 1] * radius, cv.z + STAR_DATA[i * 7 + 2] * radius,
                    size, 0, STAR_DATA[i * 7 + 4] * b, STAR_DATA[i * 7 + 5] * b, STAR_DATA[i * 7 + 6] * b);
        }
    }

    /** The sun's glare at a point (additive). */
    public static void sun(Ctx c, double x, double y, double z, float size, float k) {
        VertexConsumer gl = c.vcp.getBuffer(RenderLayer.getEyes(GLARE));
        Bill.sprite(gl, c.ms, x, y, z, size, 0, k, k * 0.97f, k * 0.9f);
        Bill.sprite(gl, c.ms, x, y, z, size * 0.3f, 0.4f, k, k, k);
    }

    // ------------------------------------------------------------------------------------------------ bodies

    public enum Body {EARTH, MOON, GIANT, RED}

    public static Quaternionf baseRot(Body b) {
        return switch (b) {
            case EARTH -> EARTH_Q;
            case MOON -> MOON_Q;
            case GIANT -> GIANT_Q;
            case RED -> RED_Q;
        };
    }

    public static Identifier texture(Body b) {
        return switch (b) {
            case EARTH -> EARTH;
            case MOON -> MOON;
            case GIANT -> GIANT;
            case RED -> RED;
        };
    }

    /** A rotation that turns body b so its sunlit face looks along {@code toViewer} (for full-moon shots). */
    public static Quaternionf facing(Body b, Vector3f toViewer) {
        Vector3f lit = baseRot(b).conjugate(new Quaternionf()).transform(new Vector3f(SUN));
        return new Quaternionf().rotationTo(lit, new Vector3f(toViewer).normalize());
    }

    /**
     * Draw a planet with its baked lighting. {@code rot} is the full orientation (baseRot for the scene's sun);
     * k dims it (1 = as baked). Earth gets its clouds and atmosphere.
     */
    public static void planet(Ctx c, Body b, Vector3f center, float radius, Quaternionf rot, float k) {
        DoaMesh s = sphere();
        if (s == null) return;
        s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(texture(b))), center, radius, rot, Bill.FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> set(o, k, k, k, 1));
        if (b == Body.EARTH) {
            s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityTranslucent(CLOUDS)), center, radius * 1.008f, rot, Bill.FULL, 0, false,
                    (x, y, z, nx, ny, nz, u, v, o) -> set(o, k, k, k, 1));
            atmosphere(c, center, radius * 1.03f, rot, new float[]{0.42f, 0.66f, 1f}, k);
        } else if (b == Body.RED) {
            atmosphere(c, center, radius * 1.02f, rot, new float[]{0.9f, 0.5f, 0.3f}, k * 0.45f);
        } else if (b == Body.GIANT) {
            atmosphere(c, center, radius * 1.01f, rot, new float[]{0.95f, 0.8f, 0.55f}, k * 0.35f);
        }
    }

    /** A thin glowing limb: brightest at grazing angles on the day side. */
    public static void atmosphere(Ctx c, Vector3f center, float radius, Quaternionf rot, float[] col, float k) {
        DoaMesh s = sphere();
        if (s == null || k <= 0.01f) return;
        Vector3f cv = c.camv();
        float sx = SUN.x, sy = SUN.y, sz = SUN.z;
        s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(WHITE)), center, radius, rot, Bill.FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float vx = x - cv.x, vy = y - cv.y, vz = z - cv.z;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float f = 1 - Math.abs((nx * vx + ny * vy + nz * vz) / len);
                    float d = nx * sx + ny * sy + nz * sz;
                    float day = MathHelper.clamp((d + 0.2f) * 1.6f, 0, 1);
                    float a = f * f * f * (0.08f + 0.92f * day) * k;
                    float dusk = Math.max(0, 1 - Math.abs(d) * 4) * 0.6f;
                    set(o, a * (col[0] + dusk), a * (col[1] + dusk * 0.3f), a * col[2], 1);
                });
    }

    /** The giant's rings, with the planet's shadow falling across them. */
    public static void rings(Ctx c, Vector3f center, float radius, Quaternionf rot, float k) {
        DoaMesh ring = DoaModels.RING;
        if (ring == null) return;
        Vector3f gc = new Vector3f(center);
        float r2 = radius * radius;
        ring.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityTranslucent(RING)), center, radius, rot, Bill.FULL, 0, false,
                (x, y, z, nx, ny, nz, u, v, o) -> {
                    float rx = x - gc.x, ry = y - gc.y, rz = z - gc.z;
                    float along = rx * SUN.x + ry * SUN.y + rz * SUN.z;
                    float px = rx - SUN.x * along, py = ry - SUN.y * along, pz = rz - SUN.z * along;
                    boolean shadow = along < 0 && px * px + py * py + pz * pz < r2;
                    float lit = (shadow ? 0.05f : 0.55f + 0.45f * Math.abs(nx * SUN.x + ny * SUN.y + nz * SUN.z)) * k;
                    set(o, lit, lit * 0.96f, lit * 0.9f, 1);
                });
    }

    /** A spiral galaxy: a disc in the plane spanned by e1, e2 (additive), with a glowing core. */
    public static void galaxy(Ctx c, double x, double y, double z, float[] e1, float[] e2, float radius, float k) {
        if (k <= 0.01f) return;
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(GALAXY));
        Bill.plane(vc, c.ms, x, y, z, e1, e2, radius, k, k, k, 1);
        Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(GLARE)), c.ms, x, y, z, radius * 0.25f, 0, k * 0.5f, k * 0.45f, k * 0.35f);
    }
}
