package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahDamageTuningTest {
    @AfterEach
    void resetTuning() {
        GeburahDamageTuning.resetAbnormalityOverride();
    }
    @Test
    void abnormalityDamageUsesFiveTimesDamageAndTwoPointZeroRedResistance() {
        assertEquals(10.0D,
                GeburahDamageTuning.effectiveRedMultiplier(true, true, 0.3D));
        assertEquals(0.3D,
                GeburahDamageTuning.effectiveRedMultiplier(false, true, 0.3D));
        assertEquals(0.3D,
                GeburahDamageTuning.effectiveRedMultiplier(true, false, 0.3D));
    }

    @Test
    void playerResistanceOverrideIsScopedToTheDirectAttackCall() {
        assertEquals(0.4D, GeburahDamageTuning.effectivePlayerRedMultiplier(true, 0.4D));
        double during = GeburahDamageTuning.withNeutralPlayerRed(() ->
                GeburahDamageTuning.effectivePlayerRedMultiplier(true, -0.5D));
        assertEquals(1.0D, during);
        assertEquals(-0.5D, GeburahDamageTuning.effectivePlayerRedMultiplier(true, -0.5D));
    }

    @Test
    void abnormalityOverrideCanBeDisabledWithoutChangingAttacks() {
        GeburahDamageTuning.configureAbnormalityOverride(false, 5.0D, 2.0D);
        assertEquals(0.3D,
                GeburahDamageTuning.effectiveRedMultiplier(true, true, 0.3D));
    }
}
