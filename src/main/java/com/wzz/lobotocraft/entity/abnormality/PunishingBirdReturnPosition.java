package com.wzz.lobotocraft.entity.abnormality;

import net.minecraft.core.BlockPos;

final class PunishingBirdReturnPosition {
    private PunishingBirdReturnPosition() {
    }

    static BlockPos aboveNest(BlockPos nest) {
        return nest == null ? null : nest.above();
    }

    static boolean madeProgress(double currentDistanceSqr, double bestDistanceSqr) {
        return currentDistanceSqr + 0.01D < bestDistanceSqr;
    }
}
