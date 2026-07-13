package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端按 R -> 服务端切换「宣判」状态。
 * 无载荷，所以 fromBytes / toBytes 都是空的。
 * 必须保留公开无参构造，MessageCreater 反序列化时要用。
 */
public class ToggleJudgementPacket implements IMessage {

    public ToggleJudgementPacket() {
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        ButterflyFuneralWeapon.toggleJudgement(player);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}