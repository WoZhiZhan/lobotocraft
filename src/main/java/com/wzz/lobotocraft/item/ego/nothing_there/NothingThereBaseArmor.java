package com.wzz.lobotocraft.item.ego.nothing_there;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class NothingThereBaseArmor extends BaseEgoArmor {

    public NothingThereBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 5);
        map.put("EmployeeLevel", 5);
        return map;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.ALEPH;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "nothing_there";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.2f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.5f;
    }

    @Override
    public float getBlackResistance() {
        return 0.5f;
    }

    @Override
    public float getPaleResistance() {
        return 1.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.detail");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.story.1");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.story.2");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.resistance.red");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.resistance.white");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.resistance.black");
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.nothing_there_armor.resistance.pale");
    }
}
