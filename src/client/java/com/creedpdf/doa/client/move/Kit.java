package com.creedpdf.doa.client.move;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.fx.Fx;
import com.creedpdf.doa.client.render.Bill;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/** Shared pieces for the seven moves: textures, camera moves, beams, rings, bursts, captions and fades. */
public final class Kit {
    public static final Identifier COLUMN = DoaMod.id("textures/effect/add_light_column.png");
    public static final Identifier TRAIL = DoaMod.id("textures/effect/add_brush_trail.png");
    public static final Identifier RING = DoaMod.id("textures/effect/add_ink_ring.png");
    public static final Identifier RING_INK = DoaMod.id("textures/effect/ink_ring.png");
    public static final Identifier HALO = DoaMod.id("textures/effect/add_halo.png");
    public static final Identifier SKIN = DoaMod.id("textures/effect/orb_skin.png");
    public static final Identifier EDGE = DoaMod.id("textures/effect/add_orb_edge.png");
    public static final Identifier STREAK = DoaMod.id("textures/scene/streak.png");
    public static final Identifier BEAM = DoaMod.id("textures/scene/beam.png");
    public static final Identifier RIFT_EDGE = DoaMod.id("textures/scene/rift_edge.png");
    public static final Identifier[] SEAL = new Identifier[Pillars.COUNT];
    public static final Identifier[] CLOUD = new Identifier[4];
    public static final Identifier[] STRATA = new Identifier[8];
    public static final Identifier[] STRATA_GLOW = new Identifier[8];

    static {
        for (int i = 0; i < Pillars.COUNT; i++) SEAL[i] = DoaMod.id("textures/effect/add_seal_" + Pillars.ID[i] + ".png");
        for (int i = 0; i < 4; i++) CLOUD[i] = DoaMod.id("textures/scene/cloud_" + i + ".png");
        for (int i = 0; i < 8; i++) {
            STRATA[i] = DoaMod.id("textures/scene/strata_" + i + ".png");
            STRATA_GLOW[i] = DoaMod.id("textures/scene/strata_" + i + (i >= 3 ? "_glow" : "") + ".png");
        }
    }

    private Kit() {
    }

    public static float s(double x) {
        return Rite.smooth(x);
    }

    /** 0 -> 1 over [a0, a0+d0], then 1 -> 0 over [a1, a1+d1]. */
    public static float window(double a, double a0, double d0, double a1, double d1) {
        return Rite.smooth((a - a0) / d0) * (1 - Rite.smooth((a - a1) / d1));
    }

    public static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return new Vec3d(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
    }

    // ------------------------------------------------------------------------------------------------ camera

    /** A slow push toward the relic as it awakens. */
    public static Vec3d[] relicShot(ArmoryCoreEntity core, int i, double a) {
        Vec3d r = core.relicPos(i);
        Vec3d in = new Vec3d(core.getX() - r.x, 0, core.getZ() - r.z).normalize();
        Vec3d side = new Vec3d(-in.z, 0, in.x);
        Vec3d pos = r.add(in.multiply(3.0 - a * 0.04)).add(side.multiply(1.1)).add(0, 0.5 + a * 0.01, 0);
        return new Vec3d[]{pos, r.add(0, 0.15, 0)};
    }

    public static Vec3d orbit(Vec3d center, double th, double radius, double h) {
        return center.add(Math.sin(th) * radius, h, -Math.cos(th) * radius);
    }

    public static float baseYaw(float playerYaw) {
        return (float) Math.toRadians(playerYaw) + MathHelper.PI;
    }

    /** Rising out of the dome straight up into the sky, accelerating. */
    public static Vec3d[] ascent(Vec3d c, double k) {
        double y = 1.8 + Math.pow(k, 2.4) * 250;
        Vec3d pos = c.add(0.6, y, 2.0);
        return new Vec3d[]{pos, pos.add(0.05, 10, -3.5)};
    }

    // ------------------------------------------------------------------------------------------------ VFX

