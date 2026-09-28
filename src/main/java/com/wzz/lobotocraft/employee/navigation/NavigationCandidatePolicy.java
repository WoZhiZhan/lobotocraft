package com.wzz.lobotocraft.employee.navigation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
}
