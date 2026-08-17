package com.wzz.lobotocraft.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.lobotocraft.facility.data.FacilityDayData;
import com.lobotocraft.facility.data.MeltdownPolicy;
import com.lobotocraft.facility.data.OrdealContentPolicy;
import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.event.listener.EgoAttackOwnershipPolicy;

/**
 * A deterministic server-side walk through of gameplay branches that can be
 * exercised without booting a full Minecraft world.
 */
class GameplayFlowSimulationTest {
    @Test
    void workTicksAdvanceOrdealStagesAndKeepNormalDaysResolvable() {
        FacilityDayData facility = new FacilityDayData();

        for (int day = 1; day <= 10; day++) {
            facility.beginDay(day);
            facility.prepareSchedule(0x42L);

            for (int stage = 1; stage <= 3; stage++) {
                int threshold = facility.getMeltdownThreshold();
                for (int tick = 0; tick < threshold; tick++) {
                    assertEquals(tick + 1 == threshold, facility.advanceGauge());
                }

                OrdealTier tier = facility.getScheduledOrdeal(stage);
                boolean ordealTriggered = tier == OrdealTier.DAWN || tier == OrdealTier.NOON;
                assertEquals(!ordealTriggered,
                        MeltdownPolicy.canActivate(false, ordealTriggered));
                if (tier == OrdealTier.DUSK || tier == OrdealTier.MIDNIGHT) {
                    assertTrue(OrdealContentPolicy.isUnfinished(tier));
                }
                if (tier == OrdealTier.DAWN) facility.markDawnTriggered();
                if (tier == OrdealTier.NOON) facility.markNoonTriggered();

                facility.finishMeltdownStage();
                assertEquals(stage + 1, facility.getMeltdownLevel());
                assertEquals(0, facility.getMeltdownGauge());
            }
        }
    }

    @Test
    void suppressionEgoAndEmployeeRoutesRejectCrossedOrForgedActions() {
        UUID abnormality = UUID.randomUUID();
        String facility = "lobotocraft:facility";

        assertTrue(MeltdownPolicy.canActivate(true, false));
        assertFalse(MeltdownPolicy.canActivate(true, true));
        assertTrue(WorkStartAuthorizations.matches(abnormality, facility, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK,
                abnormality, facility, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK));
        assertFalse(WorkStartAuthorizations.matches(abnormality, facility, 119,
                WorkStartAuthorizations.Interaction.NORMAL_WORK,
                abnormality, facility, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK));
        assertFalse(WorkStartAuthorizations.matches(abnormality, facility, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK,
                abnormality, facility, 120,
                WorkStartAuthorizations.Interaction.TOOL_USE));

        // A rejected E.G.O hook must never leak vanilla damage back into the target.
        assertTrue(EgoAttackOwnershipPolicy.shouldRunCustomAttack(false, true));
        assertFalse(EgoAttackOwnershipPolicy.shouldUseVanillaFallback(false, true));
    }
}
