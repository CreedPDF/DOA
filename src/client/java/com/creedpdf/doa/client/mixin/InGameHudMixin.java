package com.creedpdf.doa.client.mixin;

import com.creedpdf.doa.client.cine.Cutscene;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During the cutscene the vanilla HUD gives way to letterbox bars, captions and impact frames. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void doa$cutsceneHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Cutscene.isActive()) {
            Cutscene.renderHud(context, tickCounter.getTickDelta(false));
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void doa$impactAfterHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Cutscene.renderHud(context, tickCounter.getTickDelta(false));
    }
}
