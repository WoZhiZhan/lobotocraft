package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahSpecialPolicyTest {
    @Test
    void firstActionAfterEgoReleaseIsBodiesEverywhere() {
        assertEquals(GeburahSpecialPolicy.Action.BODIES_EVERYWHERE,
                GeburahSpecialPolicy.choose(new GeburahSpecialPolicy.Snapshot(
                        true, true, true, 4.0D, 1,
                        true, true, true, true, true)));
    }

    @Test
    void bloodMistNeverTargetsAbnormalities() {
        assertEquals(GeburahSpecialPolicy.Action.COMBO,
                GeburahSpecialPolicy.choose(new GeburahSpecialPolicy.Snapshot(
                        true, false, true, 3.0D, 1,
                        false, false, true, false, false)));
    }

    @Test
    void crowdEnablesBodiesAndFullHealthPlayerPrefersReadySpecial() {
        assertEquals(GeburahSpecialPolicy.Action.BODIES_EVERYWHERE,
                GeburahSpecialPolicy.choose(new GeburahSpecialPolicy.Snapshot(
                        true, true, false, 5.0D, 3,
                        false, true, false, false, false)));
        assertEquals(GeburahSpecialPolicy.Action.BLOOD_MIST,
                GeburahSpecialPolicy.choose(new GeburahSpecialPolicy.Snapshot(
                        false, true, true, 4.0D, 1,
                        false, false, true, true, true)));
    }
}
