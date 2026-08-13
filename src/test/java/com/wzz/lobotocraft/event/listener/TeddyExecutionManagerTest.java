package com.wzz.lobotocraft.event.listener;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void hugAnchorRotatesWithTeddyFacing() {
        Vec3 origin = new Vec3(10.0D, 64.0D, 20.0D);
        Vec3 south = TeddyExecutionManager.relativeAnchor(origin, 0.0F, 1.0D, 0.0D);
        Vec3 west = TeddyExecutionManager.relativeAnchor(origin, 90.0F, 1.0D, 0.0D);

        assertEquals(10.0D, south.x, 1.0E-6D);
        assertEquals(21.0D, south.z, 1.0E-6D);
        assertEquals(9.0D, west.x, 1.0E-6D);
        assertEquals(20.0D, west.z, 1.0E-6D);
    }
}
