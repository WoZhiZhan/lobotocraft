package com.wzz.lobotocraft.event.listener;

/**
 * Pure routing rule for player left-click attacks.  Weapon-specific conditions such as
 * unlock state, attack charge, game mode and target type affect the E.G.O implementation,
 * but never hand control back to vanilla combat.
 */
public final class EgoAttackOwnershipPolicy {
    private EgoAttackOwnershipPolicy() {
    }

    public static boolean shouldRunCustomAttack(boolean eventAlreadyCanceled, boolean holdingBaseEgoWeapon) {
        return holdingBaseEgoWeapon && !eventAlreadyCanceled;
    }

    /**
     * A BaseEgoWeapon owns the attack even when its hook declines to deal damage.
     * Requirements, cooldowns and invalid targets must never hand control back to
     * vanilla combat.
     */
    public static boolean shouldUseVanillaFallback(boolean itemHookHandled, boolean holdingBaseEgoWeapon) {
        return !holdingBaseEgoWeapon && !itemHookHandled;
    }
}
