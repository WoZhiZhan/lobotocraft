package com.wzz.lobotocraft.item.ego.queen_bee;

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

public abstract class QueenBeeBaseArmor extends BaseEgoArmor {

    public QueenBeeBaseArmor(Type type) {
        super(ModArmorMaterial.REPENTANCE, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("EmployeeLevel", 3);
        return map;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.WAW;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String armorName() {
        return "queen_bee";
    }

    @Override
    public String getSetId() {
        return armorName();
    }

    @Override
    public float getRedResistance() {
        return 0.7f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.7f;
    }

    @Override
    public float getBlackResistance() {
        return 0.7f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§6※受到来自”威胁“目标的攻击时，减少30%受到的伤害。"));
        p_41423_.add(Component.literal("§7尽管王国会在历史上备受瞩目，但是又有谁会记得为它做出奉献与牺牲的工蜂呢？"));
        p_41423_.add(Component.literal("§7旧日的荣耀仍在继续绽放光辉。"));
    }
}