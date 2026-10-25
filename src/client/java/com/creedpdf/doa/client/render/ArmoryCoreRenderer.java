package com.creedpdf.doa.client.render;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.model.DoaMeshSet;
import com.creedpdf.doa.client.model.DoaModel;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.client.move.Kit;
import com.creedpdf.doa.client.move.Move;
import com.creedpdf.doa.client.move.MoveCtx;
import com.creedpdf.doa.client.move.Moves;
import com.creedpdf.doa.client.text.DoaFont;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the rite from the armory's timeline: each pillar first as a flickering star high in the heavens, then its
 * near-instant plunge, the impact, its name in DOA's lettering; the black core orb, the ink corruption over the
 * ground, the opaque dark sphere with its burning ragged rim and inner runes; the relics; and the running move.
 */
public class ArmoryCoreRenderer extends EntityRenderer<ArmoryCoreEntity> {
    private static final int FULL = Bill.FULL;
    private static final int OV = OverlayTexture.DEFAULT_UV;

    private static final Identifier BODY = DoaMod.id("textures/entity/pillar/body.png");
    private static final Identifier BODY_GLOW = DoaMod.id("textures/entity/pillar/body_glow.png");
    private static final Identifier[] PANEL = new Identifier[Pillars.COUNT];
    private static final Identifier[] PANEL_GLOW = new Identifier[Pillars.COUNT];
    private static final Identifier RELIC = DoaMod.id("textures/entity/relic.png");
    private static final Identifier RELIC_GLOW = DoaMod.id("textures/entity/relic_glow.png");
    private static final Identifier EDGE_INK = DoaMod.id("textures/effect/orb_edge.png");

    static {
        for (int i = 0; i < Pillars.COUNT; i++) {
            PANEL[i] = DoaMod.id("textures/entity/pillar/panel_" + Pillars.ID[i] + ".png");
            PANEL_GLOW[i] = DoaMod.id("textures/entity/pillar/panel_" + Pillars.ID[i] + "_glow.png");
        }
    }

    private static final int DOME_COLS = 128;
    private static final double BAND = 2.5;
    private static final double PILLAR_TOP = 14.3;

    private double oculus;

    public ArmoryCoreRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public Identifier getTexture(ArmoryCoreEntity entity) {
        return Kit.SKIN;
    }

    @Override
    public boolean shouldRender(ArmoryCoreEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ArmoryCoreEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        Bill.update(this.dispatcher.camera);
        int relI = e.getReleaseTicks();
        double t = e.getRiteTicks() + (relI >= 0 ? 0 : td);
        double rel = relI >= 0 ? relI + td : -1;
        double time = e.age + td;
        Vec3d origin = e.getLerpedPos(td);
        Vec3d cam = this.dispatcher.camera.getPos().subtract(origin);
        MoveCtx mc = null;
        Move move = null;
        if (e.getAbility() >= 0) {
            move = Moves.get(e.getAbility());
            mc = new MoveCtx(e, e.getAbilityTicks() + td, td, time, ms, vcp, origin, cam, Cutscene.isAbilityCutscene(e));
        }
        boolean pocket = mc != null && mc.mine && Moves.inPocket(e.getAbility(), mc.a);
        if (!pocket) {
            MoveCtx base = new MoveCtx(e, 0, td, time, ms, vcp, origin, cam, false);
            for (int i = 0; i < Pillars.COUNT; i++) {
                if (!Rite.visible(i, t + 14)) continue;
                Vec3d b = e.pillarBase(i).subtract(origin);
                renderSeal(i, b, t, rel, time, ms, vcp);
                if (!Rite.visible(i, t)) continue;
                renderPillar(e, base, i, b, t, rel, time, ms, vcp, cam);
            }
            if (rel < 0 || Rite.dome(t, rel) > 0) {
                renderCore(t, rel, time, ms, vcp);
                renderCorruption(t, rel, time, ms, vcp);
                oculus = move != null ? move.oculus(mc.a) : 0;
                renderDome(t, rel, time, ms, vcp);
            }
            if (t >= Rite.SEALED) for (int i = 0; i < Pillars.COUNT; i++) renderRelic(e, i, origin, t, rel, time, ms, vcp);
        }
        if (move != null) move.render(mc);
        super.render(e, yaw, td, ms, vcp, light);
    }

    // ------------------------------------------------------------------------------------------------ pillars

