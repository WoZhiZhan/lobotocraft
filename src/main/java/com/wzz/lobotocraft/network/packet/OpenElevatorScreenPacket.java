package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.ElevatorScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenElevatorScreenPacket implements IMessage {
    private BlockPos pos;
    private int teleportDistance;
    private boolean teleportUp;

    public OpenElevatorScreenPacket(BlockPos pos, int teleportDistance, boolean teleportUp) {
        this.pos = pos;
        this.teleportDistance = teleportDistance;
        this.teleportUp = teleportUp;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.teleportDistance = buf.readInt();
        this.teleportUp = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(teleportDistance);
        buf.writeBoolean(teleportUp);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen(
                    new ElevatorScreen(pos, teleportDistance, teleportUp)
            );
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
