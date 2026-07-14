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
    private final String postFix;
    private boolean hasAnimation = true;

    public BaseEgoWeaponModel(String weaponName, String postFix) {
        this.weaponName = weaponName;
        this.postFix = postFix;
    }

    @Override
    public ResourceLocation getModelResource(BaseEgoWeapon animatable) {
        if (postFix != null) {
            return ResourceUtil.createInstance(ModMain.MODID, "geo/weapon/" + weaponName + "_weapon_" + postFix + ".geo.json");
        }
        return ResourceUtil.createInstance(ModMain.MODID, "geo/weapon/" + weaponName + "_weapon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BaseEgoWeapon animatable) {
        if (postFix != null) {
            return ResourceUtil.createInstance(ModMain.MODID, "textures/weapon/" + weaponName + "_weapon_" + postFix + ".png");
        }
        return ResourceUtil.createInstance(ModMain.MODID, "textures/weapon/" + weaponName + "_weapon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BaseEgoWeapon animatable) {
        String animationPath = "animations/weapon/" + weaponName + "_weapon.animation.json";
        if (postFix != null) {
            animationPath = "animations/weapon/" + weaponName + "_weapon_" + postFix + ".animation.json";
        }
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
