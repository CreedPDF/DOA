package com.creedpdf.doa.client.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Camera-facing quads built from the camera's own yaw and pitch (screen-right and screen-up), so textures and text
 * always read the right way round. Positions are relative to the current matrix origin.
 */
public final class Bill {
    public static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    public static float rx, ry, rz, ux, uy, uz, fx, fy, fz;

    private Bill() {
    }

    public static void update(Camera cam) {
        float yaw = cam.getYaw() * MathHelper.RADIANS_PER_DEGREE, pitch = cam.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        fx = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        fy = -MathHelper.sin(pitch);
        fz = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        // right = forward x up
        float x = -fz, z = fx;
        float len = MathHelper.sqrt(x * x + z * z);
        if (len < 1e-5f) {
            x = 1;
            z = 0;
            len = 1;
        }
        rx = x / len;
        ry = 0;
        rz = z / len;
        // up = right x forward
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
    }

    public static void vtx(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, float x, float y, float z, float u, float v,
                           float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(Math.min(1, Math.max(0, r)), Math.min(1, Math.max(0, g)), Math.min(1, Math.max(0, b)), Math.min(1, Math.max(0, a)))
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(en, 0, 1, 0);
    }

    /** A camera-facing quad (both sides), half-size hw x hh, rotated by angle in the screen plane, uv rect u0..u1, v0..v1. */
    public static void quad(VertexConsumer vc, MatrixStack ms, double x, double y, double z, float hw, float hh, float angle,
                            float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        float c = MathHelper.cos(angle), s = MathHelper.sin(angle);
        // rotated right / up
        float arx = rx * c + ux * s, ary = ry * c + uy * s, arz = rz * c + uz * s;
        float aux = -rx * s + ux * c, auy = -ry * s + uy * c, auz = -rz * s + uz * c;
        float px = (float) x, py = (float) y, pz = (float) z;
        float[][] p = {
                {px - arx * hw - aux * hh, py - ary * hw - auy * hh, pz - arz * hw - auz * hh, u0, v1},
                {px + arx * hw - aux * hh, py + ary * hw - auy * hh, pz + arz * hw - auz * hh, u1, v1},
                {px + arx * hw + aux * hh, py + ary * hw + auy * hh, pz + arz * hw + auz * hh, u1, v0},
                {px - arx * hw + aux * hh, py - ary * hw + auy * hh, pz - arz * hw + auz * hh, u0, v0}};
        for (int k = 0; k < 4; k++) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
        for (int k = 3; k >= 0; k--) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
    }

    public static void sprite(VertexConsumer vc, MatrixStack ms, double x, double y, double z, float size, float angle, float r, float g, float b) {
        quad(vc, ms, x, y, z, size, size, angle, 0, 0, 1, 1, r, g, b, 1);
    }

    /** A ribbon from a to b facing the camera (both sides); v runs 0 at a to 1 at b. */
    public static void ribbon(VertexConsumer vc, MatrixStack ms, double ax, double ay, double az, double bx, double by, double bz,
                              double camx, double camy, double camz, float halfWidth, float r, float g, float b, float a, float v0, float v1) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) / 2 - camx, my = (ay + by) / 2 - camy, mz = (az + bz) / 2 - camz;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6) return;
        sx = sx / len * halfWidth;
        sy = sy / len * halfWidth;
        sz = sz / len * halfWidth;
        float[][] p = {
                {(float) (ax - sx), (float) (ay - sy), (float) (az - sz), 0, v0},
                {(float) (ax + sx), (float) (ay + sy), (float) (az + sz), 1, v0},
                {(float) (bx + sx), (float) (by + sy), (float) (bz + sz), 1, v1},
                {(float) (bx - sx), (float) (by - sy), (float) (bz - sz), 0, v1}};
        for (int k = 0; k < 4; k++) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
        for (int k = 3; k >= 0; k--) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
    }

    /** A flat disc-quad lying in the plane spanned by unit vectors (ax..) and (bx..), both sides. */
    public static void plane(VertexConsumer vc, MatrixStack ms, double x, double y, double z, float[] e1, float[] e2, float size,
                             float r, float g, float b, float a) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        float px = (float) x, py = (float) y, pz = (float) z;
        float[][] p = new float[4][5];
        float[][] c = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (int k = 0; k < 4; k++) {
            p[k][0] = px + (e1[0] * c[k][0] + e2[0] * c[k][1]) * size;
            p[k][1] = py + (e1[1] * c[k][0] + e2[1] * c[k][1]) * size;
            p[k][2] = pz + (e1[2] * c[k][0] + e2[2] * c[k][1]) * size;
            p[k][3] = c[k][2];
            p[k][4] = c[k][3];
        }
        for (int k = 0; k < 4; k++) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
        for (int k = 3; k >= 0; k--) vtx(vc, m, en, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, b, a);
    }
}
