package com.wzz.lobotocraft.item.ego.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EgoDamageRecoveryPolicyTest {
    @Test
    void recoveryUsesOnlyHealthActuallyRemoved() {
        assertEquals(2.0F, EgoDamageRecoveryPolicy.actualHealthLoss(20.0F, 18.0F));
        assertEquals(0.0F, EgoDamageRecoveryPolicy.actualHealthLoss(20.0F, 20.0F));
        assertEquals(3.0F, EgoDamageRecoveryPolicy.actualHealthLoss(3.0F, 0.0F));
    }

    @Test
    void finalDamageIsCappedByRemainingHealth() {
        assertEquals(2.0F, EgoDamageRecoveryPolicy.finalDamageToHealth(20.0F, 2.0F));
        assertEquals(3.0F, EgoDamageRecoveryPolicy.finalDamageToHealth(3.0F, 50.0F));
        assertEquals(0.0F, EgoDamageRecoveryPolicy.finalDamageToHealth(20.0F, 0.0F));
    }
}
