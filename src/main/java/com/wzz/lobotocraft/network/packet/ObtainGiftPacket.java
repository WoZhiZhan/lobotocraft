package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class ObtainGiftPacket implements IMessage {
    private int abnormalityId;
    private String giftItemId;
    
    public ObtainGiftPacket(int abnormalityId, String giftItemId) {
        this.abnormalityId = abnormalityId;
        this.giftItemId = giftItemId;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.abnormalityId = buf.readInt();
        this.giftItemId = buf.readUtf(128);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.abnormalityId);
        buf.writeUtf(this.giftItemId, 128);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        // Wire-compatible no-op: only server-side work completion may grant gifts.
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
