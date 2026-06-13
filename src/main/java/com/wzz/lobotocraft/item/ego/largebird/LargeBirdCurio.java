package com.wzz.lobotocraft.item.ego.largebird;

import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;

public class LargeBirdCurio extends BaseEgoCurio {
    public LargeBirdCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "largebird";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作成功率 + 3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 3").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 免疫大鸟的魅惑效果").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 武器施加的负面效果的概率翻倍").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof ServerPlayer player) {
            if (EgoArmorHelper.isFullEGO(player, "end_bird") && MentalValueUtil.getMentalValue(player) <= 0f) {
                MentalValueUtil.setMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player));
            }
        }
    }

    @Override
    public Attribute getAttribute() {
        return Attributes.MAX_HEALTH;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.0024f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 1;
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public UUID getAttributeUUID() {
        return UUID.fromString("afe483c4-47cb-4337-8be9-76010a8dec6c");
    }

    @Override
    public float getAttributeBonus() {
        return 3f;
    }
}