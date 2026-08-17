package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahOutgoingDamagePolicyTest {
    @Test
    void appliesEgoFlatBonusesBeforeMultipliers() {
        assertEquals(20.57F, GeburahOutgoingDamagePolicy.apply(
                12.0F, true, true, 3, true, 0, false), 0.001F);
    }

    @Test
    void slownessScalesPerLevelAndExhaustionReducesFinalDamage() {
        assertEquals(4.68F, GeburahOutgoingDamagePolicy.apply(
                12.0F, false, false, 5, false, 2, true), 0.001F);
    }

    @Test
    void killBonusDoesNotLeakOutsideEgo() {
        assertEquals(12.0F, GeburahOutgoingDamagePolicy.apply(
                12.0F, false, false, 5, false, -1, false), 0.001F);
    }
}
