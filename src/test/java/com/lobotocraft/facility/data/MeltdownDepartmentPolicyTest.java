package com.lobotocraft.facility.data;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeltdownDepartmentPolicyTest {
    @Test
    void mapsSefiraSuppressionsToFacilityDepartmentIds() {
        assertEquals("control", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.MALKUTH));
        assertEquals("information", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.YESOD));
        assertEquals("training", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.HOD));
        assertEquals("safety", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.NETZACH));
        assertEquals("central", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.TIPHERETH));
        assertEquals("welfare", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.CHESED));
        assertEquals("disciplinary", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.GEBURAH));
        assertEquals("extraction", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.BINAH));
        assertEquals("record", MeltdownDepartmentPolicy.departmentId(CoreSuppressionType.HOKMA));
    }

    @Test
    void completedDepartmentIsProtectedOnOrdinaryDays() {
        int completed = CoreSuppressionType.MALKUTH.getCompletionBit()
            | CoreSuppressionType.YESOD.getCompletionBit();

        assertTrue(MeltdownDepartmentPolicy.isProtected("control", null, completed, true));
        assertTrue(MeltdownDepartmentPolicy.isProtected("information", null, completed, true));
        assertFalse(MeltdownDepartmentPolicy.isProtected("training", null, completed, true));
    }

    @Test
    void currentSuppressionDepartmentIsAlwaysProtected() {
        assertTrue(MeltdownDepartmentPolicy.isProtected("disciplinary",
            CoreSuppressionType.GEBURAH, 0, true));
        assertTrue(MeltdownDepartmentPolicy.isProtected("disciplinary",
            CoreSuppressionType.GEBURAH, 0, false));
        assertFalse(MeltdownDepartmentPolicy.hasCompletedRewardImmunity("disciplinary",
            CoreSuppressionType.GEBURAH, 0, true));
    }

    @Test
    void middleAndLowerBossExceptionsIgnoreEarlierDepartmentRewards() {
        int completed = CoreSuppressionType.MALKUTH.getCompletionBit();

        assertFalse(MeltdownDepartmentPolicy.isProtected("control",
            CoreSuppressionType.TIPHERETH, completed, true));
        assertFalse(MeltdownDepartmentPolicy.isProtected("control",
            CoreSuppressionType.BINAH, completed, true));
        assertFalse(MeltdownDepartmentPolicy.isProtected("control",
            CoreSuppressionType.HOKMA, completed, true));
        assertTrue(MeltdownDepartmentPolicy.isProtected("central",
            CoreSuppressionType.TIPHERETH, completed, true));
    }

    @Test
    void independentSpecialMeltdownsIgnoreCompletedDepartmentRewards() {
        int completed = CoreSuppressionType.MALKUTH.getCompletionBit();

        assertFalse(MeltdownDepartmentPolicy.isProtected("control", null, completed, false));
    }
}
