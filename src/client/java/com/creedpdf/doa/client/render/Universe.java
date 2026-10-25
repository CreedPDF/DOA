package com.creedpdf.doa.client.render;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.client.move.Kit;
import com.creedpdf.doa.client.move.MoveCtx;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A true-scale(ish) universe that the camera can zoom through continuously, from just above the Earth's clouds to
 * the cosmic web. Units are Earth radii (ER). Every frame everything is placed relative to the viewer and scaled so
 * the current subject fills the view; far things are compressed toward the sky without changing their apparent size,
 * so one continuous camera move passes through every scale with no cuts.
 *
 * Planets are real spheres lit by the actual direction of the sun (terminators, phases, night-side city lights,
 * clouds, layered atmospheres); the sun is a glowing photosphere; the Milky Way is a 3D cloud of tens of thousands of
 * stars, dust and nebulae in spiral arms; beyond it hang thousands of galaxies strung along filaments.
 */
public final class Universe {
    // ---- scale
    public static final double AU = 23_455;
    public static final double LY = 63_241 * AU;
    public static final double GAL_R = 50_000 * LY;

    // ---- layout
    public static final Vec3d SUN_DIR = new Vec3d(-0.62, 0.18, -0.76).normalize();
    public static final Vec3d SUN = SUN_DIR.multiply(AU);
    public static final double SUN_R = 109;
    /** The ecliptic: Earth (the origin) lies on it, one AU from the sun. */
    private static final Vec3d ORB_E1 = SUN_DIR.multiply(-1);
    private static final Vec3d ORB_N = new Vec3d(0, 1, 0).subtract(SUN_DIR.multiply(SUN_DIR.y)).normalize();
    private static final Vec3d ORB_E2 = ORB_N.crossProduct(ORB_E1).normalize();

    private static Vec3d onEcliptic(double ang, double R, double tilt) {
        return SUN.add(ORB_E1.multiply(Math.cos(ang) * R)).add(ORB_E2.multiply(Math.sin(ang) * R)).add(ORB_N.multiply(Math.sin(ang) * R * tilt));
    }
    public static final double MOON_DIST = 60;
    public static final double MOON_R = 0.273;
    /** Galactic frame: the sun sits 26,000 light years from the centre, in the disc. */
    public static final Vec3d GAL_N = new Vec3d(0.3, 0.9, 0.3).normalize();
    private static final Vec3d GAL_E1 = GAL_N.crossProduct(new Vec3d(1, 0, 0)).normalize();
    private static final Vec3d GAL_E2 = GAL_N.crossProduct(GAL_E1).normalize();
    public static final Vec3d GALAXY = SUN.add(GAL_E1.multiply(26_000 * LY));

    public static final Identifier SUN_TEX = DoaMod.id("textures/space/sun.png");
    public static final Identifier NIGHT = DoaMod.id("textures/space/earth_night.png");
    public static final Identifier SOFT = DoaMod.id("textures/effect/soft.png");

    /** Planets of the solar system: orbit (AU), radius (ER), texture body, tint, phase. */
    private static final double[][] PLANETS = {
            {0.39, 0.38, 1, 0.75, 0.70, 0.65, 1.1}, {0.72, 0.95, 2, 1.00, 0.92, 0.74, 2.6}, {1.52, 0.53, 3, 1, 1, 1, 4.2},
            {5.2, 11.2, 2, 1, 1, 1, 0.7}, {9.5, 9.4, 2, 0.98, 0.88, 0.66, 5.3}, {19.2, 4.0, 2, 0.62, 0.86, 0.86, 3.3}, {30.1, 3.9, 2, 0.4, 0.55, 0.95, 1.9}};

    private Universe() {
    }

    // ------------------------------------------------------------------------------------------------ view

    /** A viewpoint in universe space and the scale that makes the subject fill the view. */
    public static final class View {
        public final Vec3d cam;
        public final double scale;
        public final double time;

        /** Where the viewer's eye is in render space (everything is placed around it). */
        public final Vec3d eye;

        public View(Vec3d cam, Vec3d focus, double time, Vec3d eye) {
            this.cam = cam;
            this.eye = eye;
            double d = Math.max(1e-6, cam.distanceTo(focus));
            this.scale = 32 / d;
            this.time = time;
        }

