package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFacilityProfileDataTest {
    @Test
    void storesPlayerDepartmentOutsidePersonnelCapacity() {
        PlayerFacilityProfileData profiles = new PlayerFacilityProfileData();
        UUID player = UUID.randomUUID();

        assertTrue(profiles.setDepartment(player, EmployeeDepartment.CONTROL));
        assertEquals(EmployeeDepartment.CONTROL, profiles.department(player));

        PlayerFacilityProfileData loaded = PlayerFacilityProfileData.load(
                profiles.save(new CompoundTag()));
        assertEquals(EmployeeDepartment.CONTROL, loaded.department(player));
    }

    @Test
    void onlyOpenedRealDepartmentsCanBeSelected() {
        assertTrue(PlayerFacilityProfileData.canSelect(EmployeeDepartment.CONTROL, true));
        assertFalse(PlayerFacilityProfileData.canSelect(EmployeeDepartment.CONTROL, false));
        assertFalse(PlayerFacilityProfileData.canSelect(EmployeeDepartment.UNASSIGNED, true));
    }
}
