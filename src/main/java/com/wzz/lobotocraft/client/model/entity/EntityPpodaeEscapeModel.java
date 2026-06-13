package com.wzz.lobotocraft.client.model.entity;

import com.wzz.lobotocraft.entity.abnormality.EntityPpodae;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

public class EntityPpodaeEscapeModel extends BaseModel<EntityPpodae> {
    @Override
    public ResourceLocation getAnimationResource(EntityPpodae entity) {
        return ResourceUtil.createInstance("animations/"+entity.name()+"_escape.animation.json");
    }

    @Override
    public ResourceLocation getModelResource(EntityPpodae entity) {
        return ResourceUtil.createInstance("geo/"+entity.name()+"_escape.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityPpodae entity) {
        return ResourceUtil.createInstance("textures/entities/" + entity.getTexture() + "_escape.png");
    }
}
