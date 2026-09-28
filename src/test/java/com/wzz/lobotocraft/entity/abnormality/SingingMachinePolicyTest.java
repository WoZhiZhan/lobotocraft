package com.wzz.lobotocraft.entity.abnormality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingingMachinePolicyTest {
    @Test
    void highFortitudeOrLowTemperanceCausesConsumption() {
        assertTrue(SingingMachinePolicy.consumesWorkerAfterWork(4, 5));
        assertTrue(SingingMachinePolicy.consumesWorkerAfterWork(1, 2));
        assertFalse(SingingMachinePolicy.consumesWorkerAfterWork(3, 3));
    }

    @Test
    void controlledDamageUsesOriginalFortitudeLevelFormula() {
        assertEquals(19.0F, SingingMachinePolicy.controlledAttackDamage(1));
        assertEquals(28.0F, SingingMachinePolicy.controlledAttackDamage(4));
        assertEquals(31.0F, SingingMachinePolicy.controlledAttackDamage(5));
    }
}
