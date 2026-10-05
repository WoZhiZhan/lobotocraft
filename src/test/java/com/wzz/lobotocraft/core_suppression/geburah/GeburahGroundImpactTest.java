package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GeburahGroundImpactTest {
    @Test
    void onlyEgoGroundStrikeGetsTheNewEffect() {
        for (GeburahMove move : GeburahMove.values()) {
            assertFalse(GeburahGroundImpact.enabled(move, false, false));
            assertFalse(GeburahGroundImpact.enabled(move, false, true));
            assertFalse(GeburahGroundImpact.enabled(move, true, false),
                    "an EGO combo snapshot must not keep the effect after exhaustion");
            assertEquals(move == GeburahMove.VERTICAL_SLASH,
                    GeburahGroundImpact.enabled(move, true, true));
        }
    }

    @Test
    void nearbyQualityDistanceAndSharedBudgetBoundWork() {
        assertEquals(20, GeburahGroundImpact.count(0, 0, 0));
        assertEquals(10, GeburahGroundImpact.count(1, 0, 0));
        assertEquals(4, GeburahGroundImpact.count(2, 0, 0));
        assertEquals(10, GeburahGroundImpact.count(0, 25 * 25, 0));
        assertEquals(4, GeburahGroundImpact.count(0, 0, 60));
        assertEquals(0, GeburahGroundImpact.count(0, 0, 64));
        assertEquals(0, GeburahGroundImpact.count(0, 49 * 49, 0));
        assertEquals(0, GeburahGroundImpact.count(0, Double.NaN, 0));
    }

    @Test
    void seededFakeBlocksHaveBoundedLifetimeThenFadeOnlyAfterLanding() {
        var layout = GeburahGroundImpact.layout(823, 20, 2.2D);
        assertEquals(layout, GeburahGroundImpact.layout(823, 20, 2.2D));
        assertEquals(20, GeburahGroundImpact.layout(823, Integer.MAX_VALUE, 2.2D).size());
        for (int i = 0; i < layout.size(); i++) {
            var shard = layout.get(i);
            assertTrue(Math.hypot(shard.offsetX(), shard.offsetZ()) <= 2.2D);
            assertEquals(i < 4, shard.delay() == 0);
            assertTrue(shard.lifetime() <= GeburahGroundImpact.MAX_LIFETIME);
            assertTrue(shard.height(4) > 0);
            assertEquals(0, shard.height(shard.duration()));
            assertEquals(0, shard.opacity(-1, 3));
            assertEquals(0, shard.opacity(shard.duration(), 3));
            assertEquals(1, shard.opacity(shard.flightTime(3), 3));
            assertTrue(shard.opacity(shard.duration() - 1, 3) < shard.opacity(4, 3));
            assertEquals(0, shard.tiltAt(shard.flightTime(3), 3));
        }
    }

    @Test
    void heavierBlocksDescendAndSettleWithoutHoveringOrSpinningOnFloor() {
        for (var shard : GeburahGroundImpact.layout(7, 20, 2.2)) {
            double landing = shard.flightTime(3);
            double peak = landing / 2;
            assertTrue(landing < 11, "long floating flight");
            assertTrue(shard.height(peak) <= 1.2, "excessive floor-block launch");
            assertTrue(shard.height(peak + 1) < shard.height(peak));
            assertEquals(0, shard.height(landing + 1));
            assertEquals(0, shard.tiltAt(landing + 1, 3));
            assertEquals(1, shard.opacity(landing + 1, 3));
        }
    }

    @Test
    void lowCeilingReducesLaunchEnergyInsteadOfFlatteningFlightIntoAHover() {
        var shard = GeburahGroundImpact.layout(19, 1, 2.2).get(0);
        double clearance = 0.12;
        double peak = shard.flightTime(clearance) / 2;
        assertEquals(clearance, shard.height(peak, clearance), 0.000001);
        assertTrue(shard.height(peak + 0.4, clearance) < clearance);
        assertTrue(shard.flightTime(clearance) < shard.flightTime(3));
        assertEquals(0, shard.height(2, 0));
        assertEquals(0, shard.tiltAt(2, 0));
    }
}
