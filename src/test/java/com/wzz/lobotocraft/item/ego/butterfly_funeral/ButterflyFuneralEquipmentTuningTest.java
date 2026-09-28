package com.wzz.lobotocraft.item.ego.butterfly_funeral;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ButterflyFuneralEquipmentTuningTest {
    @Test
    void solemnLamentWeaponIsWaw() {
        assertEquals(RiskLevel.WAW, ButterflyFuneralEquipmentTuning.weaponRiskLevel());
    }
}
