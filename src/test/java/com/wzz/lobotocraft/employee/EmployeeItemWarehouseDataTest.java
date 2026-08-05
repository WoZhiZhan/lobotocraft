package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.item.ego.wingbeat.WingBeatBaseArmor;
import com.wzz.lobotocraft.item.ego.wingbeat.WingBeatCurio;
import com.wzz.lobotocraft.item.ego.wingbeat.WingBeatWeapon;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeItemWarehouseDataTest {
    @Test
    void pagingKeepsOneWritableBlankPageAtFullBoundaries() {
        assertEquals(1, EmployeeItemWarehouseData.pageCountForSlotCount(-1));
        assertEquals(1, EmployeeItemWarehouseData.pageCountForSlotCount(0));
        assertEquals(1, EmployeeItemWarehouseData.pageCountForSlotCount(53));
        assertEquals(2, EmployeeItemWarehouseData.pageCountForSlotCount(54));
        assertEquals(2, EmployeeItemWarehouseData.pageCountForSlotCount(55));
        assertEquals(2, EmployeeItemWarehouseData.pageCountForSlotCount(107));
        assertEquals(3, EmployeeItemWarehouseData.pageCountForSlotCount(108));
    }

    @Test
    void pagingCannotExceedPersistentStorageLimit() {
        assertEquals(EmployeeItemWarehouseData.MAX_PAGES,
                EmployeeItemWarehouseData.pageCountForSlotCount(Integer.MAX_VALUE));
    }

    @Test
    void giftsCanBeStoredButEgoWeaponsAndArmorCannot() {
        assertTrue(EmployeeItemWarehouseData.isWarehouseEligibleItemType(WingBeatCurio.class));
        assertTrue(EmployeeItemWarehouseData.isWarehouseEligibleItemType(Item.class));
        assertFalse(EmployeeItemWarehouseData.isWarehouseEligibleItemType(WingBeatWeapon.class));
        assertFalse(EmployeeItemWarehouseData.isWarehouseEligibleItemType(WingBeatBaseArmor.class));
        assertFalse(EmployeeItemWarehouseData.isWarehouseEligibleItemType(null));
    }
}
