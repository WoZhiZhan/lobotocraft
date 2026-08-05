package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleBiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AStarPathfinderTest {
    @Test
    void choosesLowestCostPath() {
        Graph graph = new Graph();
        NavigationNode start = node(0);
        NavigationNode middle = node(1);
        NavigationNode goal = node(2);
        graph.connect(start, goal, 10.0D);
        graph.connect(start, middle, 2.0D);
        graph.connect(middle, goal, 2.0D);

        PathSearchResult result = graph.pathfinder((node, ignored) -> 0.0D)
                .search(start, goal, goal::equals,
                        new AStarPathfinder.SearchOptions(20, 1.0D));

        assertEquals(PathSearchResult.Status.REACHED_GOAL, result.status());
        assertEquals(4.0D, result.path().totalCost());
        assertEquals(List.of(middle, goal), pathNodes(result.path()));
    }

    @Test
    void reopensNodeWhenCheaperRouteAppears() {
        Graph graph = new Graph();
        NavigationNode start = node(0);
        NavigationNode expensive = node(1);
        NavigationNode detour = node(2);
        NavigationNode goal = node(3);
        graph.connect(start, expensive, 5.0D);
        graph.connect(start, detour, 1.0D);
        graph.connect(detour, expensive, 1.0D);
        graph.connect(expensive, goal, 1.0D);
        Map<NavigationNode, Double> heuristic = Map.of(
                start, 0.0D, expensive, 0.0D, detour, 4.0D, goal, 0.0D);

        PathSearchResult result = graph.pathfinder((node, ignored) -> heuristic.get(node))
                .search(start, goal, goal::equals,
                        new AStarPathfinder.SearchOptions(20, 1.0D));

        assertEquals(3.0D, result.path().totalCost());
        assertEquals(List.of(detour, expensive, goal), pathNodes(result.path()));
    }

    @Test
    void distinguishesBudgetPartialFromUnreachable() {
        Graph graph = new Graph();
        NavigationNode start = node(0);
        NavigationNode first = node(1);
        NavigationNode second = node(2);
        NavigationNode goal = node(3);
        graph.connect(start, first, 1.0D);
        graph.connect(first, second, 1.0D);
        graph.connect(second, goal, 1.0D);

        PathSearchResult partial = graph.pathfinder(AStarPathfinderTest::distance)
                .search(start, goal, goal::equals,
                        new AStarPathfinder.SearchOptions(2, 1.0D));
        assertEquals(PathSearchResult.Status.PARTIAL_BUDGET_EXHAUSTED, partial.status());
        assertFalse(partial.path().reachesFinalGoal());

        PathSearchResult unreachable = new Graph().pathfinder(AStarPathfinderTest::distance)
                .search(start, goal, goal::equals,
                        new AStarPathfinder.SearchOptions(10, 1.0D));
        assertEquals(PathSearchResult.Status.UNREACHABLE, unreachable.status());
        assertNull(unreachable.path());
    }

    @Test
    void boundarySegmentAvoidsCloserDeadEndAndUsesSideDoor() {
        NavigationNode start = node(0, 0);
        NavigationNode finalTarget = node(40, 0);
        Map<NavigationNode, List<NavigationNode>> edges = new HashMap<>();
        for (int x = 0; x < 12; x++) connectBoth(edges, node(x, 0), node(x + 1, 0));
        connectBoth(edges, node(0, 0), node(0, 1));
        connectBoth(edges, node(0, 1), node(0, 2));
        for (int x = 0; x < 19; x++) connectBoth(edges, node(x, 2), node(x + 1, 2));

        AStarPathfinder pathfinder = new AStarPathfinder(current -> {
            List<NavigationTransition> neighbors = new ArrayList<>();
            for (NavigationNode next : edges.getOrDefault(current, List.of())) {
                neighbors.add(new NavigationTransition(
                        next, NavigationAction.WALK, List.of(), 1.0D));
            }
            return neighbors;
        }, NavigationTransition::cost, AStarPathfinderTest::distance);

        PathSearchResult result = pathfinder.search(start, finalTarget,
                candidate -> candidate.feetPosition().getX() >= 19
                        && distance(candidate, finalTarget) < distance(start, finalTarget),
                new AStarPathfinder.SearchOptions(4096, 1.0D));

        assertEquals(PathSearchResult.Status.REACHED_GOAL, result.status());
        assertEquals(node(19, 2), result.path().endpoint());
        assertTrue(pathNodes(result.path()).contains(node(0, 1)));
        assertFalse(pathNodes(result.path()).contains(node(1, 0)));
    }

    @Test
    void boundaryDetourMayStartByMovingAwayFromAnInsideTarget() {
        NavigationNode start = node(0, 0);
        NavigationNode insideTarget = node(4, 0);
        Map<NavigationNode, List<NavigationNode>> edges = new HashMap<>();
        connectBoth(edges, start, node(-1, 0));
        connectBoth(edges, node(-1, 0), node(-2, 0));
        connectBoth(edges, node(-2, 0), node(-3, 0));

        AStarPathfinder pathfinder = new AStarPathfinder(current -> {
            List<NavigationTransition> neighbors = new ArrayList<>();
            for (NavigationNode next : edges.getOrDefault(current, List.of())) {
                neighbors.add(new NavigationTransition(
                        next, NavigationAction.WALK, List.of(), 1.0D));
            }
            return neighbors;
        }, NavigationTransition::cost, AStarPathfinderTest::distance);

        PathSearchResult direct = pathfinder.search(start, insideTarget, insideTarget::equals,
                new AStarPathfinder.SearchOptions(20, 1.0D));
        PathSearchResult detour = pathfinder.search(start, insideTarget,
                candidate -> Math.abs(candidate.feetPosition().getX()) >= 3,
                new AStarPathfinder.SearchOptions(20, 1.0D));

        assertEquals(PathSearchResult.Status.UNREACHABLE, direct.status());
        assertEquals(PathSearchResult.Status.REACHED_GOAL, detour.status());
        assertEquals(node(-3, 0), detour.path().endpoint());
    }

    @Test
    void obsoleteSearchStopsCooperatively() {
        AStarPathfinder pathfinder = new AStarPathfinder(current -> List.of(
                new NavigationTransition(node(current.feetPosition().getX() + 1),
                        NavigationAction.WALK, List.of(), 1.0D)),
                NavigationTransition::cost, AStarPathfinderTest::distance);
        AtomicInteger checks = new AtomicInteger();

        PathSearchResult result = pathfinder.search(node(0), node(1_000),
                node(1_000)::equals, new AStarPathfinder.SearchOptions(2_000, 1.0D),
                () -> checks.incrementAndGet() > 8);

        assertEquals(PathSearchResult.Status.UNREACHABLE, result.status());
        assertNull(result.path());
        assertTrue(result.visitedNodes() < 8);
    }

    private static void connectBoth(Map<NavigationNode, List<NavigationNode>> edges,
                                    NavigationNode first, NavigationNode second) {
        edges.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
        edges.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
    }

    private static List<NavigationNode> pathNodes(NavigationPath path) {
        return path.steps().stream().map(NavigationPathStep::node).toList();
    }

    private static NavigationNode node(int x) {
        return new NavigationNode(new BlockPos(x, 64, 0));
    }

    private static NavigationNode node(int x, int z) {
        return new NavigationNode(new BlockPos(x, 64, z));
    }

    private static double distance(NavigationNode node, NavigationNode goal) {
        return Math.abs(node.feetPosition().getX() - goal.feetPosition().getX())
                + Math.abs(node.feetPosition().getZ() - goal.feetPosition().getZ());
    }

    private static final class Graph {
        private final Map<NavigationNode, List<NavigationTransition>> edges = new HashMap<>();

        void connect(NavigationNode from, NavigationNode to, double cost) {
            edges.computeIfAbsent(from, ignored -> new ArrayList<>())
                    .add(new NavigationTransition(to, NavigationAction.WALK, List.of(), cost));
        }

        AStarPathfinder pathfinder(ToDoubleBiFunction<NavigationNode, NavigationNode> heuristic) {
            return new AStarPathfinder(node -> edges.getOrDefault(node, List.of()),
                    NavigationTransition::cost, heuristic);
        }
    }
}
