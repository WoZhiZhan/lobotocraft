package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ElevatorRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> POSITIONS = new ConcurrentHashMap<>();

    private ElevatorRegistry() {
    }

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        POSITIONS.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> positions = POSITIONS.get(level.dimension());
        if (positions != null) positions.remove(pos);
    }

    public static Set<BlockPos> positions(ServerLevel level) {
        Set<BlockPos> positions = POSITIONS.get(level.dimension());
        return positions == null ? Set.of() : Set.copyOf(positions);
    }

    /** Includes persisted elevator markers so chunk unload/reload cannot erase route discovery. */
    public static Set<BlockPos> knownPositions(ServerLevel level) {
        Set<BlockPos> known = new HashSet<>(positions(level));
        CompanyRoomData.get(level).getAreas(level.dimension().location()).stream()
                .filter(area -> area.type == FacilityAreaType.ELEVATOR)
                .map(area -> area.markerPos)
                .filter(position -> position != null)
                .forEach(position -> known.add(position.immutable()));
        return Set.copyOf(known);
    }
}
