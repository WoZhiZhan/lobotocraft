package com.wzz.lobotocraft.item.ego.punishing_bird;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PunishingBirdCurio extends BaseEgoCurio {
    public PunishingBirdCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "punishing_bird";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 移动速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家造成伤害时，有10%的概率使下次造成的伤害翻倍").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家受到伤害时，有10%的概率使受到的这次伤害减半").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("48f1ea97-0b18-495a-b1d1-42440c85e63c"), this.getCurrentClassName() +  " Move Speed Bonus",
                        Attributes.MOVEMENT_SPEED, 0.004),
                new AttributeEntry(UUID.fromString("a8f0bb58-4f7a-48cb-bd08-a8bb24ed5e30"), this.getCurrentClassName() +  " Attack Speed Bonus",
                        Attributes.ATTACK_SPEED, 0.008)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}