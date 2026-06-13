package com.wzz.lobotocraft.item.ego.blue_star;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 新星之声 饰品(眼部)。
 * 效果:精神值+15、移动速度+0.006(介绍写"+10")。
 * 套装效果(武器+护甲+饰品)文本见此处,逻辑在 BlueStarSetEvent。
 */
public class BlueStarCurio extends BaseEgoCurio {

    public BlueStarCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "blue_star";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return List.of(
                // 精神值 +15
                new AttributeEntry(UUID.fromString("b1ce0001-0001-4000-8000-00000000a001"),
                        this.getCurrentClassName() + " Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 15D),
                // 移动速度 +0.006(介绍标注为 +10)
                new AttributeEntry(UUID.fromString("b1ce0002-0002-4000-8000-00000000a002"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.006D)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("  • 精神值 +15").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 移动速度 +10").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（武器 + 护甲 + 饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 使用新星之声造成伤害时，使目标减少30%移动速度，持续10秒，不可叠加，每次造成伤害刷新")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家精神值为满时，新星之声光束的伤害提高25%")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家受到致命伤害时，强行保留1%生命与精神原地复活一次")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
