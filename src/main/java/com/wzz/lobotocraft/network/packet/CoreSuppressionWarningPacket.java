package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.core.CoreSuppressionWarningClientState;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class CoreSuppressionWarningPacket implements IMessage {
    private int type;
    private int remainingSeconds;
    private int confirmed;
    private int total;
    private boolean needsChoice;

    public CoreSuppressionWarningPacket(int type, int remainingSeconds,
                                        int confirmed, int total, boolean needsChoice) {
        this.type = type;
        this.remainingSeconds = remainingSeconds;
        this.confirmed = confirmed;
        this.total = total;
        this.needsChoice = needsChoice;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        type = buf.readInt();
        remainingSeconds = buf.readVarInt();
        confirmed = buf.readVarInt();
        total = buf.readVarInt();
        needsChoice = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(type);
        buf.writeVarInt(remainingSeconds);
        buf.writeVarInt(confirmed);
        buf.writeVarInt(total);
        buf.writeBoolean(needsChoice);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        CoreSuppressionWarningClientState.update(
                type, remainingSeconds, confirmed, total, needsChoice);
    }
}
