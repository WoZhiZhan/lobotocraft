package com.wzz.lobotocraft.item.ego.fragment_of_the_universe;

import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FragmentUniverseCurio extends BaseEgoCurio {
    public FragmentUniverseCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "fragment_of_the_universe";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.fragments_somewhere_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 2; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.fragments_somewhere_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        if (workType == WorkType.ATTACHMENT) {
            return 0.0024f;
        }
        return 0.0016f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 2;
    }
}
