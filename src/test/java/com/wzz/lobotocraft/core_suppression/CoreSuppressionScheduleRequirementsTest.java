package com.wzz.lobotocraft.core_suppression;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoreSuppressionScheduleRequirementsTest {
    @Test
    void upperLayerUsesOriginalEnergyAndMeltdownSixRequirement() {
        List<CoreSuppressionType> upper = List.of(
                CoreSuppressionType.MALKUTH,
                CoreSuppressionType.YESOD,
                CoreSuppressionType.HOD,
                CoreSuppressionType.NETZACH);

        upper.forEach(type -> assertAll(type.name(),
                () -> assertTrue(type.isImplemented()),
                () -> assertTrue(type.isEnergyQuotaRequired()),
                () -> assertEquals(6, type.getRequiredMeltdownLevel()),
                () -> assertEquals(0, type.getRequiredBossPhases()),
                () -> assertTrue(type.isFacilityObjectiveMet(600, 600, 6)),
                () -> assertFalse(type.isFacilityObjectiveMet(599, 600, 6)),
                () -> assertFalse(type.isFacilityObjectiveMet(600, 600, 5))));
    }

    @Test
    void middleAndLowerLayerRulesRemainDisabledPlaceholders() {
        assertPlaceholder(CoreSuppressionType.TIPHERETH, false, 10, 0);
        assertTrue(CoreSuppressionType.CHESED.isImplemented());
        assertTrue(CoreSuppressionType.CHESED.isEnergyQuotaRequired());
        assertEquals(8, CoreSuppressionType.CHESED.getRequiredMeltdownLevel());
        assertEquals(0, CoreSuppressionType.CHESED.getRequiredBossPhases());
        assertTrue(CoreSuppressionType.CHESED.isFacilityObjectiveMet(800, 800, 8));
        assertTrue(CoreSuppressionType.GEBURAH.isImplemented());
        assertFalse(CoreSuppressionType.GEBURAH.isEnergyQuotaRequired());
        assertEquals(0, CoreSuppressionType.GEBURAH.getRequiredMeltdownLevel());
        assertEquals(2, CoreSuppressionType.GEBURAH.getRequiredBossPhases());
        assertFalse(CoreSuppressionType.GEBURAH.isFacilityObjectiveMet(10_000, 1, 10));
        assertPlaceholder(CoreSuppressionType.BINAH, false, 0, 3);
        assertPlaceholder(CoreSuppressionType.HOKMA, true, 10, 0);
    }

    private static void assertPlaceholder(CoreSuppressionType type, boolean energyRequired,
                                          int meltdownLevel, int bossPhases) {
        assertAll(type.name(),
                () -> assertFalse(type.isImplemented()),
                () -> assertEquals(energyRequired, type.isEnergyQuotaRequired()),
                () -> assertEquals(meltdownLevel, type.getRequiredMeltdownLevel()),
                () -> assertEquals(bossPhases, type.getRequiredBossPhases()),
                () -> assertFalse(type.isFacilityObjectiveMet(10_000, 1, 10)));
    }
}
