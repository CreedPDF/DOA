package com.creedpdf.doa.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Blender-built model with named parts ("doa_mesh_2"): per-corner position, uv, normal and a flag, in blocks,
 * Minecraft axes. Each part has a pivot so the game can animate it.
 */
public class DoaMeshSet {
    public interface Pose {
        /** Transform part {@code name} about its pivot (matrices are already translated to the pivot). Return false to hide it. */
        boolean pose(String name, MatrixStack ms);
    }

    public interface Tint {
        /** rgba for a corner of {@code part} with face flag {@code flag}; n = world-ish normal after posing. */
        void tint(String part, int flag, float nx, float ny, float nz, float[] out);
    }

    public static final class Part {
        public final String name;
        public final float[] pivot;
        final float[] v;
        final int[] q;
        final int stride;

        Part(String name, float[] pivot, float[] v, int[] q, int stride) {
            this.name = name;
            this.pivot = pivot;
            this.v = v;
            this.q = q;
            this.stride = stride;
        }

        /** Centroid of the part's corners. */
        public Vector3f centroid() {
            Vector3f c = new Vector3f();
            int n = v.length / stride;
            for (int i = 0; i < n; i++) c.add(v[i * stride], v[i * stride + 1], v[i * stride + 2]);
            return n > 0 ? c.div(n) : c;
        }
    }

    public final Map<String, Part> parts = new LinkedHashMap<>();
    public final List<Part> list = new ArrayList<>();

    public static DoaMeshSet parse(JsonObject o) {
        DoaMeshSet m = new DoaMeshSet();
        int stride = o.has("stride") ? o.get("stride").getAsInt() : 9;
        for (JsonElement el : o.getAsJsonArray("parts")) {
            JsonObject p = el.getAsJsonObject();
            JsonArray va = p.getAsJsonArray("verts"), qa = p.getAsJsonArray("quads"), pv = p.getAsJsonArray("pivot");
            float[] v = new float[va.size()];
            for (int i = 0; i < v.length; i++) v[i] = va.get(i).getAsFloat();
            int[] q = new int[qa.size()];
            for (int i = 0; i < q.length; i++) q[i] = qa.get(i).getAsInt();
            Part part = new Part(p.get("name").getAsString(), new float[]{pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()}, v, q, stride);
            m.parts.put(part.name, part);
            m.list.add(part);
        }
        return m;
    }

    public void render(MatrixStack ms, VertexConsumer vc, int light, Pose pose, Tint tint) {
        float[] c = {1, 1, 1, 1};
        Vector3f n = new Vector3f();
        for (Part p : list) {
            ms.push();
            ms.translate(p.pivot[0], p.pivot[1], p.pivot[2]);
            boolean show = pose == null || pose.pose(p.name, ms);
            ms.translate(-p.pivot[0], -p.pivot[1], -p.pivot[2]);
            if (show) emit(p, ms, vc, light, tint, c, n);
            ms.pop();
        }
    }

    public void renderPart(String name, MatrixStack ms, VertexConsumer vc, int light, Tint tint) {
        Part p = parts.get(name);
        if (p != null) emit(p, ms, vc, light, tint, new float[]{1, 1, 1, 1}, new Vector3f());
    }

    private static void emit(Part p, MatrixStack ms, VertexConsumer vc, int light, Tint tint, float[] c, Vector3f n) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        Matrix3f nm = en.getNormalMatrix();
        int s = p.stride;
        for (int i = 0; i < p.q.length; i++) {
            int b = p.q[i] * s;
            n.set(p.v[b + 5], p.v[b + 6], p.v[b + 7]);
            int flag = s > 8 ? (int) p.v[b + 8] : 0;
            if (tint != null) {
                WN.set(n);
                nm.transform(WN);
                tint.tint(p.name, flag, WN.x, WN.y, WN.z, c);
            }
            vc.vertex(m, p.v[b], p.v[b + 1], p.v[b + 2]).color(cl(c[0]), cl(c[1]), cl(c[2]), cl(c[3])).texture(p.v[b + 3], p.v[b + 4])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, n.x, n.y, n.z);
        }
    }

    private static final Vector3f WN = new Vector3f();

    private static float cl(float x) {
        return x < 0 ? 0 : Math.min(1, x);
    }
}
