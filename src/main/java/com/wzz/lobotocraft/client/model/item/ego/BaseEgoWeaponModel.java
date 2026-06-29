package com.wzz.lobotocraft.client.model.item.ego;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * E.G.O武器通用模型
 */
public class BaseEgoWeaponModel extends GeoModel<BaseEgoWeapon> {
    
    private final String weaponName;
    private boolean hasAnimation = true;

    public BaseEgoWeaponModel(String weaponName) {
        this.weaponName = weaponName;
    }

    @Override
    public ResourceLocation getModelResource(BaseEgoWeapon animatable) {
        return ResourceUtil.createInstance(ModMain.MODID, "geo/weapon/" + weaponName + "_weapon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BaseEgoWeapon animatable) {
        if ("children_galaxy".equals(weaponName)) {
            return ResourceUtil.createInstance(ModMain.MODID, "textures/item/children_galaxy_weapon.png");
        }
        return ResourceUtil.createInstance(ModMain.MODID, "textures/weapon/" + weaponName + "_weapon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BaseEgoWeapon animatable) {
        String animationPath = "animations/weapon/" + weaponName + "_weapon.animation.json";
        ResourceLocation animationResource = ResourceUtil.createInstance(ModMain.MODID, animationPath);
        if (ResourceUtil.exists(animationResource)) {
            return animationResource;
        }
        hasAnimation = false;
        return null;
    }

    @Override
    public void setCustomAnimations(BaseEgoWeapon animatable, long instanceId, AnimationState<BaseEgoWeapon> animationState) {
        if (!hasAnimation) return;
        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}