        /** Universe point to camera-relative render space (compressed beyond 60 blocks); also returns the size factor. */
        public double[] place(Vec3d p) {
            double x = (p.x - cam.x) * scale, y = (p.y - cam.y) * scale, z = (p.z - cam.z) * scale;
            double d = Math.sqrt(x * x + y * y + z * z);
            double k = 1;
            if (d > 55) {
                double dc = 55 + 30 * (1 - Math.exp(-(d - 55) / 30));
                k = dc / d;
            }
            return new double[]{eye.x + x * k, eye.y + y * k, eye.z + z * k, k, d};
        }
    }

    // ------------------------------------------------------------------------------------------------ the whole thing

    public static void render(MoveCtx c, View v) {
        MatrixStack ms = c.ms;
        Vec3d cam = c.cam;
        double dSun = v.cam.distanceTo(SUN), dGal = v.cam.distanceTo(GALAXY);
        Cosmos.blackout(c.cosmos, 94);
        // the sky from inside the galaxy, giving way to the deep field once outside it
        float inside = (float) (1 - Kit.s((Math.log10(dGal / GAL_R) + 0.5) / 0.9));
        float deep = Kit.s((Math.log10(dGal / GAL_R) - 0.1) / 1.2);
        Cosmos.skybox(c.cosmos, Cosmos.MILKY, 90, inside * 0.85f, new Quaternionf().rotateY(0.6f));
        Cosmos.skybox(c.cosmos, Cosmos.DEEP, 89, deep * 0.9f, new Quaternionf());
        float stars = (float) (1 - Kit.s((Math.log10(dSun) - 9.5) / 2.0));
        Cosmos.stars(c.cosmos, 86, stars * Math.max(0.15f, inside));
        localStars(c, v, dSun);
        galaxy(c, v, dGal);
        web(c, v, dGal);
        sun(c, v);
        planets(c, v);
        earthAndMoon(c, v);
    }

    // ------------------------------------------------------------------------------------------------ bodies

    public static Vec3d moonPos(double time) {
        double a = time * 0.0004 + 2.2;
        return new Vec3d(Math.cos(a) * MOON_DIST, Math.sin(a) * 4, Math.sin(a) * MOON_DIST);
    }

    public static Quaternionf earthRot(double time) {
        return new Quaternionf().rotateZ(0.41f).rotateY((float) (time * 0.0006 + 1.9));
    }

    public static Vec3d planetPos(int i, double time) {
        double[] p = PLANETS[i];
        double a = p[6] + time * 0.0006 / Math.pow(p[0], 1.5);
        return onEcliptic(a, p[0] * AU, 0.03);
    }

    private static void earthAndMoon(MoveCtx c, View v) {
        Vec3d e = Vec3d.ZERO;
        double[] pe = v.place(e);
        float re = (float) (1 * v.scale * pe[3]);
        if (re > 0.004) {
            Vec3d l = SUN.subtract(e).normalize();
            earth(c.cosmos, (float) pe[0], (float) pe[1], (float) pe[2], re, earthRot(v.time), l, v.time);
        } else dot(c, pe, 0.35f, 0.55f, 0.8f, 1);
        Vec3d m = moonPos(v.time);
        double[] pm = v.place(m);
        float rm = (float) (MOON_R * v.scale * pm[3]);
        if (rm > 0.004) {
            body(c.cosmos, Cosmos.MOON, (float) pm[0], (float) pm[1], (float) pm[2], rm, Cosmos.MOON_Q, SUN.subtract(m).normalize(), new float[]{1, 1, 1}, 0.85f,
                    null, 0);
        } else if (pm[4] < 2000) dot(c, pm, 0.6f, 0.6f, 0.58f, 0.6f);
    }

