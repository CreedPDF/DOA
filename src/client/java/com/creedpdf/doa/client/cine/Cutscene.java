package com.creedpdf.doa.client.cine;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.move.Kit;
import com.creedpdf.doa.client.move.Moves;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.client.text.Toasts;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The caller's cutscenes: the summoning (an opening look to the heavens, a shot for every pillar as it hangs as a
 * star and plunges, an overhead shot while the sphere closes) and each relic's move. Hand-inked impact frames in
 * the pillar's colour punctuate every strike; camera shake is felt by everyone nearby.
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
            if (!(e instanceof ArmoryCoreEntity core) || core.isRemoved() || core.getOwnerNetId() != mc.player.getId() || core.isReleasing()) continue;
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

    public static void onImpact(ArmoryCoreEntity core, int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(core.pillarBase(i));
        shake = Math.max(shake, 2.4f * (float) MathHelper.clamp(1 - d / 80, 0, 1));
        if (core == active || core.getOwnerNetId() == mc.player.getId()) {
            impactStart = clientTicks;
            impactPillar = i;
        }
    }

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
        Vec3d[] pl;
        double t, blendStart, blendLen;
        if (abilityMode) {
            int ab = core.getAbility();
            t = core.getAbilityTicks() + tickDelta;
            pl = Moves.get(ab).camera(core, t, playerYaw);
            blendLen = 16;
            blendStart = Abilities.LENGTH[ab] - blendLen;
        } else {
            t = core.getRiteTicks() + tickDelta;
            pl = riteShot(core, (float) t, playerEye, playerYaw);
            blendLen = 22;
            blendStart = Rite.CUT_END - blendLen;
        }
        if (pl == null) return null;
        Vec3d pos = pl[0], look = pl[1];
        Vec3d dir = look.subtract(pos);
        float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180 / Math.PI);
        float blend = Rite.smooth((t - blendStart) / blendLen);
        if (blend > 0) {
            pos = Kit.lerp(pos, playerEye, blend);
            yaw = yaw + MathHelper.wrapDegrees(playerYaw - yaw) * blend;
            pitch = pitch + (playerPitch - pitch) * blend;
        }
        return new Shot(pos, yaw, pitch, blend, blend < 0.92f);
    }

    private static Vec3d[] riteShot(ArmoryCoreEntity core, float t, Vec3d playerEye, float playerYaw) {
        Vec3d c = core.getPos();
        if (t < Rite.spawn(0) - 2) {
            float k = Rite.smooth(t / (Rite.spawn(0) - 2));
            double yr = Math.toRadians(playerYaw);
            Vec3d back = new Vec3d(Math.sin(yr), 0, -Math.cos(yr));
            Vec3d p0 = playerEye.add(back.multiply(3.6)).add(0, 0.4, 0);
            Vec3d p1 = c.add(back.multiply(6)).add(0, 0.8, 0);
            return new Vec3d[]{Kit.lerp(p0, p1, k), Kit.lerp(playerEye, c.add(back.multiply(-8)).add(0, 60, 0), Rite.smooth(t / 30.0))};
        }
        if (t < Rite.SEAL) {
            int i = MathHelper.clamp((int) Math.floor((t - Rite.spawn(0) + 2) / Rite.SLOT), 0, Pillars.COUNT - 1);
            float u = t - Rite.spawn(i);
            Vec3d b = core.pillarBase(i);
            Vec3d d = new Vec3d(b.x - c.x, 0, b.z - c.z).normalize();
            Vec3d side = new Vec3d(-d.z, 0, d.x).multiply(i % 2 == 0 ? 1 : -1);
            Vec3d pos = c.add(d.multiply(Rite.PILLAR_RADIUS - 10.5 - u * 0.02)).add(side.multiply(6.5 + u * 0.03)).add(0, 1.1 + u * 0.02, 0);
            pos = new Vec3d(pos.x, Math.max(pos.y, b.y + 1.1), pos.z);
            // look up at the star, whip down with the plunge, settle on the planted pillar
            float plunge = Rite.smooth((u - Rite.STAR + 1) / (Rite.PLUNGE + 2.0));
            Vec3d sky = b.add(0, 70, 0);
            Vec3d planted = b.add(0, 7, 0);
            return new Vec3d[]{pos, Kit.lerp(sky, planted, plunge)};
        }
        float u = t - Rite.SEAL;
        double th = u * 0.0065 + Math.PI * 0.85;
        double hr = 30 - Math.min(u, 200) * 0.025;
        return new Vec3d[]{c.add(Math.sin(th) * hr, 38 - Math.min(u, 200) * 0.02, -Math.cos(th) * hr), c.add(0, 3, 0)};
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

    // ------------------------------------------------------------------------------------------------ overlay

    /** Draws the cutscene overlay. Returns true if the vanilla HUD should be hidden. */
    public static boolean renderHud(DrawContext ctx, float tickDelta) {
        boolean act = isActive();
        float f = now(tickDelta) - impactStart;
        boolean impact = f >= 0 && f < 9;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
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
            if (abilityMode) Moves.get(core.getAbility()).hud(ctx, core, t, w, h);
            float bars = Rite.smooth(t / 14.0) * (1 - Rite.smooth((t - (len - 18)) / 18.0));
            int bh = (int) (h * 0.11f * bars);
            if (bh > 0) {
                ctx.fill(0, 0, w, bh, 0xFF000000);
                ctx.fill(0, h - bh, w, h, 0xFF000000);
            }
            if (!abilityMode) caption(ctx, t, w, h, bh);
        }
        if (impact) impactFrame(ctx, f, w, h);
        if (!act) Toasts.render(ctx);
        return act;
    }

    private static void caption(DrawContext ctx, float t, int w, int h, int bh) {
        String big = null, small = null;
        int color = 0xE8E0D0;
        float alpha = 0;
        if (t < Rite.spawn(0)) {
            big = "召";
            small = "THE ORBITAL ARMORY ANSWERS";
            alpha = Rite.smooth((t - 6) / 10.0) * (1 - Rite.smooth((t - Rite.spawn(0) + 8) / 8.0));
        } else if (t < Rite.SEAL) {
            int i = MathHelper.clamp((int) Math.floor((t - Rite.spawn(0) + 2) / Rite.SLOT), 0, Pillars.COUNT - 1);
            float u = t - Rite.land(i) - 4;
            if (u > 0) {
                big = Pillars.KANJI[i];
                small = Pillars.NAME[i];
                color = Pillars.rgb(i);
                alpha = Rite.smooth(u / 5.0) * (1 - Rite.smooth((t - (Rite.spawn(i) + Rite.SLOT - 4)) / 5.0));
            }
        } else if (t > Rite.RISE_START + 20 && t < Rite.CUT_END - 18) {
            big = "封";
            small = "THE SEAL CLOSES";
            alpha = Rite.smooth((t - Rite.RISE_START - 20) / 12.0) * (1 - Rite.smooth((t - (Rite.CUT_END - 30)) / 10.0));
        }
        if (big == null || alpha <= 0.02f) return;
        int a = (int) (alpha * 255) << 24;
        float y = h - bh - 70;
        DoaFont.hud(ctx, big, w / 2f, y, 34, 0, true, a | color, ((int) (alpha * 130) << 24) | color);
        DoaFont.hud(ctx, small, w / 2f, y + 40, 11, 0.32f, true, a | color, ((int) (alpha * 150) << 24));
        float lw = DoaFont.width(small, 0.32f) * 11 * 0.55f * alpha;
        ctx.fill((int) (w / 2f - lw), (int) (y + 36), (int) (w / 2f + lw), (int) (y + 37), ((int) (alpha * 150) << 24) | color);
    }

    /** Four hand-inked frames slammed down over ~6 ticks, then the last one fades. */
    private static void impactFrame(DrawContext ctx, float f, int w, int h) {
        int frame = f < 1.5f ? 0 : f < 3f ? 1 : f < 4.5f ? 2 : 3;
        float alpha = f < 6 ? 1 : 1 - (f - 6) / 3f;
        alpha = MathHelper.clamp(alpha * alpha, 0, 1);
        int step = (int) (f / 1.5f);
        int jx = (int) (MathHelper.sin(step * 17.3f + impactPillar) * 6);
        int jy = (int) (MathHelper.sin(step * 11.1f + impactPillar * 2) * 4);
        float zoom = 1.02f + (frame == 3 ? (f - 4.5f) * 0.012f : 0);
        int dw = (int) (w * zoom) + 16, dh = (int) (h * zoom) + 10;
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
}
