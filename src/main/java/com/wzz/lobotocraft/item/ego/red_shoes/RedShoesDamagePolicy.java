package com.wzz.lobotocraft.item.ego.red_shoes;

import com.wzz.lobotocraft.capability.IEmployeeStats;

public final class RedShoesDamagePolicy {
    private RedShoesDamagePolicy() {
    }

    public static float temperanceDamageBonus(int temperanceLevel) {
        return Math.max(0, temperanceLevel);
    }

    public static float damageBonus(IEmployeeStats stats) {
        return temperanceDamageBonus(stats.getTemperanceLevel());
    }
}
