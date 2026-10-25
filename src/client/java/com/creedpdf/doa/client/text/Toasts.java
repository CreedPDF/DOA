package com.creedpdf.doa.client.text;

import com.creedpdf.doa.net.DoaNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

/** The armory's messages, written low on the screen in DOA's lettering with a brushed underline. */
public final class Toasts {
    private static String text = "";
    private static int color = 0xFFFFFF;
    private static long shownAt = -10000;

    private Toasts() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DoaNet.ToastPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            String k = payload.key();
            text = k.startsWith("=") ? k.substring(1) : I18n.translate(k, payload.arg());
            color = payload.color() & 0xFFFFFF;
            shownAt = System.currentTimeMillis();
        }));
    }

    public static void render(DrawContext ctx) {
        long age = System.currentTimeMillis() - shownAt;
        if (age > 3600 || text.isEmpty()) return;
        float a = Math.min(1, age / 200f) * Math.min(1, (3600 - age) / 600f);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float y = h - 72 - (1 - Math.min(1, age / 250f)) * 6;
        int alpha = (int) (a * 255) << 24;
        DoaFont.hud(ctx, text, w / 2f, y, 11, 0.06f, true, alpha | color, ((int) (a * 160) << 24));
        float lw = DoaFont.width(text, 0.06f) * 11 * 0.5f * Math.min(1, age / 400f);
        ctx.fill((int) (w / 2f - lw), (int) (y + 13), (int) (w / 2f + lw), (int) (y + 14), ((int) (a * 140) << 24) | color);
    }

    @SuppressWarnings("unused")
    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
