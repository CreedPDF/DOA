package com.creedpdf.doa;

import com.creedpdf.doa.registry.DoaBlocks;
import com.creedpdf.doa.registry.DoaEntities;
import com.creedpdf.doa.registry.DoaItems;
import com.creedpdf.doa.registry.DoaParticles;
import com.creedpdf.doa.registry.DoaSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DOA - Delux's Orbital Armory. */
public class DoaMod implements ModInitializer {
    public static final String MOD_ID = "doa";
    public static final Logger LOG = LoggerFactory.getLogger("DOA");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DoaSounds.init();
        DoaBlocks.init();
        DoaEntities.init();
        DoaItems.init();
        DoaParticles.init();
        LOG.info("The Orbital Armory is listening.");
    }
}
