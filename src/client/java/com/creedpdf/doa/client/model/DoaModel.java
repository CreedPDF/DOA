package com.creedpdf.doa.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DOA's own model format ("doa_model_1", written by tools/blender/doa_models.py): bones with pivots, and cubes
 * with one atlas rectangle per face and an optional single-axis rotation. Units are model pixels (1/16 block),
 * y up, front facing north (-Z).
 */
public class DoaModel {
    /** A bone's pose: rotation in radians about x, y, z (applied z, y, x) around the bone pivot, plus offset in px. */
    public interface Poser {
        void pose(String bone, float[] rot, float[] offset);
    }

    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private record Bone(String name, String parent, float[] pivot) {
    }

    /** One quad: 4 corners (x,y,z) in px, 4 uvs, normal. */
    private record Quad(float[] pos, float[] uv, float[] normal) {
    }

    private final List<Bone> bones = new ArrayList<>();
    private final Map<String, List<Quad>> quads = new HashMap<>();
    private final Map<String, Bone> byName = new HashMap<>();

    public static DoaModel parse(JsonObject root) {
        DoaModel m = new DoaModel();
        JsonArray tex = root.getAsJsonArray("texture");
        float tw = tex.get(0).getAsFloat(), th = tex.get(1).getAsFloat();
        for (JsonElement el : root.getAsJsonArray("bones")) {
            JsonObject b = el.getAsJsonObject();
            String parent = b.has("parent") && !b.get("parent").isJsonNull() ? b.get("parent").getAsString() : null;
            Bone bone = new Bone(b.get("name").getAsString(), parent, vec(b.getAsJsonArray("pivot")));
            m.bones.add(bone);
            m.byName.put(bone.name, bone);
            m.quads.put(bone.name, new ArrayList<>());
        }
        for (JsonElement el : root.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            String bone = c.get("bone").getAsString();
            float[] lo = vec(c.getAsJsonArray("from")), hi = vec(c.getAsJsonArray("to"));
            JsonObject uv = c.getAsJsonObject("uv");
            JsonObject rot = c.has("rot") && c.get("rot").isJsonObject() ? c.getAsJsonObject("rot") : null;
            List<Quad> list = m.quads.computeIfAbsent(bone, k -> new ArrayList<>());
            for (String f : FACES) {
                if (!uv.has(f)) continue;
                JsonArray r = uv.getAsJsonArray(f);
                float u = r.get(0).getAsFloat(), v = r.get(1).getAsFloat(), w = r.get(2).getAsFloat(), h = r.get(3).getAsFloat();
                float[][] corners = corners(f, lo, hi);
                float[] n = normal(f);
                if (rot != null) {
                    String axis = rot.get("axis").getAsString();
                    float ang = (float) Math.toRadians(rot.get("angle").getAsFloat());
                    float[] piv = vec(rot.getAsJsonArray("pivot"));
                    for (float[] p : corners) rotate(p, axis, ang, piv);
                    rotate(n, axis, ang, new float[]{0, 0, 0});
                }
                float[] pos = new float[12];
                for (int k = 0; k < 4; k++) System.arraycopy(corners[k], 0, pos, k * 3, 3);
                float u0 = u / tw, v0 = v / th, u1 = (u + w) / tw, v1 = (v + h) / th;
                list.add(new Quad(pos, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, n));
            }
        }
        return m;
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** Corners TL, TR, BR, BL seen from outside the face. */
    private static float[][] corners(String f, float[] lo, float[] hi) {
        float x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        return switch (f) {
            case "north" -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case "east" -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "west" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case "up" -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            default -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    private static float[] normal(String f) {
        return switch (f) {
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "east" -> new float[]{1, 0, 0};
            case "west" -> new float[]{-1, 0, 0};
            case "up" -> new float[]{0, 1, 0};
            default -> new float[]{0, -1, 0};
        };
    }

    private static void rotate(float[] p, String axis, float a, float[] piv) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float x = p[0] - piv[0], y = p[1] - piv[1], z = p[2] - piv[2];
        float nx = x, ny = y, nz = z;
        switch (axis) {
            case "x" -> {
                ny = y * c - z * s;
                nz = y * s + z * c;
            }
            case "y" -> {
                nx = x * c + z * s;
                nz = -x * s + z * c;
            }
            default -> {
                nx = x * c - y * s;
                ny = x * s + y * c;
            }
        }
        p[0] = nx + piv[0];
        p[1] = ny + piv[1];
        p[2] = nz + piv[2];
    }

    /** Render the model; the matrix stack is in block units with the model origin at its current translation. */
    public void render(MatrixStack matrices, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a, Poser poser) {
        float[] rot = new float[3], off = new float[3];
        for (Bone bone : bones) {
            matrices.push();
            applyChain(matrices, bone, poser, rot, off);
            MatrixStack.Entry e = matrices.peek();
            Matrix4f m = e.getPositionMatrix();
            int cr = (int) (r * 255), cg = (int) (g * 255), cb = (int) (b * 255), ca = (int) (a * 255);
            for (Quad q : quads.get(bone.name)) {
                // emitted BL, BR, TR, TL: counter-clockwise seen from outside, so culled layers keep the outer face
                for (int j = 3; j >= 0; j--) {
                    int k = j;
                    vc.vertex(m, q.pos[k * 3] / 16f, q.pos[k * 3 + 1] / 16f, q.pos[k * 3 + 2] / 16f)
                            .color(cr, cg, cb, ca)
                            .texture(q.uv[k * 2], q.uv[k * 2 + 1])
                            .overlay(overlay)
                            .light(light)
                            .normal(e, q.normal[0], q.normal[1], q.normal[2]);
                }
            }
            matrices.pop();
        }
    }

    private void applyChain(MatrixStack matrices, Bone bone, Poser poser, float[] rot, float[] off) {
        if (bone.parent != null) {
            Bone p = byName.get(bone.parent);
            if (p != null) applyChain(matrices, p, poser, rot, off);
        }
        rot[0] = rot[1] = rot[2] = 0;
        off[0] = off[1] = off[2] = 0;
        if (poser != null) poser.pose(bone.name, rot, off);
        if (rot[0] == 0 && rot[1] == 0 && rot[2] == 0 && off[0] == 0 && off[1] == 0 && off[2] == 0) return;
        float px = bone.pivot[0] / 16f, py = bone.pivot[1] / 16f, pz = bone.pivot[2] / 16f;
        matrices.translate(px + off[0] / 16f, py + off[1] / 16f, pz + off[2] / 16f);
        if (rot[2] != 0) matrices.multiply(new org.joml.Quaternionf().rotateZ(rot[2]));
        if (rot[1] != 0) matrices.multiply(new org.joml.Quaternionf().rotateY(rot[1]));
        if (rot[0] != 0) matrices.multiply(new org.joml.Quaternionf().rotateX(rot[0]));
        matrices.translate(-px, -py, -pz);
    }

    @SuppressWarnings("unused")
    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }
}
