package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahMajorAttackControllerTest {
    @Test
    void normalBloodMistEmitsEveryAnimationCalibratedDamageCue() {
        GeburahMajorAttackController controller = new GeburahMajorAttackController();
        controller.start(GeburahMajorMove.BLOOD_MIST);
        List<Integer> hitTicks = collectHitTicks(controller);

        assertEquals(List.of(8, 39, 45, 59, 63, 67, 78), hitTicks);
        assertEquals(16, GeburahMajorMove.BLOOD_MIST.cues().get(5).minDamage());
        assertEquals(20, GeburahMajorMove.BLOOD_MIST.cues().get(5).maxDamage());
        assertFalse(controller.isActive());
    }

    @Test
    void egoBloodMistAndBodiesEverywhereUseIndependentTimelines() {
        GeburahMajorAttackController ego = new GeburahMajorAttackController();
        ego.start(GeburahMajorMove.EGO_BLOOD_MIST);
        assertEquals(List.of(8, 39, 62, 75, 96), collectHitTicks(ego));

        GeburahMajorAttackController bodies = new GeburahMajorAttackController();
        bodies.start(GeburahMajorMove.BODIES_EVERYWHERE);
        assertEquals(List.of(46), collectHitTicks(bodies));
    }

    @Test
    void missedBloodMistOpeningAbortsAllRemainingHits() {
        GeburahMajorAttackController controller = new GeburahMajorAttackController();
        controller.start(GeburahMajorMove.BLOOD_MIST);
        while (controller.tick() != GeburahMajorAttackController.Event.HIT) {
            assertTrue(controller.isActive());
        }
        assertTrue(controller.currentCue().opening());
        controller.abort();

        assertFalse(controller.isActive());
    }

    private static List<Integer> collectHitTicks(GeburahMajorAttackController controller) {
        List<Integer> ticks = new ArrayList<>();
        int guard = 0;
        while (controller.isActive() && guard++ < 300) {
            if (controller.tick() == GeburahMajorAttackController.Event.HIT) {
                ticks.add(controller.elapsedTicks());
            }
        }
        return ticks;
    }
}
