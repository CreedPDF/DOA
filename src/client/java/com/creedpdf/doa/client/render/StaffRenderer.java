package com.creedpdf.doa.client.render;

import com.creedpdf.doa.DoaMod;
import com.creedpdf.doa.client.model.DoaMeshSet;
import com.creedpdf.doa.client.model.DoaModels;
import com.creedpdf.doa.entity.ArmoryCoreEntity;
import com.creedpdf.doa.item.ArmoryStaffItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * The Staff of the Orbital Armory, drawn from its Blender model with its own animations: the halo turns, the seven
 * beads orbit inside it, the gem breathes and the paper talismans sway. While the call is charging the halo spins
 * up, the beads flare and the gem blazes; while the caller's sphere stands the beads burn in their pillars' colours.
 */
public final class StaffRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier TEX = DoaMod.id("textures/item/staff.png");
    private static final Identifier GLOW = DoaMod.id("textures/item/staff_glow.png");

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        DoaMeshSet staff = DoaModels.STAFF;
        if (staff == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float td = mc.getRenderTickCounter().getTickDelta(false);
        double time = (mc.world != null ? mc.world.getTime() : 0) + td;
        float charge = 0;
        boolean awake = false;
        PlayerEntity p = mc.player;
        if (p != null && p.isUsingItem() && p.getActiveItem() == stack) charge = Math.min(1, (p.getItemUseTime() + td) / ArmoryStaffItem.CHARGE);
        if (p != null && mc.world != null && mode != ModelTransformationMode.GUI) {
            for (Entity e : mc.world.getEntities())
                if (e instanceof ArmoryCoreEntity core && core.getOwnerNetId() == p.getId()) {
                    awake = true;
                    break;
                }
        }
        ms.push();
        switch (mode) {
            case GUI -> {
                ms.translate(0.5, 0.5, 0.5);
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30));
                ms.scale(0.42f, 0.42f, 0.42f);
                ms.translate(0, -1.08, 0);
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                ms.translate(0.5, 0.2, 0.5);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8 - charge * 10));
                ms.scale(0.8f, 0.8f, 0.8f);
                ms.translate(0, -0.75, 0);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                ms.translate(0.5, 0.35, 0.5);
                ms.scale(1.0f, 1.0f, 1.0f);
                ms.translate(0, -0.85, 0);
            }
            default -> {
                ms.translate(0.5, 0.2, 0.5);
                ms.scale(0.45f, 0.45f, 0.45f);
            }
        }
        final float ch = charge;
        final double spin = time * (0.03 + ch * 0.35);
        DoaMeshSet.Pose pose = (n, m) -> {
            switch (n) {
                case "halo" -> m.multiply(RotationAxis.POSITIVE_Z.rotation((float) spin));
                case "beads" -> {
                    m.multiply(RotationAxis.POSITIVE_Z.rotation((float) (-spin * 1.7)));
                    float s = 1 + ch * 0.35f;
                    m.scale(s, s, 1);
                }
                case "gem" -> {
                    m.translate(0, Math.sin(time * 0.1) * 0.012, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotation((float) (time * (0.04 + ch * 0.3))));
                    float s = 1 + ch * 0.25f;
                    m.scale(s, s, s);
                }
                case "tassel" -> m.multiply(RotationAxis.POSITIVE_X.rotation((float) (Math.sin(time * 0.08) * 0.12 + ch * 0.2 * Math.sin(time * 0.6))));
                default -> {
                }
            }
            return true;
        };
        staff.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, pose, null);
        float base = awake ? 1.25f : 0.75f;
        float pulse = (float) (0.85 + 0.15 * Math.sin(time * 0.15));
        staff.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), Bill.FULL, pose, (part, f, nx, ny, nz, o) -> {
            float k = switch (part) {
                case "gem" -> (0.6f + ch * 1.4f) * pulse;
                case "beads" -> base * (1 + ch * 1.2f);
                case "halo" -> 0.4f + ch * 1.2f;
                default -> 0.35f + ch * 0.4f;
            };
            o[0] = k;
            o[1] = part.equals("gem") ? k * 0.85f : k;
            o[2] = k;
            o[3] = 1;
        });
        ms.pop();
    }
}
