package com.wzz.lobotocraft.item.debug;

import net.minecraft.world.entity.player.Player;

/** Pure type policy kept separate from Item registry initialization for testing. */
final class RemovalTargetPolicy {
    private RemovalTargetPolicy() {
    }

    static boolean isProtectedPlayerType(Class<?> entityType) {
        return entityType != null && Player.class.isAssignableFrom(entityType);
    }
}
