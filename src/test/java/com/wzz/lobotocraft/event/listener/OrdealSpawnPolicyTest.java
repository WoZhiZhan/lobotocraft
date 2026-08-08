package com.wzz.lobotocraft.event.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrdealSpawnPolicyTest {
    @Test
    void dawnsScaleByOpenedDepartments() {
        assertEquals(6, OrdealSpawnPolicy.dawnDepartmentCount(6));
        assertEquals(5, OrdealSpawnPolicy.AMBER_DAWNS_PER_DEPARTMENT);
    }

    @Test
    void noonFamiliesUseTheirOriginalDepartmentLimits() {
        assertEquals(8, OrdealSpawnPolicy.greenNoonDepartmentCount(8));
        assertEquals(1, OrdealSpawnPolicy.crimsonNoonTotalCount(8));
        assertEquals(5, OrdealSpawnPolicy.violetNoonDepartmentCount(8));
        assertEquals(4, OrdealSpawnPolicy.indigoNoonDepartmentCount(8));
        assertEquals(3, OrdealSpawnPolicy.INDIGO_NOON_PER_DEPARTMENT);
    }
}
