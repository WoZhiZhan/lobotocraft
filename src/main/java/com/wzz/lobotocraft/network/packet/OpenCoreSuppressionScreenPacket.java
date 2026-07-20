package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.CoreSuppressionDialogueScreen;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class OpenCoreSuppressionScreenPacket implements IMessage {
    private int type;
    private int entityId;

    public OpenCoreSuppressionScreenPacket(int type, int entityId) {
        this.type = type;
        this.entityId = entityId;
    }

    @Override
    public boolean sendToClient() {
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

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        CoreSuppressionType suppressionType = CoreSuppressionType.byOrdinal(type);
        if (minecraft.level == null || suppressionType == null) return;
        if (minecraft.level.getEntity(entityId) instanceof LivingEntity npc) {
            minecraft.setScreen(new CoreSuppressionDialogueScreen(suppressionType, entityId, npc));
        }
    }
}
