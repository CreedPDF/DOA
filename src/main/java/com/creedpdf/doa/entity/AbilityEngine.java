package com.creedpdf.doa.entity;

import com.creedpdf.doa.Abilities;
import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the seven moves: who they strike and when. Everything visible is drawn by the client from the
 * synced move clock; nothing here spawns vanilla particles, explosions or glow outlines.
 */
public class AbilityEngine {
    private final ArmoryCoreEntity core;
    final int[] readyAt = new int[Pillars.COUNT];
    private final List<Integer> targets = new ArrayList<>();
    private final List<Vec3d> points = new ArrayList<>();
    private final Map<UUID, int[]> frozen = new HashMap<>();

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

    private Vec3d ringPoint(ServerWorld sw, double r0, double r1) {
        Vec3d c = core.getPos();
        double a = sw.random.nextDouble() * Math.PI * 2, r = r0 + sw.random.nextDouble() * (r1 - r0);
        return surface(sw, c.x + Math.sin(a) * r, c.z - Math.cos(a) * r, c.y);
    }

    private Vec3d ahead(ServerWorld sw, ServerPlayerEntity owner, double dist) {
        Vec3d look = owner == null ? new Vec3d(0, 0, -1) : owner.getRotationVec(1f).multiply(1, 0, 1);
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

    private void knock(LivingEntity e, Vec3d from, double h, double v) {
        Vec3d push = e.getPos().subtract(from).multiply(1, 0, 1);
        push = push.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : push.normalize();
        e.addVelocity(push.x * h, v, push.z * h);
        e.velocityModified = true;
    }

    private boolean ally(Entity e, ServerPlayerEntity owner) {
        if (e == owner || e instanceof ArmoryCoreEntity) return true;
        if (e instanceof PlayerEntity p) return core.isOwnedBy(p);
        return e instanceof TameableEntity t && owner != null && owner.getUuid().equals(t.getOwnerUuid());
    }

    private LivingEntity living(ServerWorld sw, int id) {
        Entity e = sw.getEntityById(id);
        return e instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    private void area(ServerWorld sw, ServerPlayerEntity owner, Vec3d p, double radius, float dmg, double knock, int fire) {
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(radius), Entity::isAlive)) {
            if (ally(e, owner)) continue;
            double d = e.getPos().distanceTo(p);
            if (d > radius) continue;
            damage(sw, e, (float) (dmg * (1 - 0.5 * d / radius)));
            if (knock > 0) knock(e, p, knock, 0.5 + knock * 0.3);
            if (fire > 0) e.setOnFireFor(fire);
        }
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
                for (LivingEntity e : hostiles(sw, c, Abilities.RANGE, 32)) targets.add(e.getId());
            }
            case Abilities.REDEMPTION -> {
                for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(Abilities.RANGE),
                        e -> e.isAlive() && e.getType().isIn(EntityTypeTags.UNDEAD))) targets.add(e.getId());
            }
            case Abilities.WRATH -> {
                List<LivingEntity> hs = hostiles(sw, c, Abilities.RANGE, Abilities.BRK_METEORS);
                for (LivingEntity e : hs) points.add(e.getPos());
                while (points.size() < Abilities.BRK_METEORS) points.add(ringPoint(sw, Rite.ORB_RADIUS + 6, Rite.ORB_RADIUS + 44));
            }
            case Abilities.FATE -> {
                LivingEntity best = null;
                for (LivingEntity e : hostiles(sw, c, Abilities.RANGE, 64))
                    if (best == null || e.getMaxHealth() > best.getMaxHealth()) best = e;
                Vec3d p = best != null ? best.getPos() : ahead(sw, owner, Rite.ORB_RADIUS + 16);
                if (p.subtract(c).horizontalLength() < Rite.ORB_RADIUS + 6) p = ahead(sw, owner, Rite.ORB_RADIUS + 16);
                points.add(p);
            }
            case Abilities.VOID -> {
                List<LivingEntity> hs = hostiles(sw, c, Abilities.RANGE, 64);
                for (LivingEntity e : hs) targets.add(e.getId());
                // tear the sky above where the enemies gather, or around the sphere if there are none
                for (int k = 0; k < Abilities.RFT_RIFTS; k++) {
                    Vec3d at;
                    if (k < hs.size()) at = hs.get(k * Math.max(1, hs.size() / Abilities.RFT_RIFTS) % hs.size()).getPos();
                    else at = ringPoint(sw, Rite.ORB_RADIUS + 10, Rite.ORB_RADIUS + 34);
                    Vec3d d = at.subtract(c).multiply(1, 0, 1);
                    if (d.length() < Rite.ORB_RADIUS + 8)
                        at = c.add((d.lengthSquared() < 1e-3 ? new Vec3d(1, 0, 0) : d.normalize()).multiply(Rite.ORB_RADIUS + 12));
                    points.add(new Vec3d(at.x, c.y + 42 + k * 5, at.z));
                }
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
        // the caller is shielded while their cutscene plays
        if (owner != null && a % 40 == 1) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false));
        switch (i) {
            case Abilities.DEATH -> eclipse(sw, owner, a);
            case Abilities.REDEMPTION -> dawn(sw, owner, a);
            case Abilities.WRATH -> breaker(sw, owner, a);
            case Abilities.SOUL -> heart(sw, owner, a);
            case Abilities.FATE -> beyond(sw, owner, a);
            case Abilities.SILENCE -> moonrise(sw, owner, a, t);
            case Abilities.VOID -> rift(sw, owner, a);
            default -> {
            }
        }
    }

    private void eclipse(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d c = core.getPos();
        if (a == Abilities.ECL_DARK) play(DoaSounds.ORB_RISE, c.add(0, 10, 0), 8f, 0.7f);
        if (a == Abilities.ECL_TOTAL - 40) play(DoaSounds.VERDICT, c.add(0, 10, 0), 10f, 0.8f);
        for (int k = 0; k < targets.size(); k++) {
            int hit = Abilities.eclipseStrike(k);
            LivingEntity e = living(sw, targets.get(k));
            if (e == null) continue;
            if (a == hit - 10 && k % 4 == 0) play(DoaSounds.SPEAR, e.getPos(), 3f, 0.7f);
            if (a == hit) {
                damage(sw, e, Math.max(e.getMaxHealth() * 4f, 200f));
                area(sw, owner, e.getPos(), 3, 12, 0.8, 0);
            }
        }
        if (a == Abilities.ECL_LIGHT) play(DoaSounds.SEAL, c.add(0, 6, 0), 6f, 0.8f);
    }

    private void dawn(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d c = core.getPos();
        if (a == Abilities.DAWN_RISE) play(DoaSounds.FATE_ASCEND, c.add(0, 4, 0), 4f, 1.1f);
        if (a == Abilities.DAWN_HEAL - 30) play(DoaSounds.ABSOLUTION, c.add(0, 4, 0), 6f, 1f);
        if (a != Abilities.DAWN_HEAL) return;
        List<PlayerEntity> blessed = new ArrayList<>(sw.getEntitiesByClass(PlayerEntity.class, new Box(c, c).expand(Rite.ORB_RADIUS), PlayerEntity::isAlive));
        if (owner != null && !blessed.contains(owner)) blessed.add(owner);
        for (PlayerEntity p : blessed) {
            p.setHealth(p.getMaxHealth());
            p.extinguish();
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20f);
            for (StatusEffectInstance inst : new ArrayList<>(p.getStatusEffects()))
                if (!inst.getEffectType().value().isBeneficial()) p.removeStatusEffect(inst.getEffectType());
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 3600, 4));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3600, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 3600, 0));
        }
        // the dawn burns what should not walk
        for (int id : targets) {
            LivingEntity e = living(sw, id);
            if (e != null) {
                damage(sw, e, 60f);
                e.setOnFireFor(8f);
            }
        }
    }

    private void breaker(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d c = core.getPos();
        if (a == Abilities.BRK_UP - 60) play(DoaSounds.FATE_ASCEND, c.add(0, 4, 0), 4f, 0.8f);
        if (a == Abilities.BRK_METEOR - 20) play(DoaSounds.WAR_DRUMS, c.add(0, 12, 0), 10f, 1f);
        for (int k = 0; k < points.size(); k++) {
            int hit = Abilities.meteorHit(k);
            Vec3d p = points.get(k);
            if (a == hit - 12 && k % 4 == 0) play(DoaSounds.SPEAR, p, 5f, 0.8f + (k % 5) * 0.05f);
            if (a == hit) {
                if (k % 3 == 0) play(DoaSounds.IMPACT, p, 5f, 0.8f + (k % 4) * 0.06f);
                area(sw, owner, p, 4.5, 20, 1.2, 6);
            }
        }
    }

    private void heart(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d c = core.getPos();
        if (a == Abilities.HRT_DIVE) play(DoaSounds.ORB_CLOSE, c, 6f, 0.7f);
        if (a == Abilities.HRT_DRAIN - 20) play(DoaSounds.SOUL, c.add(0, 6, 0), 8f, 0.9f);
        if (a >= Abilities.HRT_DRAIN && a <= Abilities.HRT_DRAIN_END && (a - Abilities.HRT_DRAIN) % 14 == 0) {
            int drained = 0;
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e == null) continue;
                damage(sw, e, 4f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 3, false, false));
                drained++;
            }
            if (owner != null && drained > 0) owner.heal(Math.min(4f, drained * 0.6f));
        }
        if (a == Abilities.HRT_CLIMAX) {
            play(DoaSounds.FATE_STRIKE, c.add(0, 4, 0), 8f, 1.2f);
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e != null) damage(sw, e, 24f);
            }
            if (owner != null) {
                owner.heal(10f);
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 2400, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 2400, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1));
            }
        }
    }

    private void beyond(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d p = points.isEmpty() ? core.getPos() : points.get(0);
        if (a == Abilities.BYD_STRIKE - 16) play(DoaSounds.FATE_STRIKE, p, 12f, 0.9f);
        if (a == Abilities.BYD_STRIKE) {
            area(sw, owner, p, 14, 70, 2.4, 0);
            sw.setWeather(12000, 0, false, false);
            if (owner != null) {
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 6000, 2));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 3600, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 3600, 1));
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 6000, 0));
            }
        }
    }

    private void moonrise(ServerWorld sw, ServerPlayerEntity owner, int a, int t) {
        Vec3d c = core.getPos();
        if (a == Abilities.MOON_NIGHT) play(DoaSounds.ORB_RISE, c.add(0, 8, 0), 6f, 0.6f);
        if (a == Abilities.MOON_TOLL) play(DoaSounds.SILENCE, c.add(0, 8, 0), 12f, 1f);
        if (a != Abilities.MOON_TOLL + 4) return;
        targets.clear();
        for (MobEntity m : sw.getEntitiesByClass(MobEntity.class, new Box(c, c).expand(Abilities.RANGE),
                e -> e.isAlive() && e.squaredDistanceTo(c) <= Abilities.RANGE * Abilities.RANGE)) {
            if (ally(m, owner)) continue;
            frozen.put(m.getUuid(), new int[]{t + Abilities.MOON_FREEZE, m.hasNoGravity() ? 1 : 0});
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

    private void rift(ServerWorld sw, ServerPlayerEntity owner, int a) {
        Vec3d c = core.getPos();
        if (a == Abilities.RFT_OPEN) play(DoaSounds.VOID_OPEN, c.add(0, 30, 0), 12f, 0.9f);
        if (a == Abilities.RFT_SEAL - 28) play(DoaSounds.VOID_COLLAPSE, c.add(0, 30, 0), 12f, 1f);
        if (a >= Abilities.RFT_LIFT && a < Abilities.RFT_TAKE && !points.isEmpty()) {
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e == null) continue;
                Vec3d best = points.get(0);
                for (Vec3d p : points) if (p.squaredDistanceTo(e.getPos()) < best.squaredDistanceTo(e.getPos())) best = p;
                Vec3d d = best.subtract(e.getPos());
                double len = Math.max(1, d.length());
                float k = Rite.clamp01((a - Abilities.RFT_LIFT) / 60.0);
                Vec3d tang = new Vec3d(-d.z, 0, d.x).normalize().multiply(0.08);
                e.setVelocity(d.x / len * 0.22 * k + tang.x, 0.12 + 0.18 * k, d.z / len * 0.22 * k + tang.z);
                e.velocityModified = true;
                e.fallDistance = 0;
            }
        }
        if (a == Abilities.RFT_TAKE) {
            for (int id : targets) {
                LivingEntity e = living(sw, id);
                if (e == null) continue;
                if (e.getY() > c.y + 14) damage(sw, e, Math.max(e.getMaxHealth() * 4f, 200f));
                else damage(sw, e, 30f);
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
