package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class ClerkTombstoneData extends SavedData {
    private static final String ID = "lobotocraft_clerk_tombstones";
    private static final String POSITIONS_KEY = "Positions";

    private final Set<Long> positions = new HashSet<>();

    public static ClerkTombstoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ClerkTombstoneData::load,
                ClerkTombstoneData::new,
                ID
        );
    }

    public static ClerkTombstoneData load(CompoundTag tag) {
        ClerkTombstoneData data = new ClerkTombstoneData();
        for (long position : tag.getLongArray(POSITIONS_KEY)) {
            data.positions.add(position);
        }
        return data;
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            setDirty();
        }
    }

    public Set<BlockPos> getPositions() {
        Set<BlockPos> result = new HashSet<>();
        for (long position : positions) {
            result.add(BlockPos.of(position));
        }
        return result;
    }

    public void clearAll() {
        if (!positions.isEmpty()) {
            positions.clear();
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(POSITIONS_KEY, positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
