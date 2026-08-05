package com.wzz.lobotocraft.employee.navigation;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationSnapshotStorageTest {
    @Test
    void missingCellsInsideACompleteSnapshotAreImplicitAir() {
        NavigationSnapshot snapshot = new NavigationSnapshot(new BlockPos(0, 0, 0),
                new BlockPos(4, 4, 4), Map.of(), Set.of(), true);

        assertTrue(snapshot.cell(new BlockPos(2, 2, 2)).isPresent());
        assertFalse(snapshot.cell(new BlockPos(5, 2, 2)).isPresent());
    }

    @Test
    void unloadedCellsRemainUnavailableRatherThanTurningIntoAir() {
        BlockPos unavailable = new BlockPos(2, 2, 2);
        NavigationSnapshot snapshot = new NavigationSnapshot(new BlockPos(0, 0, 0),
                new BlockPos(4, 4, 4), Map.of(), Set.of(unavailable), false);

        assertFalse(snapshot.cell(unavailable).isPresent());
        assertTrue(snapshot.cell(new BlockPos(1, 2, 2)).isPresent());
    }

    @Test
    void sharedCacheViewDelegatesOnlyInsideRequestedBounds() {
        BlockPos cached = new BlockPos(2, 2, 2);
        NavigationSnapshot snapshot = NavigationSnapshot.shared(new BlockPos(1, 1, 1),
                new BlockPos(3, 3, 3), position -> position.equals(cached)
                        ? java.util.Optional.of(NavigationSnapshot.airCell())
                        : java.util.Optional.empty());

        assertTrue(snapshot.cell(cached).isPresent());
        assertFalse(snapshot.cell(new BlockPos(0, 2, 2)).isPresent());
        assertFalse(snapshot.cell(new BlockPos(3, 2, 2)).isPresent());
    }
}
