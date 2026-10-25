package com.creedpdf.doa.client;

import com.creedpdf.doa.client.cine.Cutscene;
import com.creedpdf.doa.client.fx.Fx;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.client.render.ArmoryCoreRenderer;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public class DoaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(DoaEntities.ARMORY_CORE, ArmoryCoreRenderer::new);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new DoaModels());
        Fx.register();
        ArmoryCoreEntity.clientTick = Fx::tick;
        ClientTickEvents.END_CLIENT_TICK.register(Cutscene::clientTick);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(com.creedpdf.doa.registry.DoaItems.ARMORY_STAFF,
                new com.creedpdf.doa.client.render.StaffRenderer());
        com.creedpdf.doa.client.text.Toasts.init();
    }
}
