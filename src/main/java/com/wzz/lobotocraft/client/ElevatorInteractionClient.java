package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ElevatorTravelPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class ElevatorInteractionClient {
    private static final int HOLD_TICKS = 5;
    private static final int BAR_SEGMENTS = 10;

    private static BlockPos activeElevator;
    private static Direction heldDirection;
    private static int heldTicks;
    private static boolean latchedUntilRelease;

    private ElevatorInteractionClient() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || event.getEntity() != minecraft.player
                || findNearestElevator(minecraft.player) == null) return;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            reset();
            return;
        }

        BlockPos elevator = findNearestElevator(player);
        if (elevator == null) {
            reset();
            return;
        }
        if (!elevator.equals(activeElevator)) {
            activeElevator = elevator;
            heldDirection = null;
            heldTicks = 0;
        }

        boolean space = minecraft.options.keyJump.isDown();
        boolean shift = minecraft.options.keyShift.isDown();
        if (!space && !shift) {
            heldDirection = null;
            heldTicks = 0;
            latchedUntilRelease = false;
            showPrompt(player);
            return;
        }
        if (space && shift) {
            heldDirection = null;
            heldTicks = 0;
            player.displayClientMessage(Component.translatable(
                            "message.lobotocraft.elevator.choose_direction")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (latchedUntilRelease) {
            player.displayClientMessage(Component.translatable(
                            "message.lobotocraft.elevator.release_to_reuse")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        Direction direction = space ? Direction.UP : Direction.DOWN;
        if (direction != heldDirection) {
            heldDirection = direction;
            heldTicks = 0;
        }
        heldTicks = Math.min(HOLD_TICKS, heldTicks + 1);
        showProgress(player, direction, heldTicks);
        if (heldTicks >= HOLD_TICKS) {
            MessageLoader.getLoader().sendToServer(new ElevatorTravelPacket(activeElevator, direction == Direction.UP));
            latchedUntilRelease = true;
        }
    }

    private static void showPrompt(LocalPlayer player) {
        Component prompt = Component.translatable("message.lobotocraft.elevator.prompt",
                Component.literal("[SPACE]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.literal("[SHIFT]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.displayClientMessage(prompt, true);
    }

    private static void showProgress(LocalPlayer player, Direction direction, int progress) {
        ChatFormatting color = direction == Direction.UP ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        int filledSegments = Math.min(BAR_SEGMENTS,
                (progress * BAR_SEGMENTS + HOLD_TICKS - 1) / HOLD_TICKS);
        Component bar = Component.translatable(direction == Direction.UP
                        ? "message.lobotocraft.elevator.direction.up"
                        : "message.lobotocraft.elevator.direction.down")
                .withStyle(color, ChatFormatting.BOLD)
                .append(Component.literal("I".repeat(filledSegments)).withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal("I".repeat(BAR_SEGMENTS - filledSegments))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        player.displayClientMessage(bar, true);
    }

    private static BlockPos findNearestElevator(LocalPlayer player) {
        if (player == null) return null;
        BlockPos origin = player.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(4, 4, 4))) {
            if (!(player.level().getBlockEntity(candidate) instanceof ElevatorBlockEntity)) continue;
            double distance = player.position().distanceToSqr(Vec3.atCenterOf(candidate.above()));
            if (distance <= ElevatorBlockEntity.INTERACTION_RADIUS * ElevatorBlockEntity.INTERACTION_RADIUS
                    && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate.immutable();
            }
        }
        return nearest;
    }

    private static void reset() {
        activeElevator = null;
        heldDirection = null;
        heldTicks = 0;
        latchedUntilRelease = false;
    }

    private enum Direction { UP, DOWN }
}
