package com.wzz.lobotocraft.item.ego.ppodae;

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

public abstract class PpodaeBaseArmor extends BaseEgoArmor {

    public PpodaeBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.TETH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "ppodae";
    }

    @Override
    public String getSetId() {
        return "ppodae";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.5f;
    }

    @Override
    public float getBlackResistance() {
        return 0.8f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.detail");
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.story." + index);
        }
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.resistance.red");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.resistance.white");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.resistance.black");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.ppodae_armor.resistance.pale");
    }
}
