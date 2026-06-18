package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 忏悔饰品
 * 位置：头部
 * 效果：最大精神值+2
 * 特殊效果：
 * - 对"一罪与百善"工作成功率+10%
 * - 装备+武器+饰品 = 免疫白夜和使徒伤害
 */
public class RepentanceCurio extends BaseEgoCurio {

    public RepentanceCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "repentance";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("  • 最大精神值 +2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 对一罪与百善工作成功率 +10%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备锁定 + 装备 + 饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用悔恨武器造成伤害后，10秒内减少20%的移动速度，使玩家造成的红色伤害+10%，不可叠加，每次造成伤害刷新持续时间").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("当“白夜”出逃时效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 免疫白夜和使徒的伤害").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 无法对白夜和使徒造成伤害").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("  • 无法进行工作").withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
        return List.of(
                new AttributeEntry(UUID.fromString("29f82af0-fe7f-4f2a-a316-b9a6a1eaafa7"),
                        this.getCurrentClassName() + " Extra Mental Value Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 2D)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}
