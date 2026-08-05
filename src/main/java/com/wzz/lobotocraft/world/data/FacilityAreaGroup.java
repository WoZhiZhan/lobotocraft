package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Geometry helpers for logical facility areas represented by multiple cuboids. */
public final class FacilityAreaGroup {
    private FacilityAreaGroup() {
    }

    public static String groupAt(List<FacilityAreaEntry> areas, BlockPos position) {
        if (areas == null || position == null) return "";
        return FacilityAreaSelection.containing(areas, null, position)
                .filter(FacilityAreaGroup::hasGroup)
                .map(area -> area.groupId)
                .orElse("");
    }

    public static List<FacilityAreaEntry> members(List<FacilityAreaEntry> areas, String groupId) {
        if (areas == null || groupId == null || groupId.isBlank()) return List.of();
        return areas.stream()
                .filter(area -> area != null && groupId.equals(area.groupId))
                .toList();
    }

    public static boolean contains(List<FacilityAreaEntry> areas, String groupId, BlockPos position) {
        if (position == null) return false;
        return members(areas, groupId).stream().anyMatch(area -> contains(area, position));
    }

    public static boolean contains(FacilityAreaEntry area, BlockPos position) {
        return area != null && position != null
                && position.getX() >= area.minPos.getX() && position.getX() <= area.maxPos.getX()
                && position.getY() >= area.minPos.getY() && position.getY() <= area.maxPos.getY()
                && position.getZ() >= area.minPos.getZ() && position.getZ() <= area.maxPos.getZ();
    }

    private static boolean hasGroup(FacilityAreaEntry area) {
        return area != null && area.groupId != null && !area.groupId.isBlank();
    }

}
