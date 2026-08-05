package com.wzz.lobotocraft.entity.abnormality;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishingBirdReturnPositionTest {
    @Test
    void returnTargetsAirAboveSolidNestInsteadOfInsideItsCollision() {
        BlockPos nest = new BlockPos(12, 64, -8);
        BlockPos standing = PunishingBirdReturnPosition.aboveNest(nest);

        assertEquals(nest.getX(), standing.getX());
        assertEquals(nest.getY() + 1, standing.getY());
        assertEquals(nest.getZ(), standing.getZ());
    }

    @Test
    void missingLegacyNestDoesNotInventAWorldPosition() {
        assertNull(PunishingBirdReturnPosition.aboveNest(null));
    }

    @Test
    void circlingWithoutGettingCloserDoesNotResetTheReturnWatchdog() {
        assertFalse(PunishingBirdReturnPosition.madeProgress(100.0D, 100.0D));
        assertFalse(PunishingBirdReturnPosition.madeProgress(101.0D, 100.0D));
        assertTrue(PunishingBirdReturnPosition.madeProgress(99.0D, 100.0D));
    }
}
