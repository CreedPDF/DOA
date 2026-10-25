package com.creedpdf.doa.client.cine;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.render.AbilityView;
import com.creedpdf.doa.client.render.SpaceScene;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The caller's cutscenes: the summoning (an opening look to the heavens, a low shot for every pillar as it plunges,
 * an overhead shot while the sphere closes) and one for each relic's ability. Hand-inked impact frames in the
 * pillar's colour punctuate every strike; camera shake is felt by everyone nearby.
 */
public final class Cutscene {
    private static final int FRAMES = 4;
    private static final Identifier[][] IMPACT = new Identifier[Pillars.COUNT][FRAMES];

    static {
        for (int i = 0; i < Pillars.COUNT; i++)
            for (int k = 0; k < FRAMES; k++) IMPACT[i][k] = DoaMod.id("textures/gui/impact/" + Pillars.ID[i] + "_" + k + ".png");
    }

    public static ArmoryCoreEntity active;
    private static boolean abilityMode;
    private static int clientTicks;
    private static float shake;
    private static float impactStart = -100;
    private static int impactPillar;

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
        abilityMode = false;
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ArmoryCoreEntity core) || core.isRemoved() || core.getOwnerNetId() != mc.player.getId() || core.isReleasing())
                continue;
            if (core.getRiteTicks() < Rite.CUT_END) {
                active = core;
                break;
            }
            int ab = core.getAbility();
            if (ab >= 0 && core.getAbilityTicks() < Abilities.LENGTH[ab]) {
                active = core;
                abilityMode = true;
                break;
            }
        }
    }

    public static boolean isActive() {
        return active != null && !active.isRemoved();
    }

    public static boolean isAbilityCutscene(ArmoryCoreEntity core) {
        return isActive() && abilityMode && active == core;
    }

    public static void addShake(ArmoryCoreEntity core, float strength, float range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(core.getPos());
        float s = strength * (float) MathHelper.clamp(1 - d / (range * 4), 0, 1);
        if (core == active) s = Math.max(s, strength);
        shake = Math.max(shake, s);
    }

    /** A pillar lands: shake for everyone near it, an impact frame for the caller. */
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

    /** An ability's climax: an impact frame in the pillar's colour for the caller, shake for all. */
    public static void impact(ArmoryCoreEntity core, int i, float strength) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        addShake(core, strength, 24);
        if (core.getOwnerNetId() == mc.player.getId()) {
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
        Vec3d[] pl = abilityMode ? abilityShot(core, tickDelta, playerYaw) : riteShot(core, tickDelta, playerEye, playerYaw);
        if (pl == null) return null;
        Vec3d pos = pl[0], look = pl[1];
        double blendStart, blendLen;
        double t;
        if (abilityMode) {
            int ab = core.getAbility();
            t = core.getAbilityTicks() + tickDelta;
            blendLen = 14;
            blendStart = Abilities.LENGTH[ab] - blendLen;
        } else {
            t = core.getRiteTicks() + tickDelta;
            blendLen = 22;
            blendStart = Rite.CUT_END - blendLen;
        }
        Vec3d dir = look.subtract(pos);
        float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180 / Math.PI);
        float blend = Rite.smooth((t - blendStart) / blendLen);
        if (blend > 0) {
            pos = lerp(pos, playerEye, blend);
            yaw = yaw + MathHelper.wrapDegrees(playerYaw - yaw) * blend;
            pitch = pitch + (playerPitch - pitch) * blend;
        }
        return new Shot(pos, yaw, pitch, blend, blend < 0.92f);
    }

    private static Vec3d[] riteShot(ArmoryCoreEntity core, float tickDelta, Vec3d playerEye, float playerYaw) {
        float t = core.getRiteTicks() + tickDelta;
        Vec3d c = core.getPos();
        Vec3d pos, look;
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
        return new Vec3d[]{pos, look};
    }

    private static Vec3d[] relicShot(ArmoryCoreEntity core, int i, double a) {
        Vec3d r = core.relicPos(i);
        Vec3d in = new Vec3d(core.getX() - r.x, 0, core.getZ() - r.z).normalize();
        Vec3d side = new Vec3d(-in.z, 0, in.x);
        Vec3d pos = r.add(in.multiply(2.4 - a * 0.04)).add(side.multiply(0.9)).add(0, 0.4 + a * 0.01, 0);
        return new Vec3d[]{pos, r.add(0, 0.1, 0)};
    }

    private static Vec3d orbit(Vec3d center, double th, double radius, double h) {
        return center.add(Math.sin(th) * radius, h, -Math.cos(th) * radius);
    }

    private static Vec3d[] abilityShot(ArmoryCoreEntity core, float td, float playerYaw) {
        int ab = core.getAbility();
        double a = core.getAbilityTicks() + td;
        Vec3d c = core.getPos();
        double R = Rite.ORB_RADIUS;
        double base = Math.toRadians(playerYaw) + Math.PI;
        switch (ab) {
            case Abilities.DEATH -> {
                if (a < 18) return relicShot(core, ab, a);
                return new Vec3d[]{orbit(c, base + a * 0.004, 30, 34), c.add(0, 2, 0)};
            }
            case Abilities.REDEMPTION -> {
                if (a < 14) return relicShot(core, ab, a);
                double th = base + a * 0.02;
                return new Vec3d[]{orbit(c, th, 5.5, 1.2), c.add(0, 1.5 + Rite.smooth((a - 14) / 40.0) * 14, 0)};
            }
            case Abilities.WRATH -> {
                if (a < 14) return relicShot(core, ab, a);
                return new Vec3d[]{orbit(c, base + a * 0.003, 44, 46), c.add(0, 4, 0)};
            }
            case Abilities.SOUL -> {
                if (a < 14) return relicShot(core, ab, a);
                if (a < 86) return new Vec3d[]{orbit(c, base + 1 + a * 0.006, 31, 13), c.add(0, 9, 0)};
                return new Vec3d[]{c.add(2.6, 1.9, 2.6), c.add(0, 1.4, 0)};
            }
            case Abilities.FATE -> {
                if (a < Abilities.FATE_SPACE_IN) {
                    double y = 1.8 + Math.pow(a / Abilities.FATE_SPACE_IN, 2.6) * 240;
                    Vec3d pos = c.add(0.6, y, 2.0);
                    return new Vec3d[]{pos, pos.add(0.05, 10, -4)};
                }
                if (a < Abilities.FATE_SPACE_OUT) {
                    Vec3d s = c.add(0, SpaceScene.HEIGHT, 0);
                    Vec3d limb = s.add(8, -22, -40);
                    Vec3d stars = s.add(SpaceScene.FATE.multiply(66));
                    if (a < Abilities.FATE_ALIGN) return new Vec3d[]{s.add(0, 0, -(a - 30) * 0.03), limb};
                    if (a < Abilities.FATE_FIRE) {
                        float k = Rite.smooth((a - Abilities.FATE_ALIGN) / 30.0);
                        return new Vec3d[]{s.add(0, 0, -2), lerp(limb, stars, k)};
                    }
                    float k2 = Rite.smooth((a - Abilities.FATE_FIRE) / 20.0);
                    Vec3d earth = s.add(SpaceScene.EARTH);
                    return new Vec3d[]{s.add(0, 2, -2 + 8 * k2), lerp(stars, lerp(stars, earth, 0.55), k2)};
                }
                Vec3d p = AbilityView.point(core);
                Vec3d d = p.subtract(c).multiply(1, 0, 1);
                double th = Math.atan2(d.x, -d.z) + 0.7;
                return new Vec3d[]{orbit(p, th + (a - Abilities.FATE_SPACE_OUT) * 0.004, 30, 21), p.add(0, 5, 0)};
            }
            case Abilities.SILENCE -> {
                if (a < 14) return relicShot(core, ab, a);
                return new Vec3d[]{orbit(c, base + 0.5 + a * 0.004, R + 11, 2.0), c.add(0, 6, 0)};
            }
            case Abilities.VOID -> {
                if (a < 10) return relicShot(core, ab, a);
                Vec3d p = AbilityView.point(core);
                Vec3d d = p.subtract(c);
                Vec3d mid = c.add(d.multiply(0.42));
                Vec3d side = new Vec3d(-d.z, 0, d.x).normalize();
                return new Vec3d[]{mid.add(side.multiply(17 - (a - 10) * 0.035)).add(0, 9, 0), p};
            }
            default -> {
                return null;
            }
        }
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
        boolean impact = f >= 0 && f < 9;
        if (!act && !impact) return false;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = mc.textRenderer;
        if (act) {
            ArmoryCoreEntity core = active;
            float t, len;
            if (abilityMode) {
                t = core.getAbilityTicks() + tickDelta;
                len = Abilities.LENGTH[core.getAbility()];
            } else {
                t = core.getRiteTicks() + tickDelta;
                len = Rite.CUT_END;
            }
            float bars = Rite.smooth(t / 14.0) * (1 - Rite.smooth((t - (len - 18)) / 18.0));
            int bh = (int) (h * 0.11f * bars);
            if (abilityMode) abilityOverlay(ctx, core, t, w, h);
            if (bh > 0) {
                ctx.fill(0, 0, w, bh, 0xFF000000);
                ctx.fill(0, h - bh, w, h, 0xFF000000);
            }
            if (abilityMode) abilityCaption(ctx, tr, core.getAbility(), t, w, h, bh);
            else caption(ctx, tr, t, w, h, bh);
        }
        if (impact) impactFrame(ctx, f, w, h);
        return act;
    }

    private static void abilityOverlay(DrawContext ctx, ArmoryCoreEntity core, float a, int w, int h) {
        int ab = core.getAbility();
        if (ab == Abilities.FATE) {
            float in = Rite.smooth((a - (Abilities.FATE_SPACE_IN - 6)) / 6.0) * (1 - Rite.smooth((a - Abilities.FATE_SPACE_IN) / 8.0));
            float out = Rite.smooth((a - (Abilities.FATE_SPACE_OUT - 5)) / 5.0) * (1 - Rite.smooth((a - Abilities.FATE_SPACE_OUT) / 8.0));
            float k = Math.max(in, out);
            if (k > 0.01f) ctx.fill(0, 0, w, h, ((int) (k * 255) << 24) | 0xFFF8EC);
        }
        if (ab == Abilities.SILENCE) {
            float k = Rite.smooth((a - Abilities.SILENCE_TOLL - 2) / 10.0) * (1 - Rite.smooth((a - (Abilities.LENGTH[ab] - 18)) / 12.0));
            if (k > 0.01f) {
                ctx.fill(0, 0, w, h, ((int) (k * 46) << 24) | 0xEDE6D2);
                int band = (int) (Math.min(w, h) * 0.12f);
                for (int s = 0; s < band; s += 2) {
                    int al = (int) (k * 90 * (1 - s / (float) band)) << 24;
                    ctx.fill(0, s, w, s + 2, al | 0xF2ECDC);
                    ctx.fill(0, h - s - 2, w, h - s, al | 0xF2ECDC);
                }
            }
        }
    }

    private static void abilityCaption(DrawContext ctx, TextRenderer tr, int ab, float a, int w, int h, int bh) {
        float alpha = Rite.smooth((a - 3) / 8.0) * (1 - Rite.smooth((a - 40) / 10.0));
        if (alpha <= 0.02f) return;
        drawCaption(ctx, tr, Pillars.KANJI[ab], ArmoryCoreRendererText.spaced(Abilities.TITLE[ab]), Abilities.LINE[ab], Pillars.rgb(ab), alpha, w, h, bh);
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
                small = ArmoryCoreRendererText.spaced(Pillars.NAME[i]);
                color = Pillars.rgb(i);
                alpha = Rite.smooth(u / 6.0) * (1 - Rite.smooth((t - (Rite.spawn(i) + Rite.SLOT - 8)) / 6.0));
            }
        } else if (t > Rite.RISE_START + 20 && t < Rite.CUT_END - 18) {
            big = "封";
            small = "T H E   S E A L   C L O S E S";
            alpha = Rite.smooth((t - Rite.RISE_START - 20) / 12.0) * (1 - Rite.smooth((t - (Rite.CUT_END - 30)) / 10.0));
        }
        if (big == null || alpha <= 0.02f) return;
        drawCaption(ctx, tr, big, small, null, color, alpha, w, h, bh);
    }

    private static void drawCaption(DrawContext ctx, TextRenderer tr, String big, String small, String line, int color, float alpha,
                                    int w, int h, int bh) {
        int a = (int) (alpha * 255) << 24;
        var m = ctx.getMatrices();
        m.push();
        float s = 3f;
        int bw = tr.getWidth(big);
        int y0 = h - bh - (line != null ? 70 : 58);
        m.translate(w / 2f - bw * s / 2f, y0, 0);
        m.scale(s, s, 1);
        ctx.drawText(tr, Text.literal(big), 0, 0, a | color, false);
        m.pop();
        int sw = tr.getWidth(small);
        int ys = h - bh - (line != null ? 30 : 18);
        ctx.drawText(tr, Text.literal(small), w / 2 - sw / 2, ys, a | color, false);
        int lw = (int) (sw * 0.55f * alpha);
        ctx.fill(w / 2 - lw, ys - 6, w / 2 + lw, ys - 5, ((int) (alpha * 150) << 24) | color);
        if (line != null) {
            int lw2 = tr.getWidth(line);
            ctx.drawText(tr, Text.literal(line), w / 2 - lw2 / 2, ys + 13, a | 0xD8D0C0, false);
        }
    }

    /** Four hand-inked frames slammed down over ~6 ticks, then the last one fades. */
    private static void impactFrame(DrawContext ctx, float f, int w, int h) {
        int frame = f < 1.5f ? 0 : f < 3f ? 1 : f < 4.5f ? 2 : 3;
        float alpha = f < 6 ? 1 : 1 - (f - 6) / 3f;
        alpha = MathHelper.clamp(alpha * alpha, 0, 1);
        int step = (int) (f / 1.5f);
        int jx = (int) (MathHelper.sin(step * 17.3f + impactPillar) * 6);
        int jy = (int) (MathHelper.sin(step * 11.1f + impactPillar * 2) * 4);
        float zoom = 1.02f + (frame == 3 ? (f - 4.5f) * 0.01f : 0);
        int dw = (int) (w * zoom) + 16, dh = (int) (h * zoom) + 10;
        // keep the frame's aspect: cover the screen
        float sa = 768f / 432f;
        if (dw / (float) dh < sa) dw = (int) (dh * sa);
        else dh = (int) (dw / sa);
        int x = (w - dw) / 2 + jx, y = (h - dh) / 2 + jy;
        RenderSystem.enableBlend();
        ctx.setShaderColor(1, 1, 1, alpha);
        ctx.drawTexture(IMPACT[impactPillar][frame], x, y, dw, dh, 0, 0, 768, 432, 768, 432);
        ctx.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Tiny shared text helper (kept here so the HUD does not depend on renderer internals). */
    static final class ArmoryCoreRendererText {
        static String spaced(String s) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < s.length(); k++) {
                if (k > 0) b.append(' ');
                b.append(s.charAt(k));
            }
            return b.toString();
        }
    }
}
