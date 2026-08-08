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
    private int energy;
    private int energyQuota;
    private int meltdownLevel;
    private int requiredMeltdownLevel;
    private int visualStage;
    private int completedMask;
    private boolean fullVisualEffects;

    public CoreSuppressionSyncPacket(boolean active, int type, String ownerName,
                                     int energy, int energyQuota,
                                     int meltdownLevel, int requiredMeltdownLevel,
                                     int visualStage, int completedMask,
                                     boolean fullVisualEffects) {
        this.active = active;
        this.type = type;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.energy = energy;
        this.energyQuota = energyQuota;
        this.meltdownLevel = meltdownLevel;
        this.requiredMeltdownLevel = requiredMeltdownLevel;
        this.visualStage = visualStage;
        this.completedMask = completedMask;
        this.fullVisualEffects = fullVisualEffects;
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
        energy = buf.readInt();
        energyQuota = buf.readInt();
        meltdownLevel = buf.readInt();
        requiredMeltdownLevel = buf.readInt();
        visualStage = buf.readInt();
        completedMask = buf.readInt();
        fullVisualEffects = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeInt(type);
        buf.writeUtf(ownerName);
        buf.writeInt(energy);
        buf.writeInt(energyQuota);
        buf.writeInt(meltdownLevel);
        buf.writeInt(requiredMeltdownLevel);
        buf.writeInt(visualStage);
        buf.writeInt(completedMask);
        buf.writeBoolean(fullVisualEffects);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        CoreSuppressionClientState.update(active, type, ownerName, energy, energyQuota,
                meltdownLevel, requiredMeltdownLevel, visualStage, completedMask,
                fullVisualEffects);
    }
}
