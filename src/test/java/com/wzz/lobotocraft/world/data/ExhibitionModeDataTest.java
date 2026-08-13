package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhibitionModeDataTest {
    @Test
    void modeAndRecoveryCounterRoundTrip() {
        ExhibitionModeData data = new ExhibitionModeData();
        assertFalse(data.isEnabled());
        data.setEnabled(true);
        data.recordRecovery();
        data.recordRecovery();

        ExhibitionModeData loaded = ExhibitionModeData.load(data.save(new CompoundTag()));
        assertTrue(loaded.isEnabled());
        assertEquals(2L, loaded.getRecoverySerial());
    }
}
