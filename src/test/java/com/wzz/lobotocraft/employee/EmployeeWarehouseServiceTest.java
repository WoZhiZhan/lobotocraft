package com.wzz.lobotocraft.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeWarehouseServiceTest {
    @Test
    void bulkDepositLeavesGiftsEquippedButDeathRecoveryCollectsThem() {
        assertFalse(EmployeeWarehouseService.shouldTransferCurios(false));
        assertTrue(EmployeeWarehouseService.shouldTransferCurios(true));
    }
}
