package com.wzz.lobotocraft.world.gear;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runtime-only initial batch: no chunk forcing, no worldgen-side entity or SavedData writes. */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class GearChurchResidents {
    public static final String DATA_ID = "lobotocraft_gear_church_residents";
    private GearChurchResidents() {}
    public static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_ID);
    }
    public static int populateNear(ServerLevel level, StructureStart start, Vec3 observer) {
        if (!start.isValid() || !(start.getStructure() instanceof GearChurchStructure)
                || start.getPieces().isEmpty() || !(start.getPieces().get(0) instanceof GearChurchPiece piece)) return 0;
        var record = data(level).get(piece.anchor(), piece.rotation());
        return populateRecord(level, record, observer);
    }
    public static boolean withinResidentRange(BlockPos anchor, Vec3 observer) {
        return observer.distanceToSqr(Vec3.atCenterOf(anchor)) <= 128 * 128;
    }
    public static int populateRecord(ServerLevel level, Record record, Vec3 observer) {
        if (!withinResidentRange(record.anchor, observer)) return 0;
        int added = 0;
        for (int slot = 0; slot < GearChurchLayout.RESIDENTS; slot++) {
            if ((record.mask & (1 << slot)) != 0) continue;
            BlockPos pos = GearChurchLayout.world(GearChurchLayout.SPAWNS.get(slot), record.anchor, record.rotation);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                    || !level.getBlockState(pos).getFluidState().isEmpty()) continue;
            // A neighboring chunk can be loaded before its structure placement finishes.
            var local = GearChurchLayout.SPAWNS.get(slot);
            BlockPos roof = GearChurchLayout.world(new BlockPos(local.getX(),
                    GearChurchLayout.roofHeight(local.getX(), local.getZ()), local.getZ()), record.anchor, record.rotation);
            if (level.getBlockState(roof).isAir()) continue;
            UUID id = residentId(level, record.anchor, slot);
            if (level.getEntity(id) != null) {
                record.mask |= 1 << slot; data(level).setDirty(); continue;
            }
            var mob = ModEntities.gear_follower.get().create(level);
            if (mob == null) continue;
            mob.setUUID(id);
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            mob.setChurch(record.anchor.asLong(), slot);
            if (!level.noCollision(mob) || !level.getEntities(mob, mob.getBoundingBox(),
                    e -> e instanceof net.minecraft.world.entity.LivingEntity && e.isAlive()).isEmpty()) continue;
            if (level.addFreshEntity(mob)) {
                record.mask |= 1 << slot; data(level).setDirty(); added++;
            }
        }
        return added;
    }
    /** Called by placed pieces: serialization/index mutation always executes on the server thread. */
    public static void registerPlaced(ServerLevel level, BlockPos anchor, Rotation rotation) {
        level.getServer().execute(() -> data(level).get(anchor, rotation));
    }
    public static UUID residentId(ServerLevel level, BlockPos anchor, int slot) {
        return UUID.nameUUIDFromBytes((level.dimension().location() + "/gear_church/" + anchor.asLong()
                + "/" + slot).getBytes(StandardCharsets.UTF_8));
    }
    private static List<StructureStart> loadedStarts(ServerLevel level, BlockPos probe,
                                                     net.minecraft.world.level.levelgen.structure.Structure church) {
        if (!level.hasChunkAt(probe)) return List.of();
        List<StructureStart> starts = new ArrayList<>();
        var loaded = level.getChunkAt(probe);
        for (long reference : loaded.getReferencesForStructure(church)) {
            var sourceChunk = new ChunkPos(reference);
            if (!level.hasChunk(sourceChunk.x, sourceChunk.z)) continue;
            var start = level.getChunk(sourceChunk.x, sourceChunk.z).getStartForStructure(church);
            if (start != null && start.isValid()) starts.add(start);
        }
        return starts;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 0) return;
        var level = event.getServer().overworld();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var church = registry.get(GearChurchLayout.ID);
        if (church == null) return;
        Set<Long> processed = new HashSet<>();
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) continue;
            for (int dx : new int[]{-64, 0, 64}) for (int dz : new int[]{-64, 0, 64}) {
                BlockPos probe = player.blockPosition().offset(dx, 0, dz);
                if (!level.hasChunkAt(probe)) continue;
                for (var start : loadedStarts(level, probe, church)) {
                    if (!start.getPieces().isEmpty() && start.getPieces().get(0) instanceof GearChurchPiece piece)
                        data(level).get(piece.anchor(), piece.rotation());
                }
                for (var record : data(level).recordsAt(new ChunkPos(probe))) {
                    // A far player must not claim the dedup slot before a nearby player is considered.
                    if (withinResidentRange(record.anchor, player.position()) && processed.add(record.anchor.asLong()))
                        populateRecord(level, record, player.position());
                }
            }
        }
    }
    public static boolean blocksNatural(MobSpawnType reason, boolean follower, boolean indoors) {
        return !follower && indoors && (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION);
    }
    @SubscribeEvent public static void preventNaturalSpawn(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        ServerLevel level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        var church = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(GearChurchLayout.ID);
        if (church == null) return;
        BlockPos pos = event.getEntity().blockPosition();
        if (!(event.getLevel() instanceof net.minecraft.server.level.WorldGenRegion)) {
            if (blocksNatural(event.getSpawnType(), event.getEntity() instanceof com.wzz.lobotocraft.entity.gear.EntityGearFollower,
                    data(level).indoors(pos))) event.setResult(Event.Result.DENY);
            return;
        }
        var region = (net.minecraft.server.level.WorldGenRegion) event.getLevel();
        var starts = List.of(level.structureManager().forWorldGenRegion(region).getStructureAt(pos, church));
        for (var start : starts) {
            if (!start.isValid() || start.getPieces().isEmpty() || !(start.getPieces().get(0) instanceof GearChurchPiece piece)) continue;
            boolean indoor = GearChurchLayout.indoors(GearChurchLayout.local(pos, piece.anchor(), piece.rotation()));
            if (blocksNatural(event.getSpawnType(), event.getEntity() instanceof com.wzz.lobotocraft.entity.gear.EntityGearFollower, indoor)) {
                event.setResult(Event.Result.DENY); return;
            }
        }
    }
    public static final class Record {
        final BlockPos anchor; final Rotation rotation; int mask;
        Record(BlockPos anchor, Rotation rotation, int mask) { this.anchor = anchor; this.rotation = rotation; this.mask = mask; }
        public int spawnedCount() { return Integer.bitCount(mask & 0xFFFF); }
    }
    public static final class Data extends SavedData {
        private final Map<Long, Record> records = new HashMap<>();
        // Rebuilt from saved records; no permanent references to levels, entities or players.
        private final Map<Long, List<Record>> byChunk = new HashMap<>();
        public Record get(BlockPos anchor, Rotation rotation) {
            return records.computeIfAbsent(anchor.asLong(), k -> {
                var record = new Record(anchor, rotation, 0); index(record); setDirty(); return record;
            });
        }
        private void index(Record record) {
            var a = GearChurchLayout.world(BlockPos.ZERO, record.anchor, record.rotation);
            var b = GearChurchLayout.world(new BlockPos(GearChurchLayout.WIDTH - 1, 0, GearChurchLayout.DEPTH - 1),
                    record.anchor, record.rotation);
            for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++)
                for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++)
                    byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(record);
        }
        public List<Record> recordsAt(ChunkPos chunk) { return List.copyOf(byChunk.getOrDefault(chunk.toLong(), List.of())); }
        public boolean indoors(BlockPos pos) {
            for (var record : recordsAt(new ChunkPos(pos)))
                if (GearChurchLayout.indoors(GearChurchLayout.local(pos, record.anchor, record.rotation))) return true;
            return false;
        }
        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag entry : tag.getList("Churches", Tag.TAG_COMPOUND)) {
                var c = (CompoundTag) entry;
                BlockPos pos = BlockPos.of(c.getLong("Anchor"));
                try {
                    if (!data.records.containsKey(pos.asLong())) {
                        var record = new Record(pos, Rotation.valueOf(c.getString("Rotation")), c.getInt("Mask") & 0xFFFF);
                        data.records.put(pos.asLong(), record); data.index(record);
                    }
                }
                catch (IllegalArgumentException ignored) {}
            }
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            var list = new ListTag();
            records.values().forEach(r -> {
                var c = new CompoundTag(); c.putLong("Anchor", r.anchor.asLong());
                c.putString("Rotation", r.rotation.name()); c.putInt("Mask", r.mask); list.add(c);
            });
            tag.put("Churches", list); return tag;
        }
    }
}
