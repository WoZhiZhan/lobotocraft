package com.wzz.lobotocraft.item.ego.wingbeat;

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

public abstract class WingBeatBaseArmor extends BaseEgoArmor {

    public WingBeatBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ZAYIN;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "wingbeat";
    }

    @Override
    public String getSetId() {
        return "wingbeat";
    }

    @Override
    public float getRedResistance() {
        return 0.8f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
    }

    @Override
    public float getBlackResistance() {
        return 1.0f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.story.1");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.story.2");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.resistance.red");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.resistance.white");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.resistance.black");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.wingbeat_armor.resistance.pale");
    }
}
