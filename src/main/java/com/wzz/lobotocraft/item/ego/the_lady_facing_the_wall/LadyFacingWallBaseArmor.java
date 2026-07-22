package com.wzz.lobotocraft.item.ego.the_lady_facing_the_wall;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LadyFacingWallBaseArmor extends BaseEgoArmor {

    public LadyFacingWallBaseArmor(Type type) {
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
        return "the_lady_facing_the_wall";
    }

    @Override
    public String getSetId() {
        return "the_lady_facing_the_wall";
    }

    @Override
    public float getRedResistance() {
        return 1.5f;
    }

    @Override
    public float getWhiteResistance() {
        return 0.8f;
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
        p_41423_.add(Component.literal("§7如果你想隐藏内心中的想法，那就用孤独掩盖他们。"));
        p_41423_.add(Component.literal("§7如果你不愿分享自身的全部，那就将与分享相反的意图置于内心的首位。"));
        p_41423_.add(Component.literal("§7这件护甲能够保护穿戴者脆弱的心灵。"));
        p_41423_.add(Component.literal("§c红色伤害：1.5"));
        p_41423_.add(Component.literal("§f白色伤害：0.8"));
        p_41423_.add(Component.literal("§5黑色伤害：0.8"));
        p_41423_.add(Component.literal("§b蓝色伤害：2.0"));
    }
}