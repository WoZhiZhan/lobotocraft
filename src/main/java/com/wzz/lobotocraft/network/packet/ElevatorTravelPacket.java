package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public final class ElevatorTravelPacket implements IMessage {
    private BlockPos elevatorPos;
    private boolean up;

    public ElevatorTravelPacket() {
    }

    public ElevatorTravelPacket(BlockPos elevatorPos, boolean up) {
        this.elevatorPos = elevatorPos.immutable();
        this.up = up;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        elevatorPos = buf.readBlockPos();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(elevatorPos);
        buf.writeBoolean(up);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender == null) return;
        if (elevatorPos == null) {
            sendFeedback(sender, ElevatorBlockEntity.TravelResult.INVALID_ENTITY, up);
            return;
        }
        if (!RequestThrottle.allow(sender, "elevator_travel", 250L)) {
            sendFeedback(sender, ElevatorBlockEntity.TravelResult.COOLDOWN, up);
            return;
        }
        if (!sender.level().hasChunkAt(elevatorPos)
                || !(sender.level().getBlockEntity(elevatorPos) instanceof ElevatorBlockEntity elevator)) {
            sendFeedback(sender, ElevatorBlockEntity.TravelResult.INVALID_ENTITY, up);
            return;
        }

        ElevatorBlockEntity.TravelResult result = elevator.tryTravel(sender, up);
        sendFeedback(sender, result, up);
    }

    public static void sendFeedback(Player player, ElevatorBlockEntity.TravelResult result,
                                    boolean up) {
        if (player == null || result == null || result == ElevatorBlockEntity.TravelResult.SUCCESS) return;
        String key = switch (result) {
            case NO_DESTINATION -> up
                    ? "message.lobotocraft.elevator.no_destination_up"
                    : "message.lobotocraft.elevator.no_destination_down";
            case TOO_FAR -> "message.lobotocraft.elevator.too_far";
            case COOLDOWN -> "message.lobotocraft.elevator.cooldown";
            case LOCKED_AREA -> "message.lobotocraft.elevator.locked_area";
            case BLOCKED_DESTINATION -> "message.lobotocraft.elevator.blocked_destination";
            case INVALID_ENTITY -> "message.lobotocraft.elevator.invalid";
            case SUCCESS -> null;
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable(key)
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
