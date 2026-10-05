package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GeburahEgoAttackTrialTest {
    @AfterEach
    void clear() {
        GeburahBladeTrailState.clear();
    }

    @Test
    void trialIsStrictlyEgoSlashAndStabNotGroundOrWillOfBattle() {
        assertTrue(GeburahEgoAttackVisuals.animationEligible("EGOslash", true));
        assertTrue(GeburahEgoAttackVisuals.animationEligible("EGOstab", true));
        for (String name : List.of("slash", "stab", "EGOverticalslash", "EGOwillofbattle", "EGOslash_begin")) {
            assertFalse(GeburahEgoAttackVisuals.animationEligible(name, true));
        }
        assertFalse(GeburahEgoAttackVisuals.animationEligible("EGOstab", false));
        for (GeburahMove move : GeburahMove.values()) {
            assertFalse(GeburahEgoAttackVisuals.enabled(false, true, true, move));
            assertFalse(GeburahEgoAttackVisuals.enabled(true, true, false, move));
            assertFalse(GeburahEgoAttackVisuals.enabled(true, false, true, move));
        }
        assertFalse(GeburahEgoAttackVisuals.enabled(true, true, true, GeburahMove.VERTICAL_SLASH));
    }

    @Test
    void disablingTrialRestoresExactLegacyTrailAndSixTickExpiry() {
        capture(true, GeburahMove.SLASH);
        var legacy = GeburahBladeTrailState.recentTrail(22, 6);
        assertEquals(legacy, GeburahBladeTrailState.visualTrail(22, false));
        assertFalse(GeburahBladeTrailState.visualTrail(22, false).get(0).enhanced());
        assertTrue(GeburahBladeTrailState.visualTrail(27, false).isEmpty());
    }

    @Test
    void onlyEligibleSlashGetsLongerDarkerTailAndTransitionsResetIt() {
        capture(true, GeburahMove.SLASH);
        assertFalse(GeburahBladeTrailState.visualTrail(27, true).isEmpty());
        assertTrue(GeburahBladeTrailState.visualTrail(29, true).isEmpty());
        capture(true, GeburahMove.STAB);
        assertTrue(GeburahBladeTrailState.visualTrail(27, true).isEmpty());
        capture(false, GeburahMove.SLASH);
        assertEquals(GeburahBladeTrailState.recentTrail(22, 6),
                GeburahBladeTrailState.visualTrail(22, true));
        capture(true, GeburahMove.SLASH);
        GeburahBladeTrailState.capture(4, 21, List.of(new Vec3(1, 0, 0)),
                true, GeburahMove.SLASH, false);
        assertEquals(1, GeburahBladeTrailState.visualTrail(21, true).size());
        assertFalse(GeburahBladeTrailState.visualTrail(21, true).get(0).enhanced());
        assertTrue(GeburahEgoAttackVisuals.trailFade(0.8F) < 0.04F);
        assertEquals(0, GeburahEgoAttackVisuals.trailFade(1));
    }

    @Test
    void zeroOrRejectedDamageDoesNotFabricateContactButSuppressionCounts() {
        assertFalse(GeburahEgoAttackVisuals.validHit(false, 10, false));
        assertFalse(GeburahEgoAttackVisuals.validHit(false, 0, true));
        assertFalse(GeburahEgoAttackVisuals.validHit(true, 0, false));
        assertFalse(GeburahEgoAttackVisuals.validHit(true, Float.NaN, false));
        assertTrue(GeburahEgoAttackVisuals.validHit(true, 1, false));
        assertTrue(GeburahEgoAttackVisuals.validHit(true, 0, true));
        assertEquals(0, GeburahEgoAttackVisuals.hitSegments(0, 49 * 49));
        assertEquals(4, GeburahEgoAttackVisuals.hitSegments(2, 0));
        assertEquals(8, GeburahEgoAttackVisuals.hitSegments(0, 25 * 25));
    }

    private void capture(boolean enhanced, GeburahMove move) {
        GeburahBladeTrailState.clear();
        GeburahBladeTrailState.capture(4, 20, List.of(Vec3.ZERO), true, move, enhanced);
        GeburahBladeTrailState.capture(4, 20.5, List.of(new Vec3(1, 0, 0)), true, move, enhanced);
    }
}
