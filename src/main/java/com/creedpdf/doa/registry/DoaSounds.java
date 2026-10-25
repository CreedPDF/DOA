package com.creedpdf.doa.registry;

import com.creedpdf.doa.DoaMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class DoaSounds {
    public static final SoundEvent CALL = reg("call");
    public static final SoundEvent DESCEND = reg("descend");
    public static final SoundEvent IMPACT = reg("impact");
    public static final SoundEvent REVEAL = reg("reveal");
    public static final SoundEvent SEAL = reg("seal");
    public static final SoundEvent ORB_RISE = reg("orb_rise");
    public static final SoundEvent ORB_CLOSE = reg("orb_close");
    public static final SoundEvent ORB_HUM = reg("orb_hum");
    public static final SoundEvent DISSOLVE = reg("dissolve");
    public static final SoundEvent ASCEND = reg("ascend");
    public static final SoundEvent RELIC = reg("relic");

    private DoaSounds() {
    }

    private static SoundEvent reg(String name) {
        Identifier id = DoaMod.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }
}
