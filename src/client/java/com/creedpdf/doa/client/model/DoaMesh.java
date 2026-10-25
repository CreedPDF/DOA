package com.creedpdf.doa.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A smooth mesh exported from Blender (format "doa_mesh_1"): per-corner position, uv and normal, in quads.
 * Used for the planets, clouds, atmospheres and rings of the Fate cutscene.
 */
public class DoaMesh {
    /** Colour for a corner given its world-relative position and normal; writes rgba into out. */
    public interface Shader {
        void shade(float x, float y, float z, float nx, float ny, float nz, float u, float v, float[] out);
    }

    private final float[] verts;
    private final int[] quads;

    private DoaMesh(float[] verts, int[] quads) {
        this.verts = verts;
        this.quads = quads;
    }

    public static DoaMesh parse(JsonObject o) {
        JsonArray v = o.getAsJsonArray("verts"), q = o.getAsJsonArray("quads");
        float[] verts = new float[v.size()];
        for (int i = 0; i < verts.length; i++) verts[i] = v.get(i).getAsFloat();
        int[] quads = new int[q.size()];
        for (int i = 0; i < quads.length; i++) quads[i] = q.get(i).getAsInt();
        return new DoaMesh(verts, quads);
    }

    /**
     * Emit the mesh at {@code center} (relative to the matrix origin), scaled and rotated, shading each corner.
     * {@code uOff} scrolls the texture horizontally (cloud drift).
     */
    public void render(MatrixStack ms, VertexConsumer vc, Vector3f center, float scale, Quaternionf rot, int light,
                       float uOff, boolean flip, Shader shader) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        Vector3f p = new Vector3f(), n = new Vector3f();
        float[] c = new float[4];
        for (int q = 0; q < quads.length; q += 4) {
            for (int k = 0; k < 4; k++) {
                int idx = quads[q + (flip ? 3 - k : k)] * 8;
                p.set(verts[idx], verts[idx + 1], verts[idx + 2]).mul(scale);
                rot.transform(p);
                p.add(center);
                n.set(verts[idx + 5], verts[idx + 6], verts[idx + 7]);
                rot.transform(n);
                float u = verts[idx + 3] + uOff, v = verts[idx + 4];
                shader.shade(p.x, p.y, p.z, n.x, n.y, n.z, u, v, c);
                vc.vertex(m, p.x, p.y, p.z).color(clamp(c[0]), clamp(c[1]), clamp(c[2]), clamp(c[3])).texture(u, v)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, n.x, n.y, n.z);
            }
        }
    }

    private static float clamp(float x) {
        return x < 0 ? 0 : Math.min(1, x);
    }
}
