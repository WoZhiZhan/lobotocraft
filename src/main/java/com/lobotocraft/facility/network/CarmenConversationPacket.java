package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.CarmenConversationOverlay;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record CarmenConversationPacket(String message) {
    private static final int MAX_MESSAGE_LENGTH = 512;

    public static void encode(CarmenConversationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
    }

    public static CarmenConversationPacket decode(FriendlyByteBuf buffer) {
        return new CarmenConversationPacket(buffer.readUtf(MAX_MESSAGE_LENGTH));
    }

    public static void handle(CarmenConversationPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> CarmenConversationOverlay.add(packet.message)));
        context.setPacketHandled(true);
    }
}
