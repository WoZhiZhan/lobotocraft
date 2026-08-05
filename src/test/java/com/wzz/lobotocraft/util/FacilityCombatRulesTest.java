package com.wzz.lobotocraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityCombatRulesTest {
    @Test
    void blocksDamageOnlyWhenBothEmployeesAreClearHeaded() {
        assertTrue(FacilityCombatRules.blocksEmployeeFriendlyFire(false, false));
        assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(true, false));
        assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(false, true));
        assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(true, true));
    }

    @Test
    void blocksEveryHumanAndAiPlayerPairWhenBothAreClearHeaded() {
        for (FacilityCombatRules.CombatActor victim : playerActors()) {
            for (FacilityCombatRules.CombatActor attacker : playerActors()) {
                assertTrue(FacilityCombatRules.blocksEmployeeFriendlyFire(
                        victim, attacker, false, false, false), attacker + " -> " + victim);
            }
        }
    }

    @Test
    void eitherPanickingParticipantAllowsEveryHumanAndAiPlayerPair() {
        for (FacilityCombatRules.CombatActor victim : playerActors()) {
            for (FacilityCombatRules.CombatActor attacker : playerActors()) {
                assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                        victim, attacker, false, true, false));
                assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                        victim, attacker, false, false, true));
                assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                        victim, attacker, false, true, true));
            }
        }
    }

    @Test
    void selfDamageAndForgedNonPlayerSourcesAreNotClassifiedAsFriendlyFire() {
        assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                FacilityCombatRules.CombatActor.HUMAN_PLAYER,
                FacilityCombatRules.CombatActor.HUMAN_PLAYER, true, false, false));
        for (FacilityCombatRules.CombatActor player : playerActors()) {
            assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                    player, FacilityCombatRules.CombatActor.NON_PLAYER, false, false, false));
            assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                    FacilityCombatRules.CombatActor.NON_PLAYER, player, false, false, false));
        }
    }

    @Test
    void projectileOwnerUsesTheSameHumanAiPolicy() {
        // DamageSource attribution resolves a projectile owner before entering this policy.
        assertTrue(FacilityCombatRules.blocksEmployeeFriendlyFire(
                FacilityCombatRules.CombatActor.AI_EMPLOYEE,
                FacilityCombatRules.CombatActor.HUMAN_PLAYER, false, false, false));
        assertFalse(FacilityCombatRules.blocksEmployeeFriendlyFire(
                FacilityCombatRules.CombatActor.HUMAN_PLAYER,
                FacilityCombatRules.CombatActor.AI_EMPLOYEE, false, true, false));
    }

    private static FacilityCombatRules.CombatActor[] playerActors() {
        return new FacilityCombatRules.CombatActor[]{
                FacilityCombatRules.CombatActor.HUMAN_PLAYER,
                FacilityCombatRules.CombatActor.AI_EMPLOYEE
        };
    }
}
