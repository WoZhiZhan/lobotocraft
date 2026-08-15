package com.lobotocraft.facility.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Persisted containment-room locator captured when a meltdown starts. */
public record MeltdownTargetLocator(String dimensionId, long roomMarker,
                                    boolean hasRoomMarker) {
    public MeltdownTargetLocator {
        dimensionId = dimensionId == null ? "" : dimensionId;
    }

    public static MeltdownTargetLocator of(ResourceLocation dimension, BlockPos marker) {
        return new MeltdownTargetLocator(dimension == null ? "" : dimension.toString(),
            marker == null ? 0L : marker.asLong(), marker != null);
    }

    public static MeltdownTargetLocator unknown() {
        return new MeltdownTargetLocator("", 0L, false);
    }

    public ResourceLocation dimension() {
        return ResourceLocation.tryParse(dimensionId);
    }

    public BlockPos marker() {
        return hasRoomMarker ? BlockPos.of(roomMarker) : null;
    }

    public boolean isKnown() {
        return dimension() != null && hasRoomMarker;
    }
}
