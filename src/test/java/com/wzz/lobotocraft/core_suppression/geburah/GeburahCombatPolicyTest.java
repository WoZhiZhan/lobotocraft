package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahCombatPolicyTest {
    @Test
    void fullHealthTargetPrefersReadySpecialAtLegalRange() {
        assertEquals(GeburahCombatPolicy.Action.WILL_OF_BATTLE,
                GeburahCombatPolicy.choose(snapshot(true, 10.0D, true, true, false)));
    }

    @Test
    void injuredTargetPrefersStrongCombo() {
        assertEquals(GeburahCombatPolicy.Action.COMBO,
                GeburahCombatPolicy.choose(snapshot(false, 10.0D, true, true, false)));
    }

    @Test
    void cooldownOrUnsafeLandingHardRejectsSpecial() {
        assertEquals(GeburahCombatPolicy.Action.COMBO,
                GeburahCombatPolicy.choose(snapshot(true, 10.0D, false, true, false)));
        assertEquals(GeburahCombatPolicy.Action.COMBO,
                GeburahCombatPolicy.choose(snapshot(true, 10.0D, true, false, false)));
    }

    @Test
    void killPriorityDoesNotBypassHardConditions() {
        assertEquals(GeburahCombatPolicy.Action.COMBO,
                GeburahCombatPolicy.choose(snapshot(true, 10.0D, false, true, true)));
    }

    private static GeburahCombatPolicy.Snapshot snapshot(boolean full, double distance,
                                                          boolean ready, boolean safe,
                                                          boolean killPriority) {
        return new GeburahCombatPolicy.Snapshot(
                full, distance, 0.8F, ready, safe, killPriority);
    }
}
