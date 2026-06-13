package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.world.data.GenerationBiggerData;
import net.minecraft.server.level.ServerLevel;

public record StructureInfo(String name) {

    public GenerationBiggerData.Entry data(ServerLevel level) {
        return GenerationBiggerData.get(level).getOrCreate(name);
    }

    public void setGenerated(ServerLevel level, int radius, int x, int z) {
        var d = data(level);
        d.generated = true;
        d.currentRadius = radius;
        d.centerX = x;
        d.centerZ = z;
        GenerationBiggerData.get(level).setDirty();
    }

    public boolean isGenerated(ServerLevel level) {
        var d = data(level);
        return d.generated;
    }
}