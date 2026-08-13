package com.wzz.lobotocraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NothingThereRevivePolicyTest {
    @Test
    void treatsInfinityAsLethalBeforeTheDamageIsApplied() {
        assertTrue(NothingThereRevivePolicy.isLethal(Float.POSITIVE_INFINITY, 20.0F));
        assertFalse(NothingThereRevivePolicy.isLethal(19.99F, 20.0F));
    }

    @Test
    void blocksOnlyDuringTheNinetySecondCooldown() {
        assertTrue(NothingThereRevivePolicy.isCooldownActive(100L, 101L));
        assertFalse(NothingThereRevivePolicy.isCooldownActive(101L, 101L));
    }

    @Test
    void restoresAQuarterOfTheCurrentMaximumHealth() {
        assertEquals(25.0F, NothingThereRevivePolicy.restoredHealth(100.0F));
        assertEquals(1.0F, NothingThereRevivePolicy.restoredHealth(0.0F));
    }
}
