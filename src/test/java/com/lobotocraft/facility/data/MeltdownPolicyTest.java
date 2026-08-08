package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
