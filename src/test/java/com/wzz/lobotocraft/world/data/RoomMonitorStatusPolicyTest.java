package com.wzz.lobotocraft.world.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoomMonitorStatusPolicyTest {
    @Test
    void onlyAuthoritativeEscapeStateIsClickableForSuppression() {
        assertEquals(RoomEntry.STATUS_ESCAPED,
                RoomMonitorStatusPolicy.trackedStatus(true, false));
        assertEquals(RoomEntry.STATUS_OCCUPIED,
                RoomMonitorStatusPolicy.trackedStatus(false, true));
        assertEquals(RoomEntry.STATUS_LOST,
                RoomMonitorStatusPolicy.trackedStatus(false, false));
        assertEquals(RoomEntry.STATUS_LOST,
                RoomMonitorStatusPolicy.missingBoundStatus());
    }
}
