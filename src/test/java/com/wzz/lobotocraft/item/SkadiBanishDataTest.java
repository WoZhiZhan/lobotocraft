package com.wzz.lobotocraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkadiBanishDataTest {
    @Test
    void transitionOriginAndContainmentBindingSurviveSave() {
        ResourceLocation originDimension = ResourceLocation.tryParse("lobotocraft:loboto");
        ResourceLocation homeDimension = ResourceLocation.tryParse("lobotocraft:loboto");
        BlockPos homeMarker = new BlockPos(12, 34, 56);
        SkadiBanishData data = new SkadiBanishData();
        data.setSkadiOrigin(originDimension, 12.5D, 35.0D, 56.5D,
                homeDimension, homeMarker);

        SkadiBanishData loaded = SkadiBanishData.load(data.save(new CompoundTag()));
        assertTrue(loaded.hasSkadiOrigin());
        assertEquals(originDimension, loaded.getOriginDimension());
        assertEquals(homeDimension, loaded.getHomeDimension());
        assertEquals(homeMarker, loaded.getHomeMarker());
        assertEquals(12.5D, loaded.getOriginX());
        assertEquals(35.0D, loaded.getOriginY());
        assertEquals(56.5D, loaded.getOriginZ());

        loaded.clearSkadiOrigin();
        assertFalse(loaded.hasSkadiOrigin());
        assertNull(loaded.getOriginDimension());
        assertNull(loaded.getHomeDimension());
        assertNull(loaded.getHomeMarker());
    }
}
