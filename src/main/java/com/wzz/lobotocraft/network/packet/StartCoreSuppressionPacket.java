package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.entity.EntityCoreSuppressionNpc;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class StartCoreSuppressionPacket implements IMessage {
    private int type;
    private int entityId;

    public StartCoreSuppressionPacket(int type, int entityId) {
        this.type = type;
        this.entityId = entityId;
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        type = buf.readInt();
        entityId = buf.readInt();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(type);
        buf.writeInt(entityId);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        CoreSuppressionType requested = CoreSuppressionType.byOrdinal(type);
        if (player == null || requested == null) return;
        if (!(player.level().getEntity(entityId) instanceof EntityCoreSuppressionNpc npc)
                || npc.getCoreSuppressionType() != requested
                || player.distanceToSqr(npc) > 64.0D) {
            return;
        }
        CoreSuppressionManager.startChallenge(player, requested);
    }
}
