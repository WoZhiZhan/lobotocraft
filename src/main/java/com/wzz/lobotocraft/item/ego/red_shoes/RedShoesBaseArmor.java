package com.wzz.lobotocraft.item.ego.red_shoes;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RedShoesBaseArmor extends BaseEgoArmor {

    public RedShoesBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HE;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "red_shoes";
    }

    @Override
    public String getSetId() {
        return "red_shoes";
    }

    @Override
    public float getRedResistance() {
        return 0.5f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.2f;
    }

    @Override
    public float getBlackResistance() {
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.detail");
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.story." + index);
        }
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.resistance.red");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.resistance.white");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.resistance.black");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.red_shoes_armor.resistance.pale");
    }
}
