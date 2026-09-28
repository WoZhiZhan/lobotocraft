package com.wzz.lobotocraft.entity.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EgoWeaponDisplayCatalogTest {
    @Test
    void suppressionOnlyMustOpenPathWeaponHasUnloadedEntityFallback() {
        EgoWeaponDisplayCatalog.WeaponSummary summary =
                EgoWeaponDisplayCatalog.find("S-03-07");
        assertNotNull(summary);
        assertEquals("RED", summary.damageType());
        assertEquals("27-32", summary.attackPower());
        assertEquals("0.6", summary.attackSpeed());
        assertEquals("不变", summary.attackRange());
    }
}
