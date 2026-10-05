package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class FacilityRegionProbeTest {
    @Test
    void nearbyQueriesReuseOneProvenRegionInsteadOfRepeatingCollisionWork() {
        AtomicInteger expansions = new AtomicInteger();
        var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current -> {
            expansions.incrementAndGet();
            int x = current.feetPosition().getX();
            return List.of(edge(x - 1), edge(x + 1));
        });
        assertTrue(probe.isConnected(node(0), () -> true));
        int afterFirst = expansions.get();
        for (int i = 1; i <= 64; i++) assertTrue(probe.isConnected(node(i), () -> true));
        assertTrue(expansions.get() <= afterFirst + 64,
                "nearby requests repeated a full region search");
    }

    @Test
    void sealed633NodeIslandIsRejectedAndNegativeClosureCanBeReused() {
        AtomicInteger expansions = new AtomicInteger();
        var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current -> {
            expansions.incrementAndGet();
            int x = current.feetPosition().getX();
            return x < 632 ? List.of(edge(x + 1)) : List.of();
        });
        assertFalse(probe.isConnected(node(0), () -> true));
        assertEquals(633, expansions.get());
        assertFalse(probe.isConnected(node(100), () -> true));
        assertEquals(633, expansions.get());
    }

    @Test
    void reachingOneWayChildrenDoesNotCertifyTheirConnectivity() {
        var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current -> {
            int x = current.feetPosition().getX();
            return x < 1023 ? List.of(edge(x + 1)) : List.of();
        });
        assertTrue(probe.isConnected(node(0), () -> true));
        assertFalse(probe.isConnected(node(500), () -> true),
                "a one-way child was incorrectly certified from its ancestor");
    }

    @Test
    void joiningTwoSmallKnownRegionsStillCountsTheirCombinedReachableNodes() {
        var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current -> {
            int x = current.feetPosition().getX();
            if (x == -1) return List.of(edge(0), edge(1000));
            if (x >= 0 && x < 632 || x >= 1000 && x < 1632) return List.of(edge(x + 1));
            return List.of();
        });
        assertFalse(probe.isConnected(node(0), () -> true));
        assertFalse(probe.isConnected(node(1000), () -> true));
        assertTrue(probe.isConnected(node(-1), () -> true),
                "negative shortcuts discarded nodes needed by the combined region");
    }

    @Test
    void budgetAbortFailsClosedWithoutCachingAnIncompleteRejection() {
        var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current ->
                List.of(edge(current.feetPosition().getX() + 1)));
        AtomicInteger allowance = new AtomicInteger(10);
        assertFalse(probe.isConnected(node(0), () -> allowance.getAndDecrement() > 0));
        assertTrue(probe.isConnected(node(0), () -> true),
                "a budget-aborted partial search poisoned subsequent validation");
        assertTrue(probe.isConnected(node(0), () -> false),
                "an already proven certificate should not need new collision work");
    }

    @Test
    void probeThresholdRemains1024AndRequestCachesDoNotSurviveGeometryChanges() {
        for (int length : new int[]{69, 633, 1023, 1024}) {
            var probe = new NavigationCandidatePolicy.FacilityRegionProbe(current -> {
                int x = current.feetPosition().getX();
                return x + 1 < length ? List.of(edge(x + 1)) : List.of();
            });
            assertEquals(length >= 1024, probe.isConnected(node(0), () -> true));
        }
        var laterRequest = new NavigationCandidatePolicy.FacilityRegionProbe(current -> List.of());
        assertFalse(laterRequest.isConnected(node(0), () -> true));
    }

    private static NavigationNode node(int x) { return new NavigationNode(new BlockPos(x, 64, 0)); }
    private static NavigationTransition edge(int x) {
        return new NavigationTransition(node(x), NavigationAction.WALK, List.of(), 1.0D);
    }
}
