package com.wzz.lobotocraft.item.ego.fragment_of_the_universe;

import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
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
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 沟通工作成功率 + 3%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作成功率 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用EGO彼方的裂片每次造成伤害为全图所有玩家恢复3点精神值（对陷入恐慌的玩家无效）").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家受到黑色伤害时会回复5点精神值").withStyle(ChatFormatting.LIGHT_PURPLE));
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
