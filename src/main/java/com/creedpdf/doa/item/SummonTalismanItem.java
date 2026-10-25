package com.creedpdf.doa.item;

import com.creedpdf.doa.Rite;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.registry.DoaEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** 召 - the ofuda that calls the Orbital Armory down. Use again inside the sealed sphere to release it. */
public class SummonTalismanItem extends Item {
    public SummonTalismanItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        List<ArmoryCoreEntity> mine = world.getEntitiesByClass(ArmoryCoreEntity.class, user.getBoundingBox().expand(160),
                e -> e.isOwnedBy(user) && !e.isRemoved());
        if (!mine.isEmpty()) {
            ArmoryCoreEntity core = mine.get(0);
            if (core.isReleasing()) {
                user.sendMessage(Text.translatable("message.doa.releasing").formatted(Formatting.GRAY), true);
            } else if (core.getRiteTicks() < Rite.SEALED) {
                user.sendMessage(Text.translatable("message.doa.in_progress").formatted(Formatting.DARK_RED), true);
            } else {
                core.startRelease();
                user.sendMessage(Text.translatable("message.doa.release").formatted(Formatting.GRAY), true);
                user.getItemCooldownManager().set(this, 60);
            }
            return TypedActionResult.success(stack);
        }
        ArmoryCoreEntity core = new ArmoryCoreEntity(DoaEntities.ARMORY_CORE, world);
        core.begin(user);
        world.spawnEntity(core);
        user.getItemCooldownManager().set(this, 100);
        user.sendMessage(Text.translatable("message.doa.call").formatted(Formatting.DARK_RED), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.doa.summon_talisman.tip1").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.doa.summon_talisman.tip2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.doa.summon_talisman.tip3").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
