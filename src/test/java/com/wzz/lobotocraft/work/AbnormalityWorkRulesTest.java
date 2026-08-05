package com.wzz.lobotocraft.work;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnormalityWorkRulesTest {
    @Test
    void registrationRequiresTheExactBoundInstance() {
        UUID target = UUID.randomUUID();
        assertTrue(AbnormalityWorkRules.isRegisteredBinding(
                target, "O-03-03", target, "O-03-03"));
        assertFalse(AbnormalityWorkRules.isRegisteredBinding(
                UUID.randomUUID(), "O-03-03", target, "O-03-03"));
        assertFalse(AbnormalityWorkRules.isRegisteredBinding(
                target, "O-03-03", target, "T-01-54"));
        assertFalse(AbnormalityWorkRules.isRegisteredBinding(
                null, "O-03-03", target, "O-03-03"));
    }

    @Test
    void onlyAiWorkersConflictWithAnotherAiReservation() {
        assertTrue(AbnormalityWorkRules.hasAiReservationConflict(true, true));
        assertFalse(AbnormalityWorkRules.hasAiReservationConflict(false, true));
        assertFalse(AbnormalityWorkRules.hasAiReservationConflict(true, false));
    }

    @Test
    void pendingNavigationCanBeReplacedButFormalWorkCannot() {
        assertTrue(AbnormalityWorkRules.canReplacePendingAiOrder(false));
        assertFalse(AbnormalityWorkRules.canReplacePendingAiOrder(true));
    }
}
