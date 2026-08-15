package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeltdownPolicyTest {
    @Test
    void ordinaryDayNeverStartsOrAnnouncesMeltdown() {
        assertFalse(MeltdownPolicy.canActivate(false, false));
        assertEquals(0, MeltdownPolicy.stageNotificationType(false, false));
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
        assertTrue(MeltdownCause.QLIPHOTH_OVERLOAD.requiresCoreSuppression());
        assertTrue(MeltdownCause.LEGACY_OR_UNKNOWN.requiresCoreSuppression());
        assertFalse(MeltdownCause.APOCALYPSE_BIRD_EGG.requiresCoreSuppression());
    }

    @Test
    void suppressionEndRemovesOnlySuppressionBoundInstances() {
        FacilityDayData data = new FacilityDayData();
        UUID suppressionTarget = UUID.randomUUID();
        UUID birdTarget = UUID.randomUUID();
        MeltdownLifecycle.start(data, suppressionTarget, 10L, 2, "O-01",
            MeltdownCause.QLIPHOTH_OVERLOAD);
        MeltdownLifecycle.start(data, birdTarget, 10L, 2, "O-02",
            MeltdownCause.APOCALYPSE_BIRD_EGG);

        assertEquals(1, MeltdownLifecycle.endSuppressionBound(data));
        assertEquals(1, data.getActiveMeltdowns().size());
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

        assertEquals(4, overload.requestedTargetCount(11, 3, 99));
        assertEquals(4, birdEgg.requestedTargetCount(11, 3, 4));
        assertTrue(overload.excludeWorkingTargets());
        assertFalse(birdEgg.excludeWorkingTargets());
    }
}
