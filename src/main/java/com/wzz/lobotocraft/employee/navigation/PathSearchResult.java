package com.wzz.lobotocraft.employee.navigation;

import java.util.Objects;

public record PathSearchResult(Status status, NavigationPath path,
                               int visitedNodes, int generatedTransitions) {
    public PathSearchResult {
        status = Objects.requireNonNull(status, "status");
        if (visitedNodes < 0 || generatedTransitions < 0) {
            throw new IllegalArgumentException("search counters must be non-negative");
        }
    }

    public boolean reachedGoal() {
        return status == Status.REACHED_GOAL;
    }

    public enum Status {
        REACHED_GOAL,
        PARTIAL_BUDGET_EXHAUSTED,
        PARTIAL_UNREACHABLE,
        UNREACHABLE
    }
}
