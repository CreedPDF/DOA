package com.creedpdf.doa.client.render;

import com.creedpdf.doa.entity.ArmoryCoreEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Client-side decoding of the running ability's synced targets and strike points. */
public final class AbilityView {
    private AbilityView() {
    }

    public static List<LivingEntity> targets(ArmoryCoreEntity core) {
        List<LivingEntity> out = new ArrayList<>();
        NbtCompound d = core.getAbilityData();
        for (int id : d.getIntArray("ids")) {
            Entity e = core.getWorld().getEntityById(id);
            if (e instanceof LivingEntity l && l.isAlive()) out.add(l);
        }
        return out;
    }

    public static List<Vec3d> points(ArmoryCoreEntity core) {
        List<Vec3d> out = new ArrayList<>();
        int[] p = core.getAbilityData().getIntArray("pts");
        for (int k = 0; k + 2 < p.length; k += 3) out.add(new Vec3d(p[k] / 8.0, p[k + 1] / 8.0, p[k + 2] / 8.0));
        return out;
    }

    public static Vec3d point(ArmoryCoreEntity core) {
        List<Vec3d> p = points(core);
        return p.isEmpty() ? core.getPos() : p.get(0);
    }
}
