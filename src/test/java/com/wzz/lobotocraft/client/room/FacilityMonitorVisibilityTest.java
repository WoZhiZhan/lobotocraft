package com.wzz.lobotocraft.client.room;

import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityMonitorVisibilityTest {

    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");

    @Test
    void hidesLockedDepartmentsButShowsSynchronizedContainments() {
        FacilityMonitorVisibility visibility = FacilityMonitorVisibility.fromAreas(List.of(
                area("dept/control/passage/sefira-malkuth-1"),
                area("dept/control/containment/left-upper-way5"),
                area("dept/control/containment/right-upper-way4")));
        FacilityLayoutTemplate layout = FacilityLayoutTemplate.defaultLayout();

        assertTrue(visibility.isVisible(layout.areaById(
                "dept/control/background").orElseThrow()));
        assertTrue(visibility.isVisible(layout.areaById(
                "dept/control/containment/left-upper-way5").orElseThrow()));
        assertTrue(visibility.isVisible(layout.areaById(
                "dept/control/containment/right-upper-way4").orElseThrow()));
        assertFalse(visibility.isVisible(layout.areaById(
                "dept/control/containment/left-down-way2").orElseThrow()));
        assertFalse(visibility.isVisible(layout.areaById(
                "dept/information/background").orElseThrow()));
    }

    @Test
    void keepsCentralUpperAndLowerVisibilityIndependent() {
        FacilityMonitorVisibility visibility = FacilityMonitorVisibility.fromAreas(List.of(
                area("dept/central/upper/passage/sefira-tiphereth1-1")));
        FacilityLayoutTemplate layout = FacilityLayoutTemplate.defaultLayout();

        assertTrue(visibility.isVisible(layout.areaById(
                "dept/central/upper/background").orElseThrow()));
        assertFalse(visibility.isVisible(layout.areaById(
                "dept/central/lower/background").orElseThrow()));
    }

    @Test
    void unknownOrMalformedGroupsFailClosed() {
        FacilityMonitorVisibility visibility = FacilityMonitorVisibility.fromAreas(List.of(
                area("legacy-room"), area("dept/central/sideways/passage/test")));
        FacilityLayoutTemplate layout = FacilityLayoutTemplate.defaultLayout();

        assertFalse(visibility.isVisible(layout.areaById(
                "dept/control/background").orElseThrow()));
        assertFalse(visibility.isVisible(layout.areaById(
                "dept/central/upper/background").orElseThrow()));
    }

    private static FacilityAreaEntry area(String groupId) {
        return new FacilityAreaEntry(DIMENSION, groupId, BlockPos.ZERO,
                BlockPos.ZERO, BlockPos.ZERO, "control", FacilityAreaType.OTHER, groupId);
    }
}
