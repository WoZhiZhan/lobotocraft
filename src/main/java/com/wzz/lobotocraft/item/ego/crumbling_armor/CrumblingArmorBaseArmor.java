package com.wzz.lobotocraft.item.ego.crumbling_armor;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CrumblingArmorBaseArmor extends BaseEgoArmor {

    public CrumblingArmorBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("TemperanceLevel", 2);
        return map;
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
        return "crumbling_armor";
    }

    @Override
    public String getSetId() {
        return "crumbling_armor";
    }

    @Override
    public float getRedResistance() {
        return 0.6f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.9f;
    }

    @Override
    public float getBlackResistance() {
        return 0.9f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7为如果让有赴死之心的员工穿上这套护甲，它便会挡下所有的伤害。"));
        p_41423_.add(Component.literal("§7但在这个地方很难找到一个不怕丧命的人。"));
        p_41423_.add(Component.literal("§c红色伤害：0.6"));
        p_41423_.add(Component.literal("§f白色伤害：0.9"));
        p_41423_.add(Component.literal("§5黑色伤害：0.9"));
        p_41423_.add(Component.literal("§b蓝色伤害：2.0"));
    }
}