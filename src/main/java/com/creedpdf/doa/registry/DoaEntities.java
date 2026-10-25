package com.creedpdf.doa.registry;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class DoaEntities {
    public static final EntityType<ArmoryCoreEntity> ARMORY_CORE = Registry.register(Registries.ENTITY_TYPE,
            DoaMod.id("armory_core"),
            EntityType.Builder.<ArmoryCoreEntity>create(ArmoryCoreEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .trackingTickInterval(1)
                    .build("armory_core"));

    private DoaEntities() {
    }

    public static void init() {
    }
}
