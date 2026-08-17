package com.wzz.lobotocraft.core_suppression.chesed;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChesedEncounterDataTest {
    @Test
    void persistsSelectedDamageTypesAndHandledOverloadLevel() {
        ChesedEncounterData data = new ChesedEncounterData();
        data.start(ignored -> 0);
        assertTrue(data.handleOverload(5, bound -> bound - 1));

        ChesedEncounterData restored = ChesedEncounterData.load(data.save(new CompoundTag()));

        assertTrue(restored.isActive());
        assertEquals(5, restored.getLastHandledOverloadLevel());
        assertEquals(3, Integer.bitCount(restored.getSelectedDamageMask()));
        assertEquals(2, restored.getVisualStage());
    }

    @Test
    void ignoresDuplicateOverloadNotification() {
        ChesedEncounterData data = new ChesedEncounterData();
        data.start(ignored -> 0);
        assertTrue(data.handleOverload(2, ignored -> 0));
        int selected = data.getSelectedDamageMask();

        assertFalse(data.handleOverload(2, bound -> bound - 1));
        assertEquals(selected, data.getSelectedDamageMask());
    }
}
