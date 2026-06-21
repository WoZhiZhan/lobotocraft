package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class LockInputPacket implements IMessage {
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (Minecraft.getInstance().player != null) {
            LocalPlayer player = Minecraft.getInstance().player;
            player.input.leftImpulse = 0;
            player.input.forwardImpulse = 0;
            player.input.jumping = false;
            player.input.shiftKeyDown = false;
            player.input.up = false;
            player.input.down = false;
        }
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
