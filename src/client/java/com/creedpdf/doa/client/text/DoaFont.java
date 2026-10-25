package com.creedpdf.doa.client.text;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.client.render.Bill;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** DOA's own lettering (tools/textures/doa_font.py): every caption, name and toast is drawn with it. */
public final class DoaFont {
    public static final Identifier INK = DoaMod.id("textures/font/doa.png");
    public static final Identifier GLOW = DoaMod.id("textures/font/doa_glow.png");
    public static final Identifier ADD = DoaMod.id("textures/font/doa_add.png");

    private record Glyph(int i, float x0, float w, float adv) {
    }

    private static final Map<Integer, Glyph> GLYPHS = new HashMap<>();
    private static int cell = 96, cols = 16, texW = 1536, texH = 1024;

    private DoaFont() {
    }

    public static void reload(ResourceManager rm) {
        GLYPHS.clear();
        Optional<Resource> res = rm.getResource(DoaMod.id("fontdata/doa.json"));
        if (res.isEmpty()) return;
        try (Reader r = res.get().getReader()) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            cell = o.get("cell").getAsInt();
            cols = o.get("cols").getAsInt();
            texW = o.getAsJsonArray("size").get(0).getAsInt();
            texH = o.getAsJsonArray("size").get(1).getAsInt();
            JsonObject g = o.getAsJsonObject("glyphs");
            for (String k : g.keySet()) {
                JsonObject e = g.getAsJsonObject(k);
                GLYPHS.put(k.codePointAt(0), new Glyph(e.get("i").getAsInt(), e.get("x0").getAsFloat(), e.get("w").getAsFloat(), e.get("adv").getAsFloat()));
            }
        } catch (Exception e) {
            DoaMod.LOG.error("DOA font failed to load", e);
        }
    }

    private static Glyph glyph(int cp) {
        Glyph g = GLYPHS.get(cp);
        if (g == null) g = GLYPHS.get(Character.toUpperCase(cp));
        if (g == null) g = GLYPHS.get((int) ' ');
        return g == null ? new Glyph(-1, 0, 0, 0.3f) : g;
    }

    /** Width in units of the glyph height, with extra tracking (fraction of the height) between letters. */
    public static float width(String s, float tracking) {
        float w = 0;
        int n = 0;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            w += glyph(cp).adv + tracking;
            n++;
        }
        return n > 0 ? w - tracking : 0;
    }

    // ------------------------------------------------------------------------------------------------ HUD

    /**
     * Draw in screen space. x is the left edge (or centre if centred), size is the glyph height in GUI pixels.
     * A soft halo of the glow colour sits behind the ink.
     */
    public static void hud(DrawContext ctx, String s, float x, float y, float size, float tracking, boolean centred, int argb, int glowArgb) {
        if (GLYPHS.isEmpty() || s.isEmpty()) return;
        float w = width(s, tracking) * size;
        float x0 = centred ? x - w / 2 : x;
        RenderSystem.enableBlend();
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? glowArgb : argb;
            if (pass == 0 && ((c >>> 24) & 0xFF) == 0) continue;
            ctx.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, ((c >>> 24) & 0xFF) / 255f);
            Identifier tex = pass == 0 ? GLOW : INK;
            float cx = x0;
            for (int k = 0; k < s.length(); ) {
                int cp = s.codePointAt(k);
                k += Character.charCount(cp);
                Glyph g = glyph(cp);
                if (g.i >= 0) {
                    int col = g.i % cols, row = g.i / cols;
                    var m = ctx.getMatrices();
                    m.push();
                    m.translate(cx - g.x0 * size, y, 0);
                    float sc = size / cell;
                    m.scale(sc, sc, 1);
                    ctx.drawTexture(tex, 0, 0, cell, cell, col * cell, row * cell, cell, cell, texW, texH);
                    m.pop();
                }
                cx += (g.adv + tracking) * size;
            }
        }
        ctx.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------------ world

    /** Draw in the world, facing the camera, centred on (x, y, z); size is the glyph height in blocks. Additive light. */
    public static void world(MatrixStack ms, VertexConsumerProvider vcp, String s, double x, double y, double z, float size, float tracking,
                             float r, float g, float b) {
        if (GLYPHS.isEmpty() || s.isEmpty()) return;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(ADD));
        float w = width(s, tracking) * size;
        float cx = -w / 2;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            Glyph gl = glyph(cp);
            if (gl.i >= 0) {
                int col = gl.i % cols, row = gl.i / cols;
                float u0 = (float) (col * cell) / texW, v0 = (float) (row * cell) / texH;
                float u1 = u0 + (float) cell / texW, v1 = v0 + (float) cell / texH;
                // glyph cell centre offset along the camera's right vector
                float off = cx - gl.x0 * size + size / 2;
                Bill.quad(vc, ms, x + Bill.rx * off, y + Bill.ry * off, z + Bill.rz * off, size / 2, size / 2, 0, u0, v0, u1, v1, r, g, b, 1);
            }
            cx += (gl.adv + tracking) * size;
        }
    }
}
