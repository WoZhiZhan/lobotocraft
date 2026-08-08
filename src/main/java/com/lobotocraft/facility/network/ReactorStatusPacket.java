package com.lobotocraft.facility.network;

import com.lobotocraft.facility.client.ClientReactorState;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record ReactorStatusPacket(BlockPos reactorPos, float progress, boolean failed) {
    public static void encode(ReactorStatusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.reactorPos);
        buffer.writeFloat(packet.progress);
        buffer.writeBoolean(packet.failed);
    }

    public static ReactorStatusPacket decode(FriendlyByteBuf buffer) {
        return new ReactorStatusPacket(buffer.readBlockPos(), buffer.readFloat(), buffer.readBoolean());
    }

    public static void handle(ReactorStatusPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientReactorState.update(packet.reactorPos, packet.progress, packet.failed)));
        context.setPacketHandled(true);
    }
}
