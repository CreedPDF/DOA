package com.creedpdf.doa.client.render;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.model.DoaModel;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the whole rite from the armory's timeline: the seven falling pillars with their seals, light columns,
 * brush-stroke trails, shockwaves and floating names; the black core orb, the ink corruption spreading over the
 * ground, the opaque dark sphere rising with a burning ragged rim, its inner runes, and the relics the pillars release.
 */
public class ArmoryCoreRenderer extends EntityRenderer<ArmoryCoreEntity> {
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final int OV = OverlayTexture.DEFAULT_UV;

    private static final Identifier[] PILLAR = new Identifier[Pillars.COUNT];
    private static final Identifier[] PILLAR_GLOW = new Identifier[Pillars.COUNT];
    private static final Identifier[] SEAL = new Identifier[Pillars.COUNT];
    private static final Identifier[] LABEL = new Identifier[Pillars.COUNT];
    private static final Identifier RELIC = DoaMod.id("textures/entity/relic.png");
    private static final Identifier RELIC_GLOW = DoaMod.id("textures/entity/relic_glow.png");
    private static final Identifier COLUMN = DoaMod.id("textures/effect/add_light_column.png");
    private static final Identifier TRAIL = DoaMod.id("textures/effect/add_brush_trail.png");
    private static final Identifier RING_ADD = DoaMod.id("textures/effect/add_ink_ring.png");
    private static final Identifier RING_INK = DoaMod.id("textures/effect/ink_ring.png");
    private static final Identifier SKIN = DoaMod.id("textures/effect/orb_skin.png");
    private static final Identifier EDGE = DoaMod.id("textures/effect/add_orb_edge.png");
    private static final Identifier EDGE_INK = DoaMod.id("textures/effect/orb_edge.png");
    private static final Identifier HALO = DoaMod.id("textures/effect/add_halo.png");

    static {
        for (int i = 0; i < Pillars.COUNT; i++) {
            String id = Pillars.ID[i];
            PILLAR[i] = DoaMod.id("textures/entity/pillar/" + id + ".png");
            PILLAR_GLOW[i] = DoaMod.id("textures/entity/pillar/" + id + "_glow.png");
            SEAL[i] = DoaMod.id("textures/effect/add_seal_" + id + ".png");
            LABEL[i] = DoaMod.id("textures/gui/label/add_" + id + ".png");
        }
    }

    private static final int DOME_COLS = 96;
    private static final double BAND = 3.0;

    private final TextRenderer text;

    public ArmoryCoreRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.text = ctx.getTextRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public Identifier getTexture(ArmoryCoreEntity entity) {
        return SKIN;
    }

    @Override
    public boolean shouldRender(ArmoryCoreEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ArmoryCoreEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        int relI = e.getReleaseTicks();
        double t = e.getRiteTicks() + (relI >= 0 ? 0 : td);
        double rel = relI >= 0 ? relI + td : -1;
        double time = e.age + td;
        Vec3d origin = e.getLerpedPos(td);
        Vec3d cam = this.dispatcher.camera.getPos().subtract(origin);

        for (int i = 0; i < Pillars.COUNT; i++) {
            if (!Rite.visible(i, t + 12)) continue;
            Vec3d b = e.pillarBase(i).subtract(origin);
            renderSeal(e, i, b, t, rel, time, ms, vcp);
            if (!Rite.visible(i, t)) continue;
            renderPillar(e, i, b, t, rel, time, ms, vcp, cam);
        }
        if (rel < 0 || Rite.dome(t, rel) > 0) {
            renderCore(t, rel, time, ms, vcp);
            renderCorruption(t, rel, time, ms, vcp);
            renderDome(t, rel, time, ms, vcp);
        }
        if (t >= Rite.SEALED) {
            for (int i = 0; i < Pillars.COUNT; i++) renderRelic(e, i, e.pillarBase(i).subtract(origin), t, rel, time, ms, vcp);
        }
        super.render(e, yaw, td, ms, vcp, light);
    }

    // ------------------------------------------------------------------------------------------------ pillars

