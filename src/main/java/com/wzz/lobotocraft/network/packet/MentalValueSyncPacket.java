package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class MentalValueSyncPacket implements IMessage {
    private float mentalValue;
    private float maxMentalValue;

    public MentalValueSyncPacket(float mentalValue, float maxMentalValue) {
        this.mentalValue = mentalValue;
        this.maxMentalValue = maxMentalValue;
    }

    @Override
    public boolean sendToClient() { return true; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.mentalValue = buf.readFloat();
        this.maxMentalValue = buf.readFloat();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(mentalValue);
        buf.writeFloat(maxMentalValue);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                mental.setMentalValue(mentalValue);
                mental.setMaxMentalValue(maxMentalValue);
            });
        }
    }
}