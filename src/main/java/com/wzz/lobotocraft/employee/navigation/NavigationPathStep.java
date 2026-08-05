package com.wzz.lobotocraft.employee.navigation;

import java.util.Objects;

public record NavigationPathStep(NavigationNode from, NavigationNode node,
                                 NavigationTransition transition, double stepCost,
                                 double cumulativeCost) {
    public NavigationPathStep {
        from = Objects.requireNonNull(from, "from");
        node = Objects.requireNonNull(node, "node");
        transition = Objects.requireNonNull(transition, "transition");
        requireCost(stepCost, "stepCost");
        requireCost(cumulativeCost, "cumulativeCost");
    }

    private static void requireCost(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
