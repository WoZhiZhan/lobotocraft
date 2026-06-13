package com.wzz.lobotocraft.client.model.item.ego;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * E.G.O饰品通用模型
 */
public class BaseEgoCurioModel extends GeoModel<BaseEgoCurio> {

    private final String curioName;
    private boolean hasAnimation = true;

    public BaseEgoCurioModel(String curioName) {
        this.curioName = curioName;
    }

    @Override
    public ResourceLocation getModelResource(BaseEgoCurio animatable) {
        return ResourceUtil.createInstance(ModMain.MODID, "geo/curio/" + curioName + "_curio.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BaseEgoCurio animatable) {
        return ResourceUtil.createInstance(ModMain.MODID, "textures/curio/" + curioName + "_curio.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BaseEgoCurio animatable) {
        String animationPath = "animations/curio/" + curioName + "_curio.animation.json";
        ResourceLocation animationResource = ResourceUtil.createInstance(ModMain.MODID, animationPath);
        if (ResourceUtil.exists(animationResource)) {
            return animationResource;
        }
        hasAnimation = false;
        return null;
    }

    @Override
    public void setCustomAnimations(BaseEgoCurio animatable, long instanceId, AnimationState<BaseEgoCurio> animationState) {
        if (!hasAnimation) return;
        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}
