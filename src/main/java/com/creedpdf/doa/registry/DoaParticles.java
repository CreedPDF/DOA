package com.creedpdf.doa.registry;

import com.creedpdf.doa.DoaMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Particle types exist so their sprites get stitched; the client spawns tinted instances of them directly. */
public final class DoaParticles {
    public static final SimpleParticleType MOTE = reg("mote");
    public static final SimpleParticleType SPARK = reg("spark");
    public static final SimpleParticleType PETAL = reg("petal");
    public static final SimpleParticleType INK = reg("ink");
    public static final SimpleParticleType SHARD = reg("shard");

    private DoaParticles() {
    }

    private static SimpleParticleType reg(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, DoaMod.id(name), FabricParticleTypes.simple(true));
    }

    public static void init() {
    }
}
