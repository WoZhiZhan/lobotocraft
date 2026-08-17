package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahStatusEffectPolicyTest {
    @Test
    void rejectedEffectsRaiseSpeedToLevelThreeAndThenCap() {
        assertEquals(0, GeburahStatusEffectPolicy.nextSpeedAmplifier(-1));
        assertEquals(1, GeburahStatusEffectPolicy.nextSpeedAmplifier(0));
        assertEquals(2, GeburahStatusEffectPolicy.nextSpeedAmplifier(1));
        assertEquals(2, GeburahStatusEffectPolicy.nextSpeedAmplifier(2));
    }
}
