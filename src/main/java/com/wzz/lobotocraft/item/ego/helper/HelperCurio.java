package com.wzz.lobotocraft.item.ego.helper;

import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HelperCurio extends BaseEgoCurio {
    public HelperCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "helper";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 工作成功率 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 洞察工作成功率 + 3%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家每有1级正义，粉碎机MK4武器造成的伤害提高1点。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 使用\"粉碎机Mk4\"特殊攻击造成伤害时恢复6点生命值。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 4;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        float bonus = 0.01f;
        if (workType == WorkType.INSIGHT) {
            if (EgoArmorHelper.isFullEGO(player, "happy_teddy"))
                bonus += 0.03f;
            return bonus;
        }
        return bonus;
    }
}
