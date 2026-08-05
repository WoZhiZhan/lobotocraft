package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityAreaSelectionTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void elevatorWinsAnOverlapWithSmallerContainmentRoom() {
        FacilityAreaEntry elevator = area("elevator", FacilityAreaType.ELEVATOR,
                0, 0, 0, 10, 4, 10);
        FacilityAreaEntry containment = area("containment", FacilityAreaType.CONTAINMENT,
                4, 1, 4, 6, 3, 6);

        FacilityAreaEntry selected = FacilityAreaSelection.containing(
                List.of(containment, elevator), DIMENSION, new BlockPos(5, 2, 5)).orElseThrow();

        assertEquals("elevator", selected.groupId);
    }

    @Test
    void corridorWinsContainmentOverlapAtTheDoorway() {
        FacilityAreaEntry corridor = area("corridor", FacilityAreaType.CORRIDOR,
                0, 0, 0, 20, 5, 20);
        FacilityAreaEntry containment = area("containment", FacilityAreaType.CONTAINMENT,
                4, 1, 4, 6, 3, 6);

        FacilityAreaEntry selected = FacilityAreaSelection.containing(
                List.of(corridor, containment), DIMENSION, new BlockPos(5, 2, 5)).orElseThrow();

        assertEquals("corridor", selected.groupId);
    }

    @Test
    void dimensionFilterPreventsCrossWorldMatches() {
        FacilityAreaEntry area = area("elevator", FacilityAreaType.ELEVATOR,
                0, 0, 0, 10, 4, 10);
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

        assertTrue(FacilityAreaSelection.containing(
                List.of(area), other, new BlockPos(5, 2, 5)).isEmpty());
    }

    private static FacilityAreaEntry area(String group, FacilityAreaType type,
                                          int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ) {
        return new FacilityAreaEntry(DIMENSION, group, BlockPos.ZERO,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                "control", type, group);
    }
}
