package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;

final class ChesedRainSoundPolicy {
    private ChesedRainSoundPolicy() {
    }

    static boolean shouldPlay(boolean active, CoreSuppressionType type) {
        return active && type == CoreSuppressionType.CHESED;
    }
}
