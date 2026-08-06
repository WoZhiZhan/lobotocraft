package com.wzz.lobotocraft.item.ego.snowqueen;

public final class SnowQueenAttackPolicy {
    private SnowQueenAttackPolicy() {
    }

    public static boolean canApplyKissSpecial(float attackStrength) {
        return attackStrength >= 1.0F;
    }
}
