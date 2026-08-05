package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NavigationLineOfSightTest {
    private static final BlockPos MINIMUM = new BlockPos(0, 64, 0);
    private static final BlockPos MAXIMUM = new BlockPos(4, 67, 0);
    private static final Vec3 START = new Vec3(0.5D, 65.62D, 0.5D);
    private static final Vec3 END = new Vec3(3.5D, 65.62D, 0.5D);

    @Test
    void clearSnapshotAllowsCombatSight() {
        NavigationSnapshot snapshot = new NavigationSnapshot(
                MINIMUM, MAXIMUM, Map.of(), true);
        assertTrue(NavigationLineOfSight.isClear(snapshot, START, END));
    }

    @Test
    void solidWallRejectsAnOtherwiseNearbyCombatEndpoint() {
        CellData wall = new CellData(Shapes.block(), false, false,
                true, false, 1.5F, CellData.Hazard.NONE);
        NavigationSnapshot snapshot = new NavigationSnapshot(MINIMUM, MAXIMUM,
                Map.of(new BlockPos(1, 65, 0), wall), true);
        assertFalse(NavigationLineOfSight.isClear(snapshot, START, END));
    }

    @Test
    void segmentIntersectionHandlesThinCollisionShapes() {
        AABB thinWall = new AABB(1.45D, 64.0D, 0.0D,
                1.55D, 67.0D, 1.0D);
        assertTrue(NavigationLineOfSight.intersects(START, END, thinWall));
        assertFalse(NavigationLineOfSight.intersects(START, END,
                thinWall.move(0.0D, 0.0D, 2.0D)));
    }

    @Test
    void unknownSnapshotSpaceIsNeverAssumedVisible() {
        NavigationSnapshot snapshot = new NavigationSnapshot(
                MINIMUM, new BlockPos(1, 67, 0), Map.of(), true);
        assertFalse(NavigationLineOfSight.isClear(snapshot, START, END));
    }
}
