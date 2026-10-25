package com.creedpdf.doa.registry;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.block.OrbShellBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class DoaBlocks {
    /** Invisible, unbreakable wall that gives the dark sphere (and the planted pillars) their collision. */
    public static final Block ORB_SHELL = Registry.register(Registries.BLOCK, DoaMod.id("orb_shell"),
            new OrbShellBlock(AbstractBlock.Settings.create()
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .nonOpaque()
                    .noBlockBreakParticles()
                    .sounds(BlockSoundGroup.STONE)
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .allowsSpawning((state, world, pos, type) -> false)
                    .solidBlock((state, world, pos) -> false)
                    .suffocates((state, world, pos) -> false)
                    .blockVision((state, world, pos) -> false)));

    private DoaBlocks() {
    }

    public static void init() {
    }
}
