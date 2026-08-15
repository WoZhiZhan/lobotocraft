package com.wzz.lobotocraft.entity.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QliphothCounterPolicyTest {
    @Test
    void onlyTheFirstTransitionToZeroTriggersMeltdown() {
        QliphothCounterPolicy.Result first = QliphothCounterPolicy.decrease(1, 1);
        assertEquals(0, first.value());
        assertTrue(first.firstMeltdown());

        QliphothCounterPolicy.Result repeated = QliphothCounterPolicy.decrease(0, 1);
        assertEquals(0, repeated.value());
        assertFalse(repeated.firstMeltdown());
    }
}
