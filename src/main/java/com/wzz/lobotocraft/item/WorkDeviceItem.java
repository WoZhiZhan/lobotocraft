package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WorkDeviceItem extends Item {
    private static final String ENABLED_TAG = "lobotocraft_work_device_enabled";

    public WorkDeviceItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean enabled = !isEnabled(stack);
            setEnabled(stack, enabled);
            player.sendSystemMessage(Component.literal(enabled
                    ? "§a工作装置已开启：下一次工作将进入连续工作模式。"
                    : "§7工作装置已关闭。"));
            if (!enabled && player instanceof ServerPlayer serverPlayer
                    && WorkManager.isPlayerWorking(serverPlayer)) {
                WorkManager.requestStopContinuousWork(serverPlayer, "工作装置已关闭");
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isEnabled(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("开启后，下一次工作会进入连续工作模式。").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("连续工作会在本次工作完成后自动重复相同工作。").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(isEnabled(stack) ? "状态：已开启" : "状态：已关闭")
                .withStyle(isEnabled(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    public static boolean hasEnabledDevice(Player player) {
        if (player == null) return false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WorkDeviceItem && isEnabled(stack)) {
                return true;
            }
        }
        return false;
    }

    public static void disableAll(Player player) {
        if (player == null) return;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WorkDeviceItem) {
                setEnabled(stack, false);
            }
        }
    }

    public static boolean isEnabled(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().getBoolean(ENABLED_TAG);
    }

    private static void setEnabled(ItemStack stack, boolean enabled) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(ENABLED_TAG, enabled);
    }
}