    public static void column(MoveCtx c, Identifier tex, Vec3d base, double halfWidth, double y0, double y1, float r, float g, float b) {
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(tex));
        Bill.ribbon(vc, c.ms, base.x, base.y + y0, base.z, base.x, base.y + y1, base.z, c.cam.x, c.cam.y, c.cam.z, (float) halfWidth, r, g, b, 1, 1, 0);
    }

    /** A beam between two points (beam texture across). */
    public static void beam(MoveCtx c, Vec3d a, Vec3d b, double halfWidth, float r, float g, float bl) {
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(BEAM));
        Bill.ribbon(vc, c.ms, a.x, a.y, a.z, b.x, b.y, b.z, c.cam.x, c.cam.y, c.cam.z, (float) halfWidth, r, g, bl, 1, 0, 1);
    }

    public static void flatRing(MoveCtx c, Identifier tex, Vec3d at, double radius, float r, float g, float b, double spin) {
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEyes(tex));
        float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        Bill.plane(vc, c.ms, at.x, at.y, at.z, new float[]{cs, 0, sn}, new float[]{-sn, 0, cs}, (float) radius, r, g, b, 1);
    }

    public static void inkRing(MoveCtx c, Vec3d at, double radius, float alpha) {
        VertexConsumer vc = c.vcp.getBuffer(RenderLayer.getEntityTranslucent(RING_INK));
        Bill.plane(vc, c.ms, at.x, at.y, at.z, new float[]{1, 0, 0}, new float[]{0, 0, 1}, (float) radius, 0.02f, 0.015f, 0.02f, alpha);
    }

    /** A shockwave on the ground: ink ring under a ring of light. */
    public static void shock(MoveCtx c, Vec3d at, double radius, float k, float[] col) {
        if (k <= 0.01f) return;
        inkRing(c, at.add(0, 0.08, 0), radius * 0.94, k * 0.85f);
        flatRing(c, RING, at.add(0, 0.12, 0), radius, col[0] * k * 1.4f, col[1] * k * 1.4f, col[2] * k * 1.4f, 0);
    }

    public static void glow(MoveCtx c, Vec3d at, float size, float r, float g, float b) {
        Bill.sprite(c.vcp.getBuffer(RenderLayer.getEyes(HALO)), c.ms, at.x, at.y, at.z, size, 0, r, g, b);
    }

    public static void seal(MoveCtx c, int i, Vec3d at, double radius, float k, double spin) {
        float[] col = Pillars.COLOR[i];
        flatRing(c, SEAL[i], at.add(0, 0.06, 0), radius, col[0] * k, col[1] * k, col[2] * k, spin);
    }

    public static float[] bright(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    // ------------------------------------------------------------------------------------------------ particles

    private static final float[] INK = {0.02f, 0.015f, 0.02f};
    private static final float[] STONE = {0.16f, 0.15f, 0.15f};

    public static void burst(Vec3d p, float[] col, int n, Random rnd, double speed, boolean ink, boolean shards) {
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = speed * (0.3 + rnd.nextDouble());
            Fx.spawn(Fx.Kind.MOTE, p.x, p.y, p.z, Math.cos(a) * s, rnd.nextDouble() * speed, Math.sin(a) * s, bright(col, rnd.nextFloat() * 0.4f), 1f,
                    0.14f + rnd.nextFloat() * 0.22f, 20 + rnd.nextInt(30), 0.004f, 0.92f, true, 0, -0.4f, 0);
            if (ink && k % 2 == 0)
                Fx.spawn(Fx.Kind.INK, p.x, p.y, p.z, Math.cos(a) * s * 0.6, 0.03, Math.sin(a) * s * 0.6, INK, 0.8f, 0.7f + rnd.nextFloat() * 0.8f,
                        30 + rnd.nextInt(25), -0.002f, 0.9f, false, 0.04f, 1.4f, 0);
            if (shards && k % 2 == 1)
                Fx.spawn(Fx.Kind.SHARD, p.x, p.y + 0.3, p.z, Math.cos(a) * s, 0.3 + rnd.nextDouble() * 0.5, Math.sin(a) * s, STONE, 1f,
                        0.14f + rnd.nextFloat() * 0.12f, 30 + rnd.nextInt(20), 0.06f, 0.95f, false, (rnd.nextFloat() - 0.5f) * 0.6f, 0, 0);
        }
    }

    public static void mote(double x, double y, double z, double vx, double vy, double vz, float[] col, float size, int life) {
        Fx.spawn(Fx.Kind.MOTE, x, y, z, vx, vy, vz, col, 1f, size, life, 0, 0.97f, true, 0, -0.4f, 0);
    }

    public static void ink(double x, double y, double z, double vx, double vy, double vz, float size, int life) {
        Fx.spawn(Fx.Kind.INK, x, y, z, vx, vy, vz, INK, 0.85f, size, life, 0, 0.96f, false, 0.04f, 1.0f, 0);
    }

    public static boolean mine(ArmoryCoreEntity core) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && core.getOwnerNetId() == mc.player.getId();
    }

    public static void privateSound(ArmoryCoreEntity core, SoundEvent s, float pitch) {
        if (mine(core)) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(s, pitch, 1f));
    }

    // ------------------------------------------------------------------------------------------------ HUD

    public static void fade(DrawContext ctx, int w, int h, float k, int rgb) {
        if (k > 0.005f) ctx.fill(0, 0, w, h, ((int) (MathHelper.clamp(k, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF));
    }

    /** The move's title card: kanji, name and line in DOA's lettering. */
    public static void title(DrawContext ctx, int i, String name, String line, float a, int w, int h) {
        float al = window(a, 6, 10, 70, 14);
        if (al <= 0.01f) return;
        int alpha = (int) (al * 255) << 24;
        int col = Pillars.rgb(i);
        float y = h * 0.66f;
        DoaFont.hud(ctx, Pillars.KANJI[i], w / 2f, y - 44, 38, 0, true, alpha | col, ((int) (al * 120) << 24) | col);
        DoaFont.hud(ctx, name, w / 2f, y, 15, 0.32f, true, alpha | col, ((int) (al * 150) << 24));
        DoaFont.hud(ctx, line, w / 2f, y + 22, 8, 0.08f, true, alpha | 0xD8D0C0, ((int) (al * 120) << 24));
        float lw = DoaFont.width(name, 0.32f) * 15 * 0.55f * al;
        ctx.fill((int) (w / 2f - lw), (int) (y + 18), (int) (w / 2f + lw), (int) (y + 19), ((int) (al * 150) << 24) | col);
    }

    /** A smaller caption (scene labels). */
    public static void caption(DrawContext ctx, String big, String small, int col, float al, int w, int h, float y) {
        if (al <= 0.01f) return;
        int alpha = (int) (al * 255) << 24;
        DoaFont.hud(ctx, big, w / 2f, y, 13, 0.3f, true, alpha | col, ((int) (al * 150) << 24));
        if (small != null) DoaFont.hud(ctx, small, w / 2f, y + 17, 7.5f, 0.12f, true, alpha | 0xD8D0C0, ((int) (al * 110) << 24));
    }

    /** A translucent shell around the camera that tints the whole sky and the distant world (night, eclipse). */
    public static void skyTint(MoveCtx c, float radius, float r, float g, float b, float alpha) {
        skyTint(c, radius, r, g, b, alpha, null, 2);
    }

    /** As above, leaving a clear window around {@code hole} (cosine of its half-angle) where a sky body is drawn. */
    public static void skyTint(MoveCtx c, float radius, float r, float g, float b, float alpha, Vec3d hole, double cosCone) {
        com.creedpdf.doa.client.model.DoaMesh s = com.creedpdf.doa.client.model.DoaModels.PLANET;
        if (s == null || alpha <= 0.01f) return;
        org.joml.Vector3f cv = c.cosmos.camv();
        s.render(c.ms, c.vcp.getBuffer(RenderLayer.getEntityTranslucent(com.creedpdf.doa.client.render.Cosmos.WHITE)), cv, radius,
                new org.joml.Quaternionf(), Bill.FULL, 0, false, (x, y, z, nx, ny, nz, u, v, o) -> {
                    float h = (y - cv.y) / radius;
                    o[0] = r;
                    o[1] = g;
                    o[2] = b;
                    float win = 1;
                    if (hole != null) {
                        double dx = x - cv.x, dy = y - cv.y, dz = z - cv.z;
                        double cos = (dx * hole.x + dy * hole.y + dz * hole.z) / radius;
                        win = (float) MathHelper.clamp((cosCone - cos) / (1 - cosCone) * 4, 0, 1);
                    }
                    o[3] = alpha * (h > -0.1f ? 1f : 0.6f) * win;
                });
    }

    /** A direction from elevation/azimuth (radians; azimuth 0 = north, clockwise). */
    public static Vec3d dir(double azimuth, double elevation) {
        return new Vec3d(Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation), -Math.cos(azimuth) * Math.cos(elevation));
    }

    public static Matrix4f m(MatrixStack ms) {
        return ms.peek().getPositionMatrix();
    }
}
