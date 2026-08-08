package com.wzz.lobotocraft.entity.base;

import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmergencyAlarmPolicyTest {
    @Test
    void usesOriginalRiskScoresAndDepartmentScaling() {
        assertEquals(5.0F, EmergencyAlarmPolicy.riskScore(RiskLevel.ZAYIN));
        assertEquals(20.0F, EmergencyAlarmPolicy.riskScore(RiskLevel.TETH));
        assertEquals(40.0F, EmergencyAlarmPolicy.riskScore(RiskLevel.HE));
        assertEquals(60.0F, EmergencyAlarmPolicy.riskScore(RiskLevel.WAW));
        assertEquals(75.0F, EmergencyAlarmPolicy.riskScore(RiskLevel.ALEPH));
        assertEquals(15.0F, EmergencyAlarmPolicy.eventScore(RiskLevel.WAW, 4));
        assertEquals(75.0F, EmergencyAlarmPolicy.eventScore(RiskLevel.ALEPH, 0));
        assertEquals(RiskLevel.TETH, EmergencyAlarmPolicy.ordealRisk(OrdealTier.DAWN));
        assertEquals(RiskLevel.HE, EmergencyAlarmPolicy.ordealRisk(OrdealTier.NOON));
        assertEquals(RiskLevel.WAW, EmergencyAlarmPolicy.ordealRisk(OrdealTier.DUSK));
        assertEquals(RiskLevel.ALEPH, EmergencyAlarmPolicy.ordealRisk(OrdealTier.MIDNIGHT));
    }

    @Test
    void usesStrictOriginalEmergencyThresholds() {
        assertEquals(-1, EmergencyAlarmPolicy.alertLevel(10.0F));
        assertEquals(0, EmergencyAlarmPolicy.alertLevel(10.01F));
        assertEquals(0, EmergencyAlarmPolicy.alertLevel(50.0F));
        assertEquals(1, EmergencyAlarmPolicy.alertLevel(50.01F));
        assertEquals(1, EmergencyAlarmPolicy.alertLevel(80.0F));
        assertEquals(2, EmergencyAlarmPolicy.alertLevel(80.01F));
    }

    @Test
    void clampsCheatInputsAndRecoversByAliveRatio() {
        assertEquals(100.0F, EmergencyAlarmPolicy.add(90.0F, 75.0F));
        assertEquals(100.0F, EmergencyAlarmPolicy.add(Float.POSITIVE_INFINITY, 1.0F));
        assertEquals(49.5F, EmergencyAlarmPolicy.recover(50.0F, 0.5F));
        assertEquals(0.0F, EmergencyAlarmPolicy.recover(0.25F, 1.0F));
        assertEquals(50.0F, EmergencyAlarmPolicy.recover(50.0F, -20.0F));
    }
}
