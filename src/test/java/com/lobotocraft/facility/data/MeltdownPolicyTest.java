package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeltdownPolicyTest {
    @Test
    void ordinaryDayStartsMeltdownWhenTheStageHasNoOrdeal() {
        assertTrue(MeltdownPolicy.canActivate(false, false));
        assertEquals(2, MeltdownPolicy.stageNotificationType(false, false));
        assertFalse(MeltdownPolicy.canActivate(false, true));
        assertEquals(1, MeltdownPolicy.stageNotificationType(false, true));
    }

    @Test
    void normalMeltdownsStartOnDaySix() {
        assertFalse(MeltdownPolicy.normalMeltdownEnabled(1));
        assertFalse(MeltdownPolicy.normalMeltdownEnabled(5));
        assertTrue(MeltdownPolicy.normalMeltdownEnabled(6));
        assertEquals(4, MeltdownPolicy.normalMeltdownCapacity(2));
        assertEquals(2, MeltdownPolicy.normalMeltdownCapacity(0));
    }

    @Test
    void suppressionStartsMeltdownOnlyWhenStageIsNotAnOrdeal() {
        assertTrue(MeltdownPolicy.canActivate(true, false));
        assertEquals(2, MeltdownPolicy.stageNotificationType(true, false));
        assertFalse(MeltdownPolicy.canActivate(true, true));
        assertEquals(1, MeltdownPolicy.stageNotificationType(true, true));
    }

    @Test
    void interruptedWorkCannotResolveOrAdvanceFacilityProgress() {
        assertTrue(MeltdownPolicy.countsForFacilityProgress(false));
        assertFalse(MeltdownPolicy.countsForFacilityProgress(true));
    }

    @Test
    void workOnAnActiveMeltdownDoesNotAdvanceTheFacilityGauge() {
        assertTrue(MeltdownPolicy.advancesGaugeForWork(false));
        assertFalse(MeltdownPolicy.advancesGaugeForWork(true));
    }

    @Test
    void aNewBatchIsAnnouncedEvenWhileEarlierMeltdownsRemainActive() {
        assertTrue(MeltdownPolicy.shouldAnnounceActivation(3, 1));
        assertFalse(MeltdownPolicy.shouldAnnounceActivation(3, 0));
    }

    @Test
    void workCapacityRatherThanToolIdentityControlsOrdinaryEligibility() {
        assertTrue(MeltdownPolicy.hasNormalWorkCapacity(20, false));
        assertFalse(MeltdownPolicy.hasNormalWorkCapacity(0, false));
        assertFalse(MeltdownPolicy.hasNormalWorkCapacity(20, true));
    }

    @Test
    void skadiAndWeCanChangeAnythingCodesAreProtected() {
        assertTrue(MeltdownPolicy.isProtectedContainmentCode("S-03-07"));
        assertTrue(MeltdownPolicy.isProtectedContainmentCode("t-09-85"));
        assertTrue(MeltdownPolicy.isProtectedContainmentCode(" T-09-85 "));
        assertFalse(MeltdownPolicy.isProtectedContainmentCode("O-03-03"));
        assertFalse(MeltdownPolicy.isProtectedContainmentCode(null));
    }

    @Test
    void entityTypeOrPersistedRoomBindingCanProtectTarget() {
        assertTrue(MeltdownPolicy.isProtectedTarget("", "", true));
        assertTrue(MeltdownPolicy.isProtectedTarget("O-03-03", "S-03-07", false));
        assertTrue(MeltdownPolicy.isProtectedTarget("T-09-85", "", false));
        assertFalse(MeltdownPolicy.isProtectedTarget("O-03-03", "", false));
    }

    @Test
    void apocalypseBirdDirectMeltdownRejectsSkadiAndWeCanChangeAnything() {
        assertFalse(MeltdownPolicy.canReceiveDirectMeltdown("S-03-07", "", false));
        assertFalse(MeltdownPolicy.canReceiveDirectMeltdown("O-03-03", "T-09-85", false));
        assertTrue(MeltdownPolicy.canReceiveDirectMeltdown("O-03-03", "", false));
    }

    @Test
    void onlySuppressionBoundCausesAreRemovedWhenSuppressionEnds() {
        assertFalse(MeltdownCause.QLIPHOTH_OVERLOAD.requiresCoreSuppression());
        assertTrue(MeltdownCause.LEGACY_OR_UNKNOWN.requiresCoreSuppression());
        assertFalse(MeltdownCause.APOCALYPSE_BIRD_EGG.requiresCoreSuppression());
    }

    @Test
    void suppressionEndRemovesOnlySuppressionBoundInstances() {
        FacilityDayData data = new FacilityDayData();
        UUID ordinaryTarget = UUID.randomUUID();
        UUID legacyTarget = UUID.randomUUID();
        UUID birdTarget = UUID.randomUUID();
        MeltdownLifecycle.start(data, ordinaryTarget, 10L, 2, "O-01",
            MeltdownCause.QLIPHOTH_OVERLOAD);
        MeltdownLifecycle.start(data, legacyTarget, 10L, 2, "O-00",
            MeltdownCause.LEGACY_OR_UNKNOWN);
        MeltdownLifecycle.start(data, birdTarget, 10L, 2, "O-02",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        assertEquals(1, MeltdownLifecycle.endSuppressionBound(data));
        assertEquals(2, data.getActiveMeltdowns().size());
        assertEquals(MeltdownCause.QLIPHOTH_OVERLOAD,
            data.getActiveMeltdowns().get(ordinaryTarget).cause());
        assertEquals(MeltdownCause.APOCALYPSE_BIRD_EGG,
            data.getActiveMeltdowns().get(birdTarget).cause());
    }

    @Test
    void lifecycleStartUsesCauseDefinitionDuration() {
        FacilityDayData data = new FacilityDayData();
        UUID target = UUID.randomUUID();

        MeltdownLifecycle.start(data, target, 100L, 3, "O-03-03",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        FacilityDayData.ActiveMeltdown instance = data.getActiveMeltdowns().get(target);
        assertEquals(100L + MeltdownLifecycle.DEFAULT_DURATION_TICKS, instance.deadline());
        assertEquals(MeltdownCause.APOCALYPSE_BIRD_EGG, instance.cause());
    }

    @Test
    void definitionsOwnTargetCountAndActiveWorkPolicies() {
        MeltdownDefinition overload = MeltdownLifecycle.definition(
            MeltdownCause.QLIPHOTH_OVERLOAD);
        MeltdownDefinition birdEgg = MeltdownLifecycle.definition(
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        assertEquals(4, overload.requestedTargetCount(11, 8, 3, 99));
        assertEquals(2, overload.requestedTargetCount(11, 2, 3, 99));
        assertEquals(4, birdEgg.requestedTargetCount(11, 8, 3, 4));
        assertFalse(overload.excludeWorkingTargets());
        assertFalse(birdEgg.excludeWorkingTargets());
    }
}
