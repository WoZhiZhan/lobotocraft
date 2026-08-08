package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityAreaGroupTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void includesEveryCuboidWithTheSameGroupId() {
        List<FacilityAreaEntry> areas = List.of(
                area("control-main", 0, 0, 0, 4, 4, 4),
                area("control-main", 8, 0, 0, 12, 4, 4),
                area("information-main", 20, 0, 0, 24, 4, 4));

        assertEquals(2, FacilityAreaGroup.members(areas, "control-main").size());
        assertTrue(FacilityAreaGroup.contains(areas, "control-main", new BlockPos(2, 2, 2)));
        assertTrue(FacilityAreaGroup.contains(areas, "control-main", new BlockPos(10, 2, 2)));
        assertFalse(FacilityAreaGroup.contains(areas, "control-main", new BlockPos(22, 2, 2)));
    }

    @Test
    void rejectsTheGapBetweenDisjointCuboids() {
        List<FacilityAreaEntry> areas = List.of(
                area("same-room", 0, 0, 0, 4, 4, 4),
                area("same-room", 8, 0, 0, 12, 4, 4));

        assertFalse(FacilityAreaGroup.contains(areas, "same-room", new BlockPos(6, 2, 2)));
    }

    @Test
    void groupAtUsesTheSmallestContainingArea() {
        List<FacilityAreaEntry> areas = List.of(
                area("department", 0, 0, 0, 20, 20, 20),
                area("containment", 4, 4, 4, 6, 6, 6));

        assertEquals("containment", FacilityAreaGroup.groupAt(areas, new BlockPos(5, 5, 5)));
        assertEquals("", FacilityAreaGroup.groupAt(areas, new BlockPos(30, 5, 5)));
    }

    private static FacilityAreaEntry area(String groupId, int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ) {
        return new FacilityAreaEntry(DIMENSION, groupId, BlockPos.ZERO,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                "control", FacilityAreaType.OTHER, groupId);
    }
}
