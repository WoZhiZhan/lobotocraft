package com.wzz.lobotocraft.item.ego.thorn_bus;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ThornBusCurio extends BaseEgoCurio {
    public ThornBusCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "thorn_bus";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大精神值 + 10").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作成功率 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 6").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 受到任何伤害回复 2-4 点精神值").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 遇到远程和弹射物伤害会直接弹开").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 受到任何伤害时额外回复自身 5% 的精神值").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public Attribute getAttribute() {
        return ModAttributes.EXTRA_MENTAL_VALUE.get();
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.06f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 2;
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public UUID getAttributeUUID() {
        return UUID.fromString("401c1271-bbf8-41c4-b034-54d98bd777c0");
    }

    @Override
    public float getAttributeBonus() {
        return 10f;
    }
}