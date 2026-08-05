package com.wzz.lobotocraft.employee.navigation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Rejects standable target points that belong only to tiny decorative islands. */
public final class NavigationCandidatePolicy {
    public static final int MIN_LOCAL_REACHABLE_NODES = 12;

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
}
