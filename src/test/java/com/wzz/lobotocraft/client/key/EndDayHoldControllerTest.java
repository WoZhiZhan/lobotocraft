package com.wzz.lobotocraft.client.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndDayHoldControllerTest {
    @Test
    void sendsOnceOnlyAfterRequiredHold() {
        EndDayHoldController controller = new EndDayHoldController(30);
        for (int tick = 1; tick < 30; tick++) {
            assertFalse(controller.tick(true, true));
        }
        assertTrue(controller.tick(true, true));
        assertEquals(1.0F, controller.progress());
        for (int tick = 0; tick < 100; tick++) {
            assertFalse(controller.tick(true, true));
        }
    }

    @Test
    void releaseAllowsACompleteRetry() {
        EndDayHoldController controller = new EndDayHoldController(2);
        assertFalse(controller.tick(true, true));
        assertTrue(controller.tick(true, true));
        assertFalse(controller.tick(true, false));
        assertEquals(0.0F, controller.progress());
        assertFalse(controller.tick(true, true));
        assertTrue(controller.tick(true, true));
    }

    @Test
    void becomingIneligibleResetsPartialHold() {
        EndDayHoldController controller = new EndDayHoldController(3);
        assertFalse(controller.tick(true, true));
        assertFalse(controller.tick(false, true));
        assertEquals(0.0F, controller.progress());
        assertFalse(controller.tick(true, true));
        assertFalse(controller.tick(true, true));
        assertTrue(controller.tick(true, true));
    }
}
