package com.wzz.lobotocraft.client.model.entity;

import com.wzz.lobotocraft.entity.abnormality.EntityNothingThere;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class NothingThereModel extends GeoModel<EntityNothingThere> {

    private String getModelName(EntityNothingThere entity) {
        return entity.getCurrentModelName();
    }

    @Override
    public ResourceLocation getModelResource(EntityNothingThere entity) {
        String model = getModelName(entity);
        return ResourceUtil.createInstance("geo/" + model + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityNothingThere entity) {
        return ResourceUtil.createInstance("textures/entities/" + getModelName(entity) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(EntityNothingThere entity) {
        String model = getModelName(entity);
        return ResourceUtil.createInstance("animations/" + model + ".animation.json");
    }

    @Override
    public void setCustomAnimations(EntityNothingThere animatable, long instanceId, AnimationState animationState) {
        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = (EntityModelData) animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        }
    }
}
