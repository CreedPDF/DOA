package com.creedpdf.doa.item;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.net.DoaNet;
import com.creedpdf.doa.registry.DoaEntities;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Staff of the Orbital Armory. Hold use to raise it and charge the call (the halo spins up, the seven beads
 * flare); when the charge completes the armory answers. Inside the sealed sphere: look at a relic and use to awaken
 * it, sneak + use to break the seal.
 */
public class ArmoryStaffItem extends Item {
    public static final int CHARGE = 30;
    private static final int RED = 0xD8203A, GREY = 0xC8C0B0;

    public ArmoryStaffItem(Settings settings) {
        super(settings);
    }

    public static ArmoryCoreEntity coreOf(World world, PlayerEntity user) {
        List<ArmoryCoreEntity> mine = world.getEntitiesByClass(ArmoryCoreEntity.class, user.getBoundingBox().expand(200),
                e -> e.isOwnedBy(user) && !e.isRemoved());
        return mine.isEmpty() ? null : mine.get(0);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        ArmoryCoreEntity core = coreOf(world, user);
        if (core == null) {
            user.setCurrentHand(hand);
            if (!world.isClient) world.playSound(null, user.getX(), user.getY(), user.getZ(), DoaSounds.RELIC, SoundCategory.PLAYERS, 1.5f, 0.6f);
            return TypedActionResult.consume(stack);
        }
        if (world.isClient) return TypedActionResult.success(stack);
        if (core.isReleasing()) {
            DoaNet.toast(user, "message.doa.releasing", "", GREY);
        } else if (core.getRiteTicks() < Rite.SEALED) {
            DoaNet.toast(user, "message.doa.in_progress", "", RED);
        } else if (user.isSneaking()) {
            if (core.getAbility() >= 0) {
                DoaNet.toast(user, "message.doa.busy", "", GREY);
            } else {
                core.startRelease();
                DoaNet.toast(user, "message.doa.release", "", GREY);
                user.getItemCooldownManager().set(this, 60);
            }
        } else {
            int relic = core.lookedAtRelic(user);
            if (relic < 0) {
                DoaNet.toast(user, "message.doa.look", "", GREY);
            } else if (user instanceof ServerPlayerEntity sp) {
                String err = core.awaken(sp, relic);
                if (err == null) {
                    DoaNet.toast(user, "=" + Pillars.KANJI[relic] + "  " + Abilities.TITLE[relic], "", Pillars.rgb(relic));
                    user.getItemCooldownManager().set(this, 20);
                } else {
                    DoaNet.toast(user, err, err.equals("message.doa.cooldown") ? Integer.toString(core.secondsUntilReady(relic)) : "", GREY);
                }
            }
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return CHARGE;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof PlayerEntity p && coreOf(world, p) == null) {
            ArmoryCoreEntity core = new ArmoryCoreEntity(DoaEntities.ARMORY_CORE, world);
            core.begin(p);
            world.spawnEntity(core);
            p.getItemCooldownManager().set(this, 100);
            DoaNet.toast(p, "message.doa.call", "", RED);
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.doa.armory_staff.tip1").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.doa.armory_staff.tip2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.doa.armory_staff.tip3").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
