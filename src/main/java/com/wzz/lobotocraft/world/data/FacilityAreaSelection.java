package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic ownership for positions covered by overlapping facility cuboids. */
public final class FacilityAreaSelection {
    private FacilityAreaSelection() {
    }

    public static Optional<FacilityAreaEntry> containing(List<FacilityAreaEntry> areas,
                                                         ResourceLocation dimension,
                                                         BlockPos position) {
        if (areas == null || position == null) return Optional.empty();
        return areas.stream()
                .filter(area -> area != null && (dimension == null || dimension.equals(area.dimension))
                        && area.groupId != null && !area.groupId.isBlank()
                        && contains(area, position))
                .min(entryComparator());
    }

    public static Optional<FacilityAreaEntry> containing(List<FacilityAreaEntry> areas,
                                                         ResourceLocation dimension,
                                                         Vec3 position) {
        if (areas == null || position == null) return Optional.empty();
        return areas.stream()
                .filter(area -> area != null && (dimension == null || dimension.equals(area.dimension))
                        && area.groupId != null && !area.groupId.isBlank()
                        && contains(area, position))
                .min(entryComparator());
    }

    public static int typePriority(FacilityAreaType type) {
        if (type == FacilityAreaType.ELEVATOR || type == FacilityAreaType.ELEVATOR_SHAFT) return 0;
        if (type == FacilityAreaType.CORRIDOR || type == FacilityAreaType.REST_ROOM) return 1;
        if (type == FacilityAreaType.CONTAINMENT) return 2;
        return 3;
    }

    public static long volume(FacilityAreaEntry area) {
        long x = (long) area.maxPos.getX() - area.minPos.getX() + 1L;
        long y = (long) area.maxPos.getY() - area.minPos.getY() + 1L;
        long z = (long) area.maxPos.getZ() - area.minPos.getZ() + 1L;
        return Math.max(1L, x) * Math.max(1L, y) * Math.max(1L, z);
    }

    private static Comparator<FacilityAreaEntry> entryComparator() {
        return Comparator.comparingInt((FacilityAreaEntry area) -> typePriority(area.type))
                .thenComparingLong(FacilityAreaSelection::volume)
                .thenComparing(area -> area.key == null ? "" : area.key);
    }

    private static boolean contains(FacilityAreaEntry area, BlockPos position) {
        return position.getX() >= area.minPos.getX() && position.getX() <= area.maxPos.getX()
                && position.getY() >= area.minPos.getY() && position.getY() <= area.maxPos.getY()
                && position.getZ() >= area.minPos.getZ() && position.getZ() <= area.maxPos.getZ();
    }

    private static boolean contains(FacilityAreaEntry area, Vec3 position) {
        return position.x >= area.minPos.getX() && position.x < area.maxPos.getX() + 1.0D
                && position.y >= area.minPos.getY() && position.y < area.maxPos.getY() + 1.0D
                && position.z >= area.minPos.getZ() && position.z < area.maxPos.getZ() + 1.0D;
    }
}
