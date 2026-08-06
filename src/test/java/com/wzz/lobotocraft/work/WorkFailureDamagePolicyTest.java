package com.wzz.lobotocraft.work;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkFailureDamagePolicyTest {
    @Test
    void abnormalityDamageTypeOverridesEveryWorkType() {
        for (WorkType workType : WorkType.values()) {
            assertEquals("white", WorkFailureDamagePolicy.resolveDamageType("WHITE", workType));
        }
    }

    @Test
    void unknownAbnormalityDamageTypeFallsBackToWorkType() {
        assertEquals("black", WorkFailureDamagePolicy.resolveDamageType("???", WorkType.ATTACHMENT));
    }
}
