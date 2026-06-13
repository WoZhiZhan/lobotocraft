package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

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
        this.giftItemId = buf.readUtf();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.abnormalityId);
        buf.writeUtf(this.giftItemId);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            Entity entity = player.level.getEntity(abnormalityId);
            if (!(entity instanceof IAbnormality abnormality)) {
                player.sendSystemMessage(Component.literal("§c饰品获得失败：异想体消失或离你过远"));
                return;
            }
            ItemStack curio = new ItemStack(
                    ForgeRegistries.ITEMS.getValue(ResourceUtil.createInstance(abnormality.getEGOGiftData().itemId()))
            );
            if (!player.inventory.add(curio)) {
                player.drop(curio, false);
            }
        });
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
