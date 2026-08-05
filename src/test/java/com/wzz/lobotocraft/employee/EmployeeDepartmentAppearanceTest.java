package com.wzz.lobotocraft.employee;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmployeeDepartmentAppearanceTest {
    @Test
    void aiDisplayNameUsesAssignedDepartmentColor() {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), "Angela",
                EmployeeDepartment.INFORMATION);

        Component displayName = EmployeeRuntimeManager.departmentDisplayName(record);

        assertEquals("Angela", displayName.getString());
        assertEquals(EmployeeDepartment.INFORMATION.textColorRgb(),
                displayName.getStyle().getColor().getValue());
    }

    @Test
    void unassignedAiDisplayNameKeepsDefaultColor() {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), "Angela",
                EmployeeDepartment.UNASSIGNED);

        assertNull(EmployeeRuntimeManager.departmentDisplayName(record)
                .getStyle().getColor());
    }

    @Test
    void monitorUsesPlayersAssignedDepartmentInsteadOfPhysicalRoom() {
        UUID playerId = UUID.randomUUID();
        PlayerFacilityProfileData profiles = new PlayerFacilityProfileData();
        profiles.setDepartment(playerId, EmployeeDepartment.TRAINING);

        assertEquals(EmployeeDepartment.TRAINING.id(),
                RoomEmployeeSnapshotService.monitoredPlayerDepartment(profiles, playerId));
    }
}
