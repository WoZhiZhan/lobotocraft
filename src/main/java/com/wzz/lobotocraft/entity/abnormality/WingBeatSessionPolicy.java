package com.wzz.lobotocraft.entity.abnormality;

import java.util.UUID;

/** Pure validation for reconnecting a persisted fairy-feast entity to its owner. */
final class WingBeatSessionPolicy {
    private WingBeatSessionPolicy() {
    }

    static boolean canRebind(UUID entitySession, boolean playerActive, UUID playerSession) {
        return entitySession != null && playerActive && entitySession.equals(playerSession);
    }

    static boolean isActive(boolean playerActive, UUID playerSession, long expiresAt, long now) {
        return playerActive && playerSession != null && expiresAt > now;
    }
}
