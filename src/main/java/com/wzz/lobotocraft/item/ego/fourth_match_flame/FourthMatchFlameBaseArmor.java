package com.wzz.lobotocraft.item.ego.fourth_match_flame;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class FourthMatchFlameBaseArmor extends BaseEgoArmor {

    public FourthMatchFlameBaseArmor(Type type) {
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
        return "fourth_match_flame";
    }

    @Override
    public String getSetId() {
        return "fourth_match_flame";
    }

    @Override
    public float getRedResistance() {
        return 0.6f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.0f;
    }

    @Override
    public float getBlackResistance() {
        return 1.2f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public boolean useSeparateTextures() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7该异想体的核心被提取时烧成灰烬。虽然这些物质视如焦糊，但依旧充满用处。"));
        p_41423_.add(Component.literal("§7这件护甲灰暗的设计能够唤起穿戴者对世上一切光明之物强烈的仇恨。"));
        p_41423_.add(Component.literal("§7然而没人知道这副护甲能否抵御烈焰。"));
    }
}