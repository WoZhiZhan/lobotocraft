package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.WorkProgressScreen;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenWorkProgressPacket implements IMessage {
    private int abnormalityId;
    private WorkType workType;
    private float successRate;
    private int maxExtractions;
    private int entityId;
    private int observationLevel;
    private boolean continuousMode;

    public OpenWorkProgressPacket(int abnormalityId, WorkType workType, float successRate,
                                  int maxExtractions, int entityId, int observationLevel,
                                  boolean continuousMode) {
        this.abnormalityId = abnormalityId;
        this.workType = workType;
        this.successRate = successRate;
        this.maxExtractions = maxExtractions;
        this.entityId = entityId;
        this.observationLevel = observationLevel;
        this.continuousMode = continuousMode;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.workType = WorkType.values()[buf.readInt()];
        this.successRate = buf.readFloat();
        this.maxExtractions = buf.readInt();
        this.entityId = buf.readInt();
        this.observationLevel = buf.readInt();
        this.continuousMode = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(abnormalityId);
        buf.writeInt(workType.ordinal());
        buf.writeFloat(successRate);
        buf.writeInt(maxExtractions);
        buf.writeInt(entityId);
        buf.writeInt(observationLevel);
        buf.writeBoolean(continuousMode);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().setScreen(new WorkProgressScreen(
                abnormalityId, workType, successRate, maxExtractions, entityId,
                observationLevel, continuousMode));
    }
}
