package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityMoveTargetSamplerTest {

    private static final ResourceLocation COMPANY =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void prioritizesTheCenterAndKeepsTwoBlocksAwayFromWideRoomBorders() {
        FacilityWorldLayout.AreaDefinition room = area("room",
                new BlockPos(0, 3, 0), new BlockPos(10, 8, 10));
        RandomSource random = RandomSource.create(81L);
        for (int i = 0; i < 100; i++) {
            BlockPos sampled = FacilityMoveTargetSampler
                    .sample(List.of(room), random, 1, candidate -> true).orElseThrow();
            assertEquals(5, sampled.getX());
            assertEquals(5, sampled.getZ());
            assertEquals(3, sampled.getY());
        }
    }

    @Test
    void expandsOutwardFromARejectedCenterBeforeReachingRoomEdges() {
        FacilityWorldLayout.AreaDefinition room = area("room",
                new BlockPos(0, 3, 0), new BlockPos(10, 8, 10));
        BlockPos sampled = FacilityMoveTargetSampler.sample(List.of(room), RandomSource.create(9L),
                9, candidate -> candidate.getX() != 5 || candidate.getZ() != 5).orElseThrow();
        assertTrue(Math.abs(sampled.getX() - 5) <= 1);
        assertTrue(Math.abs(sampled.getZ() - 5) <= 1);
        assertTrue(sampled.getX() >= 2 && sampled.getX() <= 8);
        assertTrue(sampled.getZ() >= 2 && sampled.getZ() <= 8);
    }

    @Test
    void findsDistinctCenterOutPositionsForAFullEmployeeDispatch() {
        FacilityWorldLayout.AreaDefinition room = area("large-room",
                new BlockPos(0, 3, 0), new BlockPos(20, 8, 20));
        Set<BlockPos> occupied = new HashSet<>();
        RandomSource random = RandomSource.create(44L);
        for (int i = 0; i < 64; i++) {
            BlockPos sampled = FacilityMoveTargetSampler.sample(List.of(room), random, 128,
                    candidate -> !occupied.contains(candidate)).orElseThrow();
            assertTrue(occupied.add(sampled));
        }
        assertEquals(64, occupied.size());
    }

    @Test
    void scansUpwardUntilItFindsAStandableHeight() {
        FacilityWorldLayout.AreaDefinition room = area("room",
                new BlockPos(4, 3, 4), new BlockPos(4, 9, 4));
        BlockPos sampled = FacilityMoveTargetSampler.sample(List.of(room), RandomSource.create(2L),
                1, candidate -> candidate.getY() == 7).orElseThrow();
        assertEquals(new BlockPos(4, 7, 4), sampled);
    }

    @Test
    void treatsFusedCuboidsAsOneLogicalDestinationPool() {
        FacilityWorldLayout.AreaDefinition elevator = area("elevator",
                new BlockPos(0, 5, 0), new BlockPos(1, 5, 1));
        FacilityWorldLayout.AreaDefinition elevatorRoom = area("elevator-room",
                new BlockPos(10, 5, 10), new BlockPos(15, 5, 15));
        Set<String> components = new HashSet<>();
        RandomSource random = RandomSource.create(17L);
        for (int i = 0; i < 200; i++) {
            BlockPos sampled = FacilityMoveTargetSampler.sample(
                    List.of(elevator, elevatorRoom), random, 1, candidate -> true).orElseThrow();
            components.add(sampled.getX() < 5 ? "elevator" : "room");
        }
        assertEquals(Set.of("elevator", "room"), components);
    }

    @Test
    void returnsEmptyWhenEveryCandidateIsRejected() {
        FacilityWorldLayout.AreaDefinition room = area("room",
                new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        assertFalse(FacilityMoveTargetSampler.sample(List.of(room), RandomSource.create(1L),
                32, candidate -> false).isPresent());
    }

    @Test
    void candidatePoolIncludesDifferentStandableHeightsInOneColumn() {
        FacilityWorldLayout.AreaDefinition room = area("layered-room",
                new BlockPos(4, 3, 4), new BlockPos(4, 10, 4));

        List<BlockPos> sampled = FacilityMoveTargetSampler.sampleCandidates(
                List.of(room), RandomSource.create(7L), 1, 8,
                candidate -> candidate.getY() == 4 || candidate.getY() == 8);

        assertEquals(List.of(new BlockPos(4, 4, 4), new BlockPos(4, 8, 4)),
                sampled.stream().sorted(java.util.Comparator.comparingInt(BlockPos::getY)).toList());
    }

    @Test
    void candidatePoolCoversEachCuboidBeforeFillingWeightedAlternatives() {
        FacilityWorldLayout.AreaDefinition first = area("first",
                new BlockPos(0, 4, 0), new BlockPos(8, 4, 8));
        FacilityWorldLayout.AreaDefinition second = area("second",
                new BlockPos(100, 4, 100), new BlockPos(108, 4, 108));

        List<BlockPos> sampled = FacilityMoveTargetSampler.sampleCandidates(
                List.of(first, second), RandomSource.create(19L), 16, 4,
                candidate -> true);

        assertTrue(sampled.stream().anyMatch(candidate -> candidate.getX() < 50));
        assertTrue(sampled.stream().anyMatch(candidate -> candidate.getX() > 50));
    }

    private static FacilityWorldLayout.AreaDefinition area(String id, BlockPos first, BlockPos second) {
        return new FacilityWorldLayout.AreaDefinition(id, COMPANY, "control",
                FacilityAreaType.CORRIDOR, "dept/control/passage/test", first, second);
    }
}
