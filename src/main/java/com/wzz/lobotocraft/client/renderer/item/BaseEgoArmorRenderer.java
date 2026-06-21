package com.wzz.lobotocraft.client.renderer.item;

import com.wzz.lobotocraft.client.model.item.ego.BaseEgoArmorModel;
import com.wzz.lobotocraft.entity.base.EgoEquipmentSlot;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * E.G.O装备通用渲染器（支持多个geo.json）
 * 适用于所有E.G.O装备套装
 */
@OnlyIn(Dist.CLIENT)
public class BaseEgoArmorRenderer extends GeoArmorRenderer<BaseEgoArmor> {
    
    private final BaseEgoArmorModel model;
    
    public BaseEgoArmorRenderer(String armorName) {
        super(new BaseEgoArmorModel(armorName));
        this.model = (BaseEgoArmorModel) this.getGeoModel();
    }

    @Override
    public void prepForRender(@Nullable Entity entity, ItemStack stack, @Nullable EquipmentSlot slot, @Nullable HumanoidModel<?> baseModel) {
        if (this.model != null && stack.getItem() instanceof BaseEgoArmor armor) {
            this.model.setCurrentSlot(new EgoEquipmentSlot(slot, !armor.useSeparateModel()));
            this.model.setCurrentAnimatable(armor);
        }
        super.prepForRender(entity, stack, slot, baseModel);
    }
}