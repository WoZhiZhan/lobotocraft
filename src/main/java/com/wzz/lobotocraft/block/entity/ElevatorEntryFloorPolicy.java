package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FloorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Prevents navigation from treating an elevator on another floor as a boarding stop. */
public final class ElevatorEntryFloorPolicy {
    static final int FALLBACK_MAX_Y_DIFFERENCE = 4;

    private ElevatorEntryFloorPolicy() {
    }

    public static boolean isOnEntityFloor(ServerLevel level, BlockPos entityPosition,
                                           BlockPos elevatorPosition) {
        if (level == null || entityPosition == null || elevatorPosition == null) return false;
        List<FloorEntry> floors = CompanyRoomData.get(level)
                .getFloors(level.dimension().location());
        return isBoardingPositionOnEntityFloor(floors, entityPosition,
                elevatorPosition.above());
    }

    public static boolean isOnSameFloor(ServerLevel level, BlockPos first, BlockPos second) {
        if (level == null || first == null || second == null) return false;
        List<FloorEntry> floors = CompanyRoomData.get(level)
                .getFloors(level.dimension().location());
        return isBoardingPositionOnEntityFloor(floors, first, second);
    }

    static boolean isBoardingPositionOnEntityFloor(List<FloorEntry> sortedFloors,
                                                    BlockPos entityPosition,
                                                    BlockPos boardingPosition) {
        if (entityPosition == null || boardingPosition == null) return false;
        if (sortedFloors == null || sortedFloors.isEmpty()) {
            return Math.abs(entityPosition.getY() - boardingPosition.getY())
                    <= FALLBACK_MAX_Y_DIFFERENCE;
        }
        int entityFloor = CompanyRoomData.resolveFloorIndex(sortedFloors, entityPosition);
        int elevatorFloor = CompanyRoomData.resolveFloorIndex(sortedFloors, boardingPosition);
        return entityFloor >= 0 && entityFloor == elevatorFloor;
    }
}
