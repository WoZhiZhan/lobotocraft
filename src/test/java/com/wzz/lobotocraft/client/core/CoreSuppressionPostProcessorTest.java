package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoreSuppressionPostProcessorTest {
    @Test
    void retriesExpiredFailureAndRecoversWhenOwnedChainWasReplaced() {
        assertFalse(CoreSuppressionPostProcessor.shouldAttemptLoad(
                true, true, false, 20L, 20L));
        assertFalse(CoreSuppressionPostProcessor.shouldAttemptLoad(
                false, false, true, 39L, 40L));
        assertTrue(CoreSuppressionPostProcessor.shouldAttemptLoad(
                false, false, true, 40L, 40L));
        assertTrue(CoreSuppressionPostProcessor.shouldAttemptLoad(
                true, false, false, 20L, 20L));
    }

    @Test
    void chesedRunsBeforeGuiLikeTheOriginalMainCameraEffect() {
        assertFalse(CoreSuppressionPostProcessor.shouldRenderAfterGui(CoreSuppressionType.CHESED));
        assertTrue(CoreSuppressionPostProcessor.shouldRenderAfterGui(CoreSuppressionType.GEBURAH));
    }
}
