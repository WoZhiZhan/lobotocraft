package com.wzz.lobotocraft.client.model.item.ego;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.EgoEquipmentSlot;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * E.G.O装备通用模型（支持多个geo.json）
 * 根据装备名称和装备槽位动态加载对应的模型、纹理和动画文件
 */
public class BaseEgoArmorModel extends GeoModel<BaseEgoArmor> {

    private final String armorName;
    private boolean hasAnimation = true;
    private EgoEquipmentSlot currentSlot = null;
    private BaseEgoArmor currentAnimatable = null;

    public BaseEgoArmorModel(String armorName) {
        this.armorName = armorName;
    }

    /**
     * 设置当前渲染的装备槽位和物品
     */
    public void setCurrentSlot(EgoEquipmentSlot slot) {
        this.currentSlot = slot;
    }

    public void setCurrentAnimatable(BaseEgoArmor animatable) {
        this.currentAnimatable = animatable;
    }

    @Override
    public ResourceLocation getModelResource(BaseEgoArmor animatable) {
        String modelPath = getModelPathForSlot(currentSlot);
        return ResourceUtil.createInstance(ModMain.MODID, modelPath);
    }

    /**
     * 根据装备槽位获取对应的模型路径
     */
    private String getModelPathForSlot(EgoEquipmentSlot slot) {
        if (slot == null || slot.equipmentSlot() == null || slot.isFullEquipment()) {
            return "geo/armor/" + armorName + "_armor.geo.json";
        }
        return switch (slot.equipmentSlot()) {
            case HEAD -> "geo/armor/" + armorName + "_helmet.geo.json";
            case CHEST -> "geo/armor/" + armorName + "_chestplate.geo.json";
            case LEGS -> "geo/armor/" + armorName + "_leggings.geo.json";
            case FEET -> "geo/armor/" + armorName + "_boots.geo.json";
            default -> "geo/armor/" + armorName + "_armor.geo.json";
        };
    }

    @Override
    public ResourceLocation getTextureResource(BaseEgoArmor animatable) {
        if (currentAnimatable != null && currentAnimatable.useSeparateTextures()) {
            String texturePath = getTexturePathForSlot(currentSlot.equipmentSlot());
            return ResourceUtil.createInstance(ModMain.MODID, texturePath);
        }
        return ResourceUtil.createInstance(ModMain.MODID, "textures/armor/" + armorName + "_armor.png");
    }

    /**
     * 根据槽位获取对应的纹理路径
     */
    private String getTexturePathForSlot(EquipmentSlot slot) {
        if (slot == null) {
            return "textures/armor/" + armorName + "_armor.png";
        }
        return switch (slot) {
            case HEAD -> "textures/armor/" + armorName + "_helmet.png";
            case CHEST -> "textures/armor/" + armorName + "_chestplate.png";
            case LEGS -> "textures/armor/" + armorName + "_leggings.png";
            case FEET -> "textures/armor/" + armorName + "_boots.png";
            default -> "textures/armor/" + armorName + "_armor.png";
        };
    }

    @Override
    public ResourceLocation getAnimationResource(BaseEgoArmor animatable) {
        String animationPath = "animations/armor/" + armorName + "_armor.animation.json";
        ResourceLocation animationResource = ResourceUtil.createInstance(ModMain.MODID, animationPath);
        if (ResourceUtil.exists(animationResource)) {
            return animationResource;
        }
        hasAnimation = false;
        return null;
    }

    @Override
    public void setCustomAnimations(BaseEgoArmor animatable, long instanceId, AnimationState<BaseEgoArmor> animationState) {
        if (!hasAnimation) return;
        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}