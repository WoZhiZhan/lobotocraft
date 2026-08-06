package com.wzz.lobotocraft.item.ego.red_shoes;

public final class RedShoesDamagePolicy {
    private RedShoesDamagePolicy() {
    }

    public static float justiceDamageBonus(int justiceLevel) {
        return Math.max(0, justiceLevel);
    }
}
