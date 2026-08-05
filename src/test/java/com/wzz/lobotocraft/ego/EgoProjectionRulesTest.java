package com.wzz.lobotocraft.ego;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgoProjectionRulesTest {
    @Test
    void blocksVanillaArmorSwapIntoProjectionSlot() {
        assertFalse(EgoProjectionRules.mayAutoEquipInto(true));
        assertTrue(EgoProjectionRules.mayAutoEquipInto(false));
    }

    @Test
    void equippedEgoArmorBlocksAllBodySlotRightClickEquipment() {
        assertFalse(EgoProjectionRules.mayAutoEquipInto(
                false, true, EquipmentSlot.CHEST));
        assertFalse(EgoProjectionRules.mayAutoEquipInto(
                false, true, EquipmentSlot.LEGS));
        assertFalse(EgoProjectionRules.mayAutoEquipInto(
                false, true, EquipmentSlot.FEET));
        assertTrue(EgoProjectionRules.mayAutoEquipInto(
                false, true, EquipmentSlot.HEAD));
    }

    @Test
    void bodyEquipmentRemainsUsableWithoutEgoArmor() {
        assertTrue(EgoProjectionRules.mayAutoEquipInto(
                false, false, EquipmentSlot.CHEST));
        assertTrue(EgoProjectionRules.mayAutoEquipInto(
                false, false, EquipmentSlot.LEGS));
        assertTrue(EgoProjectionRules.mayAutoEquipInto(
                false, false, EquipmentSlot.FEET));
    }

    @Test
    void elytraUsesTheBlockedChestEquipmentPath() {
        assertTrue(Equipable.class.isAssignableFrom(ElytraItem.class));
        assertFalse(EgoProjectionRules.mayAutoEquipInto(
                false, true, EquipmentSlot.CHEST));
    }

    @Test
    void weaponCanMoveOnlyInsideOwnersInventory() {
        assertTrue(EgoProjectionRules.mayPickUp(true, true, true, false));
        assertFalse(EgoProjectionRules.mayPickUp(false, true, true, false));
        assertFalse(EgoProjectionRules.mayPickUp(true, false, true, false));
        assertFalse(EgoProjectionRules.mayPickUp(true, true, false, false));
    }

    @Test
    void creativeModeCannotCloneExportOrStealProjection() {
        assertTrue(EgoProjectionRules.mayPickUp(true, true, true, true));
        assertFalse(EgoProjectionRules.mayPickUp(true, false, true, true));
        assertFalse(EgoProjectionRules.mayPickUp(true, true, false, true));
        assertFalse(EgoProjectionRules.mayClickMove(false, true, true, true, true));
        assertFalse(EgoProjectionRules.mayClickMove(true, false, true, true, true));
        assertFalse(EgoProjectionRules.mayDiscard(true, true));
    }

    @Test
    void supportedInventoryMovesStillRejectExternalSlotsAndWrongOwners() {
        assertTrue(EgoProjectionRules.mayClickMove(true, true, true, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(false, true, true, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(true, false, true, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(true, true, false, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(true, true, true, false, false));
        assertFalse(EgoProjectionRules.mayDiscard(true, false));
        assertFalse(EgoProjectionRules.mayExistAsItemEntity(true));
        assertTrue(EgoProjectionRules.mayDiscard(false, false));
        assertTrue(EgoProjectionRules.mayExistAsItemEntity(false));
    }

    @Test
    void quickMoveAndDragAreAllowedOnlyAfterTheCallerConfirmsAnOwnInventorySlot() {
        assertTrue(EgoProjectionRules.mayClickMove(true, true, true, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(true, false, true, true, false));
        assertFalse(EgoProjectionRules.mayClickMove(true, true, false, true, false));
    }
}
