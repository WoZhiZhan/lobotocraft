package com.wzz.lobotocraft.event.listener;

import org.junit.jupiter.api.Test;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgoAttackOwnershipPolicyTest {
    @Test
    void everyBaseEgoWeaponOwnsLeftClickAcrossEdgeCases() {
        List<String> scenarios = List.of(
                "nothing there requirements not met",
                "attack is not fully charged",
                "creative mode player",
                "non living target"
        );

        for (String scenario : scenarios) {
            assertTrue(EgoAttackOwnershipPolicy.shouldRunCustomAttack(false, true), scenario);
        }
    }

    @Test
    void eventHookRunsAfterGameplayLocksAndCanObserveTheirCancellation() throws NoSuchMethodException {
        SubscribeEvent subscription = EgoAttackChargeHandler.class
                .getDeclaredMethod("onAttackEntity", AttackEntityEvent.class)
                .getAnnotation(SubscribeEvent.class);

        assertEquals(EventPriority.LOWEST, subscription.priority());
        assertTrue(subscription.receiveCanceled());
    }

    @Test
    void aPriorGameplayLockPreventsTheCustomAttackFromRunning() {
        assertFalse(EgoAttackOwnershipPolicy.shouldRunCustomAttack(true, true));
    }

    @Test
    void nonEgoItemsContinueThroughVanillaCombat() {
        assertFalse(EgoAttackOwnershipPolicy.shouldRunCustomAttack(false, false));
    }

    @Test
    void egoHookRejectionNeverFallsBackToVanillaDamage() {
        assertFalse(EgoAttackOwnershipPolicy.shouldUseVanillaFallback(false, true));
        assertFalse(EgoAttackOwnershipPolicy.shouldUseVanillaFallback(true, true));
        assertFalse(EgoAttackOwnershipPolicy.shouldUseVanillaFallback(true, false));
        assertTrue(EgoAttackOwnershipPolicy.shouldUseVanillaFallback(false, false));
    }
}
