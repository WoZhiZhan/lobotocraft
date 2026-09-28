package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChesedRainSoundPolicyTest {
    @Test
    void rainLoopsOnlyDuringAnActiveChesedSuppression() {
        assertTrue(ChesedRainSoundPolicy.shouldPlay(true, CoreSuppressionType.CHESED));
        assertFalse(ChesedRainSoundPolicy.shouldPlay(false, CoreSuppressionType.CHESED));
        assertFalse(ChesedRainSoundPolicy.shouldPlay(true, CoreSuppressionType.NETZACH));
        assertFalse(ChesedRainSoundPolicy.shouldPlay(true, null));
    }
}
