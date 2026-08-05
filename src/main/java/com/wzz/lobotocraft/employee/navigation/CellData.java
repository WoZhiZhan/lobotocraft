package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/** Minimal immutable collision data copied from the live world for background path planning. */
public record CellData(VoxelShape collisionShape, boolean traversableClosedDoor,
                       boolean softObstacle, boolean breakable, boolean unsafeNarrowSupport,
                       float destroySpeed, Hazard hazard) {
    public CellData {
        collisionShape = Objects.requireNonNull(collisionShape);
        hazard = Objects.requireNonNull(hazard);
    }

    public boolean hasCollision() {
        return !collisionShape.isEmpty();
    }

    public enum Hazard { NONE, CAUTION, DANGEROUS }
}
