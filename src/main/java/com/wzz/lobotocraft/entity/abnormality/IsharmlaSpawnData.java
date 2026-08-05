package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent, idempotent Skadi-to-Isharmla transition queue and reactor index. */
public final class IsharmlaSpawnData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "lobotocraft_isharmla_spawns";
    private static final int CHUNKS_PER_ATTEMPT = 4;
    private final Map<UUID, PendingTransition> pending = new LinkedHashMap<>();
    private final Set<UUID> completed = new LinkedHashSet<>();
    private final Map<ResourceLocation, Set<Long>> reactors = new LinkedHashMap<>();

    public static IsharmlaSpawnData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                IsharmlaSpawnData::load, IsharmlaSpawnData::new, NAME);
    }

    public void enqueue(UUID sourceUuid, ResourceLocation dimension, BlockPos origin) {
        if (sourceUuid == null || dimension == null || origin == null
                || pending.containsKey(sourceUuid) || completed.contains(sourceUuid)) return;
        pending.put(sourceUuid, new PendingTransition(sourceUuid, dimension, origin.immutable(), 0, null, 0));
        setDirty();
    }

    public void registerReactor(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) return;
        if (reactors.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>()).add(pos.asLong())) setDirty();
    }

    public void unregisterReactor(ResourceLocation dimension, BlockPos pos) {
        Set<Long> positions = reactors.get(dimension);
        if (positions != null && positions.remove(pos.asLong())) setDirty();
    }

    public void tick(MinecraftServer server) {
        if (pending.isEmpty()) return;
        for (PendingTransition transition : List.copyOf(pending.values())) {
            process(server, transition);
        }
    }

    private void process(MinecraftServer server, PendingTransition transition) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, transition.dimension));
        if (level == null) {
            transition.attempts++;
            logRetry(transition, "dimension is unavailable");
            setDirty();
            return;
        }

        BlockPos reactor = validateReactor(level, transition.reactorPos);
        if (reactor == null) reactor = nearestIndexedReactor(level, transition.origin, transition.lastFailedReactor);
        if (reactor == null) reactor = scanFacility(level, transition);
        if (reactor == null) {
            transition.attempts++;
            logRetry(transition, "no open regeneration reactor found");
            setDirty();
            return;
        }
        transition.reactorPos = reactor.immutable();
        transition.attempts++;
        setDirty();

        if (SpawnIsharmlaHook.trySpawnTransaction(level, reactor, transition.id)) {
            pending.remove(transition.id);
            completed.add(transition.id);
            while (completed.size() > 512) completed.remove(completed.iterator().next());
            setDirty();
            LOGGER.info("Completed Isharmla transition {} at {} in {}",
                    transition.id, reactor, transition.dimension);
        } else {
            transition.lastFailedReactor = reactor;
            transition.reactorPos = null;
            setDirty();
            logRetry(transition, "reactor has no collision-safe spawn point");
        }
    }

    private BlockPos nearestIndexedReactor(ServerLevel level, BlockPos origin, BlockPos excludedOnce) {
        Set<Long> positions = reactors.getOrDefault(level.dimension().location(), Set.of());
        List<BlockPos> sorted = positions.stream().map(BlockPos::of)
                .sorted(Comparator.<BlockPos>comparingDouble(origin::distSqr)
                        .thenComparingLong(BlockPos::asLong))
                .toList();
        BlockPos excludedFallback = null;
        for (BlockPos candidate : sorted) {
            BlockPos valid = validateReactor(level, candidate);
            if (valid != null && !valid.equals(excludedOnce)) return valid;
            if (valid != null) {
                excludedFallback = valid;
                continue;
            }
            unregisterReactor(level.dimension().location(), candidate);
        }
        return excludedFallback;
    }

    private BlockPos scanFacility(ServerLevel level, PendingTransition transition) {
        ResourceLocation dimension = level.dimension().location();
        List<FacilityAreaEntry> areas = FacilityWorldLayout.mergeAreas(dimension,
                CompanyRoomData.get(level).getAreas(dimension));
        Set<Long> chunkKeys = new LinkedHashSet<>();
        for (FacilityAreaEntry area : areas) {
            if (!FacilityAccessService.isGroupOpen(level.getServer(), dimension, area.groupId)) continue;
            int minX = area.minPos.getX() >> 4;
            int maxX = area.maxPos.getX() >> 4;
            int minZ = area.minPos.getZ() >> 4;
            int maxZ = area.maxPos.getZ() >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) chunkKeys.add(ChunkPos.asLong(x, z));
            }
        }
        List<Long> chunks = new ArrayList<>(chunkKeys);
        chunks.sort(Comparator.<Long>comparingDouble(value -> {
            ChunkPos pos = new ChunkPos(value);
            return transition.origin.distSqr(new BlockPos(pos.getMiddleBlockX(), transition.origin.getY(), pos.getMiddleBlockZ()));
        }).thenComparingLong(Long::longValue));
        if (chunks.isEmpty()) return null;

        int start = Math.floorMod(transition.scanCursor, chunks.size());
        for (int offset = 0; offset < Math.min(CHUNKS_PER_ATTEMPT, chunks.size()); offset++) {
            ChunkPos chunkPos = new ChunkPos(chunks.get((start + offset) % chunks.size()));
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            for (var blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof RegenerationReactorBlockEntity) {
                    registerReactor(dimension, blockEntity.getBlockPos());
                }
            }
        }
        transition.scanCursor = (start + CHUNKS_PER_ATTEMPT) % chunks.size();
        return nearestIndexedReactor(level, transition.origin, transition.lastFailedReactor);
    }

    private BlockPos validateReactor(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        level.getChunk(pos);
        return level.getBlockEntity(pos) instanceof RegenerationReactorBlockEntity
                && FacilityAccessService.isPositionOpen(level.getServer(), level.dimension().location(), pos)
                ? pos : null;
    }

    private static void logRetry(PendingTransition transition, String reason) {
        if (transition.attempts <= 1 || transition.attempts % 100 == 0) {
            LOGGER.warn("Isharmla transition {} is still pending after {} attempts: {}",
                    transition.id, transition.attempts, reason);
        }
    }

    int pendingCount() {
        return pending.size();
    }

    boolean hasPending(UUID id) {
        return pending.containsKey(id);
    }

    boolean isCompleted(UUID id) {
        return completed.contains(id);
    }

    int reactorCount(ResourceLocation dimension) {
        return reactors.getOrDefault(dimension, Set.of()).size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag transitions = new ListTag();
        for (PendingTransition transition : pending.values()) transitions.add(transition.save());
        tag.put("Pending", transitions);
        ListTag completedList = new ListTag();
        for (UUID id : completed) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Id", id);
            completedList.add(value);
        }
        tag.put("Completed", completedList);
        ListTag reactorList = new ListTag();
        reactors.forEach((dimension, positions) -> positions.forEach(value -> {
            CompoundTag reactor = new CompoundTag();
            reactor.putString("Dimension", dimension.toString());
            reactor.putLong("Pos", value);
            reactorList.add(reactor);
        }));
        tag.put("Reactors", reactorList);
        return tag;
    }

    public static IsharmlaSpawnData load(CompoundTag tag) {
        IsharmlaSpawnData data = new IsharmlaSpawnData();
        ListTag transitions = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < transitions.size(); i++) {
            PendingTransition transition = PendingTransition.load(transitions.getCompound(i));
            if (transition != null) data.pending.put(transition.id, transition);
        }
        ListTag completed = tag.getList("Completed", Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            CompoundTag value = completed.getCompound(i);
            if (value.hasUUID("Id")) data.completed.add(value.getUUID("Id"));
        }
        ListTag reactors = tag.getList("Reactors", Tag.TAG_COMPOUND);
        for (int i = 0; i < reactors.size(); i++) {
            CompoundTag reactor = reactors.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(reactor.getString("Dimension"));
            if (dimension != null) data.reactors.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>())
                    .add(reactor.getLong("Pos"));
        }
        return data;
    }

    private static final class PendingTransition {
        private final UUID id;
        private final ResourceLocation dimension;
        private final BlockPos origin;
        private int attempts;
        private BlockPos reactorPos;
        private int scanCursor;
        private BlockPos lastFailedReactor;

        private PendingTransition(UUID id, ResourceLocation dimension, BlockPos origin,
                                  int attempts, BlockPos reactorPos, int scanCursor) {
            this.id = id;
            this.dimension = dimension;
            this.origin = origin;
            this.attempts = attempts;
            this.reactorPos = reactorPos;
            this.scanCursor = scanCursor;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Dimension", dimension.toString());
            tag.putLong("Origin", origin.asLong());
            tag.putInt("Attempts", attempts);
            tag.putInt("ScanCursor", scanCursor);
            if (reactorPos != null) tag.putLong("Reactor", reactorPos.asLong());
            return tag;
        }

        private static PendingTransition load(CompoundTag tag) {
            if (!tag.hasUUID("Id")) return null;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) return null;
            BlockPos reactor = tag.contains("Reactor") ? BlockPos.of(tag.getLong("Reactor")) : null;
            return new PendingTransition(tag.getUUID("Id"), dimension, BlockPos.of(tag.getLong("Origin")),
                    Math.max(0, tag.getInt("Attempts")), reactor, Math.max(0, tag.getInt("ScanCursor")));
        }
    }
}
