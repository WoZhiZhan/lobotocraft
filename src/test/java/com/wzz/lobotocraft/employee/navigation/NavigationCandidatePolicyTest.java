package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NavigationCandidatePolicyTest {
    @Test
    void rejectsTinyIslandsAndAcceptsOrdinaryCorridorSegments() {
        NavigationNode start = node(0);
        assertFalse(NavigationCandidatePolicy.hasLocalMobility(start,
                current -> chainTransitions(current, 10)));
        assertTrue(NavigationCandidatePolicy.hasLocalMobility(start,
                current -> chainTransitions(current, 12)));
    }

    @Test
    void facilityConnectedRegionRejectsSealedIslandsBelowProbeCap() {
        NavigationNode start = node(0);
        assertFalse(NavigationCandidatePolicy.isFacilityConnectedRegion(start,
                current -> chainTransitions(current,
                        NavigationCandidatePolicy.FACILITY_REGION_PROBE_CAP - 1)));
        assertTrue(NavigationCandidatePolicy.isFacilityConnectedRegion(start,
                current -> chainTransitions(current,
                        NavigationCandidatePolicy.FACILITY_REGION_PROBE_CAP)));
    }

    @Test
    void facilityConnectedRegionAcceptsRegionsLargerThanTheProbeCap() {
        NavigationNode start = node(0);
        assertTrue(NavigationCandidatePolicy.isFacilityConnectedRegion(start,
                current -> chainTransitions(current, Integer.MAX_VALUE)));
    }

    private static List<NavigationTransition> chainTransitions(NavigationNode current,
                                                               int length) {
        int x = current.feetPosition().getX();
        if (x + 1 >= length) return List.of();
        NavigationNode next = node(x + 1);
        return List.of(new NavigationTransition(next, NavigationAction.WALK,
                List.of(), 1.0D));
    }

    private static NavigationNode node(int x) {
        return new NavigationNode(new BlockPos(x, 64, 0));
    }
}
