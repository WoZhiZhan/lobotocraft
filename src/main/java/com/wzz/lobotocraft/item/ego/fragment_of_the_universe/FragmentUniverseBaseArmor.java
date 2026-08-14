package com.wzz.lobotocraft.item.ego.fragment_of_the_universe;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModArmorMaterial;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class FragmentUniverseBaseArmor extends BaseEgoArmor {

    public FragmentUniverseBaseArmor(Type type) {
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
        return "fragment_of_the_universe";
    }

    @Override
    public String getSetId() {
        return "fragment_of_the_universe";
    }

    @Override
    public float getRedResistance() {
        return 1.0f;
    }

    @Override
    public float getWhiteResistance() {
        return 1.2f;
    }

    @Override
    public float getBlackResistance() {
        return 0.6f;
    }

    @Override
    public float getPaleResistance() {
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        for (int index = 1; index <= 5; index++) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_armor.story." + index,
                    ChatFormatting.GRAY);
        }
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_armor.resistance.red",
                ChatFormatting.RED);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_armor.resistance.white",
                ChatFormatting.WHITE);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_armor.resistance.black",
                ChatFormatting.DARK_PURPLE);
        EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_armor.resistance.pale",
                ChatFormatting.AQUA);
    }
}
