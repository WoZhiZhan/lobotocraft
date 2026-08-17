package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class AbnormalityCombatUtilTest {
    @Test
    void geburahAndExistingHelpersAreAbnormalitySuppressors() {
        assertTrue(AbnormalityCombatUtil.isAbnormalitySuppressorType(EntityCleaner.class));
        assertTrue(AbnormalityCombatUtil.isAbnormalitySuppressorType(EntityGeburah.class));
    }
}
