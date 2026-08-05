package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class MentalValueSyncPacket implements IMessage {
    private float mentalValue;
    private float maxMentalValue;
    private MentalValueEvent.ChangeType changeType;
    private PanicType panicType;

    public MentalValueSyncPacket(float mentalValue, float maxMentalValue) {
        this(mentalValue, maxMentalValue, MentalValueEvent.ChangeType.ERROR);
    }

    public MentalValueSyncPacket(float mentalValue, float maxMentalValue, MentalValueEvent.ChangeType changeType) {
        this(mentalValue, maxMentalValue, changeType, null);
    }

    public MentalValueSyncPacket(float mentalValue, float maxMentalValue,
                                 MentalValueEvent.ChangeType changeType, PanicType panicType) {
        this.mentalValue = mentalValue;
        this.maxMentalValue = maxMentalValue;
        this.changeType = changeType;
        this.panicType = panicType;
    }

    @Override
    public boolean sendToClient() { return true; }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.mentalValue = buf.readFloat();
        this.maxMentalValue = buf.readFloat();
        this.changeType = buf.readEnum(MentalValueEvent.ChangeType.class);
        this.panicType = buf.readBoolean() ? buf.readEnum(PanicType.class) : null;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(mentalValue);
        buf.writeFloat(maxMentalValue);
        buf.writeEnum(changeType);
        buf.writeBoolean(panicType != null);
        if (panicType != null) buf.writeEnum(panicType);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                mental.setMentalValue(mentalValue, changeType);
                mental.setMaxMentalValue(maxMentalValue);
                if (panicType != null) mental.setPanicType(panicType);
            });
        }
    }
}
