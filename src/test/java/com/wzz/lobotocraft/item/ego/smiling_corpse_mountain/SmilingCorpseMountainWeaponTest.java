package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmilingCorpseMountainWeaponTest {
    @Test
    void corruptionStacksOnlyOnFullyChargedNormalAttack() {
        assertFalse(CorruptionStackPolicy.shouldApply(0.2F));
        assertFalse(CorruptionStackPolicy.shouldApply(0.75F));
        assertFalse(CorruptionStackPolicy.shouldApply(0.998F));
        assertFalse(CorruptionStackPolicy.shouldApply(0.999F));
        assertFalse(CorruptionStackPolicy.shouldApply(Math.nextDown(1.0F)));
        assertTrue(CorruptionStackPolicy.shouldApply(1.0F));
    }
}
