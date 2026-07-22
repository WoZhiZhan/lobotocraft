package com.wzz.lobotocraft.item.ego.punishing_bird;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PunishingBirdBaseArmor extends BaseEgoArmor {

    public PunishingBirdBaseArmor(Type type) {
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
        return "punishing_bird";
    }

    @Override
    public String getSetId() {
        return "punishing_bird";
    }

    @Override
    public float getRedResistance() {
        return 0.7f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
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
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        p_41423_.add(Component.literal("§7这件护甲非常光滑，它最初被制造出来时特别地小，只有小孩才能勉强穿上。"));
        p_41423_.add(Component.literal("§7当它被鲜血浸透时，胸部的红块就会像活着一般开始蠕动。"));
        p_41423_.add(Component.literal("§c红色伤害：0.7"));
        p_41423_.add(Component.literal("§f白色伤害：0.8"));
        p_41423_.add(Component.literal("§5黑色伤害：1.2"));
        p_41423_.add(Component.literal("§b蓝色伤害：2.0"));
    }
}