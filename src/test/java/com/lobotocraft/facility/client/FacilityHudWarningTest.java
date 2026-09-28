package com.lobotocraft.facility.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FacilityHudWarningTest {
    @Test
    void meltdownFailureDoesNotRenderTheCenteredWarning() {
        assertFalse(FacilityHud.shouldRenderWarning(3));
        assertFalse(FacilityHud.shouldRenderWarning(2));
        assertTrue(FacilityHud.shouldRenderWarning(0));
        assertTrue(FacilityHud.shouldRenderWarning(1));
    }
}
