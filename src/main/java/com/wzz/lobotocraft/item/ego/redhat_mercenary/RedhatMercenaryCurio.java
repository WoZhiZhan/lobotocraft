package com.wzz.lobotocraft.item.ego.redhat_mercenary;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class RedhatMercenaryCurio extends BaseEgoCurio {
    public RedhatMercenaryCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "redhat_mercenary";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 移动速度 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 玩家手持任意EGO造成的伤害 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用“猩红创痕”的火铳造成伤害时，减少对方10%的移动速度，持续10秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家使用“猩红创痕”的切肉刀造成伤害时，使对方每秒受到2点红色dot伤害，持续15秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家每损失25%血量，造成的伤害+10%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("d39d69c6-1fd2-44ad-bd7d-134997ba64ae"),
                        this.getCurrentClassName() + "Max Health Bonus", Attributes.MOVEMENT_SPEED, 2D),
                new AttributeEntry(UUID.fromString("b4d8d3d7-8061-4b4c-bce4-f7ea74d2e7b4"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.048D),
                new AttributeEntry(UUID.fromString("18423f10-2d24-4142-b8ef-5997293af7ed"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.024D)
        );
    }
}
