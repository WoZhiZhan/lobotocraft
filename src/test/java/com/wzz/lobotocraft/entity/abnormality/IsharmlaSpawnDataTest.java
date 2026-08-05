package com.wzz.lobotocraft.entity.abnormality;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsharmlaSpawnDataTest {
    @Test
    void reactorIndexSurvivesSaveAndSupportsRemoval() {
        ResourceLocation dimension = ResourceLocation.tryParse("lobotocraft:loboto");
        BlockPos reactor = new BlockPos(3, 4, 5);
        IsharmlaSpawnData data = new IsharmlaSpawnData();
        data.registerReactor(dimension, reactor);
        data.registerReactor(dimension, reactor);
        IsharmlaSpawnData loaded = IsharmlaSpawnData.load(data.save(new CompoundTag()));
        assertEquals(1, loaded.reactorCount(dimension));
        loaded.unregisterReactor(dimension, reactor);
        assertEquals(0, loaded.reactorCount(dimension));
    }

    @Test
    void pendingTransitionSurvivesSaveAndRemainsIdempotent() {
        UUID id = UUID.randomUUID();
        IsharmlaSpawnData data = new IsharmlaSpawnData();
        data.enqueue(id, ResourceLocation.tryParse("lobotocraft:loboto"),
                new BlockPos(12, 34, 56));
        data.enqueue(id, ResourceLocation.tryParse("lobotocraft:loboto"), BlockPos.ZERO);

        assertEquals(1, data.pendingCount());
        IsharmlaSpawnData loaded = IsharmlaSpawnData.load(data.save(new CompoundTag()));
        assertTrue(loaded.hasPending(id));
        assertEquals(1, loaded.pendingCount());
    }

    @Test
    void completedTransitionCannotBeQueuedAgainAfterRestart() {
        UUID id = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        ListTag completed = new ListTag();
        CompoundTag record = new CompoundTag();
        record.putUUID("Id", id);
        completed.add(record);
        saved.put("Completed", completed);

        IsharmlaSpawnData loaded = IsharmlaSpawnData.load(saved);
        assertTrue(loaded.isCompleted(id));
        loaded.enqueue(id, ResourceLocation.tryParse("lobotocraft:loboto"), BlockPos.ZERO);
        assertFalse(loaded.hasPending(id));
    }
}
