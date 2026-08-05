package com.wzz.lobotocraft.ego;

import net.minecraft.world.entity.EquipmentSlot;

/** Server-authoritative rules for moving locked E.G.O projections. */
public final class EgoProjectionRules {
    private EgoProjectionRules() {
    }

    public static boolean mayPickUp(boolean weaponProjection, boolean ownInventory,
                                    boolean ownerMatches, boolean creative) {
        // Creative mode is intentionally not privileged: granting, cloning or
        // exporting a projection would turn a temporary entitlement into an item.
        return weaponProjection && ownInventory && ownerMatches;
    }

    public static boolean mayClickMove(boolean supportedMove, boolean ownInventorySlot,
                                       boolean clickedOwnerMatches, boolean carriedOwnerMatches,
                                       boolean creative) {
        return supportedMove && ownInventorySlot && clickedOwnerMatches && carriedOwnerMatches;
    }

    public static boolean mayDiscard(boolean projection, boolean creative) {
        return !projection;
    }

    public static boolean mayExistAsItemEntity(boolean projection) {
        return !projection;
    }

    /** Vanilla armor use swaps with the equipped stack, which would export a locked projection. */
    public static boolean mayAutoEquipInto(boolean occupiedByProjection) {
        return !occupiedByProjection;
    }

    /**
     * E.G.O armor owns all three body slots as one loadout. Once any part is
     * equipped, right-click equipment swaps cannot replace chest, legs or feet.
     */
    public static boolean mayAutoEquipInto(boolean occupiedByProjection,
                                           boolean wearingEgoArmor,
                                           EquipmentSlot targetSlot) {
        if (occupiedByProjection) return false;
        if (!wearingEgoArmor) return true;
        return targetSlot != EquipmentSlot.CHEST
                && targetSlot != EquipmentSlot.LEGS
                && targetSlot != EquipmentSlot.FEET;
    }
}
