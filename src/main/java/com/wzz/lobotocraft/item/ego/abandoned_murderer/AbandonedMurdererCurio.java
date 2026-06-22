package com.wzz.lobotocraft.item.ego.abandoned_murderer;

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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AbandonedMurdererCurio extends BaseEgoCurio {
    public AbandonedMurdererCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "abandoned_murderer";
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
        tooltip.add(Component.literal("  • 最大精神值 + 2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 玩家手持任意EGO时伤害 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备锁定 + 装备 + 武器 + 饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用悔恨武器造成伤害后，10秒内减少20%的移动速度，使玩家造成的红色伤害+10%，不可叠加，每次造成伤害刷新持续时间").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("a1d91b8d-52a5-4f66-8a66-41d6ee0670dd"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 2D),
                new AttributeEntry(UUID.fromString("37051b32-8809-4573-974f-e9d99faa87d4"),
                        this.getCurrentClassName() + " Extra Mental Value Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 2D)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}
