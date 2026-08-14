package com.wzz.lobotocraft.item;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
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
            if (CoreSuppressionManager.isDeviceRestricted(player)) {
                setEnabled(stack, false);
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.work_device.core_suppression_restricted"));
                return InteractionResultHolder.fail(stack);
            }
            boolean enabled = !isEnabled(stack);
            setEnabled(stack, enabled);
            player.sendSystemMessage(Component.translatable(enabled
                    ? "message.lobotocraft.work_device.enabled"
                    : "message.lobotocraft.work_device.disabled"));
            if (!enabled && player instanceof ServerPlayer serverPlayer
                    && WorkManager.isPlayerWorking(serverPlayer)) {
                WorkManager.requestStopContinuousWork(serverPlayer, Component.translatable(
                        "message.lobotocraft.work_device.disabled_reason"));
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
        tooltip.add(Component.translatable("tooltip.lobotocraft.work_device.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.lobotocraft.work_device.2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(isEnabled(stack)
                        ? "tooltip.lobotocraft.work_device.enabled"
                        : "tooltip.lobotocraft.work_device.disabled")
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
