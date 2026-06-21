package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.alert.EscapeAlertManager;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：通知出逃警报等级
 * alertLevel:
 *  -1 = 无出逃，停止所有警报BGM
 *   0 = 任意异想体出逃
 *   1 = WAW/ALEPH 1~3只出逃
 *   2 = ALEPH 超过3只出逃
 */
public class EscapeAlertPacket implements IMessage {

    private int alertLevel;

    public EscapeAlertPacket(int alertLevel) {
        this.alertLevel = alertLevel;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        alertLevel = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(alertLevel);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        EscapeAlertManager.getInstance().onAlertReceived(alertLevel);
    }

    @Override
    public boolean sendToClient() { return true; }
}