package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityAccessService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRoomMapPacketVisibilityTest {

    @Test
    void monitorShowsOpenAndUnexpandedSlotsOnly() {
        assertTrue(OpenRoomMapPacket.isContainmentVisible(
                FacilityAccessService.ContainmentAccess.OPEN));
        assertTrue(OpenRoomMapPacket.isContainmentVisible(
                FacilityAccessService.ContainmentAccess.UNEXPANDED));
        assertFalse(OpenRoomMapPacket.isContainmentVisible(
                FacilityAccessService.ContainmentAccess.DEPARTMENT_LOCKED));
        assertFalse(OpenRoomMapPacket.isContainmentVisible(
                FacilityAccessService.ContainmentAccess.UNKNOWN));
    }
}
