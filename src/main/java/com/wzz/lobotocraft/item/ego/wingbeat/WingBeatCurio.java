package com.wzz.lobotocraft.item.ego.wingbeat;

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

public class WingBeatCurio extends BaseEgoCurio {
    public WingBeatCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "wingbeat";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public BodyPartType getBodyPartType() {
        return BodyPartType.RIGHT_ARM;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 工作成功率 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 攻击目标时，恢复等同于造成伤害的生命值").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 免疫摔落伤害").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.016f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 1;
    }
}