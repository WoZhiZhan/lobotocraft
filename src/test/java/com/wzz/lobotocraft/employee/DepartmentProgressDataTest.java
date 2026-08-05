package com.wzz.lobotocraft.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentProgressDataTest {

    @Test
    void freshProgressStartsWithOnlyFourControlSlots() {
        DepartmentProgressData data = new DepartmentProgressData();

        assertTrue(data.isOpen(EmployeeDepartment.CONTROL));
        assertEquals(4, data.containmentSlotCapacity(EmployeeDepartment.CONTROL, 0));
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.CONTROL
                    || department == EmployeeDepartment.UNASSIGNED) continue;
            assertFalse(data.isOpen(department), department.id());
            assertEquals(0, data.containmentSlotCapacity(department, 0), department.id());
        }
    }

    @Test
    void normalDepartmentOpensFourSlotsAcrossFirstFourLevels() {
        DepartmentProgressData data = new DepartmentProgressData();
        int[] expected = {0, 1, 2, 3, 4, 4};

        for (int level = 0; level <= 5; level++) {
            assertTrue(data.setLevel(EmployeeDepartment.INFORMATION, level));
            assertEquals(expected[level],
                    data.containmentSlotCapacity(EmployeeDepartment.INFORMATION, 0));
        }
    }

    @Test
    void controlExpansionNeverChangesItsFourStartingSlots() {
        DepartmentProgressData data = new DepartmentProgressData();

        for (int level = 1; level <= 5; level++) {
            assertTrue(data.setLevel(EmployeeDepartment.CONTROL, level));
            assertEquals(4, data.containmentSlotCapacity(EmployeeDepartment.CONTROL, 0));
        }
    }

    @Test
    void architectureOpensEightSlotsAcrossFirstFourLevels() {
        DepartmentProgressData data = new DepartmentProgressData();
        int[] expected = {0, 2, 4, 6, 8, 8};

        for (int level = 0; level <= 5; level++) {
            assertTrue(data.setLevel(EmployeeDepartment.ARCHITECTURE, level));
            assertEquals(expected[level],
                    data.containmentSlotCapacity(EmployeeDepartment.ARCHITECTURE, 0));
        }
    }

    @Test
    void centralExpandsUpperAndLowerSectionsInParallel() {
        DepartmentProgressData data = new DepartmentProgressData();
        int[] expectedPerSection = {0, 1, 2, 3, 4, 4};

        for (int level = 0; level <= 5; level++) {
            assertTrue(data.setLevel(EmployeeDepartment.CENTRAL, level));
            assertEquals(level > 0, data.isCentralLowerOpen());
            assertEquals(expectedPerSection[level],
                    data.containmentSlotCapacity(EmployeeDepartment.CENTRAL, 0));
            assertEquals(expectedPerSection[level],
                    data.containmentSlotCapacity(EmployeeDepartment.CENTRAL, 1));
        }
    }
}
