package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CoreSuppressionMusicPolicyTest {
    @Test
    void chesedUsesTheSameStageMappingAsOtherUpperLayerSuppressions() {
        assertEquals("escape_alert_bgm_1",
                CoreSuppressionMusicPolicy.trackPath(CoreSuppressionType.CHESED, 0));
        assertEquals("core.chesed_1",
                CoreSuppressionMusicPolicy.trackPath(CoreSuppressionType.CHESED, 1));
        assertEquals("core.chesed_2",
                CoreSuppressionMusicPolicy.trackPath(CoreSuppressionType.CHESED, 2));
    }
}
