package com.creedpdf.doa.client.mixin;

import com.creedpdf.doa.client.cine.Cutscene;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private Vec3d pos;

    @Shadow
    private float yaw;

    @Shadow
    private float pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void doa$cutscene(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        Vec3d pos = this.pos;
        float yaw = this.yaw, pitch = this.pitch;
        if (!Cutscene.isActive() && Cutscene.shake(tickDelta) == null) return;
        if (Cutscene.isActive()) {
            Cutscene.Shot shot = Cutscene.shot(tickDelta, pos, yaw, pitch);
            if (shot != null) {
                pos = shot.pos();
                yaw = shot.yaw();
                pitch = shot.pitch();
                if (shot.thirdPerson()) this.thirdPerson = true;
            }
        }
        float[] s = Cutscene.shake(tickDelta);
        if (s != null) {
            pos = pos.add(s[0], s[1], s[2]);
            yaw += s[3];
            pitch += s[4];
        }
        setRotation(yaw, pitch);
        setPos(pos.x, pos.y, pos.z);
    }
}
