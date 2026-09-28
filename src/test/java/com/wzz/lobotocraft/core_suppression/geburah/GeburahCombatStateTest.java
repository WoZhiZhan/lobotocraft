package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahCombatStateTest {
    @Test
    void firstHealthDepletionEntersEgoAtHalfHealthWithoutOverflow() {
        GeburahCombatState state = new GeburahCombatState();

        GeburahCombatState.DamageResult result = state.applyHealthDamage(20_000.0F);

        assertTrue(result.changedPhase());
        assertFalse(result.defeated());
        assertEquals(GeburahPhase.EGO, state.phase());
        assertEquals(1500.0F, state.health());
        assertEquals(3000.0F, state.maxHealth());
        assertEquals(100.0F, state.ego());
    }

    @Test
    void laterHealthDepletionIsFinalDefeat() {
        GeburahCombatState state = new GeburahCombatState();
        state.applyHealthDamage(3000.0F);

        GeburahCombatState.DamageResult result = state.applyHealthDamage(1500.0F);

        assertTrue(result.defeated());
        assertEquals(GeburahPhase.DEFEATED, state.phase());
        assertEquals(0.0F, state.health());
    }

    @Test
    void postureDamageUsesActionWindowAndPerAttackCap() {
        GeburahCombatState state = new GeburahCombatState();

        assertEquals(2.5F, state.applyPostureDamage(10.0F, GeburahActionWindow.WINDUP));
        assertEquals(4.375F, state.applyPostureDamage(10.0F, GeburahActionWindow.RECOVERY));
        assertEquals(0.375F, state.applyPostureDamage(10.0F, GeburahActionWindow.ACTIVE));
        assertEquals(0.75F, state.applyPostureDamage(100.0F, GeburahActionWindow.RECOVERY));
        assertEquals(92.0F, state.posture());
        assertEquals(8.0F, state.postureDamageInWindow());
    }

    @Test
    void cleanThreeHitComboNearlySustainsItsOwnPostureCost() {
        GeburahCombatState state = new GeburahCombatState();

        assertTrue(state.spendComboPosture());
        state.restorePosture(6.0F);
        state.restorePosture(6.0F);
        state.restorePosture(6.0F);
        state.refundCleanRecovery();

        assertEquals(98.0F, state.posture());
    }

    @Test
    void multiTargetPostureRecoveryCapsAdditionalTargetsAtSix() {
        GeburahCombatState state = new GeburahCombatState();
        state.setPosture(0.0F);

        state.restorePostureFromAttackHits(1);
        assertEquals(6.0F, state.posture());

        state.setPosture(0.0F);
        state.restorePostureFromAttackHits(4);
        assertEquals(12.0F, state.posture());

        state.setPosture(0.0F);
        state.restorePostureFromAttackHits(20);
        assertEquals(12.0F, state.posture());
    }

    @Test
    void highFrequencyDamageSharesOneSlidingWindowBudget() {
        GeburahCombatState state = new GeburahCombatState();
        float applied = 0.0F;
        for (int i = 0; i < 20; i++) {
            applied += state.applyPostureDamage(100.0F, GeburahActionWindow.RECOVERY);
        }
        assertEquals(GeburahCombatState.MAX_POSTURE_DAMAGE_PER_WINDOW, applied);
        assertEquals(0.0F,
                state.applyPostureDamage(100.0F, GeburahActionWindow.RECOVERY));

        for (int i = 0; i < GeburahCombatState.POSTURE_DAMAGE_WINDOW_TICKS - 1; i++) {
            state.tick(false);
        }
        assertEquals(0.0F,
                state.applyPostureDamage(100.0F, GeburahActionWindow.RECOVERY));

        state.tick(false);
        assertEquals(GeburahCombatState.MAX_POSTURE_DAMAGE_PER_ATTACK,
                state.applyPostureDamage(100.0F, GeburahActionWindow.RECOVERY));
    }

    @Test
    void postureBreakLastsEightSecondsThenRestoresFullyAndProtectsForTwoSeconds() {
        GeburahCombatState state = new GeburahCombatState();
        state.setPosture(5.0F);
        state.applyPostureDamage(20.0F, GeburahActionWindow.WINDUP);

        assertEquals(GeburahCombatState.POSTURE_BREAK_TICKS, state.postureBreakTicks());
        for (int i = 0; i < GeburahCombatState.POSTURE_BREAK_TICKS; i++) state.tick(false);

        assertEquals(GeburahCombatState.MAX_POSTURE, state.posture());
        assertEquals(GeburahCombatState.POSTURE_PROTECTION_TICKS, state.postureProtectionTicks());
        assertEquals(0.0F, state.applyPostureDamage(15.0F, GeburahActionWindow.RECOVERY));
    }

    @Test
    void egoDrainsOnlyWithTargetAndReleasesAgainAfterExhaustionCooldown() {
        GeburahCombatState state = new GeburahCombatState();
        state.setPhase(GeburahPhase.EGO);
        state.setEgo(1.0F);

        for (int i = 0; i < 200; i++) state.tick(false);
        assertEquals(1.0F, state.ego());

        for (int i = 0; i < 40; i++) state.tick(true);
        assertEquals(GeburahPhase.EXHAUSTED, state.phase());
        assertEquals(0.0F, state.ego());

        for (int i = 0; i < GeburahCombatState.EGO_EXHAUSTION_TICKS; i++) state.tick(false);
        assertEquals(GeburahPhase.EGO, state.phase());
        assertEquals(100.0F, state.ego());
    }

    @Test
    void egoNaturallyRestoresPostureEveryFiveSeconds() {
        GeburahCombatState state = new GeburahCombatState();
        state.setPhase(GeburahPhase.EGO);
        state.setPosture(60.0F);

        for (int i = 0; i < 100; i++) state.tick(false);

        assertEquals(65.0F, state.posture());
    }

    @Test
    void phaseResistanceTableMatchesSpecification() {
        GeburahCombatState state = new GeburahCombatState();
        assertEquals(0.6F, state.resistance("red"));

        state.setPhase(GeburahPhase.EGO);
        assertEquals(0.2F, state.resistance("red"));
        assertEquals(0.3F, state.resistance("blue"));

        state.setPhase(GeburahPhase.EXHAUSTED);
        assertEquals(1.5F, state.resistance("white"));
    }
}
