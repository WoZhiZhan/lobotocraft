package com.wzz.lobotocraft.employee;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationJumpPolicyTest {
    @Test
    void thinFloorDressingsUseVanillaAutoStep() {
        assertFalse(NavigationExecutionPolicy.needsJump(117.0625D, 117.1875D, 0.6D, true));
        assertFalse(NavigationExecutionPolicy.needsJump(117.0D, 117.5D, 0.6D, true));
    }

    @Test
    void fullHeightLandingRequiresOneGroundedJump() {
        assertTrue(NavigationExecutionPolicy.needsJump(235.0D, 236.0D, 0.6D, true));
        assertFalse(NavigationExecutionPolicy.needsJump(235.4D, 236.0D, 0.6D, false));
    }

    @Test
    void flatAndDescendingPathsNeverJump() {
        assertFalse(NavigationExecutionPolicy.needsJump(117.0D, 117.0D, 0.6D, true));
        assertFalse(NavigationExecutionPolicy.needsJump(118.0D, 117.0D, 0.6D, true));
    }
}
