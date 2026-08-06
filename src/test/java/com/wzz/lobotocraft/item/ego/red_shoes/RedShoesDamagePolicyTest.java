package com.wzz.lobotocraft.item.ego.red_shoes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedShoesDamagePolicyTest {
    @Test
    void damageBonusUsesJusticeLevelDirectly() {
        assertEquals(1.0F, RedShoesDamagePolicy.justiceDamageBonus(1));
        assertEquals(5.0F, RedShoesDamagePolicy.justiceDamageBonus(5));
    }

    @Test
    void negativeLevelsCannotReduceBaseDamage() {
        assertEquals(0.0F, RedShoesDamagePolicy.justiceDamageBonus(-1));
    }
}
