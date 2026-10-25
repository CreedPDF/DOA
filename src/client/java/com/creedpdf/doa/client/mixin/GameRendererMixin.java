package com.creedpdf.doa.client.mixin;

import com.creedpdf.doa.client.cine.Cutscene;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No first-person hand floating in front of the cutscene camera. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void doa$hideHand(CallbackInfo ci) {
        if (Cutscene.isActive()) ci.cancel();
    }
}
