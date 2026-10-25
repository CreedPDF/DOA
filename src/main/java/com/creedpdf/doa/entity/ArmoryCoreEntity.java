package com.creedpdf.doa.entity;

import com.creedpdf.doa.Pillars;
import com.creedpdf.doa.Rite;
import com.creedpdf.doa.registry.DoaBlocks;
import com.creedpdf.doa.registry.DoaSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The invisible heart of a summoning. It stands where the caller stood and runs the whole rite: seven pillars fall
 * from the orbital armory in a circle, then the dark sphere corrupts outward and seals around the caller.
 * The client draws everything from {@link #getRiteTicks()} and {@link #getReleaseTicks()}.
 */
public class ArmoryCoreEntity extends Entity {
    private static final TrackedData<Integer> TICKS = DataTracker.registerData(ArmoryCoreEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> RELEASE = DataTracker.registerData(ArmoryCoreEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(ArmoryCoreEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<NbtCompound> GROUND = DataTracker.registerData(ArmoryCoreEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    /** Client hook (VFX), installed by the client initializer. */
    public static Consumer<ArmoryCoreEntity> clientTick = e -> {
    };

    private UUID ownerUuid;
    private int ownerMissing;
    private final int[] groundY = new int[Pillars.COUNT];
    private boolean groundKnown;

    /** Shell positions in rising order and the blocks they replaced. */
    private final List<BlockPos> shellPlan = new ArrayList<>();
    private final List<Double> shellElev = new ArrayList<>();
    private final Map<Long, BlockState> placed = new LinkedHashMap<>();
    private final Map<Long, BlockState> pillarPlaced = new LinkedHashMap<>();

    // client-side smoothed counters
    private int clientTicks = -1;
    private int clientRelease = -1;
    /** Last tick the client-side VFX processed (owned by the client code). */
    public int fxTick = -1;

    public ArmoryCoreEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TICKS, 0);
        builder.add(RELEASE, -1);
        builder.add(OWNER, -1);
        builder.add(GROUND, new NbtCompound());
    }

    public void begin(PlayerEntity owner) {
        this.ownerUuid = owner.getUuid();
        this.refreshPositionAndAngles(owner.getX(), Math.floor(owner.getY() + 0.01), owner.getZ(), 0, 0);
        this.dataTracker.set(OWNER, owner.getId());
    }

    // ------------------------------------------------------------------------------------------------ accessors

    public boolean isOwnedBy(PlayerEntity p) {
        return p.getUuid().equals(ownerUuid) || p.getId() == this.dataTracker.get(OWNER);
    }

    public int getOwnerNetId() {
        return this.dataTracker.get(OWNER);
    }

    public int getRiteTicks() {
        if (getWorld().isClient && clientTicks >= 0) return clientTicks;
        return this.dataTracker.get(TICKS);
    }

    public int getReleaseTicks() {
        if (getWorld().isClient) return clientRelease;
        return this.dataTracker.get(RELEASE);
    }

    public boolean isReleasing() {
        return getReleaseTicks() >= 0;
    }

    /** Ground height (top of the block the pillar stands on) for pillar i. */
    public double pillarGround(int i) {
        NbtCompound g = this.dataTracker.get(GROUND);
        int[] arr = g.getIntArray("g");
        if (arr.length == Pillars.COUNT) return arr[i];
        return Math.floor(getY());
    }

    public Vec3d pillarBase(int i) {
        return new Vec3d(getX() + Rite.dx(i), pillarGround(i), getZ() + Rite.dz(i));
    }

    public void startRelease() {
        if (this.dataTracker.get(RELEASE) < 0) {
            this.dataTracker.set(RELEASE, 0);
            play(DoaSounds.DISSOLVE, getPos().add(0, 4, 0), 4f, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        if (world.isClient) {
            int st = this.dataTracker.get(TICKS);
            clientTicks = clientTicks < 0 || Math.abs(clientTicks + 1 - st) > 3 ? st : clientTicks + 1;
            int sr = this.dataTracker.get(RELEASE);
            if (sr < 0) clientRelease = -1;
            else clientRelease = clientRelease < 0 || Math.abs(clientRelease + 1 - sr) > 3 ? sr : clientRelease + 1;
            clientTick.accept(this);
            return;
        }
        ServerWorld sw = (ServerWorld) world;
        if (!groundKnown) computeGround(sw);
        ServerPlayerEntity owner = resolveOwner(sw);

        int rel = this.dataTracker.get(RELEASE);
        if (rel >= 0) {
            releaseTick(sw, rel);
            if (!isRemoved()) this.dataTracker.set(RELEASE, rel + 1);
            return;
        }
        int t = this.dataTracker.get(TICKS) + 1;
        this.dataTracker.set(TICKS, t);

        if (owner == null) {
            if (++ownerMissing > 200) startRelease();
        } else {
            ownerMissing = 0;
            if (owner.isDead() || owner.squaredDistanceTo(this) > 96 * 96) startRelease();
            else if (t < Rite.CUT_END) holdOwner(owner);
        }
        riteTick(sw, t);
    }

    private void riteTick(ServerWorld sw, int t) {
        Vec3d c = getPos();
        if (t == 1) play(DoaSounds.CALL, c.add(0, 6, 0), 6f, 1f);
        for (int i = 0; i < Pillars.COUNT; i++) {
            Vec3d b = pillarBase(i);
            if (t == Rite.spawn(i)) play(DoaSounds.DESCEND, b.add(0, 12, 0), 5f, 0.95f + i * 0.02f);
            if (t == Rite.land(i)) {
                play(DoaSounds.IMPACT, b, 6f, 0.92f + i * 0.025f);
                plantPillar(sw, i);
                strike(sw, b);
            }
            if (t == Rite.land(i) + 6) play(DoaSounds.REVEAL, b.add(0, 9, 0), 3f, Pillars.pitch(i));
        }
        if (t == Rite.SEAL) {
            play(DoaSounds.SEAL, c, 4f, 1f);
            planShell();
        }
        if (t == Rite.CORE_START + 14) play(DoaSounds.ORB_RISE, c.add(0, 3, 0), 6f, 1f);
        if (t >= Rite.CORRUPT_START && t < Rite.RISE_START) repel(sw, t, false);
        if (t >= Rite.RISE_START && t <= Rite.SEALED) {
            repel(sw, t, true);
            placeShell(sw, Rite.dome(t, -1) * 90.0 + 2.0);
        }
        if (t == Rite.SEALED) play(DoaSounds.ORB_CLOSE, c.add(0, 4, 0), 8f, 1f);
        if (t > Rite.SEALED) {
            if ((t - Rite.SEALED) % 130 == 20) play(DoaSounds.ORB_HUM, c.add(0, 3, 0), 2.5f, 1f);
            for (int i = 0; i < Pillars.COUNT; i++)
                if (t == Rite.relicAppear(i)) play(DoaSounds.RELIC, pillarBase(i).add(0, 3, 0), 2f, Pillars.pitch(i));
            if (t % 20 == 0) placeShell(sw, 95);
        }
    }

    private void releaseTick(ServerWorld sw, int r) {
        // the sphere sinks from the top down: lift the shell where the rim has passed
        double edge = Rite.dome(0, r) * 90.0 + 2.0;
        removeShellAbove(sw, edge);
        if (r == Rite.REL_ASCEND_START) {
            play(DoaSounds.ASCEND, getPos().add(0, 6, 0), 6f, 1f);
            restore(sw, pillarPlaced);
        }
        if (r >= Rite.REL_END) {
            cleanup(sw);
            this.discard();
        }
    }

    private ServerPlayerEntity resolveOwner(ServerWorld sw) {
        if (ownerUuid == null) return null;
        ServerPlayerEntity p = sw.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (p == null || p.getWorld() != sw) return null;
        if (this.dataTracker.get(OWNER) != p.getId()) this.dataTracker.set(OWNER, p.getId());
        return p;
    }

    /** Keep the caller at the heart of the circle while the armory descends. */
    private void holdOwner(ServerPlayerEntity owner) {
        double dx = owner.getX() - getX(), dz = owner.getZ() - getZ();
        if (dx * dx + dz * dz > 1.6 * 1.6) {
            owner.requestTeleport(getX(), Math.max(owner.getY(), getY()), getZ());
        }
    }

    private void play(SoundEvent s, Vec3d at, float vol, float pitch) {
        getWorld().playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    // ------------------------------------------------------------------------------------------------ ground

    private void computeGround(ServerWorld sw) {
        int cy = MathHelper.floor(getY());
        for (int i = 0; i < Pillars.COUNT; i++) {
            int x = MathHelper.floor(getX() + Rite.dx(i)), z = MathHelper.floor(getZ() + Rite.dz(i));
            int found = Integer.MIN_VALUE;
            for (int y = cy + 6; y >= cy - 10; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState below = sw.getBlockState(p.down());
                BlockState here = sw.getBlockState(p);
                if (!below.getCollisionShape(sw, p.down()).isEmpty() && !below.isOf(DoaBlocks.ORB_SHELL)
                        && (here.isAir() || here.isReplaceable() || here.isOf(DoaBlocks.ORB_SHELL))) {
                    found = y;
                    break;
                }
            }
            if (found == Integer.MIN_VALUE) {
                int top = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                found = Math.abs(top - cy) < 12 ? top : cy;
            }
            groundY[i] = found;
        }
        NbtCompound g = new NbtCompound();
        g.putIntArray("g", groundY.clone());
        this.dataTracker.set(GROUND, g);
        groundKnown = true;
    }

    // ------------------------------------------------------------------------------------------------ collision

    private static boolean canReplace(BlockState s) {
        return s.isAir() || (s.isReplaceable() && s.getFluidState().isEmpty());
    }

    private void plantPillar(ServerWorld sw, int i) {
        Vec3d b = pillarBase(i);
        int y0 = MathHelper.floor(b.y);
        for (int x = MathHelper.floor(b.x - 1); x <= MathHelper.floor(b.x + 1); x++)
            for (int z = MathHelper.floor(b.z - 1); z <= MathHelper.floor(b.z + 1); z++) {
                if (Math.abs(x + 0.5 - b.x) > 0.95 || Math.abs(z + 0.5 - b.z) > 0.95) continue;
                for (int y = y0; y < y0 + 9; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = sw.getBlockState(p);
                    if (s.isOf(DoaBlocks.ORB_SHELL) || !canReplace(s)) continue;
                    pillarPlaced.put(p.asLong(), s);
                    sw.setBlockState(p, DoaBlocks.ORB_SHELL.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
    }

    private void planShell() {
        shellPlan.clear();
        shellElev.clear();
        double R = Rite.ORB_RADIUS, cx = getX(), cy = getY(), cz = getZ();
        record Cell(BlockPos pos, double elev) {
        }
        List<Cell> cells = new ArrayList<>();
        int r = (int) Math.ceil(R) + 1;
        for (int x = MathHelper.floor(cx) - r; x <= MathHelper.floor(cx) + r; x++)
            for (int y = MathHelper.floor(cy); y <= MathHelper.floor(cy) + r; y++)
                for (int z = MathHelper.floor(cz) - r; z <= MathHelper.floor(cz) + r; z++) {
                    double dx = x + 0.5 - cx, dy = y + 0.5 - cy, dz = z + 0.5 - cz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (Math.abs(d - R) > 0.88 || dy < -0.5) continue;
                    double elev = Math.toDegrees(Math.asin(MathHelper.clamp(Math.max(dy, 0) / d, 0, 1)));
                    cells.add(new Cell(new BlockPos(x, y, z), elev));
                }
        cells.sort(Comparator.comparingDouble(Cell::elev));
        for (Cell c : cells) {
            shellPlan.add(c.pos());
            shellElev.add(c.elev());
        }
    }

    private void placeShell(ServerWorld sw, double maxElev) {
        if (shellPlan.isEmpty()) planShell();
        for (int k = 0; k < shellPlan.size(); k++) {
            if (shellElev.get(k) > maxElev) break;
            BlockPos p = shellPlan.get(k);
            long key = p.asLong();
            BlockState s = sw.getBlockState(p);
            if (s.isOf(DoaBlocks.ORB_SHELL)) continue;
            if (!canReplace(s)) continue;
            if (!sw.getEntitiesByClass(PlayerEntity.class, new Box(p), e -> true).isEmpty()) continue;
            placed.putIfAbsent(key, s);
            sw.setBlockState(p, DoaBlocks.ORB_SHELL.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void removeShellAbove(ServerWorld sw, double edgeElev) {
        if (shellPlan.isEmpty()) {
            if (!placed.isEmpty() && edgeElev <= 2.5) restore(sw, placed);
            return;
        }
        for (int k = shellPlan.size() - 1; k >= 0; k--) {
            if (shellElev.get(k) <= edgeElev) break;
            BlockPos p = shellPlan.get(k);
            BlockState orig = placed.remove(p.asLong());
            if (orig != null && sw.getBlockState(p).isOf(DoaBlocks.ORB_SHELL)) sw.setBlockState(p, orig, Block.NOTIFY_ALL);
        }
        if (edgeElev <= 2.5) restore(sw, placed);
    }

    private void restore(ServerWorld sw, Map<Long, BlockState> map) {
        for (Map.Entry<Long, BlockState> e : map.entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (sw.getBlockState(p).isOf(DoaBlocks.ORB_SHELL)) sw.setBlockState(p, e.getValue(), Block.NOTIFY_ALL);
        }
        map.clear();
    }

    private void cleanup(ServerWorld sw) {
        restore(sw, placed);
        restore(sw, pillarPlaced);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient && reason.shouldDestroy()) cleanup((ServerWorld) getWorld());
        super.remove(reason);
    }

    // ------------------------------------------------------------------------------------------------ entities

    private void strike(ServerWorld sw, Vec3d b) {
        DamageSource src = sw.getDamageSources().magic();
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(b, b).expand(2.6, 4, 2.6), Entity::isAlive)) {
            if (e instanceof PlayerEntity p && isOwnedBy(p)) continue;
            e.damage(src, 10f);
            Vec3d push = e.getPos().subtract(b).multiply(1, 0, 1);
            push = push.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : push.normalize();
            e.addVelocity(push.x * 1.4, 0.55, push.z * 1.4);
            e.velocityModified = true;
        }
    }

    /** Nothing may stand inside the circle but the caller while the sphere forms. */
    private void repel(ServerWorld sw, int t, boolean expel) {
        double R = Rite.ORB_RADIUS;
        Vec3d c = getPos();
        Box box = new Box(c, c).expand(R + 1, R + 1, R + 1);
        for (Entity e : sw.getOtherEntities(this, box, en -> en.isAlive() && !en.isSpectator() && en.isAttackable())) {
            if (e instanceof PlayerEntity p && isOwnedBy(p)) continue;
            if (e instanceof PlayerEntity p && (p.isCreative())) continue;
            Vec3d d = e.getPos().subtract(c);
            double dist = d.length();
            if (dist > R - 0.6 || d.y < -2) continue;
            Vec3d dir = d.multiply(1, 0, 1);
            dir = dir.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : dir.normalize();
            if (!expel) {
                e.addVelocity(dir.x * 0.35, 0.12, dir.z * 0.35);
                e.velocityModified = true;
            } else {
                double nx = c.x + dir.x * (R + 2.2), nz = c.z + dir.z * (R + 2.2);
                int top = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(nx), MathHelper.floor(nz));
                e.requestTeleport(nx, Math.max(top, c.y), nz);
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 384 * 384;
    }

    @Override
    public boolean isImmuneToExplosion(net.minecraft.world.explosion.Explosion explosion) {
        return true;
    }

    // ------------------------------------------------------------------------------------------------ save

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        this.dataTracker.set(TICKS, nbt.getInt("Ticks"));
        this.dataTracker.set(RELEASE, nbt.contains("Release") ? nbt.getInt("Release") : -1);
        int[] g = nbt.getIntArray("Ground");
        if (g.length == Pillars.COUNT) {
            System.arraycopy(g, 0, groundY, 0, g.length);
            NbtCompound gc = new NbtCompound();
            gc.putIntArray("g", g);
            this.dataTracker.set(GROUND, gc);
            groundKnown = true;
        }
        readStates(nbt.getList("Shell", NbtElement.COMPOUND_TYPE), placed);
        readStates(nbt.getList("PillarBlocks", NbtElement.COMPOUND_TYPE), pillarPlaced);
        if (this.dataTracker.get(TICKS) >= Rite.SEAL) planShell();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("Ticks", this.dataTracker.get(TICKS));
        nbt.putInt("Release", this.dataTracker.get(RELEASE));
        if (groundKnown) nbt.putIntArray("Ground", groundY.clone());
        nbt.put("Shell", writeStates(placed));
        nbt.put("PillarBlocks", writeStates(pillarPlaced));
    }

    private static NbtList writeStates(Map<Long, BlockState> map) {
        NbtList list = new NbtList();
        for (Map.Entry<Long, BlockState> e : map.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("p", e.getKey());
            c.put("s", NbtHelper.fromBlockState(e.getValue()));
            list.add(c);
        }
        return list;
    }

    private static void readStates(NbtList list, Map<Long, BlockState> map) {
        map.clear();
        for (int k = 0; k < list.size(); k++) {
            NbtCompound c = list.getCompound(k);
            BlockState s;
            try {
                s = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), c.getCompound("s"));
            } catch (Exception ex) {
                s = Blocks.AIR.getDefaultState();
            }
            map.put(c.getLong("p"), s);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new EntitySpawnS2CPacket(this, entry);
    }
}
