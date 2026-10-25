package com.creedpdf.doa.registry;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.item.SummonTalismanItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class DoaItems {
    public static final Item SUMMON_TALISMAN = Registry.register(Registries.ITEM, DoaMod.id("summon_talisman"),
            new SummonTalismanItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    private DoaItems() {
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(SUMMON_TALISMAN));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(SUMMON_TALISMAN));
    }
}
