package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahPosturePolicyTest {
    @Test
    void abnormalityCombatHasNoComboCostOrHitRecovery() {
        assertFalse(GeburahPosturePolicy.spendsComboPosture(true));
        assertFalse(GeburahPosturePolicy.restoresPostureOnHit(true));
    }

    @Test
    void playerCombatKeepsNormalPostureEconomy() {
        assertTrue(GeburahPosturePolicy.spendsComboPosture(false));
        assertTrue(GeburahPosturePolicy.restoresPostureOnHit(false));
    }

    @Test
    void onlyPlayerResponsibilityCanDamageGeburahPosture() {
        assertTrue(GeburahPosturePolicy.takesPostureDamageFrom(true));
        assertFalse(GeburahPosturePolicy.takesPostureDamageFrom(false));
    }
}
