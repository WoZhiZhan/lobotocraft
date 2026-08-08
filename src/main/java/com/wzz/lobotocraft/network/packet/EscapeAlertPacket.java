package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.alert.EscapeAlertManager;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：通知设施紧急警报等级
 * alertLevel:
 *  -1 = NORMAL，停止警报BGM
 *   0 = LEVEL I
 *   1 = LEVEL II
 *   2 = LEVEL III
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
