package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.employee.navigation.NavigationAction;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationPathStep;
import com.wzz.lobotocraft.employee.navigation.NavigationTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NavigationExecutionPolicyTest {
    @Test
    void turnDoesNotCutTheInsideCorner() {
        NavigationPathStep corner = step(node(0, 0), node(1, 0));
        NavigationPathStep afterTurn = step(node(1, 0), node(1, 1));

        assertFalse(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(1.30D, 64.0D, 0.5D), corner, afterTurn, false, false));
        assertTrue(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(1.41D, 64.0D, 0.5D), corner, afterTurn, false, false));
    }

    @Test
    void straightPathKeepsTheWiderWaypointTolerance() {
        NavigationPathStep current = step(node(0, 0), node(1, 0));
        NavigationPathStep next = step(node(1, 0), node(2, 0));

        assertTrue(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(1.10D, 64.0D, 0.5D), current, next, false, false));
    }

    @Test
    void straightPathConsumesAWaypointOvershotAtSprintSpeed() {
        NavigationPathStep current = step(node(2, 0), node(1, 0));
        NavigationPathStep next = step(node(1, 0), node(0, 0));

        assertTrue(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(0.98D, 64.0D, 0.5D), current, next, false, false));
    }

    @Test
    void straightOvershootDoesNotConsumeAWaypointFromAnotherLane() {
        NavigationPathStep current = step(node(2, 0), node(1, 0));
        NavigationPathStep next = step(node(1, 0), node(0, 0));

        assertFalse(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(0.98D, 64.0D, 1.25D), current, next, false, false));
    }

    @Test
    void stairChainConsumesAHighHalfWaypointAfterPassingIt() {
        NavigationPathStep current = step(
                new NavigationNode(new BlockPos(0, 64, 2), 64.0D),
                new NavigationNode(new BlockPos(0, 65, 1), 65.0D),
                NavigationAction.STEP_UP);
        NavigationPathStep next = step(
                new NavigationNode(new BlockPos(0, 65, 1), 65.0D),
                new NavigationNode(new BlockPos(0, 66, 0), 66.0D),
                NavigationAction.STEP_UP);

        assertTrue(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(0.5D, 65.5D, 1.1D), current, next, false, false));
        assertFalse(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(0.5D, 66.5D, 1.1D), current, next, false, false));
    }

    @Test
    void waypointShortcutStopsBeforeATurn() {
        NavigationPathStep east = step(node(0, 0), node(1, 0));
        NavigationPathStep eastAgain = step(node(1, 0), node(2, 0));
        NavigationPathStep south = step(node(2, 0), node(2, 1));

        assertTrue(NavigationExecutionPolicy.continuesStraight(east, eastAgain));
        assertFalse(NavigationExecutionPolicy.continuesStraight(eastAgain, south));
    }

    @Test
    void onlyCardinalSameHeightWalksUseTheAdjacentCellFastPath() {
        assertTrue(NavigationExecutionPolicy.isCardinalFlatWalk(
                step(node(0, 0), node(1, 0))));
        assertFalse(NavigationExecutionPolicy.isCardinalFlatWalk(
                step(node(0, 0), node(1, 1))));
        assertFalse(NavigationExecutionPolicy.isCardinalFlatWalk(step(
                new NavigationNode(new BlockPos(0, 64, 0), 64.0D),
                new NavigationNode(new BlockPos(1, 65, 0), 65.0D),
                NavigationAction.STEP_UP)));
    }

    @Test
    void recoveryAlignmentCannotBeSkipped() {
        NavigationNode start = node(0, 0);
        NavigationTransition alignmentTransition = new NavigationTransition(
                start, NavigationAction.WALK, List.of(), 1.0D);
        NavigationPathStep alignment = new NavigationPathStep(
                start, start, alignmentTransition, 1.0D, 1.0D);

        assertFalse(NavigationExecutionPolicy.continuesStraight(
                alignment, step(start, node(1, 0))));
    }

    @Test
    void finalInteractionNodeWaitsForTheRealEntityDistance() {
        NavigationPathStep endpoint = step(node(0, 0), node(1, 0));
        Vec3 center = endpoint.node().standingPosition();

        assertFalse(NavigationExecutionPolicy.hasReachedWaypoint(
                center, endpoint, null, true, false));
        assertTrue(NavigationExecutionPolicy.hasReachedWaypoint(
                center, endpoint, null, true, true));
    }

    @Test
    void verticalWaypointCannotBeSkippedFromTheFloorBelow() {
        NavigationPathStep stepUp = step(
                new NavigationNode(new BlockPos(0, 64, 0), 64.0D),
                new NavigationNode(new BlockPos(1, 65, 0), 65.0D));

        assertFalse(NavigationExecutionPolicy.hasReachedWaypoint(
                new Vec3(1.5D, 64.0D, 0.5D), stepUp, null, false, false));
    }

    @Test
    void identicalRequestsAreIdempotentButRetargetsAreNot() {
        BlockPos block = new BlockPos(4, 64, -3);
        assertTrue(NavigationExecutionPolicy.sameMoveRequest(block, 1.6D, block, 1.6D));
        assertFalse(NavigationExecutionPolicy.sameMoveRequest(block, 1.6D,
                block.east(), 1.6D));

        Vec3 target = new Vec3(4.5D, 64.0D, -2.5D);
        assertTrue(NavigationExecutionPolicy.sameInteractionRequest(
                target, 2.5D, target, 2.5D));
        assertFalse(NavigationExecutionPolicy.sameInteractionRequest(
                target, 2.5D, target.add(0.01D, 0.0D, 0.0D), 2.5D));
    }

    @Test
    void combatEndpointUsesReliableWeaponRangeWithoutEnteringTargetBody() {
        Vec3 target = new Vec3(4.5D, 64.0D, 4.5D);
        assertTrue(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(6.5D, 64.0D, 4.5D), target, 2.65D));
        assertFalse(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(7.0D, 64.0D, 4.5D), target, 2.65D));
        assertFalse(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(5.0D, 64.0D, 4.5D), target, 2.65D));
    }

    @Test
    void rangedCombatEndpointStaysInsideMinimumAndMaximumSpacing() {
        Vec3 target = new Vec3(0.5D, 64.0D, 0.5D);
        assertFalse(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(7.99D, 64.0D, 0.5D), target, 8.0D, 22.0D));
        assertTrue(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(8.5D, 64.0D, 0.5D), target, 8.0D, 22.0D));
        assertTrue(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(22.0D, 64.0D, 0.5D), target, 8.0D, 22.0D));
        assertFalse(NavigationExecutionPolicy.validCombatEndpoint(
                new Vec3(22.5D, 64.0D, 0.5D), target, 8.0D, 22.0D));
    }

    @Test
    void smallTargetDriftKeepsPlanningButFloorChangesDoNot() {
        Vec3 planned = new Vec3(10.5D, 64.0D, 10.5D);
        assertTrue(NavigationExecutionPolicy.planningTargetStillRelevant(
                64.0D, planned, planned.add(1.0D, 0.0D, 0.5D)));
        assertFalse(NavigationExecutionPolicy.planningTargetStillRelevant(
                64.0D, planned, planned.add(2.0D, 0.0D, 0.0D)));
        assertFalse(NavigationExecutionPolicy.planningTargetStillRelevant(
                64.0D, planned, new Vec3(10.5D, 72.0D, 10.5D)));
    }

    @Test
    void existingCombatEndpointSurvivesOnlyWhileLatestTargetRemainsInRange() {
        Vec3 endpoint = new Vec3(8.5D, 64.0D, 8.5D);
        assertTrue(NavigationExecutionPolicy.validCombatEndpoint(
                endpoint, new Vec3(10.0D, 64.0D, 8.5D), 2.65D));
        assertFalse(NavigationExecutionPolicy.validCombatEndpoint(
                endpoint, new Vec3(11.0D, 64.0D, 8.5D), 2.65D));
    }

    @Test
    void movementKeepsSprintingBeforeAControlledWaypoint() {
        NavigationNode waypoint = node(1, 0);
        assertEquals(0.9F, NavigationExecutionPolicy.forwardInput(
                new Vec3(0.75D, 64.0D, 0.5D), waypoint));
        assertEquals(1.0F, NavigationExecutionPolicy.forwardInput(
                new Vec3(0.0D, 64.0D, 0.5D), waypoint));
    }

    @Test
    void offCenterReplanStartsWithARecoveryAlignment() {
        NavigationNode start = node(0, 0);
        assertTrue(NavigationExecutionPolicy.needsStartAlignment(
                new Vec3(0.15D, 64.0D, 0.15D), start));
        assertFalse(NavigationExecutionPolicy.needsStartAlignment(
                new Vec3(0.46D, 64.0D, 0.53D), start));
    }

    @Test
    void searchWindowCanContinueAcrossHorizontalAndVerticalEdges() {
        BlockPos center = new BlockPos(0, 64, 0);
        assertTrue(EmployeeNavigator.isSearchBoundary(
                new BlockPos(19, 64, 0), center, 20, 7));
        assertTrue(EmployeeNavigator.isSearchBoundary(
                new BlockPos(0, 69, 0), center, 20, 7));
        assertFalse(EmployeeNavigator.isSearchBoundary(
                new BlockPos(4, 66, 4), center, 20, 7));
    }

    private static NavigationPathStep step(NavigationNode from, NavigationNode to) {
        return step(from, to, NavigationAction.WALK);
    }

    private static NavigationPathStep step(NavigationNode from, NavigationNode to,
                                           NavigationAction action) {
        NavigationTransition transition = new NavigationTransition(
                to, action, List.of(), 1.0D);
        return new NavigationPathStep(from, to, transition, 1.0D, 1.0D);
    }

    private static NavigationNode node(int x, int z) {
        return new NavigationNode(new BlockPos(x, 64, z));
    }
}
