package com.wzz.lobotocraft.item.ego.the_lady_facing_the_wall;

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

public class LadyFacingWallCurio extends BaseEgoCurio {
    public LadyFacingWallCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "the_lady_facing_the_wall";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 工作速度 + 3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 成功率 + 3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家每次造成伤害为对方施加一层孤独效果，每层减少5%的移动速度，最多10层").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.03f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 3;
    }
}
