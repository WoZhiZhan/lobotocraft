package com.wzz.lobotocraft.item.ego.crumbling_armor;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class FoolhardyCourageCurio extends BaseEgoCurio {
    public FoolhardyCourageCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "foolhardy_courage";
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
                new AttributeEntry(UUID.fromString("c0610003-0561-4000-8000-000000000003"),
                        this.getCurrentClassName() + " Max Health Penalty", Attributes.MAX_HEALTH, -20.0D),
                new AttributeEntry(UUID.fromString("c0610004-0561-4000-8000-000000000004"),
                        this.getCurrentClassName() + " Move Speed Bonus", Attributes.MOVEMENT_SPEED, 0.08D,
                        AttributeModifier.Operation.MULTIPLY_BASE),
                new AttributeEntry(UUID.fromString("c0610005-0561-4000-8000-000000000005"),
                        this.getCurrentClassName() + " Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.04D,
                        AttributeModifier.Operation.MULTIPLY_BASE)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 -20").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("  • 移动速度 +20").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 +20").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("由内在的勇气膨胀而成的匹夫之勇。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("携带者进行沟通工作时会被处决。").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
