package com.wzz.lobotocraft.item.ego.children_galaxy;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.ClientInputUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ChildrenGalaxyBaseArmor extends BaseEgoArmor {
    public ChildrenGalaxyBaseArmor(Type type) {
        super(ModArmorMaterial.ARMOR_MATERIAL, type, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String armorName() {
        return "children_galaxy";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String getSetId() {
        return "children_galaxy";
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
        return 1.2f;
    }

    @Override
    public float getPaleResistance() {
        return 1.5f;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ClientInputUtil.isShiftPressed()) {
            for (int index = 1; index <= 3; index++) {
                EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.detail." + index,
                        ChatFormatting.GOLD);
            }
            return;
        }
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.story",
                ChatFormatting.GRAY);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.shift_details",
                ChatFormatting.GRAY);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.resistance.red",
                ChatFormatting.RED);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.resistance.white",
                ChatFormatting.WHITE);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.resistance.black",
                ChatFormatting.DARK_PURPLE);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.our_galaxy_armor.resistance.pale",
                ChatFormatting.AQUA);
    }
}
