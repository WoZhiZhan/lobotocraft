package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public record NavigationTransition(NavigationNode target, NavigationAction action,
                                   List<BlockPos> affectedBlocks, double cost) {
    public NavigationTransition {
        target = Objects.requireNonNull(target, "target");
        action = Objects.requireNonNull(action, "action");
        affectedBlocks = List.copyOf(affectedBlocks);
        if (!Double.isFinite(cost) || cost <= 0.0D) {
            throw new IllegalArgumentException("cost must be finite and positive");
        }
    }
}
