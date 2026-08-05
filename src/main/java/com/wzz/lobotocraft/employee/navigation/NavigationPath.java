package com.wzz.lobotocraft.employee.navigation;

import java.util.List;
import java.util.Objects;

public record NavigationPath(NavigationNode start, NavigationNode endpoint,
                             List<NavigationPathStep> steps, double totalCost,
                             boolean reachesFinalGoal) {
    public NavigationPath {
        start = Objects.requireNonNull(start, "start");
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (!Double.isFinite(totalCost) || totalCost < 0.0D) {
            throw new IllegalArgumentException("totalCost must be finite and non-negative");
        }
        if (steps.isEmpty() && !start.equals(endpoint)) {
            throw new IllegalArgumentException("an empty path must end at its start node");
        }
    }

    public static NavigationPath stationary(NavigationNode node, boolean reachesFinalGoal) {
        return new NavigationPath(node, node, List.of(), 0.0D, reachesFinalGoal);
    }
}
