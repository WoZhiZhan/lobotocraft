package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class CoreSuppressionSyncPacket implements IMessage {
    private boolean active;
    private int type;
    private String ownerName;
    private int dawnCompleted;
    private int middayCompleted;
    private int workCompleted;
    private int workRequired;
    private int visualStage;
    private int completedMask;

    public CoreSuppressionSyncPacket(boolean active, int type, String ownerName,
                                     int dawnCompleted, int middayCompleted,
                                     int workCompleted, int workRequired,
                                     int visualStage, int completedMask) {
        this.active = active;
        this.type = type;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.dawnCompleted = dawnCompleted;
        this.middayCompleted = middayCompleted;
        this.workCompleted = workCompleted;
        this.workRequired = workRequired;
        this.visualStage = visualStage;
        this.completedMask = completedMask;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        active = buf.readBoolean();
        type = buf.readInt();
        ownerName = buf.readUtf();
        dawnCompleted = buf.readInt();
        middayCompleted = buf.readInt();
        workCompleted = buf.readInt();
        workRequired = buf.readInt();
        visualStage = buf.readInt();
        completedMask = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeInt(type);
        buf.writeUtf(ownerName);
        buf.writeInt(dawnCompleted);
        buf.writeInt(middayCompleted);
        buf.writeInt(workCompleted);
        buf.writeInt(workRequired);
        buf.writeInt(visualStage);
        buf.writeInt(completedMask);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        CoreSuppressionClientState.update(active, type, ownerName, dawnCompleted, middayCompleted,
                workCompleted, workRequired, visualStage, completedMask);
    }
}
