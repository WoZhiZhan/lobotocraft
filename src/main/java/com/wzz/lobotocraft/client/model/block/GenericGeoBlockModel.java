package com.wzz.lobotocraft.client.model.block;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.BaseGeoBlockEntity;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GenericGeoBlockModel<T extends BaseGeoBlockEntity> extends GeoModel<T> {
    private final ResourceLocation modelPath;
    private final ResourceLocation texturePath;
    private final ResourceLocation animationPath;

    public GenericGeoBlockModel(String model, String texture, String animation) {
        this.modelPath = ResourceUtil.createInstance(ModMain.MODID, "geo/block/" + model);
        this.texturePath = ResourceUtil.createInstance(ModMain.MODID,"textures/block/" + texture);
        this.animationPath = ResourceUtil.createInstance(ModMain.MODID, "animations/block/" + animation);
    }
    
    @Override
    public ResourceLocation getModelResource(T animatable) {
        return modelPath;
    }
    
    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texturePath;
    }
    
    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animationPath;
    }
}