    private void renderSeal(ArmoryCoreEntity e, int i, Vec3d b, double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float[] c = Pillars.COLOR[i];
        double u = t - Rite.spawn(i);
        float grow = Rite.easeOut((u + 12) / 22.0);
        float flash = (float) Math.exp(-Math.max(0, t - Rite.land(i)) / 6.0) * (Rite.landed(i, t) ? 1 : 0);
        float charge = Rite.landed(i, t) ? 0 : Rite.smooth((u + 12) / (Rite.FALL + 12.0));
        float rest = Rite.landed(i, t) ? 0.45f + 0.1f * MathHelper.sin((float) time * 0.08f + i) : 0;
        float k = (charge * 0.9f + flash * 1.6f + rest);
        if (rel >= 0) k *= 1 - Rite.smooth((rel - 30) / 40.0);
        if (k <= 0.01f) return;
        double r = 3.3 * grow + flash * 0.8;
        ms.push();
        ms.translate(b.x, b.y + 0.06, b.z);
        ms.multiply(new Quaternionf().rotateY((float) (time * 0.012 + i)));
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(SEAL[i]));
        flat(vc, ms, r, c[0] * k, c[1] * k, c[2] * k);
        ms.pop();
    }

    private void renderPillar(ArmoryCoreEntity e, int i, Vec3d b, double t, double rel, double time, MatrixStack ms,
                              VertexConsumerProvider vcp, Vec3d cam) {
        float[] c = Pillars.COLOR[i];
        boolean landed = Rite.landed(i, t);
        double h = landed ? 0 : Rite.fallHeight(i, t);
        double after = t - Rite.land(i);
        if (landed && after < 10) h -= 0.35 * Math.sin(after / 10.0 * Math.PI) * Math.exp(-after / 6);
        if (rel >= 0) h += Rite.ascend(rel);
        double faceYaw = Math.PI - Rite.angle(i);

        // the pillar itself
        DoaModel model = DoaModels.PILLAR;
        if (model != null) {
            ms.push();
            ms.translate(b.x, b.y + h, b.z);
            ms.multiply(new Quaternionf().rotateY((float) faceYaw));
            int light = landed && rel < 0 ? WorldRenderer.getLightmapCoordinates(e.getWorld(),
                    BlockPos.ofFloored(e.pillarBase(i).add(0, 1.5, 0))) : FULL;
            light = Math.max(light & 0xFFFF, 0x60) | (light & 0xFFFF0000);
            DoaModel.Poser poser = (bone, rot, off) -> {
                if (bone.equals("finial")) {
                    rot[1] = (float) (time * 0.03);
                    off[1] = (float) Math.sin(time * 0.05 + i) * 0.6f;
                }
            };
            model.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(PILLAR[i])), light, OV, 1, 1, 1, 1, poser);
            float pulse = landed ? 0.75f + 0.25f * MathHelper.sin((float) time * 0.11f + i * 0.9f) : 1.0f;
            if (landed && after < 12) pulse += (float) (1.2 * Math.exp(-after / 4));
            model.render(ms, vcp.getBuffer(RenderLayer.getEyes(PILLAR_GLOW[i])), FULL, OV, pulse, pulse, pulse, 1, poser);
            ms.pop();
        }

        // descending light: the heavenly column and the brush stroke trailing the pillar
        if (!landed || after < 30 || rel >= Rite.REL_ASCEND_START) {
            float ck;
            if (rel >= Rite.REL_ASCEND_START) ck = 0.8f * (1 - Rite.smooth((rel - Rite.REL_END + 25) / 20.0));
            else if (!landed) ck = Rite.smooth((t - Rite.spawn(i) + 6) / 14.0);
            else ck = (float) Math.exp(-after / 9.0) * 1.4f;
            if (ck > 0.01f) {
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(COLUMN));
                column(vc, ms, b, cam, 0.9 + (landed ? 0.6 * Math.exp(-after / 5) : 0), 0, 90, c[0] * ck, c[1] * ck, c[2] * ck, 1, 0);
                column(vc, ms, b, cam, 0.35, 0, 90, ck * 0.9f, ck * 0.9f, ck * 0.85f, 1, 0);
            }
        }
        if (!landed && rel < 0) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TRAIL));
            double y0 = b.y + h + 9.0;
            column(vc, ms, new Vec3d(b.x, 0, b.z), cam, 1.7, y0, y0 + 34, c[0] * 1.2f, c[1] * 1.2f, c[2] * 1.2f, 1, 0);
        }
        if (rel >= Rite.REL_ASCEND_START) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TRAIL));
            double y0 = b.y + h - 30;
            column(vc, ms, new Vec3d(b.x, 0, b.z), cam, 1.4, y0, y0 + 30, c[0], c[1], c[2], 0, 1);
        }

        // the shockwave: a brushed ink ring and a ring of light racing out over the ground
        if (landed && after < 22 && rel < 0) {
            float k = Rite.clamp01(after / 22.0);
            double r = 1 + 10 * Rite.easeOut(k);
            float a = (1 - k) * (1 - k);
            ms.push();
            ms.translate(b.x, b.y + 0.1, b.z);
            ms.multiply(new Quaternionf().rotateY(i * 0.7f));
            flatColor(vcp.getBuffer(RenderLayer.getEntityTranslucent(RING_INK)), ms, r * 0.92, 0.02f, 0.015f, 0.02f, a * 0.9f);
            ms.translate(0, 0.03, 0);
            flat(vcp.getBuffer(RenderLayer.getEyes(RING_ADD)), ms, r, c[0] * a * 1.5f, c[1] * a * 1.5f, c[2] * a * 1.5f);
            ms.pop();
        }

        // the name: the brushed kanji in light with the word beneath, after it has planted
        double nameAt = after - 6;
        if (landed && nameAt > 0) {
            float a = Rite.smooth(nameAt / 12.0);
            if (rel >= 0) a *= 1 - Rite.smooth(rel / 25.0);
            if (a > 0.01f) {
                float bob = (float) Math.sin(time * 0.06 + i) * 0.08f;
                ms.push();
                ms.translate(b.x, b.y + h + 11.0 + bob, b.z);
                ms.multiply(this.dispatcher.getRotation());
                float burst = (float) Math.exp(-nameAt / 5.0);
                float s = 0.75f + burst * 1.6f;
                float k = a * (1 + burst * 1.5f);
                billboard(vcp.getBuffer(RenderLayer.getEyes(LABEL[i])), ms, s, c[0] * k, c[1] * k, c[2] * k);
                billboard(vcp.getBuffer(RenderLayer.getEyes(LABEL[i])), ms, s * 0.92f, 0.45f * k, 0.45f * k, 0.45f * k);
                ms.translate(0, -1.05, 0);
                ms.scale(0.03f, -0.03f, 0.03f);
                String name = spaced(Pillars.NAME[i]);
                float w = -text.getWidth(name) / 2f;
                int alpha = Math.max(4, (int) (a * 255)) << 24;
                Matrix4f m = ms.peek().getPositionMatrix();
                text.draw(Text.literal(name), w, 0, alpha | Pillars.rgb(i), false, m, vcp, TextRenderer.TextLayerType.NORMAL, 0, FULL);
                ms.pop();
            }
        }
    }

    private void renderRelic(ArmoryCoreEntity e, int i, Vec3d b, double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        DoaModel model = DoaModels.RELIC;
        if (model == null) return;
        double u = t - Rite.relicAppear(i);
        if (u < 0) return;
        float k = Rite.easeOut(u / 24.0);
        if (rel >= 0) k *= 1 - Rite.smooth(rel / 30.0);
        if (k <= 0.01f) return;
        Vec3d in = new Vec3d(-b.x, 0, -b.z).normalize();
        Vec3d from = b.add(in.multiply(1.1)).add(0, 4.0, 0);
        Vec3d to = b.add(in.multiply(2.2)).add(0, 2.3 + Math.sin(time * 0.05 + i) * 0.15, 0);
        Vec3d p = from.add(to.subtract(from).multiply(k));
        float[] c = Pillars.COLOR[i];
        ms.push();
        ms.translate(p.x, p.y, p.z);
        ms.multiply(new Quaternionf().rotateY((float) (Math.PI - Rite.angle(i) + time * 0.01)));
        ms.scale(k, k, k);
        ms.translate(0, -9 / 16f, 0);
        DoaModel.Poser poser = (bone, rot, off) -> {
            if (bone.equals("relic_core")) {
                rot[1] = (float) (time * 0.06);
                rot[0] = (float) Math.sin(time * 0.03) * 0.3f;
            } else if (bone.equals("relic_ring")) {
                rot[1] = (float) (-time * 0.04);
                rot[2] = 0.25f;
            }
        };
        model.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(RELIC)), FULL, OV, 1, 1, 1, 1, poser);
        float pulse = 0.8f + 0.2f * MathHelper.sin((float) time * 0.13f + i);
        model.render(ms, vcp.getBuffer(RenderLayer.getEyes(RELIC_GLOW)), FULL, OV, c[0] * pulse, c[1] * pulse, c[2] * pulse, 1, poser);
        ms.pop();
        ms.push();
        ms.translate(p.x, p.y, p.z);
        ms.multiply(this.dispatcher.getRotation());
        float hk = k * 0.35f * pulse;
        billboard(vcp.getBuffer(RenderLayer.getEyes(HALO)), ms, 1.3f * k, c[0] * hk, c[1] * hk, c[2] * hk);
        ms.pop();
    }

    // ------------------------------------------------------------------------------------------------ the orb

    private void renderCore(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float k = Rite.core(t, rel);
        if (k <= 0.01f) return;
        double r = 1.25 * k * (1 + 0.04 * Math.sin(time * 0.2));
        ms.push();
        ms.translate(0, 3.6, 0);
        ms.multiply(new Quaternionf().rotateY((float) (time * 0.02)));
        sphere(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SKIN)), ms, r, 14, 20, time);
        ms.pop();
        ms.push();
        ms.translate(0, 3.6, 0);
        ms.multiply(this.dispatcher.getRotation());
        float[] col = Pillars.colorAt(time * 0.02);
        float hk = 0.55f * k;
        billboard(vcp.getBuffer(RenderLayer.getEyes(HALO)), ms, (float) (r * 2.4), col[0] * hk, col[1] * hk, col[2] * hk);
        ms.pop();
    }

    private void renderCorruption(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float c = Rite.corrupt(t, rel);
        if (c <= 0.001f) return;
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SKIN));
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(EDGE));
        int cols = DOME_COLS;
        double y = 0.04;
        for (int j = 0; j < cols; j++) {
            double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
            double r0 = Rite.corruptEdge(a0, c, t), r1 = Rite.corruptEdge(a1, c, t);
            float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
            // filled wedge (as a degenerate quad from the centre)
            vtx(vc, m, en, 0, (float) y, 0, 0.5f, 0.5f, 1, 1, 1, 1, FULL, 0, 1, 0);
            vtx(vc, m, en, 0, (float) y, 0, 0.5f, 0.5f, 1, 1, 1, 1, FULL, 0, 1, 0);
            vtx(vc, m, en, (float) (s1 * r1), (float) y, (float) (c1 * r1), 0.5f + s1 * (float) r1 / 8f, 0.5f + c1 * (float) r1 / 8f, 1, 1, 1, 1, FULL, 0, 1, 0);
            vtx(vc, m, en, (float) (s0 * r0), (float) y, (float) (c0 * r0), 0.5f + s0 * (float) r0 / 8f, 0.5f + c0 * (float) r0 / 8f, 1, 1, 1, 1, FULL, 0, 1, 0);
            // the burning frontier
            if (c < 0.999f || rel >= 0) {
                float[] col = Pillars.colorAt(a0);
                float g = 1.1f;
                double w = 1.4;
                double i0 = Math.max(0, r0 - w), i1 = Math.max(0, r1 - w);
                float u0 = (float) j / cols * 6, u1 = (float) (j + 1) / cols * 6;
                float yy = (float) (y + 0.02);
                vtx(glow, m, en, (float) (s0 * i0), yy, (float) (c0 * i0), u0, 1, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s1 * i1), yy, (float) (c1 * i1), u1, 1, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s1 * r1), yy, (float) (c1 * r1), u1, 0, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s0 * r0), yy, (float) (c0 * r0), u0, 0, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s0 * r0), yy, (float) (c0 * r0), u0, 0, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s1 * r1), yy, (float) (c1 * r1), u1, 0, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s1 * i1), yy, (float) (c1 * i1), u1, 1, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(glow, m, en, (float) (s0 * i0), yy, (float) (c0 * i0), u0, 1, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
            }
        }
        // the seal ring that binds the seven: a thin ring of light through every pillar
        if (t >= Rite.SEAL) {
            float k = Rite.smooth((t - Rite.SEAL) / 16.0) * (rel >= 0 ? 1 - Rite.smooth(rel / 40.0) : 1);
            VertexConsumer ring = vcp.getBuffer(RenderLayer.getEyes(EDGE));
            double r = Rite.PILLAR_RADIUS;
            float yy = (float) (y + 0.05);
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                float[] col = Pillars.colorAt(a0);
                float g = 0.9f * k;
                float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
                double ri = r - 0.18, ro = r + 0.18;
                float u0 = (float) j / cols * 10, u1 = (float) (j + 1) / cols * 10;
                vtx(ring, m, en, (float) (s0 * ri), yy, (float) (c0 * ri), u0, 0.6f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s1 * ri), yy, (float) (c1 * ri), u1, 0.6f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s1 * ro), yy, (float) (c1 * ro), u1, 0.1f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s0 * ro), yy, (float) (c0 * ro), u0, 0.1f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s0 * ro), yy, (float) (c0 * ro), u0, 0.1f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s1 * ro), yy, (float) (c1 * ro), u1, 0.1f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s1 * ri), yy, (float) (c1 * ri), u1, 0.6f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
                vtx(ring, m, en, (float) (s0 * ri), yy, (float) (c0 * ri), u0, 0.6f, col[0] * g, col[1] * g, col[2] * g, 1, FULL, 0, 1, 0);
            }
        }
    }

    private void renderDome(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float p = Rite.dome(t, rel);
        if (p <= 0.0005f) return;
        double R = Rite.ORB_RADIUS;
        double et = rel >= 0 ? time : t;
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        VertexConsumer skin = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SKIN));
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(EDGE));
        VertexConsumer ink = vcp.getBuffer(RenderLayer.getEntityTranslucent(EDGE_INK));
        int cols = DOME_COLS;
        float flow = (float) (time * 0.0015);
        for (int j = 0; j < cols; j++) {
            double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
            double e0 = Rite.domeEdge(a0, p, et), e1 = Rite.domeEdge(a1, p, et);
            double eTop = Math.max(e0, e1);
            float u0 = (float) j / cols * 10, u1 = (float) (j + 1) / cols * 10;
            for (double lat = 0; lat < eTop; lat += BAND) {
                double l1 = Math.min(lat + BAND, eTop);
                double la0 = Math.min(lat, e0), la1 = Math.min(lat, e1);
                double lb0 = Math.min(l1, e0), lb1 = Math.min(l1, e1);
                if (lb0 <= la0 && lb1 <= la1) continue;
                domeVtx(skin, m, en, R, a0, la0, u0, flow);
                domeVtx(skin, m, en, R, a1, la1, u1, flow);
                domeVtx(skin, m, en, R, a1, lb1, u1, flow);
                domeVtx(skin, m, en, R, a0, lb0, u0, flow);
            }
            // the burning rim (absent once fully closed: the seams then glow softly instead)
            boolean closed = e0 >= 89.9 && e1 >= 89.9;
            float[] col = Pillars.colorAt(a0);
            float g = closed ? 0.18f + 0.08f * MathHelper.sin((float) time * 0.07f + j * 0.3f) : 1.25f;
            double w = closed ? 6 : 9;
            if (!closed || p > 0.999f) {
                double r = R + 0.03;
                double b0 = Math.max(0, e0 - w), b1 = Math.max(0, e1 - w);
                rimQuad(glow, m, en, r, a0, a1, e0, e1, b0, b1, u0, u1, col[0] * g, col[1] * g, col[2] * g, 1);
                rimQuad(glow, m, en, R - 0.03, a0, a1, e0, e1, b0, b1, u0, u1, col[0] * g, col[1] * g, col[2] * g, 1);
                if (!closed) {
                    double c0 = Math.min(90, e0 + 2.5), c1 = Math.min(90, e1 + 2.5);
                    rimQuad(ink, m, en, R + 0.05, a0, a1, c0, c1, Math.max(0, e0 - 1), Math.max(0, e1 - 1), u0, u1, 0.02f, 0.015f, 0.02f, 0.9f);
                }
            }
        }
        // runes inside the sphere: each pillar's name burned faintly into the dark wall behind it
        if (p > 0.6f) {
            float k = Rite.smooth((p - 0.6) / 0.4) * (0.32f + 0.08f * MathHelper.sin((float) time * 0.05f));
            for (int i = 0; i < Pillars.COUNT; i++) {
                float[] c = Pillars.COLOR[i];
                double a = Rite.angle(i);
                double el = Math.toRadians(24);
                double rr = R - 0.15;
                ms.push();
                ms.translate(Math.sin(a) * Math.cos(el) * rr, Math.sin(el) * rr, -Math.cos(a) * Math.cos(el) * rr);
                ms.multiply(new Quaternionf().rotateY((float) (Math.PI - a)));
                ms.multiply(new Quaternionf().rotateX((float) -el));
                billboard(vcp.getBuffer(RenderLayer.getEyes(LABEL[i])), ms, 2.6f, c[0] * k, c[1] * k, c[2] * k);
                ms.pop();
            }
        }
    }

    private static void domeVtx(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, double R, double a, double latDeg, float u, float flow) {
        double el = Math.toRadians(latDeg);
        float x = (float) (Math.sin(a) * Math.cos(el) * R), y = (float) (Math.sin(el) * R), z = (float) (-Math.cos(a) * Math.cos(el) * R);
        float v = (float) (latDeg / 90.0 * 4) + flow;
        vtx(vc, m, en, x, y, z, u, v, 1, 1, 1, 1, FULL, x / (float) R, y / (float) R, z / (float) R);
    }

    private static void rimQuad(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, double R, double a0, double a1,
                                double top0, double top1, double bot0, double bot1, float u0, float u1, float r, float g, float b, float al) {
        float[] p00 = sph(R, a0, bot0), p10 = sph(R, a1, bot1), p11 = sph(R, a1, top1), p01 = sph(R, a0, top0);
        vtx(vc, m, en, p00[0], p00[1], p00[2], u0, 1, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p10[0], p10[1], p10[2], u1, 1, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p11[0], p11[1], p11[2], u1, 0, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p01[0], p01[1], p01[2], u0, 0, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p01[0], p01[1], p01[2], u0, 0, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p11[0], p11[1], p11[2], u1, 0, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p10[0], p10[1], p10[2], u1, 1, r, g, b, al, FULL, 0, 1, 0);
        vtx(vc, m, en, p00[0], p00[1], p00[2], u0, 1, r, g, b, al, FULL, 0, 1, 0);
    }

    private static float[] sph(double R, double a, double latDeg) {
        double el = Math.toRadians(latDeg);
        return new float[]{(float) (Math.sin(a) * Math.cos(el) * R), (float) (Math.sin(el) * R), (float) (-Math.cos(a) * Math.cos(el) * R)};
    }

    private static void sphere(VertexConsumer vc, MatrixStack ms, double r, int lats, int lons, double time) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        for (int i = 0; i < lats; i++) {
            double t0 = -90 + 180.0 * i / lats, t1 = -90 + 180.0 * (i + 1) / lats;
            for (int j = 0; j < lons; j++) {
                double a0 = Math.PI * 2 * j / lons, a1 = Math.PI * 2 * (j + 1) / lons;
                float[] p00 = sph(r, a0, t0), p10 = sph(r, a1, t0), p11 = sph(r, a1, t1), p01 = sph(r, a0, t1);
                float u0 = (float) j / lons * 2, u1 = (float) (j + 1) / lons * 2, v0 = (float) i / lats, v1 = (float) (i + 1) / lats;
                vtx(vc, m, en, p00[0], p00[1], p00[2], u0, v0, 1, 1, 1, 1, FULL, 0, 1, 0);
                vtx(vc, m, en, p10[0], p10[1], p10[2], u1, v0, 1, 1, 1, 1, FULL, 0, 1, 0);
                vtx(vc, m, en, p11[0], p11[1], p11[2], u1, v1, 1, 1, 1, 1, FULL, 0, 1, 0);
                vtx(vc, m, en, p01[0], p01[1], p01[2], u0, v1, 1, 1, 1, 1, FULL, 0, 1, 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ primitives

    private static void vtx(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, float x, float y, float z, float u, float v,
                            float r, float g, float b, float a, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(Math.min(1, r), Math.min(1, g), Math.min(1, b), Math.min(1, a)).texture(u, v).overlay(OV).light(light).normal(en, nx, ny, nz);
    }

    /** Flat quad on the ground, both sides, additive (colour = brightness). */
    private static void flat(VertexConsumer vc, MatrixStack ms, double r, float cr, float cg, float cb) {
        flatColor(vc, ms, r, cr, cg, cb, 1);
    }

    private static void flatColor(VertexConsumer vc, MatrixStack ms, double r, float cr, float cg, float cb, float a) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        float s = (float) r;
        vtx(vc, m, en, -s, 0, s, 0, 1, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, s, 0, s, 1, 1, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, s, 0, -s, 1, 0, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, -s, 0, -s, 0, 0, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, -s, 0, -s, 0, 0, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, s, 0, -s, 1, 0, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, s, 0, s, 1, 1, cr, cg, cb, a, FULL, 0, 1, 0);
        vtx(vc, m, en, -s, 0, s, 0, 1, cr, cg, cb, a, FULL, 0, 1, 0);
    }

    /** Quad in the local XY plane (after a camera-facing rotation), oriented like vanilla particles. */
    private static void billboard(VertexConsumer vc, MatrixStack ms, float s, float r, float g, float b) {
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        vtx(vc, m, en, -s, -s, 0, 1, 1, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, -s, s, 0, 1, 0, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, s, s, 0, 0, 0, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, s, -s, 0, 0, 1, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, s, -s, 0, 0, 1, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, s, s, 0, 0, 0, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, -s, s, 0, 1, 0, r, g, b, 1, FULL, 0, 0, 1);
        vtx(vc, m, en, -s, -s, 0, 1, 1, r, g, b, 1, FULL, 0, 0, 1);
    }

    /** A vertical strip at (base.x, base.z) facing the camera around the Y axis, from y0 to y1 above base.y. */
    private static void column(VertexConsumer vc, MatrixStack ms, Vec3d base, Vec3d cam, double halfWidth, double y0, double y1,
                               float r, float g, float b, float vBottom, float vTop) {
        double dx = cam.x - base.x, dz = cam.z - base.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            dx = 1;
            len = 1;
        }
        float rx = (float) (dz / len * halfWidth), rz = (float) (-dx / len * halfWidth);
        float bx = (float) base.x, bz = (float) base.z, ya = (float) (base.y + y0), yb = (float) (base.y + y1);
        MatrixStack.Entry en = ms.peek();
        Matrix4f m = en.getPositionMatrix();
        vtx(vc, m, en, bx - rx, ya, bz - rz, 0, vBottom, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx + rx, ya, bz + rz, 1, vBottom, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx + rx, yb, bz + rz, 1, vTop, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx - rx, yb, bz - rz, 0, vTop, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx - rx, yb, bz - rz, 0, vTop, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx + rx, yb, bz + rz, 1, vTop, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx + rx, ya, bz + rz, 1, vBottom, r, g, b, 1, FULL, 0, 1, 0);
        vtx(vc, m, en, bx - rx, ya, bz - rz, 0, vBottom, r, g, b, 1, FULL, 0, 1, 0);
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            if (k > 0) b.append(' ');
            b.append(s.charAt(k));
        }
        return b.toString();
    }
}
