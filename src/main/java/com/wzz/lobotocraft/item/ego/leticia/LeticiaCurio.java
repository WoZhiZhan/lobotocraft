package com.wzz.lobotocraft.item.ego.leticia;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;

public class LeticiaCurio extends BaseEgoCurio {
    public LeticiaCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "leticia";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大精神值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 沟通工作失误时有 25% 概率不受到伤害惩罚").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家使用武器“蕾蒂希娅”造成伤害时，若对方黑色伤害抗性小于1.0，则提高自己10%移动速度。若对方黑色伤害抗性大于1.0，则减少对方25%移动速度。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家持有礼物时，每5秒会回复6点精神值和生命值。受到致命伤害时“礼物”效果消失，并且使玩家复活一次。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof ServerPlayer player && player.tickCount % 100 == 0 && EgoArmorHelper.isFullEGO(player, "leticia")) {
            if (player.hasEffect(ModMobEffects.LETICIA_GIFT.get()) || player.hasEffect(ModMobEffects.LETICIA_BROKEN_GIFT.get())) {
                player.heal(6f);
                MentalValueUtil.addMentalValue(player, 6f);
            }
        }
    }

    @Override
    public Attribute getAttribute() {
        return ModAttributes.EXTRA_MENTAL_VALUE.get();
    }
    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public UUID getAttributeUUID() {
        return UUID.fromString("6b3e11e4-db81-4645-86ac-0a60e4c6bd43");
    }

    @Override
    public float getAttributeBonus() {
        return 4f;
    }
}
