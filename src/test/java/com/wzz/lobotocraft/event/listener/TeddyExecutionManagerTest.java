package com.wzz.lobotocraft.event.listener;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeddyExecutionManagerTest {
    @Test
    void lockRequiresCompleteSourceAndAnchor() {
        CompoundTag data = new CompoundTag();
        data.putBoolean(TeddyExecutionManager.ACTIVE, true);
        data.putUUID(TeddyExecutionManager.SOURCE, UUID.randomUUID());
        data.putString(TeddyExecutionManager.DIMENSION, "lobotocraft:loboto");
        data.putDouble(TeddyExecutionManager.ANCHOR_X, 1.0D);
        data.putDouble(TeddyExecutionManager.ANCHOR_Y, 2.0D);
        assertFalse(TeddyExecutionManager.isLocked(data));
        data.putDouble(TeddyExecutionManager.ANCHOR_Z, 3.0D);
        assertTrue(TeddyExecutionManager.isLocked(data));
    }

    @Test
    void cleanupRemovesAllExecutionState() {
        CompoundTag data = new CompoundTag();
        data.putBoolean(TeddyExecutionManager.ACTIVE, true);
        data.putUUID(TeddyExecutionManager.SOURCE, UUID.randomUUID());
        data.putString(TeddyExecutionManager.DIMENSION, "lobotocraft:loboto");
        data.putDouble(TeddyExecutionManager.ANCHOR_X, 1.0D);
        data.putDouble(TeddyExecutionManager.ANCHOR_Y, 2.0D);
        data.putDouble(TeddyExecutionManager.ANCHOR_Z, 3.0D);
        TeddyExecutionManager.clearState(data);
        assertFalse(TeddyExecutionManager.isLocked(data));
        assertTrue(data.isEmpty());
    }
}
