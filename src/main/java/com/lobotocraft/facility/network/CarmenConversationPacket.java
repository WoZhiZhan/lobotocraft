package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.CarmenConversationOverlay;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record CarmenConversationPacket(Component message) {
    public static void encode(CarmenConversationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeComponent(packet.message);
    }

    public static CarmenConversationPacket decode(FriendlyByteBuf buffer) {
        return new CarmenConversationPacket(buffer.readComponent());
    }

    public static void handle(CarmenConversationPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> CarmenConversationOverlay.add(packet.message)));
        context.setPacketHandled(true);
    }
}
