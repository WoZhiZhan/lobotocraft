package com.wzz.lobotocraft.core_suppression.chesed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChesedDamagePolicyTest {
    @Test
    void followsOriginalDamageTypeCountsAtOverloadLevelsTwoAndFive() {
        assertEquals(1, ChesedDamagePolicy.selectionCount(1));
        assertEquals(2, ChesedDamagePolicy.selectionCount(2));
        assertEquals(2, ChesedDamagePolicy.selectionCount(4));
        assertEquals(3, ChesedDamagePolicy.selectionCount(5));
        assertEquals(3, ChesedDamagePolicy.selectionCount(8));
    }

    @Test
    void firstPhaseExcludesPaleAndDoesNotRepeatThePreviousType() {
        int first = ChesedDamagePolicy.reroll(1, 0, ignored -> 0);
        int second = ChesedDamagePolicy.reroll(1, first, ignored -> 0);

        assertFalse(ChesedDamageKind.PALE.isSelected(first));
        assertFalse(ChesedDamageKind.PALE.isSelected(second));
        assertNotEquals(first, second);
    }

    @Test
    void laterPhasesCanSelectPaleAndAmplifyOnlySelectedDamage() {
        int mask = ChesedDamagePolicy.reroll(2, 0, bound -> bound - 1);

        assertTrue(ChesedDamageKind.PALE.isSelected(mask));
        assertEquals(2, Integer.bitCount(mask));
        assertEquals(5.0F, ChesedDamagePolicy.multiplier(mask, ChesedDamageKind.PALE));
        assertEquals(1.0F, ChesedDamagePolicy.multiplier(mask, ChesedDamageKind.RED));
    }
}
