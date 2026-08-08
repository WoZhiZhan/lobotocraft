package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

final class CorruptionStackPolicy {
    private CorruptionStackPolicy() {
    }

    static boolean shouldApply(float attackMultiplier) {
        return attackMultiplier >= 1.0F;
    }
}