    private static void planets(MoveCtx c, View v) {
        for (int i = 0; i < PLANETS.length; i++) {
            double[] pd = PLANETS[i];
            Vec3d p = planetPos(i, v.time);
            double[] pp = v.place(p);
            float r = (float) (pd[1] * v.scale * pp[3]);
            Identifier tex = pd[2] == 1 ? Cosmos.MOON : pd[2] == 3 ? Cosmos.RED : Cosmos.GIANT;
            float[] tint = {(float) pd[3], (float) pd[4], (float) pd[5]};
            Vec3d l = SUN.subtract(p).normalize();
            if (r > 0.006) {
                Quaternionf q = new Quaternionf().rotateZ(0.2f * i).rotateY((float) (v.time * 0.002 * (i + 1)));
                body(c.cosmos, tex, (float) pp[0], (float) pp[1], (float) pp[2], r, q, l, tint, 1f, pd[2] == 2 ? tint : null, pd[2] == 2 ? 0.35f : 0.15f);
                if (i == 4) Cosmos.rings(c.cosmos, new Vector3f((float) pp[0], (float) pp[1], (float) pp[2]), r, new Quaternionf().rotateZ(0.47f).rotateX(0.3f), 1);
            } else if (pp[4] < 1e6) {
                dot(c, pp, tint[0], tint[1], tint[2], (float) Math.min(1, 0.4 + pd[1] * 0.05));
            }
        }
    }

