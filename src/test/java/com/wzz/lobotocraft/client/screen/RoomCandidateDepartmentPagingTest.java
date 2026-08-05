package com.wzz.lobotocraft.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomCandidateDepartmentPagingTest {
    @Test
    void informationPageHasControlOnLeftAndTrainingOnRight() {
        List<String> departments = sorted(
                "safety", "control", "training", "information");

        assertEquals(List.of("control", "information", "training", "safety"), departments);
        int information = departments.indexOf("information");
        assertEquals("control", departments.get(information - 1));
        assertEquals("training", departments.get(information + 1));
    }

    @Test
    void laterDepartmentsFollowOriginalSefiraManagerOrder() {
        assertEquals(List.of("control", "information", "training", "safety", "central",
                        "disciplinary", "welfare", "extraction", "record", "architecture"),
                sorted("record", "information", "architecture", "central", "safety",
                        "welfare", "control", "extraction", "disciplinary", "training"));
    }

    @Test
    void movementStopsAtFirstAndLastOpenedDepartment() {
        assertEquals(0, RoomMapScreen.moveCandidatePage(0, 4, -1));
        assertEquals(1, RoomMapScreen.moveCandidatePage(0, 4, 1));
        assertEquals(2, RoomMapScreen.moveCandidatePage(3, 4, -1));
        assertEquals(3, RoomMapScreen.moveCandidatePage(3, 4, 1));
    }

    private static List<String> sorted(String... departmentIds) {
        List<String> result = new ArrayList<>(List.of(departmentIds));
        result.sort(Comparator
                .comparingInt((String department) ->
                        RoomMapScreen.candidateDepartmentRank(department))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
