package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahComboControllerTest {
    @Test
    void plannerUsesPhaseRangesAndNeverRepeatsAdjacentMove() {
        for (int seed = 0; seed < 100; seed++) {
            Random normalRandom = new Random(seed);
            List<GeburahMove> normal = GeburahComboPlanner.plan(false, normalRandom::nextInt);
            assertTrue(normal.size() >= 1 && normal.size() <= 3);
            assertNoAdjacentDuplicates(normal);

            Random egoRandom = new Random(seed);
            List<GeburahMove> ego = GeburahComboPlanner.plan(true, egoRandom::nextInt);
            assertTrue(ego.size() >= 3 && ego.size() <= 6);
            assertNoAdjacentDuplicates(ego);
        }
    }

    @Test
    void injuredTargetPreferenceRaisesComboFloor() {
        for (int seed = 0; seed < 100; seed++) {
            Random normalRandom = new Random(seed);
            List<GeburahMove> normal = GeburahComboPlanner.plan(
                    false, true, normalRandom::nextInt);
            assertTrue(normal.size() >= 2 && normal.size() <= 3);

            Random egoRandom = new Random(seed);
            List<GeburahMove> ego = GeburahComboPlanner.plan(
                    true, true, egoRandom::nextInt);
            assertTrue(ego.size() >= 4 && ego.size() <= 6);
        }
    }

    @Test
    void lowPostureStillGetsOneRecoveryAttackWithoutComboCost() {
        Random random = new Random(7L);
        GeburahComboPlanner.AttackPlan plan = GeburahComboPlanner.planForPosture(
                false, true, 29.0F, random::nextInt);

        assertEquals(1, plan.moves().size());
        assertFalse(plan.spendsComboPosture());
    }

    @Test
    void fundedPostureUsesNormalPhaseComboBudget() {
        Random random = new Random(7L);
        GeburahComboPlanner.AttackPlan plan = GeburahComboPlanner.planForPosture(
                false, true, 31.0F, random::nextInt);

        assertTrue(plan.moves().size() >= 2 && plan.moves().size() <= 3);
        assertTrue(plan.spendsComboPosture());
    }

    @Test
    void chainedMoveSkipsIntermediateRecoveryAndWindup() {
        GeburahComboController controller = new GeburahComboController();
        assertEquals(GeburahComboController.Event.START_WINDUP,
                controller.start(List.of(GeburahMove.SLASH, GeburahMove.STAB), false));

        int windups = 1;
        int chainTransitions = 0;
        int recoveries = 0;
        int hits = 0;
        int guard = 0;
        while (controller.isAttacking() && guard++ < 200) {
            GeburahComboController.Event event = controller.tick();
            if (event == GeburahComboController.Event.START_WINDUP) windups++;
            if (event == GeburahComboController.Event.START_CHAIN_STRIKE) chainTransitions++;
            if (event == GeburahComboController.Event.START_RECOVERY) recoveries++;
            if (event == GeburahComboController.Event.HIT) hits++;
        }

        assertEquals(1, windups);
        assertEquals(1, chainTransitions);
        assertEquals(1, recoveries);
        assertEquals(2, hits);
        assertFalse(controller.isAttacking());
        assertTrue(controller.consumeCleanRecovery());
    }

    @Test
    void damageDuringFinalRecoveryForfeitsRefund() {
        GeburahComboController controller = new GeburahComboController();
        controller.start(List.of(GeburahMove.VERTICAL_SLASH), true);
        while (controller.stage() != GeburahComboController.Stage.RECOVERY) controller.tick();
        controller.markRecoveryHit();
        while (controller.isAttacking()) controller.tick();

        assertFalse(controller.consumeCleanRecovery());
    }

    @Test
    void chainBoundaryCanReplaceThePreplannedMove() {
        GeburahComboController controller = new GeburahComboController();
        controller.start(List.of(GeburahMove.SLASH, GeburahMove.STAB), true);
        while (controller.tick() != GeburahComboController.Event.START_CHAIN_STRIKE) {
            assertTrue(controller.isAttacking());
        }

        assertTrue(controller.replaceCurrentMove(GeburahMove.VERTICAL_SLASH));
        assertEquals(GeburahMove.VERTICAL_SLASH, controller.move());
    }

    private static void assertNoAdjacentDuplicates(List<GeburahMove> moves) {
        for (int i = 1; i < moves.size(); i++) {
            assertNotEquals(moves.get(i - 1), moves.get(i));
        }
    }
}
