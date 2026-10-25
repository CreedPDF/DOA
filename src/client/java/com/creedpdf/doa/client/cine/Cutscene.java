package com.creedpdf.doa.client.cine;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The summoning cutscene, seen only by the caller: an opening look to the heavens, a low dramatic shot for every
 * pillar as it plunges (with a rugged, hand-inked impact frame in that pillar's colour as it strikes), an overhead
 * shot while the dark sphere corrupts and closes, then a fall back into the caller's own eyes.
 * Camera shake is felt by everyone nearby.
 */
public final class Cutscene {
    private static final Identifier[] IMPACT = {DoaMod.id("textures/gui/impact_0.png"), DoaMod.id("textures/gui/impact_1.png"),
            DoaMod.id("textures/gui/impact_2.png")};

    public static ArmoryCoreEntity active;
    private static int clientTicks;
    private static float shake;
    private static float impactStart = -100;
    private static int impactPillar;

    /** The shot computed this frame (for the camera mixin). */
    public record Shot(Vec3d pos, float yaw, float pitch, float blend, boolean thirdPerson) {
    }

    private Cutscene() {
    }

    // ------------------------------------------------------------------------------------------------ state

    public static void clientTick(MinecraftClient mc) {
        clientTicks++;
        shake *= 0.86f;
        if (shake < 0.01f) shake = 0;
        active = null;
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmoryCoreEntity core && !core.isRemoved() && core.getOwnerNetId() == mc.player.getId()
                    && !core.isReleasing() && core.getRiteTicks() < Rite.CUT_END) {
                active = core;
                break;
            }
        }
    }

    public static boolean isActive() {
        return active != null && !active.isRemoved();
    }

    public static void addShake(ArmoryCoreEntity core, float strength, float range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(core.getPos());
        float s = strength * (float) MathHelper.clamp(1 - d / (range * 4), 0, 1);
        shake = Math.max(shake, s);
    }

    public static void onImpact(ArmoryCoreEntity core, int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(core.pillarBase(i));
        shake = Math.max(shake, 2.2f * (float) MathHelper.clamp(1 - d / 72, 0, 1));
        if (core == active || core.getOwnerNetId() == mc.player.getId()) {
            impactStart = clientTicks;
            impactPillar = i;
        }
    }

    private static float now(float tickDelta) {
        return clientTicks + tickDelta;
    }

    // ------------------------------------------------------------------------------------------------ camera

    public static Shot shot(float tickDelta, Vec3d playerEye, float playerYaw, float playerPitch) {
        ArmoryCoreEntity core = active;
        if (core == null) return null;
        float t = core.getRiteTicks() + tickDelta;
        Vec3d c = core.getPos();
        Vec3d pos;
        Vec3d look;
        if (t < Rite.spawn(0) - 2) {
            float k = Rite.smooth(t / (Rite.spawn(0) - 2));
            double yr = Math.toRadians(playerYaw);
            Vec3d back = new Vec3d(Math.sin(yr), 0, -Math.cos(yr));
            Vec3d p0 = playerEye.add(back.multiply(3.6)).add(0, 0.4, 0);
            Vec3d p1 = c.add(back.multiply(5.5)).add(0, 0.7, 0);
            pos = lerp(p0, p1, k);
            look = lerp(playerEye, c.add(back.multiply(-6)).add(0, 45, 0), Rite.smooth(t / 30.0));
        } else if (t < Rite.SEAL) {
            int i = MathHelper.clamp((int) Math.floor((t - Rite.spawn(0) + 2) / Rite.SLOT), 0, Pillars.COUNT - 1);
            float u = t - Rite.spawn(i);
            Vec3d b = core.pillarBase(i);
            Vec3d d = new Vec3d(b.x - c.x, 0, b.z - c.z).normalize();
            Vec3d side = new Vec3d(-d.z, 0, d.x).multiply(i % 2 == 0 ? 1 : -1);
            pos = c.add(d.multiply(Rite.PILLAR_RADIUS - 6.8 - u * 0.02)).add(side.multiply(4.4 + u * 0.035)).add(0, 0.9 + u * 0.02, 0);
            pos = new Vec3d(pos.x, Math.max(pos.y, b.y + 0.9 + u * 0.02), pos.z);
            double fallTop = b.y + Math.min(Rite.fallHeight(i, t) + 4.5, 32);
            float settle = Rite.smooth((u - Rite.FALL + 2) / 8.0);
            look = new Vec3d(b.x, fallTop + (b.y + 4.8 - fallTop) * settle, b.z);
        } else {
            float u = t - Rite.SEAL;
            double th = u * 0.0065 + Math.PI * 0.85;
            double hr = 19 - Math.min(u, 200) * 0.02;
            pos = c.add(Math.sin(th) * hr, 25 - Math.min(u, 200) * 0.015, -Math.cos(th) * hr);
            look = c.add(0, 2.5, 0);
        }
        Vec3d dir = look.subtract(pos);
        float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180 / Math.PI);
        float blend = Rite.smooth((t - (Rite.CUT_END - 22)) / 22.0);
        if (blend > 0) {
            pos = lerp(pos, playerEye, blend);
            yaw = yaw + MathHelper.wrapDegrees(playerYaw - yaw) * blend;
            pitch = pitch + (playerPitch - pitch) * blend;
        }
        return new Shot(pos, yaw, pitch, blend, blend < 0.92f);
    }

    /** Shake offsets: {dx, dy, dz, dyaw, dpitch}. */
    public static float[] shake(float tickDelta) {
        if (shake <= 0) return null;
        float n = now(tickDelta);
        float a = shake;
        return new float[]{
                (MathHelper.sin(n * 2.31f) + MathHelper.sin(n * 5.17f) * 0.5f) * a * 0.06f,
                (MathHelper.sin(n * 2.73f + 1) + MathHelper.sin(n * 6.1f) * 0.5f) * a * 0.08f,
                (MathHelper.sin(n * 2.05f + 2) + MathHelper.sin(n * 4.7f) * 0.5f) * a * 0.06f,
                (MathHelper.sin(n * 3.3f + 3)) * a * 0.9f,
                (MathHelper.sin(n * 3.9f + 4)) * a * 0.9f};
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return new Vec3d(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
    }

    // ------------------------------------------------------------------------------------------------ overlay

    /** Draws the cutscene overlay. Returns true if the vanilla HUD should be hidden. */
    public static boolean renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean act = isActive();
        float f = now(tickDelta) - impactStart;
        boolean impact = f >= 0 && f < 7;
        if (!act && !impact) return false;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = mc.textRenderer;
        if (act) {
            ArmoryCoreEntity core = active;
            float t = core.getRiteTicks() + tickDelta;
            float bars = Rite.smooth(t / 20.0) * (1 - Rite.smooth((t - (Rite.CUT_END - 20)) / 20.0));
            int bh = (int) (h * 0.11f * bars);
            if (bh > 0) {
                ctx.fill(0, 0, w, bh, 0xFF000000);
                ctx.fill(0, h - bh, w, h, 0xFF000000);
            }
            caption(ctx, tr, t, w, h, bh);
        }
        if (impact) impactFrame(ctx, f, w, h);
        return act;
    }

    private static void caption(DrawContext ctx, TextRenderer tr, float t, int w, int h, int bh) {
        String big = null, small = null;
        int color = 0xE8E0D0;
        float alpha = 0;
        if (t < Rite.spawn(0)) {
            big = "召";
            small = "T H E   O R B I T A L   A R M O R Y   A N S W E R S";
            alpha = Rite.smooth((t - 6) / 10.0) * (1 - Rite.smooth((t - Rite.spawn(0) + 8) / 8.0));
        } else if (t < Rite.SEAL) {
            int i = MathHelper.clamp((int) Math.floor((t - Rite.spawn(0) + 2) / Rite.SLOT), 0, Pillars.COUNT - 1);
            float u = t - Rite.land(i) - 6;
            if (u > 0) {
                big = Pillars.KANJI[i];
                small = spaced(Pillars.NAME[i]);
                color = Pillars.rgb(i);
                alpha = Rite.smooth(u / 6.0) * (1 - Rite.smooth((t - (Rite.spawn(i) + Rite.SLOT - 8)) / 6.0));
            }
        } else if (t > Rite.RISE_START + 20 && t < Rite.CUT_END - 18) {
            big = "封";
            small = "T H E   S E A L   C L O S E S";
            alpha = Rite.smooth((t - Rite.RISE_START - 20) / 12.0) * (1 - Rite.smooth((t - (Rite.CUT_END - 30)) / 10.0));
        }
        if (big == null || alpha <= 0.02f) return;
        int a = (int) (alpha * 255) << 24;
        var m = ctx.getMatrices();
        m.push();
        float s = 3f;
        int bw = tr.getWidth(big);
        m.translate(w / 2f - bw * s / 2f, h - bh - 58, 0);
        m.scale(s, s, 1);
        ctx.drawText(tr, Text.literal(big), 0, 0, a | color, false);
        m.pop();
        int sw = tr.getWidth(small);
        ctx.drawText(tr, Text.literal(small), w / 2 - sw / 2, h - bh - 18, a | color, false);
        int lw = (int) (sw * 0.55f * alpha);
        ctx.fill(w / 2 - lw, h - bh - 24, w / 2 + lw, h - bh - 23, ((int) (alpha * 150) << 24) | color);
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            if (k > 0) b.append(' ');
            b.append(s.charAt(k));
        }
        return b.toString();
    }

    private static void impactFrame(DrawContext ctx, float f, int w, int h) {
        float[] c = Pillars.COLOR[impactPillar];
        int base = impactPillar % 3;
        int frame;
        float r = c[0], g = c[1], b = c[2], a = 1;
        boolean black = false;
        if (f < 1) {
            frame = base;
        } else if (f < 2) {
            frame = (base + 1) % 3;
            r = r + (1 - r) * 0.55f;
            g = g + (1 - g) * 0.55f;
            b = b + (1 - b) * 0.55f;
            black = true;
        } else if (f < 3) {
            frame = (base + 2) % 3;
        } else {
            frame = base;
            float k = 1 - (f - 3) / 4f;
            a = k * k;
        }
        int jx = (int) ((MathHelper.sin(f * 17.3f + impactPillar) * 0.5f) * 8);
        int jy = (int) ((MathHelper.sin(f * 11.1f + impactPillar * 2) * 0.5f) * 6);
        if (black) ctx.fill(0, 0, w, h, 0xFF000000);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        ctx.setShaderColor(r, g, b, a);
        ctx.drawTexture(IMPACT[frame], -10 + jx, -8 + jy, w + 20, h + 16, 0, 0, 640, 360, 640, 360);
        ctx.setShaderColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
