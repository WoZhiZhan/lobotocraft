package com.wzz.lobotocraft.client.model.entity;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class BaseModel<T extends BaseGeoEntity> extends GeoModel<T> {
    private String name;
    private String texture;

    public BaseModel() {
        this(null, null);
    }

    public BaseModel(String name, String texture) {
        this.name = name;
        this.texture = texture;
    }

    public String getName(T e) {
        String name = this.name;
        if (name == null) {
            name = e.name();
            this.name = name;
        }
        return name;
    }

    public String getTexture(T e) {
        // 不缓存实体贴图:BaseModel 实例被同类型所有实体共享,
        // 缓存会导致多皮肤实体(清道夫三种外观)与运行时换肤(狼吞人后)全部失效
        if (this.texture != null) return this.texture;
        return e.getTexture();
    }

    @Override
    public ResourceLocation getAnimationResource(T entity) {
        return ResourceUtil.createInstance("animations/" + this.getName(entity) + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(T entity) {
        return ResourceUtil.createInstance("geo/" + this.getName(entity) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T entity) {
        return ResourceUtil.createInstance("textures/entities/" + this.getTexture(entity) + ".png");
    }

    @Override
    public void setCustomAnimations(BaseGeoEntity animatable, long instanceId, AnimationState animationState) {
        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData entityData = (EntityModelData) animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * ((float)Math.PI / 180F));
            head.setRotY(entityData.netHeadYaw() * ((float)Math.PI / 180F));
        }
    }
}