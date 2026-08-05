package com.wzz.lobotocraft.event.listener;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkadiBlessingManagerTest {
    @Test
    void bindingRequiresSourceDimensionPositionAndEpoch() {
        CompoundTag data = new CompoundTag();
        data.putUUID(SkadiBlessingManager.SOURCE_UUID, UUID.randomUUID());
        data.putString(SkadiBlessingManager.SOURCE_DIMENSION, "lobotocraft:loboto");
        data.putLong(SkadiBlessingManager.SOURCE_POSITION, 123L);
        assertFalse(SkadiBlessingManager.hasCompleteBinding(data));
        data.putUUID(SkadiBlessingManager.SOURCE_EPOCH, UUID.randomUUID());
        assertTrue(SkadiBlessingManager.hasCompleteBinding(data));
    }

    @Test
    void clearingBindingRemovesEverySourceField() {
        CompoundTag data = new CompoundTag();
        data.putUUID(SkadiBlessingManager.SOURCE_UUID, UUID.randomUUID());
        data.putString(SkadiBlessingManager.SOURCE_DIMENSION, "lobotocraft:loboto");
        data.putLong(SkadiBlessingManager.SOURCE_POSITION, 123L);
        data.putUUID(SkadiBlessingManager.SOURCE_EPOCH, UUID.randomUUID());
        SkadiBlessingManager.clearBinding(data);
        assertFalse(SkadiBlessingManager.hasCompleteBinding(data));
        assertTrue(data.isEmpty());
    }
}
