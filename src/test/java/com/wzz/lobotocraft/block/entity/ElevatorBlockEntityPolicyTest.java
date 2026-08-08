package com.wzz.lobotocraft.block.entity;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ElevatorBlockEntityPolicyTest {
    @Test
    void cooldownAcceptsOnlyTheCurrentShortWindow() {
        assertTrue(ElevatorBlockEntity.isTeleportCooldownActive(100L, 120L));
        assertFalse(ElevatorBlockEntity.isTeleportCooldownActive(100L, 121L));
        assertFalse(ElevatorBlockEntity.isTeleportCooldownActive(120L, 120L));
        assertFalse(ElevatorBlockEntity.isTeleportCooldownActive(120L, 100L));
    }

    @Test
    void landingSearchStartsAtTheElevatorAndOffersUniqueAlternatives() {
        BlockPos destination = new BlockPos(10, 20, 30);
        var candidates = ElevatorBlockEntity.landingCandidates(destination);

        assertEquals(destination.above(), candidates.get(0));
        assertEquals(candidates.size(), candidates.stream().distinct().count());
        assertTrue(candidates.stream().anyMatch(position ->
                position.equals(destination.offset(1, 1, 0))));
    }
}
