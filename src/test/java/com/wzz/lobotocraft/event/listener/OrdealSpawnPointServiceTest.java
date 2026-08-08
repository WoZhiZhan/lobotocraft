package com.wzz.lobotocraft.event.listener;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdealSpawnPointServiceTest {

    private static final ResourceLocation COMPANY =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void groupsOnlyMeasuredOpenCorridorsOncePerDepartment() {
        BlockPos controlA = new BlockPos(150, 274, 50);
        BlockPos controlB = new BlockPos(220, 274, 50);
        BlockPos information = new BlockPos(160, 205, 50);

        List<OrdealSpawnPointService.DepartmentCorridors> result =
                OrdealSpawnPointService.classifyCorridors(COMPANY,
                        List.of(controlA, controlB, information, controlA),
                        group -> true);

        assertEquals(2, result.size());
        assertEquals("control", result.get(0).department().departmentId());
        assertEquals(Set.of(controlA, controlB), Set.copyOf(result.get(0).corridors()));
        assertEquals("information", result.get(1).department().departmentId());
        assertEquals(List.of(information), result.get(1).corridors());
    }

    @Test
    void unopenedUnknownAndNonCorridorCandidatesFailClosed() {
        BlockPos openControlCorridor = new BlockPos(150, 274, 50);
        BlockPos lockedInformationCorridor = new BlockPos(160, 205, 50);
        BlockPos controlRestRoom = new BlockPos(195, 275, 40);
        BlockPos unmappedLowerLayer = new BlockPos(200, 100, 50);

        List<OrdealSpawnPointService.DepartmentCorridors> result =
                OrdealSpawnPointService.classifyCorridors(COMPANY,
                        List.of(openControlCorridor, lockedInformationCorridor,
                                controlRestRoom, unmappedLowerLayer),
                        group -> group.startsWith("dept/control/"));

        assertEquals(1, result.size());
        assertEquals(List.of(openControlCorridor), result.get(0).corridors());
    }

    @Test
    void unmeasuredDimensionHasNoOrdealSpawnCandidates() {
        ResourceLocation overworld =
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        assertTrue(OrdealSpawnPointService.classifyCorridors(overworld,
                List.of(new BlockPos(150, 274, 50)), group -> true).isEmpty());
    }

    @Test
    void realEscapeAnchorsCoverEveryFacilityDepartmentSection() {
        List<BlockPos> anchors = List.of(
                new BlockPos(160,269,52), new BlockPos(231,269,52),
                new BlockPos(87,235,52), new BlockPos(115,235,52),
                new BlockPos(177,235,52), new BlockPos(214,235,52),
                new BlockPos(276,235,52), new BlockPos(305,235,52),
                new BlockPos(159,200,52), new BlockPos(232,200,53),
                new BlockPos(87,167,52), new BlockPos(115,167,52),
                new BlockPos(159,167,52), new BlockPos(232,167,52),
                new BlockPos(276,167,52), new BlockPos(304,167,52),
                new BlockPos(30,116,52), new BlockPos(48,116,52),
                new BlockPos(120,116,52), new BlockPos(138,116,52),
                new BlockPos(254,116,51), new BlockPos(272,116,51),
                new BlockPos(344,116,51), new BlockPos(362,116,51),
                new BlockPos(30,91,51), new BlockPos(48,91,51),
                new BlockPos(120,91,52), new BlockPos(138,91,52),
                new BlockPos(254,91,52), new BlockPos(272,91,52),
                new BlockPos(344,91,51), new BlockPos(362,91,51),
                new BlockPos(103,44,52), new BlockPos(141,44,52),
                new BlockPos(250,44,51), new BlockPos(288,44,52),
                new BlockPos(44,4,52), new BlockPos(101,4,52),
                new BlockPos(289,4,51), new BlockPos(348,4,52));

        List<OrdealSpawnPointService.DepartmentCorridors> grouped =
                OrdealSpawnPointService.classifyCorridors(COMPANY, anchors, group -> true);

        assertEquals(Set.of("control:0", "information:0", "safety:0", "training:0",
                        "welfare:0", "central:0", "central:1", "disciplinary:0",
                        "record:0", "extraction:0", "architecture:0"),
                grouped.stream().map(group -> group.department().departmentId() + ":"
                        + group.department().section()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Map.ofEntries(
                        Map.entry("control:0", 4), Map.entry("information:0", 4),
                        Map.entry("safety:0", 4), Map.entry("training:0", 4),
                        Map.entry("welfare:0", 4), Map.entry("central:0", 4),
                        Map.entry("central:1", 4), Map.entry("disciplinary:0", 4),
                        Map.entry("record:0", 2), Map.entry("extraction:0", 2),
                        Map.entry("architecture:0", 4)),
                grouped.stream().collect(java.util.stream.Collectors.toMap(
                        group -> group.department().departmentId() + ":"
                                + group.department().section(),
                        group -> group.corridors().size())));
    }
}
