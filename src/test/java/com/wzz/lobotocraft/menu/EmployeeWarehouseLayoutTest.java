package com.wzz.lobotocraft.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeWarehouseLayoutTest {
    @Test
    void warehouseDoesNotOverlapPlayerInventory() {
        int warehouseBottom = EmployeeWarehouseMenu.WAREHOUSE_SLOT_Y
                + (EmployeeWarehouseMenu.ROWS - 1) * 18 + 18;

        assertTrue(warehouseBottom < EmployeeWarehouseMenu.PLAYER_LABEL_Y);
        assertTrue(EmployeeWarehouseMenu.PLAYER_LABEL_Y
                < EmployeeWarehouseMenu.PLAYER_INVENTORY_Y);
    }

    @Test
    void hotbarFitsInsideScreenHeight() {
        assertTrue(EmployeeWarehouseMenu.PLAYER_HOTBAR_Y + 18 <= 233);
    }
}
