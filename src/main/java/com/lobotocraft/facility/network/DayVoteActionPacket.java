package com.lobotocraft.facility.network;

import com.lobotocraft.facility.day.DayTransitionService;
import com.lobotocraft.facility.day.DayVoteState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record DayVoteActionPacket(Action action) {
    public enum Action { START, APPROVE, REJECT }

    public static void encode(DayVoteActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
    }

    public static DayVoteActionPacket decode(FriendlyByteBuf buffer) {
        return new DayVoteActionPacket(buffer.readEnum(Action.class));
    }

    public static void handle(DayVoteActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> {
            switch (packet.action) {
                case START -> DayTransitionService.requestStart(sender);
                case APPROVE -> DayTransitionService.cast(sender, DayVoteState.Vote.APPROVE);
                case REJECT -> DayTransitionService.cast(sender, DayVoteState.Vote.REJECT);
            }
        });
        context.setPacketHandled(true);
    }
}
