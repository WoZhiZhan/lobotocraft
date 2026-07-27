package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.network.packet.OpenRoomMapPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 收容间监控终端。
 * 右键 -> 视角升空俯冲成俯视图（过渡动画）-> 打开二维设施平面图 -> 点击任意房间传送过去。
 */
public class RoomTerminalItem extends Item {

    public RoomTerminalItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            OpenRoomMapPacket.sendTo(serverPlayer, false);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键切换到设施监控视角").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("在平面图上点击房间即可传送").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("只显示放置了「收容间信标」的房间").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
