package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
    public static final ResourceKey<Level> LOBOTO_KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceUtil.createInstance(ModMain.MODID, "loboto"));
    
    public static final ResourceKey<DimensionType> LOBOTO_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
        ResourceUtil.createInstance(ModMain.MODID, "loboto"));
}