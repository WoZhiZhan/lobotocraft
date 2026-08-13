package com.wzz.lobotocraft.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeLevelCalculatorTest {
    @Test
    void usesOriginalAttributeLevelBoundariesAndExDisplay() {
        assertEquals(1, EmployeeLevelCalculator.statLevel(29));
        assertEquals(2, EmployeeLevelCalculator.statLevel(30));
        assertEquals(2, EmployeeLevelCalculator.statLevel(44));
        assertEquals(3, EmployeeLevelCalculator.statLevel(45));
        assertEquals(3, EmployeeLevelCalculator.statLevel(64));
        assertEquals(4, EmployeeLevelCalculator.statLevel(65));
        assertEquals(4, EmployeeLevelCalculator.statLevel(84));
        assertEquals(5, EmployeeLevelCalculator.statLevel(85));
        assertEquals(5, EmployeeLevelCalculator.statLevel(110));
        assertEquals(5, EmployeeLevelCalculator.displayedStatLevel(99));
        assertEquals(6, EmployeeLevelCalculator.displayedStatLevel(100));
        assertEquals("EX", EmployeeLevelCalculator.statGrade(110));
        assertEquals("EX", EmployeeLevelCalculator.levelGrade(6));
    }

    @Test
    void equipmentRequirementsUseRawStatsForExOnly() {
        assertEquals(5, EmployeeLevelCalculator.levelForEquipmentRequirement(99, 5, 6));
        assertEquals(6, EmployeeLevelCalculator.levelForEquipmentRequirement(100, 5, 6));
        assertEquals(5, EmployeeLevelCalculator.levelForEquipmentRequirement(100, 5, 5));
    }

    @Test
    void usesOriginalAgentModelSumThresholds() {
        assertEquals(1, EmployeeLevelCalculator.fromStatLevels(1, 1, 1, 1));
        assertEquals(1, EmployeeLevelCalculator.fromStatLevels(2, 1, 1, 1));
        assertEquals(2, EmployeeLevelCalculator.fromStatLevels(2, 2, 1, 1));
        assertEquals(2, EmployeeLevelCalculator.fromStatLevels(2, 2, 2, 2));
        assertEquals(3, EmployeeLevelCalculator.fromStatLevels(3, 2, 2, 2));
        assertEquals(3, EmployeeLevelCalculator.fromStatLevels(3, 3, 3, 2));
        assertEquals(4, EmployeeLevelCalculator.fromStatLevels(4, 3, 3, 2));
        assertEquals(4, EmployeeLevelCalculator.fromStatLevels(4, 4, 4, 3));
        assertEquals(5, EmployeeLevelCalculator.fromStatLevels(4, 4, 4, 4));
    }

    @Test
    void supportsSpecializedEmployeesInsteadOfUsingMinimum() {
        // 80, 80, 80, 20 -> IV (4 + 4 + 4 + 1 = 13), not I.
        assertEquals(4, EmployeeLevelCalculator.fromStatValues(80, 80, 80, 20));
        // A single level-V attribute can move a sum across a threshold.
        assertEquals(5, EmployeeLevelCalculator.fromStatLevels(5, 5, 5, 1));
    }

    @Test
    void clampsMalformedLevelInputsAndHandlesRawValuesAtBoundaries() {
        assertEquals(1, EmployeeLevelCalculator.fromStatLevels(0, -4, 1, 1));
        assertEquals(5, EmployeeLevelCalculator.fromStatLevels(99, 7, 6, 5));
        assertEquals(2, EmployeeLevelCalculator.fromStatValues(40, 40, 20, 20));
        assertEquals(3, EmployeeLevelCalculator.fromStatValues(60, 40, 40, 40));
        assertEquals(4, EmployeeLevelCalculator.fromStatValues(80, 80, 80, 40));
        assertEquals(5, EmployeeLevelCalculator.fromStatValues(100, 100, 100, 80));
    }
}
