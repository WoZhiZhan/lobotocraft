package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class GeburahSpecialControllerTest {
    @Test
    void willOfBattleEmitsOneHitAndOneRecovery() {
        GeburahSpecialController controller = new GeburahSpecialController();
        assertEquals(GeburahSpecialController.Event.START_WINDUP, controller.start());

        int hits = 0;
        int recoveries = 0;
        int guard = 0;
        while (controller.isActive() && guard++ < 100) {
            GeburahSpecialController.Event event = controller.tick();
            if (event == GeburahSpecialController.Event.HIT) hits++;
            if (event == GeburahSpecialController.Event.START_RECOVERY) recoveries++;
        }

        assertEquals(1, hits);
        assertEquals(1, recoveries);
        assertFalse(controller.isActive());
    }

    @Test
    void invalidLandingCanAbortIntoRecoveryWithoutHit() {
        GeburahSpecialController controller = new GeburahSpecialController();
        controller.start();

        assertEquals(GeburahSpecialController.Event.START_RECOVERY,
                controller.abortToRecovery());
        while (controller.isActive()) controller.tick();

        assertFalse(controller.isActive());
    }
}
