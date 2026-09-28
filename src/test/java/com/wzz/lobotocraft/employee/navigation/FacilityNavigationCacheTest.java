package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityNavigationCacheTest {
    @Test
    void treatsEquivalentCollisionDataAsTheStaticBaseline() {
        CellData first = cell(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false);
        CellData second = cell(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false);

        assertTrue(FacilityNavigationCache.sameCell(first, second));
    }

    @Test
    void retainsOverrideWhenCollisionBoundsChange() {
        CellData fullBlock = cell(Shapes.block(), false);
        CellData halfBlock = cell(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false);

        assertFalse(FacilityNavigationCache.sameCell(fullBlock, halfBlock));
    }

    @Test
    void retainsOverrideWhenInternalCollisionTopologyChanges() {
        VoxelShape splitBlock = Shapes.or(
                Shapes.box(0.0D, 0.0D, 0.0D, 0.4D, 1.0D, 1.0D),
                Shapes.box(0.6D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));

        assertFalse(FacilityNavigationCache.sameCell(
                cell(Shapes.block(), false), cell(splitBlock, false)));
    }

    @Test
    void retainsOverrideWhenDoorTraversalChanges() {
        assertFalse(FacilityNavigationCache.sameCell(
                cell(Shapes.block(), false), cell(Shapes.block(), true)));
    }

    private static CellData cell(VoxelShape shape, boolean traversableClosedDoor) {
        return new CellData(shape, traversableClosedDoor, false, true, false,
                1.5F, CellData.Hazard.NONE);
    }
}
