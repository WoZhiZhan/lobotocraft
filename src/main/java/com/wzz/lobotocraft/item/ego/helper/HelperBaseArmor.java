package com.wzz.lobotocraft.item.ego.helper;

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

public abstract class HelperBaseArmor extends BaseEgoArmor {

    public HelperBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PrudenceLevel", 2);
        return map;
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
        return "helper";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.6f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.3f;
    }

    @Override
    public float getBlackResistance() {
        return 0.9f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7这件护甲上随处都能看到未知来源的血迹。"));
        p_41423_.add(Component.literal("§7一些员工为自己在事件中流血而感到骄傲。"));
        p_41423_.add(Component.literal("§7但是这件护甲上的血迹却来自于对异想体核心本身的提取过程中，"));
        p_41423_.add(Component.literal("§7斑斑血迹不禁让人回想起过去的那些与未来可能到来的灾难。"));
        p_41423_.add(Component.literal("§7然而机械的叛乱早已不再是现代社会的威胁。"));
    }
}