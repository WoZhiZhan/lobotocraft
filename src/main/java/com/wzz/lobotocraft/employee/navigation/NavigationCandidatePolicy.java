package com.wzz.lobotocraft.employee.navigation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.Map;

/** Rejects standable target points that belong only to tiny decorative islands. */
public final class NavigationCandidatePolicy {
    public static final int MIN_LOCAL_REACHABLE_NODES = 12;

    /**
     * A standable point qualifies as a move target only when its walkable
     * region is large enough to hit the probe cap: genuine facility surfaces
     * continue through doors into the corridor network (tens of thousands of
     * nodes), while sealed decorative surfaces complete far below it.  The
     * welfare upper containment cluster roof, for example, is a fully sealed
     * 633-node island, so the cap must stay comfortably above it.
     */
    public static final int FACILITY_REGION_PROBE_CAP = 1024;

    private NavigationCandidatePolicy() {
    }

    public static boolean hasLocalMobility(
            NavigationNode start,
            Function<NavigationNode, List<NavigationTransition>> transitions) {
        if (start == null || transitions == null) return false;
        Set<NavigationNode> visited = new HashSet<>();
        ArrayDeque<NavigationNode> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && visited.size() < MIN_LOCAL_REACHABLE_NODES) {
            NavigationNode current = frontier.removeFirst();
            for (NavigationTransition transition : transitions.apply(current)) {
                NavigationNode target = transition.target();
                if (visited.add(target)) frontier.addLast(target);
                if (visited.size() >= MIN_LOCAL_REACHABLE_NODES) return true;
            }
        }
        return visited.size() >= MIN_LOCAL_REACHABLE_NODES;
    }

    /**
     * Stronger sibling of {@link #hasLocalMobility} for samplers whose snapshot
     * covers the surrounding facility without clipping the walkable region:
     * rejects any point whose reachable region finishes exploring below
     * {@link #FACILITY_REGION_PROBE_CAP}.  Corridor-connected surfaces reach
     * the cap long before exhausting, so completion always means a sealed
     * island such as a containment cluster roof or a decorative platform.
     */
    public static boolean isFacilityConnectedRegion(
            NavigationNode start,
            Function<NavigationNode, List<NavigationTransition>> transitions) {
        if (start == null || transitions == null) return false;
        Set<NavigationNode> visited = new HashSet<>();
        ArrayDeque<NavigationNode> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            NavigationNode current = frontier.removeFirst();
            for (NavigationTransition transition : transitions.apply(current)) {
                NavigationNode target = transition.target();
                if (visited.add(target)) {
                    frontier.addLast(target);
                    if (visited.size() >= FACILITY_REGION_PROBE_CAP) return true;
                }
            }
        }
        return false;
    }

    /**
     * One immutable-snapshot/request scope. Certificates reuse actual directed
     * reachability; merely visiting a one-way child never certifies that child.
     * Aborted work is not cached as a negative result.
     */
    public static final class FacilityRegionProbe {
        private final Function<NavigationNode, List<NavigationTransition>> generator;
        private final Map<NavigationNode, List<NavigationTransition>> edges = new HashMap<>();
        private final Map<NavigationNode, Set<NavigationNode>> predecessors = new HashMap<>();
        private final Map<NavigationNode, Boolean> proven = new HashMap<>();

        public FacilityRegionProbe(Function<NavigationNode, List<NavigationTransition>> generator) {
            this.generator = java.util.Objects.requireNonNull(generator);
        }

        public boolean isConnected(NavigationNode start, BooleanSupplier withinBudget) {
            Boolean existing = proven.get(start);
            if (existing != null) return existing;
            Set<NavigationNode> visited = new HashSet<>();
            ArrayDeque<NavigationNode> frontier = new ArrayDeque<>();
            visited.add(start);
            frontier.add(start);
            while (!frontier.isEmpty()) {
                if (!withinBudget.getAsBoolean()) return false;
                NavigationNode current = frontier.removeFirst();
                if (Boolean.TRUE.equals(proven.get(current))) {
                    certifyAncestors(current, withinBudget);
                    proven.put(start, true);
                    return true;
                }
                // A small known region still contributes nodes when joined with
                // another region; only a direct negative query can be skipped.
                for (NavigationTransition transition : transitions(current)) {
                    NavigationNode target = transition.target();
                    if (visited.add(target)) {
                        frontier.addLast(target);
                        if (visited.size() >= FACILITY_REGION_PROBE_CAP) {
                            certifyAncestors(start, withinBudget);
                            return true;
                        }
                    }
                }
            }
            // Exhaustion proves the whole reachable closure is small.
            for (NavigationNode node : visited) proven.put(node, false);
            return false;
        }

        private List<NavigationTransition> transitions(NavigationNode node) {
            return edges.computeIfAbsent(node, from -> {
                List<NavigationTransition> result = List.copyOf(generator.apply(from));
                for (NavigationTransition edge : result)
                    predecessors.computeIfAbsent(edge.target(), ignored -> new HashSet<>()).add(from);
                return result;
            });
        }

        private void certifyAncestors(NavigationNode root, BooleanSupplier withinBudget) {
            proven.put(root, true);
            Set<NavigationNode> visited = new HashSet<>();
            ArrayDeque<NavigationNode> frontier = new ArrayDeque<>();
            visited.add(root);
            frontier.add(root);
            while (!frontier.isEmpty() && withinBudget.getAsBoolean()) {
                NavigationNode current = frontier.removeFirst();
                for (NavigationNode parent : predecessors.getOrDefault(current, Set.of())) {
                    if (visited.add(parent)) {
                        proven.put(parent, true);
                        frontier.addLast(parent);
                    }
                }
            }
        }
    }
}
