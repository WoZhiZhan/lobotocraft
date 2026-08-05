package com.wzz.lobotocraft.employee;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeOrderTest {
    @Test
    void moveOrderAdvancesThroughDistinctFallbackTargets() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = new BlockPos(4, 5, 6);
        EmployeeOrder order = EmployeeOrder.move(List.of(first, first, second));

        assertEquals(first, order.targetPos);
        assertTrue(order.hasMoveTargetAlternative());
        assertTrue(order.advanceMoveTarget());
        assertEquals(second, order.targetPos);
        assertFalse(order.hasMoveTargetAlternative());
        assertFalse(order.advanceMoveTarget());
        assertEquals(second, order.targetPos);
    }
}
