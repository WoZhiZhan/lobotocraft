package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.event.ForgeModClientEvent;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class LargeBirdBorderPacket implements IMessage {
    private int ms;

    public LargeBirdBorderPacket(int ms) {
        this.ms = ms;
    }

    @Override
    public boolean sendToClient() { return true; }
    
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.ms = buf.readInt();
    }
    
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(ms);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        if (ms <= 0) {
            ForgeModClientEvent.clearAllBorderEffects();
        } else {
            ForgeModClientEvent.LargeBirdCharmEffect effect =
                    new ForgeModClientEvent.LargeBirdCharmEffect(ms);
            ForgeModClientEvent.addBorderEffect(effect);
        }
    }
}