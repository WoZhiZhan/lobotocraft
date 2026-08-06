package com.wzz.lobotocraft.item.ego.snowqueen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowQueenWeaponTest {
    @Test
    void kissSpecialRequiresFullAttackCharge() {
        assertFalse(SnowQueenAttackPolicy.canApplyKissSpecial(0.999F));
        assertTrue(SnowQueenAttackPolicy.canApplyKissSpecial(1.0F));
    }
}
