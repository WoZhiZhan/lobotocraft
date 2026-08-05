package com.wzz.lobotocraft.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeInventoryLayoutTest {
    @Test
    void sixtyFourCurioSlotsFitGuiScaleFourViewport() {
        int slots = 64;
        int columns = EmployeeInventoryMenu.calculateCurioColumns(
                List.of(new EmployeeInventoryMenu.CurioGroup("test", slots)));
        int rows = (slots + columns - 1) / columns;
        int imageWidth = columns * 18 + 16;
        int imageHeight = EmployeeInventoryMenu.CURIO_START_Y + rows * 18 + 17 + 84;

        assertEquals(22, columns);
        assertTrue(imageWidth <= 480);
        assertTrue(imageHeight <= 270);
    }

    @Test
    void emptyAndTypicalLayoutsKeepAtLeastVanillaInventoryWidth() {
        assertEquals(9, EmployeeInventoryMenu.calculateCurioColumns(List.of()));
        assertEquals(10, EmployeeInventoryMenu.calculateCurioColumns(
                List.of(new EmployeeInventoryMenu.CurioGroup("test", 10))));
    }
}
