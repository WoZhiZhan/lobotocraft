package com.wzz.lobotocraft.client.model.entity;

import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CensoredModel extends GeoModel<EntityCensored> {
    private String resourceName(EntityCensored entity) {
        return entity.hasEscape() ? "censored" : "o_03_89";
    }

    @Override
    public ResourceLocation getModelResource(EntityCensored entity) {
        return ResourceUtil.createInstance("geo/" + resourceName(entity) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityCensored entity) {
        return ResourceUtil.createInstance(
                entity.hasEscape() ? "textures/entities/censored.png"
                        : "textures/entities/abnormality/o_03_89.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EntityCensored entity) {
        return ResourceUtil.createInstance(
                "animations/" + resourceName(entity) + ".animation.json");
    }
}
