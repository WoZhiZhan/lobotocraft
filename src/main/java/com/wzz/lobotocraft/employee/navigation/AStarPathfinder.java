package com.wzz.lobotocraft.employee.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;

/** Bounded weighted A* ported from TouhouLittleMaid-AIAgent-navigation. */
public final class AStarPathfinder {
    private static final double COST_EPSILON = 1.0E-9D;

    private final NeighborProvider neighborProvider;
    private final ToDoubleFunction<NavigationTransition> transitionCost;
    private final ToDoubleBiFunction<NavigationNode, NavigationNode> heuristic;

    public AStarPathfinder(NeighborProvider neighborProvider,
                           ToDoubleFunction<NavigationTransition> transitionCost,
                           ToDoubleBiFunction<NavigationNode, NavigationNode> heuristic) {
        this.neighborProvider = Objects.requireNonNull(neighborProvider, "neighborProvider");
        this.transitionCost = Objects.requireNonNull(transitionCost, "transitionCost");
        this.heuristic = Objects.requireNonNull(heuristic, "heuristic");
    }

    public PathSearchResult search(NavigationNode start, NavigationNode heuristicGoal,
                                   Predicate<NavigationNode> goalPredicate, SearchOptions options) {
        return search(start, heuristicGoal, goalPredicate, options, () -> false);
    }

    /** Cooperatively stops an obsolete asynchronous navigation search. */
    public PathSearchResult search(NavigationNode start, NavigationNode heuristicGoal,
                                   Predicate<NavigationNode> goalPredicate, SearchOptions options,
                                   BooleanSupplier cancelled) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(heuristicGoal, "heuristicGoal");
        Objects.requireNonNull(goalPredicate, "goalPredicate");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancelled, "cancelled");
        if (goalPredicate.test(start)) {
            return new PathSearchResult(PathSearchResult.Status.REACHED_GOAL,
                    NavigationPath.stationary(start, true), 0, 0);
        }

        PriorityQueue<SearchRecord> open = new PriorityQueue<>();
        Map<NavigationNode, Double> bestCost = new HashMap<>();
        Set<NavigationNode> closed = new HashSet<>();
        long sequence = 0L;
        double startHeuristic = checkedHeuristic(start, heuristicGoal);
        SearchRecord startRecord = new SearchRecord(start, null, null, 0.0D,
                startHeuristic, options.heuristicWeight(), sequence++);
        open.add(startRecord);
        bestCost.put(start, 0.0D);
        SearchRecord bestFallback = startRecord;
        int visitedNodes = 0;
        int generatedTransitions = 0;

        while (!open.isEmpty() && visitedNodes < options.nodeBudget()) {
            if (cancelled.getAsBoolean()) {
                return new PathSearchResult(PathSearchResult.Status.UNREACHABLE, null,
                        visitedNodes, generatedTransitions);
            }
            SearchRecord current = open.poll();
            double knownCost = bestCost.getOrDefault(current.node, Double.POSITIVE_INFINITY);
            if (current.pathCost > knownCost + COST_EPSILON || !closed.add(current.node)) continue;
            visitedNodes++;
            if (isBetterFallback(current, bestFallback)) bestFallback = current;
            if (goalPredicate.test(current.node)) {
                return new PathSearchResult(PathSearchResult.Status.REACHED_GOAL,
                        reconstruct(current, true), visitedNodes, generatedTransitions);
            }

            for (NavigationTransition transition : Objects.requireNonNull(
                    neighborProvider.neighbors(current.node), "neighbor provider returned null")) {
                if (cancelled.getAsBoolean()) {
                    return new PathSearchResult(PathSearchResult.Status.UNREACHABLE, null,
                            visitedNodes, generatedTransitions);
                }
                generatedTransitions++;
                NavigationTransition checked = Objects.requireNonNull(transition, "null transition");
                NavigationNode target = checked.target();
                double tentativeCost = current.pathCost + checkedCost(transitionCost.applyAsDouble(checked));
                if (!Double.isFinite(tentativeCost)
                        || tentativeCost + COST_EPSILON >= bestCost.getOrDefault(target, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                bestCost.put(target, tentativeCost);
                closed.remove(target);
                open.add(new SearchRecord(target, current, checked, tentativeCost,
                        checkedHeuristic(target, heuristicGoal), options.heuristicWeight(), sequence++));
            }
        }

        boolean budgetExhausted = !open.isEmpty();
        if (bestFallback.parent != null) {
            return new PathSearchResult(budgetExhausted
                    ? PathSearchResult.Status.PARTIAL_BUDGET_EXHAUSTED
                    : PathSearchResult.Status.PARTIAL_UNREACHABLE,
                    reconstruct(bestFallback, false), visitedNodes, generatedTransitions);
        }
        return new PathSearchResult(PathSearchResult.Status.UNREACHABLE, null,
                visitedNodes, generatedTransitions);
    }

    private NavigationPath reconstruct(SearchRecord endpoint, boolean reachesGoal) {
        if (endpoint.parent == null) return NavigationPath.stationary(endpoint.node, reachesGoal);
        List<NavigationPathStep> reversed = new ArrayList<>();
        SearchRecord current = endpoint;
        while (current.parent != null) {
            reversed.add(new NavigationPathStep(current.parent.node, current.node, current.transition,
                    current.pathCost - current.parent.pathCost, current.pathCost));
            current = current.parent;
        }
        Collections.reverse(reversed);
        return new NavigationPath(current.node, endpoint.node, reversed, endpoint.pathCost, reachesGoal);
    }

    private boolean isBetterFallback(SearchRecord candidate, SearchRecord current) {
        int heuristicComparison = Double.compare(candidate.heuristic, current.heuristic);
        if (heuristicComparison != 0) return heuristicComparison < 0;
        int costComparison = Double.compare(candidate.pathCost, current.pathCost);
        return costComparison != 0 ? costComparison < 0 : candidate.sequence < current.sequence;
    }

    private double checkedHeuristic(NavigationNode node, NavigationNode goal) {
        double value = heuristic.applyAsDouble(node, goal);
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("heuristic must be finite and non-negative");
        }
        return value;
    }

    private static double checkedCost(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("transition cost must be finite and non-negative");
        }
        return value;
    }

    @FunctionalInterface
    public interface NeighborProvider {
        Iterable<NavigationTransition> neighbors(NavigationNode node);
    }

    public record SearchOptions(int nodeBudget, double heuristicWeight) {
        public SearchOptions {
            if (nodeBudget <= 0) throw new IllegalArgumentException("nodeBudget must be positive");
            if (!Double.isFinite(heuristicWeight) || heuristicWeight < 1.0D) {
                throw new IllegalArgumentException("heuristicWeight must be finite and at least 1.0");
            }
        }
    }

    private static final class SearchRecord implements Comparable<SearchRecord> {
        private final NavigationNode node;
        private final SearchRecord parent;
        private final NavigationTransition transition;
        private final double pathCost;
        private final double heuristic;
        private final double priority;
        private final long sequence;

        private SearchRecord(NavigationNode node, SearchRecord parent, NavigationTransition transition,
                             double pathCost, double heuristic, double heuristicWeight, long sequence) {
            this.node = node;
            this.parent = parent;
            this.transition = transition;
            this.pathCost = pathCost;
            this.heuristic = heuristic;
            this.priority = pathCost + heuristicWeight * heuristic;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(SearchRecord other) {
            int comparison = Double.compare(priority, other.priority);
            if (comparison == 0) comparison = Double.compare(heuristic, other.heuristic);
            return comparison != 0 ? comparison : Long.compare(sequence, other.sequence);
        }
    }
}
