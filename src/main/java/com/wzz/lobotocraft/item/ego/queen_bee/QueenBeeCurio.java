package com.wzz.lobotocraft.item.ego.queen_bee;

import com.wzz.lobotocraft.init.ModAttributes;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QueenBeeCurio extends BaseEgoCurio {
    public QueenBeeCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "queen_bee";
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
        tooltip.add(Component.literal("  • 最大精神值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家造成伤害命中目标后，为其施加孢子效果。使其无法恢复生命值，减少10%移动速度，且每2秒造成8点红色dot伤害，持续10秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 攻击威胁时，若目标对应伤害抗性大于0.0时，所有dot类型的伤害将不再受到抗性的影响。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 当玩家生命值低于20%时，会被敌对生物优先标记为仇恨目标。且受到的伤害提高30%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        entries.add(new AttributeEntry(UUID.fromString("badfab91-fa0e-4e98-932c-10550a12049f"),
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, 2.0D));
        entries.add(new AttributeEntry(UUID.fromString("359318fe-c8ea-456b-84df-6e3fd73f34f4"),
                this.getCurrentClassName() + " Max Mental Penalty",
                ModAttributes.EXTRA_MENTAL_VALUE.get(), 4.0D));
        return entries;
    }
}