package com.wzz.lobotocraft.item.linkage;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.client.StrangeBadgeTooltipHandler;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.StrangeBadgeEffectPacket;
import com.wzz.lobotocraft.util.SoundUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 奇怪的工牌
 * 普通状态：
 * - 介绍：一块奇怪的工牌，上面的字迹已经模糊，但似乎来自一位不能忘记的人。
 * - 右键：聊天栏显示"什么也没有发生..."
 * 恐慌状态（精神值<=0）：
 * - 介绍：红色乱码，悬停6秒后变成"你明明记得我，不是吗？"
 * - 右键：触发恐怖事件
 */
public class StrangeBadgeItem extends Item {

    public StrangeBadgeItem() {
        super(new Properties().fireResistant().stacksTo(1));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            final boolean[] isPanic = {false};
            mc.player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                isPanic[0] = mental.getMentalValue() <= 0;
            });
            if (isPanic[0]) {
                StrangeBadgeTooltipHandler.addPanicTooltip(tooltip);
            } else {
                tooltip.add(Component.literal("一块奇怪的工牌，上面的字迹已经模糊")
                        .withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("但似乎来自一位不能忘记的人。")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            final boolean[] isPanic = {false};
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                isPanic[0] = mental.getMentalValue() <= 0;
            });
            if (isPanic[0]) {
                MessageLoader.getLoader().sendToPlayer((ServerPlayer) player, new StrangeBadgeEffectPacket());
                SoundUtil.playModSound(player.level, ModSounds.TOUCH_OFF.get(), player);
            } else {
                player.sendSystemMessage(Component.literal("什么也没有发生...")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}