    private void renderSeal(int i, Vec3d b, double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float[] c = Pillars.COLOR[i];
        double u = t - Rite.spawn(i);
        float grow = Rite.easeOut((u + 14) / 30.0);
        boolean landed = Rite.landed(i, t);
        float flash = landed ? (float) Math.exp(-(t - Rite.land(i)) / 6.0) : 0;
        float charge = landed ? 0 : Rite.smooth((u + 14) / (Rite.FALL + 14.0)) * (0.6f + 0.4f * (float) Math.sin(time * 0.9));
        float rest = landed ? 0.45f + 0.1f * MathHelper.sin((float) time * 0.08f + i) : 0;
        float k = charge * 0.9f + flash * 1.8f + rest;
        if (rel >= 0) k *= 1 - Rite.smooth((rel - 30) / 40.0);
        if (k <= 0.01f) return;
        double r = 4.6 * grow + flash * 1.2;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(Kit.SEAL[i]));
        float cs = (float) Math.cos(time * 0.012 + i), sn = (float) Math.sin(time * 0.012 + i);
        Bill.plane(vc, ms, b.x, b.y + 0.06, b.z, new float[]{cs, 0, sn}, new float[]{-sn, 0, cs}, (float) r, c[0] * k, c[1] * k, c[2] * k, 1);
    }

    private void renderPillar(ArmoryCoreEntity e, MoveCtx base, int i, Vec3d b, double t, double rel, double time, MatrixStack ms,
                              VertexConsumerProvider vcp, Vec3d cam) {
        float[] c = Pillars.COLOR[i];
        boolean landed = Rite.landed(i, t);
        double h = landed ? 0 : Rite.fallHeight(i, t);
        double after = t - Rite.land(i);
        if (landed && after < 10) h -= 0.45 * Math.sin(after / 10.0 * Math.PI) * Math.exp(-after / 6);
        if (rel >= 0) h += Rite.ascend(rel);

        // ---- the star: so high and so fast it reads as a flickering star until the last instant
        if (!landed && rel < 0) {
            Vec3d head = b.add(0, h, 0);
            Vec3d d = head.subtract(cam);
            double dist = d.length();
            Vec3d drawAt = dist > 70 ? cam.add(d.multiply(70 / dist)) : head;
            float pl = Rite.plunge(i, t);
            double u = t - Rite.spawn(i);
            float flick = (float) (0.55 + 0.45 * Math.abs(Math.sin(u * 1.9 + i) * Math.sin(u * 3.7 + i * 2)));
            float appear = Rite.smooth(u / 6.0);
            float size = dist > 70 ? (float) ((1.3 + 0.5 * Math.sin(u * 0.6)) * appear * (1 + pl * 2.5)) : (float) (2.5 + pl * 3);
            VertexConsumer gl = vcp.getBuffer(RenderLayer.getEyes(Cosmos.GLARE));
            Bill.sprite(gl, ms, drawAt.x, drawAt.y, drawAt.z, size * 3.2f, (float) (u * 0.05), c[0] * flick, c[1] * flick, c[2] * flick);
            Bill.sprite(gl, ms, drawAt.x, drawAt.y, drawAt.z, size * 1.1f, 0, flick, flick, flick);
            if (pl > 0) {
                VertexConsumer sv = vcp.getBuffer(RenderLayer.getEyes(Kit.TRAIL));
                double top = Math.min(h + 160 * (1 - pl * 0.3), Rite.FALL_HEIGHT);
                double bottom = h + PILLAR_TOP * 0.5;
                Bill.ribbon(sv, ms, b.x, b.y + top, b.z, b.x, b.y + bottom, b.z, cam.x, cam.y, cam.z, 2.4f, c[0] * 1.4f, c[1] * 1.4f, c[2] * 1.4f, 1, 0, 1);
                Bill.ribbon(sv, ms, b.x, b.y + top, b.z, b.x, b.y + bottom, b.z, cam.x, cam.y, cam.z, 0.7f, 1.3f, 1.3f, 1.2f, 1, 0, 1);
            }
            float ck = Rite.smooth((u - 6) / 16.0) * 0.6f + pl;
            Kit.column(base, Kit.COLUMN, b, 0.9 + pl, 0, 110, c[0] * ck, c[1] * ck, c[2] * ck);
            if (h > 140) return;
        }

        // ---- the pillar itself
        DoaMeshSet model = DoaModels.PILLAR2, panel = DoaModels.PANEL;
        if (model != null) {
            ms.push();
            ms.translate(b.x, b.y + h, b.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotation((float) (Math.PI - Rite.angle(i))));
            int light = landed && rel < 0 ? WorldRenderer.getLightmapCoordinates(e.getWorld(), BlockPos.ofFloored(e.pillarBase(i).add(0, 2, 0))) : FULL;
            light = Math.max(light & 0xFFFF, 0x70) | (light & 0xFFFF0000);
            DoaMeshSet.Pose pose = (n, m) -> {
                if (n.equals("rings")) {
                    m.translate(0, Math.sin(time * 0.04 + i) * 0.15, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotation((float) (time * 0.02 + i)));
                } else if (n.equals("crystal")) {
                    m.translate(0, Math.sin(time * 0.05 + i) * 0.25, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotation((float) (-time * 0.035)));
                }
                return true;
            };
            model.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BODY)), light, pose, null);
            float pulse = landed ? 0.75f + 0.25f * MathHelper.sin((float) time * 0.11f + i * 0.9f) : 1.2f;
            if (landed && after < 12) pulse += (float) (1.4 * Math.exp(-after / 4));
            final float pk = pulse;
            model.render(ms, vcp.getBuffer(RenderLayer.getEyes(BODY_GLOW)), FULL, pose, (p, f, nx, ny, nz, o) -> {
                float k = p.equals("crystal") ? pk * 1.3f : pk;
                o[0] = c[0] * k;
                o[1] = c[1] * k;
                o[2] = c[2] * k;
                o[3] = 1;
            });
            if (panel != null) {
                panel.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(PANEL[i])), light, null, null);
                panel.render(ms, vcp.getBuffer(RenderLayer.getEyes(PANEL_GLOW[i])), FULL, null, (p, f, nx, ny, nz, o) -> {
                    o[0] = c[0] * pk * 1.2f;
                    o[1] = c[1] * pk * 1.2f;
                    o[2] = c[2] * pk * 1.2f;
                    o[3] = 1;
                });
            }
            ms.pop();
        }

        // ---- impact light and shockwave
        if (landed && after < 30 && rel < 0) {
            float ck = (float) Math.exp(-after / 9.0) * 1.5f;
            Kit.column(base, Kit.COLUMN, b, 1.2 + 0.9 * Math.exp(-after / 5), 0, 110, c[0] * ck, c[1] * ck, c[2] * ck);
            float k = Rite.clamp01(after / 24.0);
            Kit.shock(base, b, 1 + 15 * Rite.easeOut(k), (1 - k) * (1 - k), c);
        }
        if (rel >= Rite.REL_ASCEND_START) {
            VertexConsumer sv = vcp.getBuffer(RenderLayer.getEyes(Kit.TRAIL));
            Bill.ribbon(sv, ms, b.x, b.y + h - 40, b.z, b.x, b.y + h, b.z, cam.x, cam.y, cam.z, 1.6f, c[0], c[1], c[2], 1, 0, 1);
        }

        // ---- the name: the kanji and the word, in DOA's own lettering
        double nameAt = after - 22;
        if (landed && nameAt > 0) {
            float a = Rite.smooth(nameAt / 12.0);
            if (rel >= 0) a *= 1 - Rite.smooth(rel / 25.0);
            if (a > 0.01f) {
                float bob = (float) Math.sin(time * 0.06 + i) * 0.1f;
                float burst = (float) Math.exp(-nameAt / 5.0);
                float k = a * (1 + burst * 1.5f);
                double y = b.y + h + PILLAR_TOP + 2.4 + bob;
                DoaFont.world(ms, vcp, Pillars.KANJI[i], b.x, y, b.z, 2.0f + burst * 2.5f, 0, c[0] * k, c[1] * k, c[2] * k);
                DoaFont.world(ms, vcp, Pillars.NAME[i], b.x, y - 1.7, b.z, 0.55f, 0.35f, c[0] * a * 1.2f, c[1] * a * 1.2f, c[2] * a * 1.2f);
            }
        }
    }

    private void renderRelic(ArmoryCoreEntity e, int i, Vec3d origin, double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        DoaModel model = DoaModels.RELIC;
        if (model == null) return;
        double u = t - Rite.relicAppear(i);
        if (u < 0) return;
        float k = Rite.easeOut(u / 24.0);
        if (rel >= 0) k *= 1 - Rite.smooth(rel / 30.0);
        if (k <= 0.01f) return;
        Vec3d b = e.pillarBase(i).subtract(origin);
        Vec3d to = e.relicPos(i).subtract(origin).add(0, Math.sin(time * 0.05 + i) * 0.15, 0);
        Vec3d from = b.add(to.subtract(b).multiply(0.35)).add(0, 5, 0);
        Vec3d p = from.add(to.subtract(from).multiply(k));
        float[] c = Pillars.COLOR[i];
        ms.push();
        ms.translate(p.x, p.y, p.z);
        ms.multiply(new Quaternionf().rotateY((float) (Math.PI - Rite.angle(i) + time * 0.01)));
        ms.scale(k * 1.3f, k * 1.3f, k * 1.3f);
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
        boolean ready = e.isRelicReady(i);
        boolean active = e.getAbility() == i;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hovered = !active && mc.player != null && e.getOwnerNetId() == mc.player.getId() && e.getAbility() < 0 && rel < 0
                && e.lookedAtRelic(mc.player) == i;
        if (!ready && !active) pulse *= 0.25f;
        if (active) pulse = 1.6f + 0.4f * MathHelper.sin((float) time * 0.5f);
        if (hovered) pulse *= 1.5f;
        model.render(ms, vcp.getBuffer(RenderLayer.getEyes(RELIC_GLOW)), FULL, OV, c[0] * pulse, c[1] * pulse, c[2] * pulse, 1, poser);
        ms.pop();
        float hk = k * 0.35f * pulse;
        Bill.sprite(vcp.getBuffer(RenderLayer.getEyes(Kit.HALO)), ms, p.x, p.y, p.z, (hovered || active ? 2.4f : 1.5f) * k, 0, c[0] * hk, c[1] * hk, c[2] * hk);
        if (hovered) {
            DoaFont.world(ms, vcp, Pillars.KANJI[i] + "  " + Abilities.TITLE[i], p.x, p.y + 1.6, p.z, 0.36f, 0.2f, c[0] * 1.3f, c[1] * 1.3f, c[2] * 1.3f);
            DoaFont.world(ms, vcp, ready ? "awaken" : "resting", p.x, p.y + 1.15, p.z, 0.22f, 0.25f, 0.85f, 0.82f, 0.76f);
        }
    }

    // ------------------------------------------------------------------------------------------------ the orb

    private void renderCore(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float k = Rite.core(t, rel);
        if (k <= 0.01f) return;
        double r = 1.8 * k * (1 + 0.04 * Math.sin(time * 0.2));
        var s = DoaModels.PLANET;
        if (s != null)
            s.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Kit.SKIN)), new org.joml.Vector3f(0, 5f, 0), (float) r,
                    new Quaternionf().rotateY((float) (time * 0.02)), FULL, 0, false, (x, y, z, nx, ny, nz, u, v, o) -> {
                        o[0] = 1;
                        o[1] = 1;
                        o[2] = 1;
                        o[3] = 1;
                    });
        float[] col = Pillars.colorAt(time * 0.02);
        float hk = 0.55f * k;
        Bill.sprite(vcp.getBuffer(RenderLayer.getEyes(Kit.HALO)), ms, 0, 5, 0, (float) (r * 2.4), 0, col[0] * hk, col[1] * hk, col[2] * hk);
    }

    private void renderCorruption(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float c = Rite.corrupt(t, rel);
        if (c <= 0.001f) return;
        int cols = DOME_COLS;
        float y = 0.04f;
        {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Kit.SKIN));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                double r0 = Rite.corruptEdge(a0, c, t), r1 = Rite.corruptEdge(a1, c, t);
                float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
                Bill.vtx(vc, m, en, 0, y, 0, 0.5f, 0.5f, 1, 1, 1, 1);
                Bill.vtx(vc, m, en, 0, y, 0, 0.5f, 0.5f, 1, 1, 1, 1);
                Bill.vtx(vc, m, en, (float) (s1 * r1), y, (float) (c1 * r1), 0.5f + s1 * (float) r1 / 8f, 0.5f + c1 * (float) r1 / 8f, 1, 1, 1, 1);
                Bill.vtx(vc, m, en, (float) (s0 * r0), y, (float) (c0 * r0), 0.5f + s0 * (float) r0 / 8f, 0.5f + c0 * (float) r0 / 8f, 1, 1, 1, 1);
            }
        }
        if (c < 0.999f || rel >= 0) {
            VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(Kit.EDGE));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                double r0 = Rite.corruptEdge(a0, c, t), r1 = Rite.corruptEdge(a1, c, t);
                float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
                float[] col = Pillars.colorAt(a0);
                double i0 = Math.max(0, r0 - 1.8), i1 = Math.max(0, r1 - 1.8);
                float u0 = (float) j / cols * 8, u1 = (float) (j + 1) / cols * 8, yy = y + 0.02f;
                float g = 1.1f;
                quad2(glow, m, en, (float) (s0 * i0), yy, (float) (c0 * i0), u0, 1, (float) (s1 * i1), yy, (float) (c1 * i1), u1, 1,
                        (float) (s1 * r1), yy, (float) (c1 * r1), u1, 0, (float) (s0 * r0), yy, (float) (c0 * r0), u0, 0, col[0] * g, col[1] * g, col[2] * g);
            }
        }
        if (t >= Rite.SEAL) {
            float k = Rite.smooth((t - Rite.SEAL) / 16.0) * (rel >= 0 ? 1 - Rite.smooth(rel / 40.0) : 1);
            VertexConsumer ring = vcp.getBuffer(RenderLayer.getEyes(Kit.EDGE));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            double r = Rite.PILLAR_RADIUS;
            float yy = y + 0.05f;
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                float[] col = Pillars.colorAt(a0);
                float g = 0.9f * k;
                float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
                double ri = r - 0.25, ro = r + 0.25;
                float u0 = (float) j / cols * 14, u1 = (float) (j + 1) / cols * 14;
                quad2(ring, m, en, (float) (s0 * ri), yy, (float) (c0 * ri), u0, 0.6f, (float) (s1 * ri), yy, (float) (c1 * ri), u1, 0.6f,
                        (float) (s1 * ro), yy, (float) (c1 * ro), u1, 0.1f, (float) (s0 * ro), yy, (float) (c0 * ro), u0, 0.1f, col[0] * g, col[1] * g, col[2] * g);
            }
        }
    }

    private void renderDome(double t, double rel, double time, MatrixStack ms, VertexConsumerProvider vcp) {
        float p = Rite.dome(t, rel);
        if (p <= 0.0005f) return;
        double R = Rite.ORB_RADIUS;
        double et = rel >= 0 ? time : t;
        int cols = DOME_COLS;
        float flow = (float) (time * 0.0015);
        double[] edge = new double[cols + 1];
        for (int j = 0; j <= cols; j++) {
            double a = j * Math.PI * 2 / cols;
            double e = Rite.domeEdge(a, p, et);
            if (oculus > 0.01) e = Math.min(e, 90 - oculus + 2.5 * Math.sin(a * 9 + time * 0.1));
            edge[j] = e;
        }
        {
            VertexConsumer skin = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(Kit.SKIN));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                double e0 = edge[j], e1 = edge[j + 1];
                float u0 = (float) j / cols * 14, u1 = (float) (j + 1) / cols * 14;
                if (Math.min(e0, e1) > 4) {
                    float[] q0 = sph(R, a0, 0), q1 = sph(R, a1, 0);
                    Bill.vtx(skin, m, en, q0[0], -8f, q0[2], u0, -0.3f + flow, 1, 1, 1, 1);
                    Bill.vtx(skin, m, en, q1[0], -8f, q1[2], u1, -0.3f + flow, 1, 1, 1, 1);
                    Bill.vtx(skin, m, en, q1[0], 0f, q1[2], u1, flow, 1, 1, 1, 1);
                    Bill.vtx(skin, m, en, q0[0], 0f, q0[2], u0, flow, 1, 1, 1, 1);
                }
                double eTop = Math.max(e0, e1);
                for (double lat = 0; lat < eTop; lat += BAND) {
                    double l1 = Math.min(lat + BAND, eTop);
                    double la0 = Math.min(lat, e0), la1 = Math.min(lat, e1), lb0 = Math.min(l1, e0), lb1 = Math.min(l1, e1);
                    if (lb0 <= la0 && lb1 <= la1) continue;
                    domeVtx(skin, m, en, R, a0, la0, u0, flow);
                    domeVtx(skin, m, en, R, a1, la1, u1, flow);
                    domeVtx(skin, m, en, R, a1, lb1, u1, flow);
                    domeVtx(skin, m, en, R, a0, lb0, u0, flow);
                }
            }
        }
        {
            VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(Kit.EDGE));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                double e0 = edge[j], e1 = edge[j + 1];
                boolean closed = e0 >= 89.9 && e1 >= 89.9;
                float[] col = Pillars.colorAt(a0);
                float g = closed ? 0.18f + 0.08f * MathHelper.sin((float) time * 0.07f + j * 0.3f) : 1.25f;
                double w = closed ? 5 : 7;
                float u0 = (float) j / cols * 14, u1 = (float) (j + 1) / cols * 14;
                double b0 = Math.max(0, e0 - w), b1 = Math.max(0, e1 - w);
                for (double rr : new double[]{R + 0.03, R - 0.03}) {
                    float[] p00 = sph(rr, a0, b0), p10 = sph(rr, a1, b1), p11 = sph(rr, a1, e1), p01 = sph(rr, a0, e0);
                    quad2(glow, m, en, p00[0], p00[1], p00[2], u0, 1, p10[0], p10[1], p10[2], u1, 1, p11[0], p11[1], p11[2], u1, 0, p01[0], p01[1], p01[2], u0, 0,
                            col[0] * g, col[1] * g, col[2] * g);
                }
            }
        }
        if (p < 0.999f || oculus > 0.01) {
            VertexConsumer ink = vcp.getBuffer(RenderLayer.getEntityTranslucent(EDGE_INK));
            MatrixStack.Entry en = ms.peek();
            Matrix4f m = en.getPositionMatrix();
            for (int j = 0; j < cols; j++) {
                double a0 = j * Math.PI * 2 / cols, a1 = (j + 1) * Math.PI * 2 / cols;
                double e0 = edge[j], e1 = edge[j + 1];
                if (e0 >= 89.9 && e1 >= 89.9) continue;
                float u0 = (float) j / cols * 14, u1 = (float) (j + 1) / cols * 14;
                float[] p00 = sph(R + 0.05, a0, Math.max(0, e0 - 1)), p10 = sph(R + 0.05, a1, Math.max(0, e1 - 1)),
                        p11 = sph(R + 0.05, a1, Math.min(90, e1 + 2.5)), p01 = sph(R + 0.05, a0, Math.min(90, e0 + 2.5));
                Bill.vtx(ink, m, en, p00[0], p00[1], p00[2], u0, 1, 0.02f, 0.015f, 0.02f, 0.9f);
                Bill.vtx(ink, m, en, p10[0], p10[1], p10[2], u1, 1, 0.02f, 0.015f, 0.02f, 0.9f);
                Bill.vtx(ink, m, en, p11[0], p11[1], p11[2], u1, 0, 0.02f, 0.015f, 0.02f, 0.9f);
                Bill.vtx(ink, m, en, p01[0], p01[1], p01[2], u0, 0, 0.02f, 0.015f, 0.02f, 0.9f);
            }
        }
        if (p > 0.6f) {
            float k = Rite.smooth((p - 0.6) / 0.4) * (0.32f + 0.08f * MathHelper.sin((float) time * 0.05f));
            for (int i = 0; i < Pillars.COUNT; i++) {
                float[] c = Pillars.COLOR[i];
                float[] q = sph(R - 0.4, Rite.angle(i), 26);
                DoaFont.world(ms, vcp, Pillars.KANJI[i], q[0], q[1], q[2], 4.5f, 0, c[0] * k, c[1] * k, c[2] * k);
            }
        }
    }

    private static void domeVtx(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, double R, double a, double latDeg, float u, float flow) {
        float[] q = sph(R, a, latDeg);
        Bill.vtx(vc, m, en, q[0], q[1], q[2], u, (float) (latDeg / 90.0 * 5) + flow, 1, 1, 1, 1);
    }

    private static void quad2(VertexConsumer vc, Matrix4f m, MatrixStack.Entry en, float x0, float y0, float z0, float u0, float v0, float x1, float y1,
                              float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3,
                              float r, float g, float b) {
        Bill.vtx(vc, m, en, x0, y0, z0, u0, v0, r, g, b, 1);
        Bill.vtx(vc, m, en, x1, y1, z1, u1, v1, r, g, b, 1);
        Bill.vtx(vc, m, en, x2, y2, z2, u2, v2, r, g, b, 1);
        Bill.vtx(vc, m, en, x3, y3, z3, u3, v3, r, g, b, 1);
        Bill.vtx(vc, m, en, x3, y3, z3, u3, v3, r, g, b, 1);
        Bill.vtx(vc, m, en, x2, y2, z2, u2, v2, r, g, b, 1);
        Bill.vtx(vc, m, en, x1, y1, z1, u1, v1, r, g, b, 1);
        Bill.vtx(vc, m, en, x0, y0, z0, u0, v0, r, g, b, 1);
    }

    static float[] sph(double R, double a, double latDeg) {
        double el = Math.toRadians(latDeg);
        return new float[]{(float) (Math.sin(a) * Math.cos(el) * R), (float) (Math.sin(el) * R), (float) (-Math.cos(a) * Math.cos(el) * R)};
    }
}
