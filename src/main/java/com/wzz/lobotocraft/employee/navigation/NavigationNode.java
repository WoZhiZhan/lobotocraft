package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** A player-sized navigation node with its actual (possibly fractional) floor height. */
public record NavigationNode(BlockPos feetPosition, double standingY) {
    public NavigationNode(BlockPos feetPosition) {
        this(feetPosition, feetPosition.getY());
    }

    public NavigationNode {
        feetPosition = Objects.requireNonNull(feetPosition, "feetPosition").immutable();
        if (!Double.isFinite(standingY)
                || Math.floor(standingY) != feetPosition.getY()) {
            throw new IllegalArgumentException("standingY must be inside the feet block");
        }
    }

    public Vec3 standingPosition() {
        return new Vec3(feetPosition.getX() + 0.5D, standingY,
                feetPosition.getZ() + 0.5D);
    }
}
