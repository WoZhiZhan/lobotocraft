package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenChatScreenPacket implements IMessage {

    public OpenChatScreenPacket() {
    }

    @Override
    public boolean sendToClient() { return true; }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen) && !(Minecraft.getInstance().screen instanceof PauseScreen) && Minecraft.getInstance().player != null &&
                !Minecraft.getInstance().player.isDeadOrDying()) {
            Minecraft.getInstance().setScreen(new ChatScreen(""));
        }
    }
}