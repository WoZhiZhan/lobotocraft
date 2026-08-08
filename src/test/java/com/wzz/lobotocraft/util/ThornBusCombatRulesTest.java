package com.wzz.lobotocraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ThornBusCombatRulesTest {
    @Test
    void temperanceDamageBandsDoNotOverwriteEachOther() {
        assertEquals(new ThornBusCombatRules.DamageBand(9, 24),
                ThornBusCombatRules.damageBandForTemperance(3));
        assertEquals(new ThornBusCombatRules.DamageBand(3, 8),
                ThornBusCombatRules.damageBandForTemperance(4));
        assertEquals(new ThornBusCombatRules.DamageBand(1, 3),
                ThornBusCombatRules.damageBandForTemperance(5));
    }

    @Test
    void attackUsesThreeDeterministicServerTicks() {
        assertEquals(3, ThornBusCombatRules.attackHitCount());
        assertEquals(3, ThornBusCombatRules.attackHitTick(0));
        assertEquals(6, ThornBusCombatRules.attackHitTick(1));
        assertEquals(10, ThornBusCombatRules.attackHitTick(2));
    }

    @Test
    void blackHitRetainsOneToFourRangeAndUsesChargeMultiplier() {
        assertEquals(0.2F, ThornBusCombatRules.scaleBlackDamage(1, 0.2F));
        assertEquals(0.8F, ThornBusCombatRules.scaleBlackDamage(4, 0.2F));
        assertEquals(1.0F, ThornBusCombatRules.scaleBlackDamage(1, 1.0F));
        assertEquals(4.0F, ThornBusCombatRules.scaleBlackDamage(4, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> ThornBusCombatRules.scaleBlackDamage(0, 1.0F));
    }
}
