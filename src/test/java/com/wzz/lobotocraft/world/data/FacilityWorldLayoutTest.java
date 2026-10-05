package com.wzz.lobotocraft.world.data;

import com.wzz.lobotocraft.client.room.FacilityLayoutTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityWorldLayoutTest {

    private static final ResourceLocation COMPANY =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void measuredLayoutContainsUpperAndMiddleAreasAndUsesOriginalNodes() {
        List<FacilityAreaEntry> areas = FacilityWorldLayout.mergeAreas(COMPANY, List.of());
        assertEquals(88, areas.size());
        for (FacilityAreaEntry area : areas) {
            assertTrue(FacilityLayoutTemplate.defaultLayout().areaById(area.groupId).isPresent(),
                    () -> "Unknown original layout node: " + area.groupId);
        }
        List<FacilityWorldLayout.ContainmentBinding> containments =
                FacilityWorldLayout.containmentBindings(COMPANY);
        assertEquals(48, containments.size());
        for (FacilityWorldLayout.ContainmentBinding binding : containments) {
            assertTrue(FacilityLayoutTemplate.defaultLayout().areaById(binding.groupId()).isPresent(),
                    () -> "Unknown original containment node: " + binding.groupId());
        }
    }

    @Test
    void middleLayerUsesTheMeasuredCentralWelfareAndDisciplinaryCuboids() {
        assertArea(new BlockPos(195, 135, 30), "central/upper-main",
                "dept/central/upper/passage/sefira-tiphereth1-1");
        assertArea(new BlockPos(120, 121, 50), "central/upper-left-corridor",
                "dept/central/upper/passage/tiphereth1-left-way1");
        assertArea(new BlockPos(195, 96, 30), "central/lower-secondary",
                "dept/central/lower/passage/dept-tiphereth2-1");
        assertArea(new BlockPos(80, 130, 30), "welfare/upper-main",
                "dept/welfare/passage/dept-chesed-1");
        assertArea(new BlockPos(12, 121, 50), "welfare/upper-elevator",
                "dept/welfare/passage/chesed-down-hub-d");
        assertArea(new BlockPos(310, 100, 30), "disciplinary/lower-main",
                "dept/disciplinary/passage/sefira-geburah-1");
        assertArea(new BlockPos(378, 96, 50), "disciplinary/lower-elevator",
                "dept/disciplinary/passage/geburah-down-hub");
    }

    @Test
    void reversedMeasurementCornersAreNormalized() {
        FacilityWorldLayout.AreaDefinition corridor = FacilityWorldLayout
                .areaAt(COMPANY, new BlockPos(150, 274, 50)).orElseThrow();
        assertEquals("control/left-corridor", corridor.id());
        assertEquals(new BlockPos(130, 270, 47), corridor.minPos());
        assertEquals(new BlockPos(178, 278, 56), corridor.maxPos());
    }

    @Test
    void elevatorAndItsNearestElevatorRoomShareOneMonitorNode() {
        assertSameMonitorNode(new BlockPos(138, 240, 40), new BlockPos(140, 240, 50));
        assertSameMonitorNode(new BlockPos(138, 205, 40), new BlockPos(135, 205, 50));
        assertSameMonitorNode(new BlockPos(64, 240, 40), new BlockPos(64, 240, 50));
        assertSameMonitorNode(new BlockPos(327, 240, 40), new BlockPos(327, 240, 50));
    }

    @Test
    void containmentMarkerGetsStableDepartmentAndOriginalSlot() {
        BlockPos marker = new BlockPos(160, 270, 30);
        FacilityAreaEntry dynamic = new FacilityAreaEntry(COMPANY, "dynamic", marker,
                marker.offset(-10, -5, -10), marker.offset(10, 5, 10), "",
                FacilityAreaType.CONTAINMENT, "");

        FacilityAreaEntry bound = FacilityWorldLayout.mergeAreas(COMPANY, List.of(dynamic)).stream()
                .filter(area -> "dynamic".equals(area.key)).findFirst().orElseThrow();
        assertEquals("control", bound.departmentId);
        assertEquals("dept/control/containment/left-upper-way5", bound.groupId);
        assertNotNull(FacilityLayoutTemplate.defaultLayout().areaById(bound.groupId).orElse(null));

        RoomEntry room = new RoomEntry(COMPANY, marker);
        FacilityWorldLayout.applyRoomBinding(room);
        assertEquals("control", room.departmentId);
    }

    @Test
    void completeAreaLookupRecognizesDynamicContainmentInterior() {
        BlockPos marker = new BlockPos(160, 270, 30);
        FacilityAreaEntry dynamic = new FacilityAreaEntry(
                COMPANY, "dynamic-containment", marker,
                marker.offset(-4, -1, -4), marker.offset(4, 3, 4),
                "", FacilityAreaType.CONTAINMENT, "");

        FacilityAreaEntry selected = FacilityWorldLayout.facilityAreaAt(
                COMPANY, List.of(dynamic), marker.offset(2, 0, 0)).orElseThrow();

        assertEquals(FacilityAreaType.CONTAINMENT, selected.type);
        assertEquals("control", selected.departmentId);
        assertEquals("dept/control/containment/left-upper-way5", selected.groupId);
    }

    @Test
    void containmentSlotsAreUniqueAndCompletePerDepartmentSection() {
        Map<String, Set<Integer>> slotsByDepartment = FacilityWorldLayout
                .containmentBindings(COMPANY).stream()
                .collect(Collectors.groupingBy(
                        binding -> containmentSection(binding.groupId()),
                        Collectors.mapping(FacilityWorldLayout.ContainmentBinding::slotIndex,
                                Collectors.toSet())));

        assertEquals(Set.of("control", "information", "safety", "training", "welfare",
                        "central/upper", "central/lower", "disciplinary", "extraction",
                        "record", "architecture"),
                slotsByDepartment.keySet());
                slotsByDepartment.forEach((department, slots) -> assertEquals(
                "architecture".equals(department)
                        ? Set.of(0, 1, 2, 3, 4, 5, 6, 7)
                        : Set.of(0, 1, 2, 3), slots, department));
    }

    @Test
    void everyFixedContainmentHasAnAuthoritativeEscapeBlock() {
        List<FacilityWorldLayout.ContainmentBinding> bindings =
                FacilityWorldLayout.containmentBindings(COMPANY);
        Set<BlockPos> escapeBlocks = bindings.stream()
                .map(FacilityWorldLayout.ContainmentBinding::escapePos)
                .collect(Collectors.toSet());

        assertEquals(40, escapeBlocks.size(), "the measured facility contains 40 physical exits");
        bindings.forEach(binding -> {
            BlockPos marker = binding.markerPos();
            BlockPos escape = binding.escapePos();
            assertNotNull(escape, () -> "missing fixed exit for " + marker);
            assertTrue(Math.abs(escape.getX() - marker.getX()) <= 32
                            && Math.abs(escape.getY() - marker.getY()) <= 12
                            && Math.abs(escape.getZ() - marker.getZ()) <= 32,
                    () -> "fixed exit is outside the bounded containment search: " + binding);
            assertTrue(!new ChunkPos(marker).equals(new ChunkPos(escape)),
                    () -> "fixture no longer covers the cross-chunk exit case: " + binding);
        });
    }

    @Test
    void unmeasuredDimensionsDoNotReceiveCompanyGeometry() {
        ResourceLocation overworld =
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        assertTrue(FacilityWorldLayout.mergeAreas(overworld, List.of()).isEmpty());
    }

    @Test
    void facilityChunkPlanLoadsOnlyOpenSectionsAndStaysBounded() {
        Set<ChunkPos> controlOnly = FacilityWorldLayout.facilityChunks(COMPANY,
                (department, section) -> "control".equals(department));
        Set<ChunkPos> centralUpperOnly = FacilityWorldLayout.facilityChunks(COMPANY,
                (department, section) -> "central".equals(department) && section == 0);
        Set<ChunkPos> centralLowerOnly = FacilityWorldLayout.facilityChunks(COMPANY,
                (department, section) -> "central".equals(department) && section == 1);
        Set<ChunkPos> all = FacilityWorldLayout.facilityChunks(COMPANY,
                (department, section) -> true);

        assertTrue(controlOnly.contains(new ChunkPos(12, 3)),
                "control main room must stay ticking");
        assertTrue(controlOnly.contains(new ChunkPos(10, 1)),
                "control containment footprint must stay ticking");
        assertTrue(controlOnly.stream().noneMatch(chunk -> chunk.x <= 4),
                "locked left-side departments must not be loaded by control alone");
        assertTrue(!centralUpperOnly.isEmpty());
        assertTrue(!centralLowerOnly.isEmpty());
        assertTrue(all.containsAll(centralUpperOnly));
        assertTrue(all.containsAll(centralLowerOnly));
        assertEquals(113, all.size(), "the measured company should remain a bounded ticket set");
        assertTrue(all.size() <= 128, () -> "facility plan exceeded ticket budget: " + all.size());
    }

    private static void assertSameMonitorNode(BlockPos elevator, BlockPos elevatorRoom) {
        String elevatorGroup = FacilityWorldLayout.areaAt(COMPANY, elevator).orElseThrow().groupId();
        String roomGroup = FacilityWorldLayout.areaAt(COMPANY, elevatorRoom).orElseThrow().groupId();
        assertEquals(roomGroup, elevatorGroup);
    }

    private static void assertArea(BlockPos position, String expectedId, String expectedGroup) {
        FacilityWorldLayout.AreaDefinition area = FacilityWorldLayout
                .areaAt(COMPANY, position).orElseThrow();
        assertEquals(expectedId, area.id());
        assertEquals(expectedGroup, area.groupId());
    }

    private static String containmentSection(String groupId) {
        String prefix = "dept/";
        String suffix = "/containment/";
        int start = groupId.indexOf(prefix) + prefix.length();
        int end = groupId.indexOf(suffix);
        return groupId.substring(start, end);
    }
}
