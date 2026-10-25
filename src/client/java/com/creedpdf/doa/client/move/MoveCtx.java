package com.creedpdf.doa.client.move;

import com.creedpdf.doa.client.render.Cosmos;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** Everything a move needs to draw one frame. Positions are relative to the armory (the matrix origin). */
public final class MoveCtx {
    public final ArmoryCoreEntity core;
    public final double a, time;
    public final float td;
    public final MatrixStack ms;
    public final VertexConsumerProvider vcp;
    public final Vec3d origin, cam;
    /** True when this client is the caller watching the cutscene (pocket scenes are private). */
    public final boolean mine;
    public final Cosmos.Ctx cosmos;

    public MoveCtx(ArmoryCoreEntity core, double a, float td, double time, MatrixStack ms, VertexConsumerProvider vcp, Vec3d origin, Vec3d cam,
                   boolean mine) {
        this.core = core;
        this.a = a;
        this.td = td;
        this.time = time;
        this.ms = ms;
        this.vcp = vcp;
        this.origin = origin;
        this.cam = cam;
        this.mine = mine;
        this.cosmos = new Cosmos.Ctx(ms, vcp, cam, time);
    }

    /** World position to matrix-relative. */
    public Vec3d rel(Vec3d world) {
        return world.subtract(origin);
    }
}
