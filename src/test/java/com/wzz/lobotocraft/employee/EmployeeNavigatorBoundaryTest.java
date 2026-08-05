package com.wzz.lobotocraft.employee;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wzz.lobotocraft.employee.navigation.AStarPathfinder;
import com.wzz.lobotocraft.employee.navigation.NavigationAction;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationPath;
import com.wzz.lobotocraft.employee.navigation.NavigationPathStep;
import com.wzz.lobotocraft.employee.navigation.NavigationTransition;
import com.wzz.lobotocraft.employee.navigation.PathSearchResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class EmployeeNavigatorBoundaryTest {
    @Test
    void onlyLargeVerticalTransfersPreferAnElevatorBeforeAStar() {
        assertFalse(EmployeeNavigator.shouldPreferElevatorFirst(130.0D, 116.0D));
        assertFalse(EmployeeNavigator.shouldPreferElevatorFirst(130.0D, 162.0D));
        assertFalse(EmployeeNavigator.shouldPreferElevatorFirst(130.0D, 92.0D));
        assertFalse(EmployeeNavigator.shouldPreferElevatorFirst(130.0D, 82.0D));
        assertTrue(EmployeeNavigator.shouldPreferElevatorFirst(130.0D, 81.0D));
        assertTrue(EmployeeNavigator.shouldPreferElevatorFirst(270.0D, 130.0D));
    }

    @Test
    void budgetExhaustedProgressCanBeExecutedAsARouteSegment() {
        NavigationNode start = node(0, 64, 0);
        NavigationNode waypoint = node(3, 64, 0);
        NavigationTransition transition = new NavigationTransition(
                waypoint, NavigationAction.WALK, List.of(), 3.0D);
        NavigationPath path = new NavigationPath(start, waypoint,
                List.of(new NavigationPathStep(start, waypoint, transition, 3.0D, 3.0D)),
                3.0D, false);

        assertTrue(EmployeeNavigator.isExecutablePartial(new PathSearchResult(
                PathSearchResult.Status.PARTIAL_BUDGET_EXHAUSTED, path, 32, 64)));
        assertFalse(EmployeeNavigator.isExecutablePartial(new PathSearchResult(
                PathSearchResult.Status.PARTIAL_UNREACHABLE, path, 32, 64)));
    }

    @Test
    void sameFloorLongRoutesNeverUseVerticalBoundaryAsProgress() {
        BlockPos center = new BlockPos(0, 64, 0);

        assertTrue(EmployeeNavigator.isSearchBoundary(
                new BlockPos(19, 64, 0), center, 20, 7, false));
        assertFalse(EmployeeNavigator.isSearchBoundary(
                new BlockPos(0, 69, 0), center, 20, 7, false));
        assertTrue(EmployeeNavigator.isSearchBoundary(
                new BlockPos(0, 69, 0), center, 20, 7, true));
    }

    @Test
    void lateralCorridorWinsOverIrrelevantStairsForFarSameFloorTarget() {
        NavigationNode start = node(0, 64, 0);
        NavigationNode target = node(40, 64, 0);
        Map<NavigationNode, List<NavigationTransition>> edges = new HashMap<>();

        NavigationNode previous = start;
        for (int z = 1; z <= 19; z++) {
            NavigationNode next = node(0, 64, z);
            connect(edges, previous, next, 1.0D);
            previous = next;
        }
        previous = start;
        for (int y = 65; y <= 69; y++) {
            NavigationNode next = node(0, y, 0);
            connect(edges, previous, next, 1.35D);
            previous = next;
        }

        AStarPathfinder pathfinder = new AStarPathfinder(
                node -> edges.getOrDefault(node, List.of()),
                NavigationTransition::cost, EmployeeNavigatorBoundaryTest::heuristic);
        PathSearchResult result = pathfinder.search(start, target,
                node -> EmployeeNavigator.isSearchBoundary(
                        node.feetPosition(), start.feetPosition(), 20, 7, false),
                new AStarPathfinder.SearchOptions(4096, 1.0D));

        assertEquals(PathSearchResult.Status.REACHED_GOAL, result.status());
        assertEquals(node(0, 64, 19), result.path().endpoint());
    }

    private static void connect(Map<NavigationNode, List<NavigationTransition>> edges,
                                NavigationNode first, NavigationNode second, double cost) {
        edges.computeIfAbsent(first, ignored -> new ArrayList<>()).add(
                new NavigationTransition(second, NavigationAction.WALK, List.of(), cost));
        edges.computeIfAbsent(second, ignored -> new ArrayList<>()).add(
                new NavigationTransition(first, NavigationAction.WALK, List.of(), cost));
    }

    private static NavigationNode node(int x, int y, int z) {
        return new NavigationNode(new BlockPos(x, y, z));
    }

    private static double heuristic(NavigationNode from, NavigationNode to) {
        return Math.abs(from.feetPosition().getX() - to.feetPosition().getX())
                + Math.abs(from.feetPosition().getZ() - to.feetPosition().getZ())
                + Math.abs(from.standingY() - to.standingY()) * 0.35D;
    }
}
