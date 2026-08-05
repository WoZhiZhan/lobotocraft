package com.wzz.lobotocraft.employee;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmployeeNavigatorElevatorSelectionTest {
    @Test
    void prefersShaftWhoseExitIsOnTheTargetSide() {
        Vec3 player = new Vec3(250.5D, 169.0D, 44.5D);
        BlockPos target = new BlockPos(260, 202, 44);
        BlockPos lowerRight = new BlockPos(260, 168, 44);
        BlockPos upperRight = new BlockPos(260, 201, 44);
        BlockPos lowerLeft = new BlockPos(145, 168, 44);
        BlockPos upperLeft = new BlockPos(145, 201, 44);

        double rightShaft = EmployeeNavigator.elevatorRouteScore(
                player, target, lowerRight, upperRight);
        double leftShaft = EmployeeNavigator.elevatorRouteScore(
                player, target, lowerLeft, upperLeft);

        assertTrue(rightShaft < leftShaft);
    }

    @Test
    void sameFloorDetourPrefersTheReachableNearbyEntryShaft() {
        Vec3 player = new Vec3(105.5D, 45.0D, 52.5D);
        BlockPos target = new BlockPos(285, 45, 52);
        BlockPos nearbyEntry = new BlockPos(100, 44, 52);
        BlockPos nearbyLowerExit = new BlockPos(100, 4, 52);
        BlockPos farEntry = new BlockPos(270, 44, 52);
        BlockPos farLowerExit = new BlockPos(270, 4, 52);

        double nearby = EmployeeNavigator.elevatorDetourScore(
                player, target, nearbyEntry, nearbyLowerExit);
        double far = EmployeeNavigator.elevatorDetourScore(
                player, target, farEntry, farLowerExit);

        assertTrue(nearby < far);
    }

    @Test
    void crossFloorContinuationPrefersTargetSideExitOverReturningNearby() {
        Vec3 player = new Vec3(12.5D, 5.0D, 52.5D);
        BlockPos target = new BlockPos(269, 45, 52);
        double returnLeft = EmployeeNavigator.elevatorRouteScore(player, target,
                new BlockPos(12, 4, 52), new BlockPos(12, 44, 52));
        double crossToRight = EmployeeNavigator.elevatorRouteScore(player, target,
                new BlockPos(378, 4, 52), new BlockPos(269, 44, 52));

        assertTrue(crossToRight < returnLeft);
    }
}
