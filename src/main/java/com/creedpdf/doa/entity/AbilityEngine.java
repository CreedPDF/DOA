package com.creedpdf.doa.entity;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server side of the seven relics: who they strike, when, and how hard. */
public class AbilityEngine {
    private final ArmoryCoreEntity core;
    final int[] readyAt = new int[Pillars.COUNT];
    private final List<Integer> targets = new ArrayList<>();
    private final List<Vec3d> points = new ArrayList<>();
    private final Map<UUID, int[]> frozen = new HashMap<>();   // uuid -> {until, hadNoGravity}

    AbilityEngine(ArmoryCoreEntity core) {
        this.core = core;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private List<LivingEntity> hostiles(ServerWorld sw, Vec3d c, double range, int max) {
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(range),
                e -> e.isAlive() && e instanceof Monster && !(e instanceof PlayerEntity) && e.squaredDistanceTo(c) <= range * range);
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(c)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    private Vec3d surface(ServerWorld sw, double x, double z, double fallbackY) {
        int top = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
        return new Vec3d(x, Math.abs(top - fallbackY) < 40 ? top : fallbackY, z);
    }

    private Vec3d ahead(ServerWorld sw, ServerPlayerEntity owner, double dist) {
        Vec3d look = owner.getRotationVec(1f).multiply(1, 0, 1);
        if (look.lengthSquared() < 1e-4) look = new Vec3d(0, 0, -1);
        look = look.normalize();
        Vec3d c = core.getPos();
        return surface(sw, c.x + look.x * dist, c.z + look.z * dist, c.y);
    }

    private void play(SoundEvent s, Vec3d at, float vol, float pitch) {
        core.getWorld().playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    private void sync(int ability, int a) {
        NbtCompound d = new NbtCompound();
        d.putIntArray("ids", targets.stream().mapToInt(Integer::intValue).toArray());
        int[] pts = new int[points.size() * 3];
        for (int k = 0; k < points.size(); k++) {
            pts[k * 3] = (int) Math.round(points.get(k).x * 8);
            pts[k * 3 + 1] = (int) Math.round(points.get(k).y * 8);
            pts[k * 3 + 2] = (int) Math.round(points.get(k).z * 8);
        }
        d.putIntArray("pts", pts);
        core.setAbilityState(ability, a, d);
    }

    private int readyMask(int t) {
        int m = 0;
        for (int i = 0; i < Pillars.COUNT; i++) if (t >= readyAt[i]) m |= 1 << i;
        return m;
    }

    private void damage(ServerWorld sw, LivingEntity e, float amount) {
        e.damage(sw.getDamageSources().magic(), amount);
    }

    private boolean ally(Entity e, ServerPlayerEntity owner) {
        if (e == owner || e instanceof ArmoryCoreEntity) return true;
        if (e instanceof PlayerEntity p) return core.isOwnedBy(p);
        return e instanceof TameableEntity t && owner != null && owner.getUuid().equals(t.getOwnerUuid());
    }

    // ------------------------------------------------------------------------------------------------ start

    /** @return null if started, or a message key explaining why not. */
    String start(int i, ServerWorld sw, ServerPlayerEntity owner, int t) {
        if (core.getAbility() >= 0) return "message.doa.busy";
        if (t < readyAt[i]) return "message.doa.cooldown";
        targets.clear();
        points.clear();
        Vec3d c = core.getPos();
        switch (i) {
            case Abilities.DEATH, Abilities.SOUL -> {
                for (LivingEntity e : hostiles(sw, c, Abilities.RANGE, 48)) targets.add(e.getId());
            }
            case Abilities.WRATH -> {
                List<LivingEntity> hs = hostiles(sw, c, Abilities.RANGE, Abilities.WRATH_SPEARS);
                for (LivingEntity e : hs) points.add(e.getPos());
                var rnd = sw.random;
                while (points.size() < Abilities.WRATH_SPEARS) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = Rite.ORB_RADIUS + 5 + rnd.nextDouble() * 34;
                    points.add(surface(sw, c.x + Math.sin(a) * r, c.z - Math.cos(a) * r, c.y));
                }
            }
            case Abilities.FATE -> {
                LivingEntity best = null;
                for (LivingEntity e : hostiles(sw, c, Abilities.RANGE, 64))
                    if (best == null || e.getMaxHealth() > best.getMaxHealth()) best = e;
                Vec3d p = best != null ? best.getPos() : ahead(sw, owner, Rite.ORB_RADIUS + 12);
                if (p.subtract(c).horizontalLength() < Rite.ORB_RADIUS + 6) p = ahead(sw, owner, Rite.ORB_RADIUS + 12);
                points.add(p);
            }
            case Abilities.VOID -> {
                List<LivingEntity> hs = hostiles(sw, c, Abilities.RANGE, 64);
                Vec3d p;
                if (!hs.isEmpty()) {
                    Vec3d sum = Vec3d.ZERO;
                    for (LivingEntity e : hs) sum = sum.add(e.getPos());
                    p = sum.multiply(1.0 / hs.size());
                    Vec3d d = p.subtract(c).multiply(1, 0, 1);
                    if (d.length() < Rite.ORB_RADIUS + 8)
                        p = d.lengthSquared() < 1e-3 ? ahead(sw, owner, Rite.ORB_RADIUS + 12) : c.add(d.normalize().multiply(Rite.ORB_RADIUS + 12));
                    p = surface(sw, p.x, p.z, c.y);
                } else {
                    p = ahead(sw, owner, Rite.ORB_RADIUS + 12);
                }
                points.add(p.add(0, 6, 0));
            }
            default -> {
            }
        }
        readyAt[i] = t + Abilities.LENGTH[i] + Abilities.COOLDOWN[i];
        core.setReadyMask(readyMask(t));
        play(DoaSounds.RELIC, core.relicPos(i), 3f, Pillars.pitch(i));
        sync(i, 0);
        return null;
    }

    // ------------------------------------------------------------------------------------------------ tick

    void tick(ServerWorld sw, ServerPlayerEntity owner, int t) {
        tickFrozen(sw, t);
        if (t % 10 == 0) core.setReadyMask(readyMask(t));
        int i = core.getAbility();
        if (i < 0) return;
        int a = core.getAbilityTicks() + 1;
        if (a >= Abilities.LENGTH[i]) {
            core.setAbilityState(-1, 0, new NbtCompound());
            return;
        }
        core.setAbilityState(i, a, null);
        Vec3d c = core.getPos();
        switch (i) {
            case Abilities.DEATH -> death(sw, a);
            case Abilities.REDEMPTION -> redemption(sw, owner, a);
            case Abilities.WRATH -> wrath(sw, owner, a);
            case Abilities.SOUL -> soul(sw, owner, a);
            case Abilities.FATE -> fate(sw, owner, a);
            case Abilities.SILENCE -> silence(sw, owner, a, t);
            case Abilities.VOID -> voidMaw(sw, owner, a);
            default -> {
            }
        }
    }

    private LivingEntity living(ServerWorld sw, int id) {
        Entity e = sw.getEntityById(id);
        return e instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    private void death(ServerWorld sw, int a) {
        if (a == Abilities.DEATH_MARK) {
            play(DoaSounds.VERDICT, core.getPos().add(0, 8, 0), 8f, 1f);
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e != null) e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false));
            }
        }
        if (a == Abilities.DEATH_STRIKE) {
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e != null) damage(sw, e, Math.max(e.getMaxHealth() * 4f, 200f));
            }
        }
    }

    private void redemption(ServerWorld sw, ServerPlayerEntity owner, int a) {
        if (a == 15) play(DoaSounds.ABSOLUTION, core.getPos().add(0, 2, 0), 5f, 1f);
        if (a != Abilities.REDEEM_HEAL) return;
        List<PlayerEntity> blessed = new ArrayList<>(sw.getEntitiesByClass(PlayerEntity.class,
                new Box(core.getPos(), core.getPos()).expand(Rite.ORB_RADIUS), p -> p.isAlive()));
        if (owner != null && !blessed.contains(owner)) blessed.add(owner);
        for (PlayerEntity p : blessed) {
            p.setHealth(p.getMaxHealth());
            p.extinguish();
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20f);
            for (StatusEffectInstance inst : new ArrayList<>(p.getStatusEffects()))
                if (!inst.getEffectType().value().isBeneficial()) p.removeStatusEffect(inst.getEffectType());
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 3));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 2400, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 2400, 0));
        }
    }

    private void wrath(ServerWorld sw, ServerPlayerEntity owner, int a) {
        if (a == 1) play(DoaSounds.WAR_DRUMS, core.getPos().add(0, 10, 0), 8f, 1f);
        for (int k = 0; k < points.size(); k++) {
            int hit = Abilities.wrathImpact(k);
            Vec3d p = points.get(k);
            if (a == hit - 12) play(DoaSounds.SPEAR, p, 4f, 0.9f + (k % 5) * 0.05f);
            if (a == hit) {
                sw.createExplosion(null, p.x, p.y + 0.5, p.z, 2.6f, false, World.ExplosionSourceType.NONE);
                for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(3.8), Entity::isAlive)) {
                    if (ally(e, owner)) continue;
                    damage(sw, e, 14f);
                    e.setOnFireFor(6f);
                }
            }
        }
    }

    private void soul(ServerWorld sw, ServerPlayerEntity owner, int a) {
        if (a == 2) play(DoaSounds.SOUL, core.getPos().add(0, 6, 0), 6f, 1f);
        if (a >= Abilities.SOUL_DRAIN_START && a <= Abilities.SOUL_DRAIN_END && (a - Abilities.SOUL_DRAIN_START) % 8 == 0) {
            int drained = 0;
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e == null) continue;
                damage(sw, e, 3.5f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20, 0, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 3, false, false));
                drained++;
            }
            if (owner != null && drained > 0) owner.heal(Math.min(4f, drained * 0.5f));
        }
        if (a == Abilities.SOUL_CLIMAX && owner != null) {
            owner.heal(8f);
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 1800, 1));
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1800, 1));
        }
    }

    private void fate(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d p = points.isEmpty() ? core.getPos() : points.get(0);
        if (a == Abilities.FATE_STRIKE - 16) play(DoaSounds.FATE_STRIKE, p, 10f, 1f);
        if (a == Abilities.FATE_STRIKE) {
            sw.createExplosion(null, p.x, p.y + 0.5, p.z, 4.5f, false, World.ExplosionSourceType.NONE);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(11), Entity::isAlive)) {
                if (ally(e, owner)) continue;
                double d = e.getPos().distanceTo(p);
                if (d > 11) continue;
                damage(sw, e, (float) (50 * (1 - d / 14)));
                Vec3d push = e.getPos().subtract(p).multiply(1, 0, 1);
                push = push.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : push.normalize();
                e.addVelocity(push.x * 2.2, 1.0, push.z * 2.2);
                e.velocityModified = true;
            }
            sw.setWeather(12000, 0, false, false);
            if (owner != null) {
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 6000, 2));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 2400, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 2400, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 6000, 0));
            }
        }
    }

    private void silence(ServerWorld sw, ServerPlayerEntity owner, int a, int t) {
        if (a == Abilities.SILENCE_TOLL) play(DoaSounds.SILENCE, core.getPos().add(0, 8, 0), 10f, 1f);
        if (a != Abilities.SILENCE_TOLL + 4) return;
        Vec3d c = core.getPos();
        targets.clear();
        for (MobEntity m : sw.getEntitiesByClass(MobEntity.class, new Box(c, c).expand(Abilities.RANGE),
                e -> e.isAlive() && e.squaredDistanceTo(c) <= Abilities.RANGE * Abilities.RANGE)) {
            if (ally(m, owner)) continue;
            frozen.put(m.getUuid(), new int[]{t + Abilities.SILENCE_FREEZE, m.hasNoGravity() ? 1 : 0});
            m.setAiDisabled(true);
            m.setNoGravity(true);
            m.setVelocity(Vec3d.ZERO);
            m.velocityModified = true;
            targets.add(m.getId());
        }
        sync(Abilities.SILENCE, a);
    }

    private void tickFrozen(ServerWorld sw, int t) {
        if (frozen.isEmpty()) return;
        var it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity en = sw.getEntity(e.getKey());
            if (!(en instanceof MobEntity m) || !m.isAlive()) {
                it.remove();
                continue;
            }
            if (t >= e.getValue()[0]) {
                m.setAiDisabled(false);
                m.setNoGravity(e.getValue()[1] == 1);
                it.remove();
            } else {
                m.setVelocity(Vec3d.ZERO);
                m.velocityModified = true;
            }
        }
    }

    void unfreezeAll(ServerWorld sw) {
        for (var e : frozen.entrySet()) {
            Entity en = sw.getEntity(e.getKey());
            if (en instanceof MobEntity m) {
                m.setAiDisabled(false);
                m.setNoGravity(e.getValue()[1] == 1);
            }
        }
        frozen.clear();
    }

    private void voidMaw(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d p = points.isEmpty() ? core.getPos() : points.get(0);
        if (a == Abilities.VOID_OPEN) play(DoaSounds.VOID_OPEN, p, 8f, 1f);
        if (a == Abilities.VOID_COLLAPSE - 28) play(DoaSounds.VOID_COLLAPSE, p, 10f, 1f);
        if (a > Abilities.VOID_OPEN && a < Abilities.VOID_COLLAPSE) {
            float grow = Rite.clamp01((a - Abilities.VOID_OPEN) / 30.0);
            for (Entity e : sw.getOtherEntities(core, new Box(p, p).expand(22), en -> en.isAlive() && !en.isSpectator())) {
                if (ally(e, owner)) continue;
                if (!(e instanceof LivingEntity) && !(e instanceof ItemEntity)) continue;
                Vec3d d = p.subtract(e.getPos().add(0, e.getHeight() / 2, 0));
                double dist = d.length();
                if (dist > 22 || dist < 1e-3) continue;
                double pull = grow * (0.05 + 0.16 * (1 - dist / 22));
                e.addVelocity(d.x / dist * pull, d.y / dist * pull + 0.02, d.z / dist * pull);
                e.velocityModified = true;
                if (e instanceof LivingEntity l && dist < 5 && a % 10 == 0) damage(sw, l, 3f);
            }
        }
        if (a == Abilities.VOID_COLLAPSE) {
            sw.createExplosion(null, p.x, p.y, p.z, 3.5f, false, World.ExplosionSourceType.NONE);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(11), Entity::isAlive)) {
                if (ally(e, owner)) continue;
                double d = e.getPos().distanceTo(p);
                if (d > 11) continue;
                damage(sw, e, (float) (38 * (1 - d / 15)));
                Vec3d push = e.getPos().subtract(p);
                push = push.lengthSquared() < 1e-4 ? new Vec3d(0, 1, 0) : push.normalize();
                e.addVelocity(push.x * 1.8, 0.8, push.z * 1.8);
                e.velocityModified = true;
            }
        }
    }

    void abort() {
        if (core.getAbility() >= 0) core.setAbilityState(-1, 0, new NbtCompound());
    }

    // ------------------------------------------------------------------------------------------------ save

    void write(NbtCompound nbt) {
        nbt.putIntArray("ReadyAt", readyAt.clone());
    }

    void read(NbtCompound nbt) {
        int[] r = nbt.getIntArray("ReadyAt");
        if (r.length == Pillars.COUNT) System.arraycopy(r, 0, readyAt, 0, r.length);
    }
}