    /** A planet lit by its sun: surface, optional haze shell. */
    public static void body(Cosmos.Ctx c, Identifier tex, float x, float y, float z, float r, Quaternionf rot, Vec3d light, float[] tint, float flatten,
                            float[] haze, float hazeK) {
        SphereMesh sm = SphereMesh.forSize(r);
        float lx = (float) light.x, ly = (float) light.y, lz = (float) light.z;
        sm.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)), x, y, z, r, rot, 0, false, Bill.FULL, (nx, ny, nz, px, py, pz, o) -> {
            float d = nx * lx + ny * ly + nz * lz;
            float lit = (float) Math.pow(MathHelper.clamp(d * 1.05f + 0.03f, 0, 1), flatten) + 0.008f;
            o[0] = tint[0] * lit;
            o[1] = tint[1] * lit;
            o[2] = tint[2] * lit;
            o[3] = 1;
        });
        if (haze != null && hazeK > 0) atmosphere(c, x, y, z, r * 1.025f, light, haze, hazeK, 3f);
    }

    /** One additive shell, brightest at the limb on the day side, reddened at the terminator. */
    public static void atmosphere(Cosmos.Ctx c, float x, float y, float z, float r, Vec3d light, float[] col, float k, float power) {
        Vec3d cam = c.cam;
        float cx = (float) cam.x, cy = (float) cam.y, cz = (float) cam.z;
        float lx = (float) light.x, ly = (float) light.y, lz = (float) light.z;
        SphereMesh.forSize(r).render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.WHITE)), x, y, z, r, new Quaternionf(), 0, false, Bill.FULL,
                (nx, ny, nz, px, py, pz, o) -> {
                    float vx = px - cx, vy = py - cy, vz = pz - cz;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float f = 1 - Math.abs((nx * vx + ny * vy + nz * vz) / len);
                    float d = nx * lx + ny * ly + nz * lz;
                    float day = MathHelper.clamp((d + 0.18f) * 1.7f, 0, 1);
                    float a = (float) Math.pow(f, power) * day * k;
                    float dusk = Math.max(0, 1 - Math.abs(d + 0.02f) * 5f);
                    o[0] = a * (col[0] + dusk * 0.9f);
                    o[1] = a * (col[1] + dusk * 0.25f);
                    o[2] = a * (col[2] * (1 - dusk * 0.6f));
                    o[3] = 1;
                });
    }

    /** The living world: surface, night lights, clouds, glint and three layers of atmosphere. */
    /** The moon's shadow on the world during an eclipse: centre direction (null for none) and angular radius. */
    public static Vec3d umbra;
    public static float umbraR = 0.07f;

    private static float shadow(float nx, float ny, float nz) {
        if (umbra == null) return 1;
        double cs = nx * umbra.x + ny * umbra.y + nz * umbra.z;
        double ang = Math.acos(MathHelper.clamp(cs, -1, 1));
        double core = ang / umbraR;
        if (core < 1) return 0.03f;
        double pen = (core - 1) / 3.5;
        return (float) (0.03 + 0.97 * Math.min(1, pen * pen * (3 - 2 * Math.min(1, pen))));
    }

    public static void earth(Cosmos.Ctx c, float x, float y, float z, float r, Quaternionf rot, Vec3d light, double time) {
        SphereMesh sm = SphereMesh.forSize(r);
        float lx = (float) light.x, ly = (float) light.y, lz = (float) light.z;
        sm.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmos.EARTH)), x, y, z, r, rot, 0, false, Bill.FULL,
                (nx, ny, nz, px, py, pz, o) -> {
                    float d = nx * lx + ny * ly + nz * lz;
                    float lit = MathHelper.clamp(d * 1.1f + 0.04f, 0, 1);
                    lit = lit * lit * (3 - 2 * lit) * 0.85f + lit * 0.15f + 0.01f;
                    float dusk = Math.max(0, 1 - Math.abs(d) * 6) * 0.3f;
                    float sh = shadow(nx, ny, nz);
                    o[0] = (lit + dusk * 0.5f) * sh;
                    o[1] = (lit + dusk * 0.18f) * sh;
                    o[2] = lit * 0.97f * sh;
                    o[3] = 1;
                });
        sm.render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(NIGHT)), x, y, z, r * 1.0005f, rot, 0, false, Bill.FULL, (nx, ny, nz, px, py, pz, o) -> {
            float d = nx * lx + ny * ly + nz * lz;
            float k = Math.max(MathHelper.clamp(-d * 4f - 0.1f, 0, 1), (1 - shadow(nx, ny, nz)) * 0.9f) * 1.2f;
            o[0] = k;
            o[1] = k;
            o[2] = k;
            o[3] = 1;
        });
        Quaternionf crot = new Quaternionf(rot).rotateY((float) (time * 0.00025));
        sm.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityTranslucent(Cosmos.CLOUDS)), x, y, z, r * 1.007f, crot, 0, false, Bill.FULL,
                (nx, ny, nz, px, py, pz, o) -> {
                    float d = nx * lx + ny * ly + nz * lz;
                    float lit = MathHelper.clamp(d * 1.2f + 0.05f, 0.015f, 1);
                    float dusk = Math.max(0, 1 - Math.abs(d) * 5) * 0.5f;
                    float sh = shadow(nx, ny, nz);
                    o[0] = (lit + dusk) * sh;
                    o[1] = (lit + dusk * 0.4f) * sh;
                    o[2] = (lit * 0.98f + dusk * 0.1f) * sh;
                    o[3] = 1;
                });
        // the sun glinting off the oceans
        Vec3d cam = c.cam;
        Vec3d toCam = new Vec3d(cam.x - x, cam.y - y, cam.z - z).normalize();
        Vec3d h = light.add(toCam).normalize();
        if (h.dotProduct(light) > 0.2) {
            Vec3d g = new Vec3d(x, y, z).add(h.multiply(r * 1.002));
            float gk = (float) MathHelper.clamp(h.dotProduct(toCam) * 1.2, 0, 1);
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GLARE)), c.ms, g.x, g.y, g.z, r * 0.14f, 0, gk * 0.7f, gk * 0.62f, gk * 0.5f);
        }
        atmosphere(c, x, y, z, r * 1.012f, light, new float[]{0.55f, 0.75f, 1.0f}, 1.1f, 6f);
        atmosphere(c, x, y, z, r * 1.03f, light, new float[]{0.32f, 0.55f, 1.0f}, 0.9f, 4f);
        atmosphere(c, x, y, z, r * 1.07f, light, new float[]{0.2f, 0.35f, 0.8f}, 0.35f, 2.5f);
    }

    /** The sun: a limb-darkened, boiling photosphere inside layers of glow. */
    private static void sun(MoveCtx c, View v) {
        double[] ps = v.place(SUN);
        float r = (float) (SUN_R * v.scale * ps[3]);
        float x = (float) ps[0], y = (float) ps[1], z = (float) ps[2];
        sunAt(c.cosmos, x, y, z, Math.max(r, 0.05f), v.time);
    }

    public static void sunAt(Cosmos.Ctx c, float x, float y, float z, float r, double time) {
        Vec3d cam = c.cam;
        float cx = (float) cam.x, cy = (float) cam.y, cz = (float) cam.z;
        if (r > 0.08f) {
            SphereMesh.forSize(r).render(c.ms, c.vcp.getBuffer(RenderLayer.getEyes(SUN_TEX)), x, y, z, r, new Quaternionf().rotateY((float) (time * 0.001)),
                    0, false, Bill.FULL, (nx, ny, nz, px, py, pz, o) -> {
                        float vx = cx - px, vy = cy - py, vz = cz - pz;
                        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                        float mu = Math.max(0, (nx * vx + ny * vy + nz * vz) / len);
                        float limb = (float) (0.35 + 0.65 * Math.pow(mu, 0.45));
                        o[0] = limb * 1.0f;
                        o[1] = limb * 0.92f;
                        o[2] = limb * 0.78f;
                        o[3] = 1;
                    });
        }
        VertexConsumer gl = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GLARE));
        float g = Math.max(r, 0.6f);
        Bill.sprite(gl, c.ms, x, y, z, g * 9, (float) (time * 0.0007), 0.9f, 0.75f, 0.5f);
        Bill.sprite(gl, c.ms, x, y, z, g * 3.2f, 0, 1, 0.95f, 0.85f);
        VertexConsumer halo = c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO));
        Bill.sprite(halo, c.ms, x, y, z, g * 2.4f, 0, 0.8f, 0.55f, 0.25f);
        Bill.sprite(halo, c.ms, x, y, z, g * 6f, 0, 0.35f, 0.2f, 0.08f);
    }

    private static void dot(MoveCtx c, double[] p, float r, float g, float b, float k) {
        Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.STAR)), c.ms, p[0], p[1], p[2], 0.35f, 0, r * k, g * k, b * k);
    }

    /** Faint orbit lines of the planets (Earth's in the pillar's colour). */
    public static void orbits(MoveCtx c, View v, float k, float[] hl) {
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.WHITE));
        for (int i = -1; i < PLANETS.length; i++) {
            double R = (i < 0 ? 1 : PLANETS[i][0]) * AU;
            boolean earth = i < 0;
            double[] prev = null;
            for (int s = 0; s <= 128; s++) {
                double a = s / 128.0 * Math.PI * 2;
                double[] q = v.place(onEcliptic(a, R, earth ? 0 : 0.03));
                if (prev != null) {
                    float b = (earth ? 0.5f : 0.12f) * k;
                    float w = (float) Math.max(0.02, 0.0025 * Math.sqrt(q[4] * q[3]));
                    Bill.ribbon(vc, c.ms, prev[0], prev[1], prev[2], q[0], q[1], q[2], c.cam.x, c.cam.y, c.cam.z, w, b * (earth ? hl[0] * 2 : 0.8f),
                            b * (earth ? hl[1] * 2 : 0.85f), b * (earth ? hl[2] * 2 : 1f), 1, 0, 1);
                }
                prev = q;
            }
        }
    }

    /** Earth's place on its orbit (always at the universe origin). */
    public static double[] earthPlaced(View v) {
        return v.place(Vec3d.ZERO);
    }

    // ------------------------------------------------------------------------------------------------ stars, galaxy, web

    private static final int LOCAL = 2500;
    private static final float[] LOC = new float[LOCAL * 5];
    private static final int GSTARS = 30000, GDUST = 6000, GNEB = 500;
    private static final float[] GS = new float[GSTARS * 7];
    private static final float[] GD = new float[GDUST * 4];
    private static final float[] GN = new float[GNEB * 7];
    private static final int WEB = 2600;
    private static final float[] WB = new float[WEB * 8];

    static {
        Random r = new Random(2024);
        for (int i = 0; i < LOCAL; i++) {
            Vec3d d = new Vec3d(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            double dist = Math.pow(r.nextDouble(), 0.5) * 1500;
            LOC[i * 5] = (float) (d.x * dist);
            LOC[i * 5 + 1] = (float) (d.y * dist);
            LOC[i * 5 + 2] = (float) (d.z * dist);
            LOC[i * 5 + 3] = (float) Math.pow(r.nextDouble(), 2.5);
            LOC[i * 5 + 4] = r.nextFloat();
        }
        for (int i = 0; i < GSTARS; i++) {
            boolean bulge = r.nextDouble() < 0.16;
            double x, y, z, rad;
            float cr, cg, cb;
            if (bulge) {
                rad = Math.abs(r.nextGaussian()) * 0.11;
                double th = r.nextDouble() * Math.PI * 2;
                x = Math.cos(th) * rad;
                z = Math.sin(th) * rad * 0.7;
                y = r.nextGaussian() * 0.06 * (1 - rad * 3);
                cr = 1f;
                cg = 0.83f;
                cb = 0.58f;
            } else {
                rad = Math.min(1.15, 0.05 + (-Math.log(1 - r.nextDouble() * 0.999)) * 0.3);
                int arm = r.nextInt(4);
                double armAng = arm * Math.PI / 2 + (arm % 2 == 1 ? 0.25 : 0);
                double th = armAng + Math.log(rad / 0.05) * 1.85 + r.nextGaussian() * (0.22 + 0.25 * (arm % 2));
                x = Math.cos(th) * rad;
                z = Math.sin(th) * rad;
                y = r.nextGaussian() * 0.012 * (1.2 - rad * 0.6);
                float warm = (float) Math.max(0, 0.45 - rad);
                cr = 0.78f + warm * 0.5f;
                cg = 0.82f + warm * 0.1f;
                cb = 1f - warm * 0.6f;
                if (r.nextDouble() < 0.07) {
                    cr = 1f;
                    cg = 0.42f;
                    cb = 0.68f;
                }
            }
            GS[i * 7] = (float) x;
            GS[i * 7 + 1] = (float) y;
            GS[i * 7 + 2] = (float) z;
            GS[i * 7 + 3] = cr;
            GS[i * 7 + 4] = cg;
            GS[i * 7 + 5] = cb;
            GS[i * 7 + 6] = (float) (0.4 + Math.pow(r.nextDouble(), 3) * 2.2) * (bulge ? 1.3f : 1f);
        }
        for (int i = 0; i < GDUST; i++) {
            double rad = 0.08 + (-Math.log(1 - r.nextDouble() * 0.99)) * 0.28;
            int arm = r.nextInt(4);
            double th = arm * Math.PI / 2 + (arm % 2 == 1 ? 0.25 : 0) + Math.log(rad / 0.05) * 1.85 - 0.18 + r.nextGaussian() * 0.12;
            GD[i * 4] = (float) (Math.cos(th) * rad);
            GD[i * 4 + 1] = (float) (r.nextGaussian() * 0.006);
            GD[i * 4 + 2] = (float) (Math.sin(th) * rad);
            GD[i * 4 + 3] = (float) (0.6 + r.nextDouble());
        }
        for (int i = 0; i < GNEB; i++) {
            double rad = 0.05 + r.nextDouble() * 0.9;
            int arm = r.nextInt(2) * 2;
            double th = arm * Math.PI / 2 + Math.log(rad / 0.05) * 1.85 + r.nextGaussian() * 0.15;
            GN[i * 7] = (float) (Math.cos(th) * rad);
            GN[i * 7 + 1] = (float) (r.nextGaussian() * 0.01);
            GN[i * 7 + 2] = (float) (Math.sin(th) * rad);
            boolean pink = r.nextDouble() < 0.4;
            GN[i * 7 + 3] = pink ? 0.9f : 0.5f;
            GN[i * 7 + 4] = pink ? 0.3f : 0.55f;
            GN[i * 7 + 5] = pink ? 0.55f : 0.9f;
            GN[i * 7 + 6] = (float) (2 + r.nextDouble() * 4);
        }
        Vec3d[][] fil = new Vec3d[22][2];
        for (int f = 0; f < 22; f++) {
            fil[f][0] = new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(3e8);
            fil[f][1] = fil[f][0].add(new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(2.5e8));
        }
        for (int i = 0; i < WEB; i++) {
            Vec3d[] f = fil[i % 22];
            Vec3d p = Kit.lerp(f[0], f[1], r.nextDouble()).add(new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(1.2e7));
            if (i < 1) p = new Vec3d(2.5e6, 0.6e6, 1.0e6);   // Andromeda, close by
            WB[i * 8] = (float) p.x;
            WB[i * 8 + 1] = (float) p.y;
            WB[i * 8 + 2] = (float) p.z;
            WB[i * 8 + 3] = (float) (i < 1 ? 1.4 : 0.3 + Math.pow(r.nextDouble(), 3) * 1.5);
            Vector3f n = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
            WB[i * 8 + 4] = n.x;
            WB[i * 8 + 5] = n.y;
            WB[i * 8 + 6] = n.z;
            WB[i * 8 + 7] = 0.4f + r.nextFloat() * 0.6f;
        }
    }

    private static void localStars(MoveCtx c, View v, double dSun) {
        float k = Kit.s((Math.log10(dSun) - 8.5) / 1.5) * (1 - Kit.s((Math.log10(dSun) - 13.5) / 1.0));
        if (k <= 0.01f) return;
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.STAR));
        for (int i = 0; i < LOCAL; i++) {
            Vec3d p = SUN.add(LOC[i * 5] * LY, LOC[i * 5 + 1] * LY, LOC[i * 5 + 2] * LY);
            double[] q = v.place(p);
            float b = (0.3f + LOC[i * 5 + 3] * 1.2f) * k;
            float t = LOC[i * 5 + 4];
            Bill.sprite(vc, c.ms, q[0], q[1], q[2], 0.12f + LOC[i * 5 + 3] * 0.4f, 0, b * (t < 0.2 ? 1 : 0.95f), b * (t < 0.2 ? 0.75f : 0.95f),
                    b * (t < 0.2 ? 0.55f : 1f));
        }
    }

    private static Vec3d galPoint(float x, float y, float z) {
        return GALAXY.add(GAL_E1.multiply(x * GAL_R)).add(GAL_N.multiply(y * GAL_R)).add(GAL_E2.multiply(z * GAL_R));
    }

    /** The Milky Way from outside: stars, dust lanes and glowing nebulae in four arms around a bulge. */
    private static void galaxy(MoveCtx c, View v, double dGal) {
        float k = Kit.s((Math.log10(dGal / GAL_R) + 0.15) / 0.6);
        if (k <= 0.01f) return;
        double sz = GAL_R * v.scale;
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.STAR));
        for (int i = 0; i < GSTARS; i++) {
            double[] q = v.place(galPoint(GS[i * 7], GS[i * 7 + 1], GS[i * 7 + 2]));
            float s = (float) Math.max(0.035, sz * 0.0045 * GS[i * 7 + 6] * q[3]);
            float b = k * 0.9f;
            Bill.sprite(vc, c.ms, q[0], q[1], q[2], s, 0, GS[i * 7 + 3] * b, GS[i * 7 + 4] * b, GS[i * 7 + 5] * b);
        }
        VertexConsumer nb = c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO));
        for (int i = 0; i < GNEB; i++) {
            double[] q = v.place(galPoint(GN[i * 7], GN[i * 7 + 1], GN[i * 7 + 2]));
            float s = (float) (sz * 0.012 * GN[i * 7 + 6] * q[3]);
            float b = k * 0.18f;
            Bill.sprite(nb, c.ms, q[0], q[1], q[2], s, 0, GN[i * 7 + 3] * b, GN[i * 7 + 4] * b, GN[i * 7 + 5] * b);
        }
        double[] q0 = v.place(GALAXY);
        Bill.sprite(nb, c.ms, q0[0], q0[1], q0[2], (float) (sz * 0.25 * q0[3]), 0, 0.5f * k, 0.38f * k, 0.22f * k);
        Bill.sprite(nb, c.ms, q0[0], q0[1], q0[2], (float) (sz * 0.08 * q0[3]), 0, 0.9f * k, 0.75f * k, 0.5f * k);
        // the disc's diffuse light, face-on
        float[] e1 = {(float) GAL_E1.x, (float) GAL_E1.y, (float) GAL_E1.z}, e2 = {(float) GAL_E2.x, (float) GAL_E2.y, (float) GAL_E2.z};
        Bill.plane(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GALAXY)), c.ms, q0[0], q0[1], q0[2], e1, e2, (float) (sz * 1.05 * q0[3]), k * 0.45f, k * 0.45f,
                k * 0.45f, 1);
        VertexConsumer dust = c.vcp.getBuffer(RenderLayer.getEntityTranslucent(SOFT));
        for (int i = 0; i < GDUST; i++) {
            double[] q = v.place(galPoint(GD[i * 4], GD[i * 4 + 1], GD[i * 4 + 2]));
            float s = (float) (sz * 0.018 * GD[i * 4 + 3] * q[3]);
            Bill.quad(dust, c.ms, q[0], q[1], q[2], s, s, 0, 0, 0, 1, 1, 0.03f, 0.02f, 0.02f, 0.35f * k);
        }
    }

    /** Beyond: galaxies strung along the filaments of the cosmic web. */
    private static void web(MoveCtx c, View v, double dGal) {
        float k = Kit.s((Math.log10(dGal / GAL_R) - 0.6) / 1.2);
        if (k <= 0.01f) return;
        for (int i = 0; i < WEB; i++) {
            Vec3d p = GALAXY.add(WB[i * 8] * LY, WB[i * 8 + 1] * LY, WB[i * 8 + 2] * LY);
            double[] q = v.place(p);
            float s = (float) Math.max(0.05, GAL_R * WB[i * 8 + 3] * v.scale * q[3]);
            Vector3f n = new Vector3f(WB[i * 8 + 4], WB[i * 8 + 5], WB[i * 8 + 6]);
            Vector3f e1 = new Vector3f(n).cross(0, 1, 0);
            if (e1.lengthSquared() < 1e-4) e1.set(1, 0, 0);
            e1.normalize();
            Vector3f e2 = new Vector3f(n).cross(e1).normalize();
            float b = k * WB[i * 8 + 7];
            if (s > 0.25f)
                Bill.plane(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GALAXY)), c.ms, q[0], q[1], q[2], new float[]{e1.x, e1.y, e1.z}, new float[]{e2.x, e2.y, e2.z},
                        s, b, b, b, 1);
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO)), c.ms, q[0], q[1], q[2], Math.max(0.12f, s * 0.5f), 0, 0.55f * b, 0.48f * b, 0.4f * b);
        }
    }

    // ------------------------------------------------------------------------------------------------ the seven stars

    /** The seven stars of the pillars, scattered then aligned, around a centre point in render space. */
    public static void fateStars(MoveCtx c, Vec3d center, Vec3d along, double align, double spread, double time) {
        Random r = new Random(7);
        Vec3d[] pos = new Vec3d[7];
        Vec3d up = along.crossProduct(new Vec3d(0, 1, 0)).normalize();
        for (int i = 0; i < 7; i++) {
            Vec3d sc = center.add(r.nextGaussian() * spread, r.nextGaussian() * spread * 0.6, r.nextGaussian() * spread * 0.4);
            Vec3d al = center.add(along.multiply((i - 3) * spread * 0.45));
            pos[i] = Kit.lerp(sc, al, align);
            float[] col = Pillars.COLOR[i];
            float pk = 0.85f + 0.15f * (float) Math.sin(time * 0.2 + i);
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Cosmos.GLARE)), c.ms, pos[i].x, pos[i].y, pos[i].z, (float) (spread * 0.35), (float) (time * 0.01),
                    col[0] * pk, col[1] * pk, col[2] * pk);
            Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(Kit.HALO)), c.ms, pos[i].x, pos[i].y, pos[i].z, (float) (spread * 0.2), 0, pk, pk, pk);
        }
        if (align > 0.6) {
            float t = (float) MathHelper.clamp((align - 0.6) / 0.4, 0, 1);
            for (int i = 0; i < 6; i++) {
                float[] col = Pillars.colorAt(i * Math.PI * 2 / 7);
                Kit.beam(c, pos[i], Kit.lerp(pos[i], pos[i + 1], t), spread * 0.03, col[0] * 1.4f, col[1] * 1.4f, col[2] * 1.4f);
            }
        }
    }

}
