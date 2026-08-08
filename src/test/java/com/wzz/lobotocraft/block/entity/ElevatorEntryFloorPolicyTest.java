package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.world.data.FloorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorEntryFloorPolicyTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.tryParse("minecraft:overworld");

    @Test
    void rejectsCloserElevatorOnAnotherFloor() {
        List<FloorEntry> floors = floorsAt(30, 16, 2);

        assertTrue(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(3), posAt(3)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(3), posAt(17)));
    }

    @Test
    void supportsIrregularFloorSpacingInBothDirections() {
        List<FloorEntry> floors = floorsAt(47, 21, 4);

        assertTrue(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(23), posAt(25)));
        assertTrue(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(48), posAt(50)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(48), posAt(23)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(5), posAt(23)));
    }

    @Test
    void fallsBackToSmallYDifferenceWithoutFloorMarkers() {
        assertTrue(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                List.of(), posAt(10), posAt(14)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                List.of(), posAt(10), posAt(15)));
    }

    @Test
    void treatsDifferentRoomHeightsWithinOneMarkedFloorAsArrived() {
        List<FloorEntry> floors = floorsAt(236, 201, 168);

        assertTrue(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(202), posAt(222)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floors, posAt(236), posAt(222)));
    }

    @Test
    void rejectsMissingPositions() {
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floorsAt(10), null, posAt(10)));
        assertFalse(ElevatorEntryFloorPolicy.isBoardingPositionOnEntityFloor(
                floorsAt(10), posAt(10), null));
    }

    private static List<FloorEntry> floorsAt(int... heights) {
        return java.util.Arrays.stream(heights)
                .mapToObj(height -> new FloorEntry(DIMENSION, posAt(height)))
                .toList();
    }

    private static BlockPos posAt(int y) {
        return new BlockPos(0, y, 0);
    }
}
