package com.wzz.lobotocraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnormalitySpawnHelperPolicyTest {
    @Test
    void normalSpawnsRespectFacilityAccess() {
        assertTrue(AbnormalitySpawnHelper.SpawnPlacementPolicy.FACILITY_RULES
                .requiresOpenFacilityArea());
    }

    @Test
    void debugEggsBypassUnlockStateButStillUseSafePlacementPath() {
        assertFalse(AbnormalitySpawnHelper.SpawnPlacementPolicy.DEBUG_BYPASS
                .requiresOpenFacilityArea());
    }
}
