package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahHitFeedbackPolicyTest {
    @Test
    void actualHealthLossControlsReactionStrength() {
        float light = GeburahHitFeedbackPolicy.severity(4.0F, 100.0F, 0.5F);
        float heavy = GeburahHitFeedbackPolicy.severity(35.0F, 100.0F, 0.5F);

        assertTrue(heavy > light);
        assertTrue(light > 0.0F);
    }

    @Test
    void invalidOrZeroDamageProducesNoReaction() {
        assertEquals(0.0F, GeburahHitFeedbackPolicy.severity(0.0F, 100.0F, 1.0F));
        assertEquals(0.0F, GeburahHitFeedbackPolicy.severity(5.0F, 0.0F, 1.0F));
    }
}
