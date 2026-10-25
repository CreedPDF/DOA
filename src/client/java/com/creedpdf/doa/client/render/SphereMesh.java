package com.creedpdf.doa.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A dense UV sphere with the same texture mapping the planet textures are painted for
 * (u = atan2(x, z)/2pi + 0.25, v = 0.5 - latitude/pi), shaded per corner by a callback so planets can be lit
 * by the real direction of their sun, rotate, show phases and cast terminators at any angle.
 */
public final class SphereMesh {
    public interface Shade {
        /** n = world normal of the corner, p = world-relative position; write rgba into o. */
        void shade(float nx, float ny, float nz, float px, float py, float pz, float[] o);
    }

    private final int lon, lat;
    private final float[] pos, uv;

    public static final SphereMesh HIGH = new SphereMesh(224, 112);
    public static final SphereMesh MID = new SphereMesh(96, 48);
    public static final SphereMesh LOW = new SphereMesh(32, 16);

    private SphereMesh(int lon, int lat) {
        this.lon = lon;
        this.lat = lat;
        pos = new float[(lon + 1) * (lat + 1) * 3];
        uv = new float[(lon + 1) * (lat + 1) * 2];
        for (int j = 0; j <= lat; j++) {
            double v = (double) j / lat;
            double la = (0.5 - v) * Math.PI;
            for (int i = 0; i <= lon; i++) {
                double u = (double) i / lon;
                double a = (u - 0.25) * Math.PI * 2;
                int k = j * (lon + 1) + i;
                pos[k * 3] = (float) (Math.cos(la) * Math.sin(a));
                pos[k * 3 + 1] = (float) Math.sin(la);
                pos[k * 3 + 2] = (float) (Math.cos(la) * Math.cos(a));
                uv[k * 2] = (float) u;
                uv[k * 2 + 1] = (float) v;
            }
        }
    }

    public static SphereMesh forSize(double pixelsish) {
        return pixelsish > 6 ? HIGH : pixelsish > 0.8 ? MID : LOW;
    }

    /**
     * Emit the sphere. {@code inside} reverses the winding (for skies drawn on the inside). uOff scrolls the texture.
     */
    public void render(MatrixStack ms, VertexConsumer vc, float cx, float cy, float cz, float radius, Quaternionf rot, float uOff, boolean inside,
                       int light, Shade shade) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        Vector3f n = new Vector3f();
        float[] c = new float[4];
        int[] q = new int[4];
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                int a = j * (lon + 1) + i, b = a + 1, d = a + lon + 1, e = d + 1;
                if (inside) {
                    q[0] = a; q[1] = b; q[2] = e; q[3] = d;
                } else {
                    q[0] = a; q[1] = d; q[2] = e; q[3] = b;
                }
                for (int k = 0; k < 4; k++) {
                    int idx = q[k];
                    n.set(pos[idx * 3], pos[idx * 3 + 1], pos[idx * 3 + 2]);
                    rot.transform(n);
                    float px = cx + n.x * radius, py = cy + n.y * radius, pz = cz + n.z * radius;
                    shade.shade(n.x, n.y, n.z, px, py, pz, c);
                    vc.vertex(m, px, py, pz).color(cl(c[0]), cl(c[1]), cl(c[2]), cl(c[3])).texture(uv[idx * 2] + uOff, uv[idx * 2 + 1])
                            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, n.x, n.y, n.z);
                }
            }
        }
    }

    private static float cl(float x) {
        return x < 0 ? 0 : Math.min(1, x);
    }
}
