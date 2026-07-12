package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public class DisplayItemActivationPacket implements IMessage {
    private ItemStack itemStack;

    public DisplayItemActivationPacket(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.itemStack = buf.readItem();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItem(itemStack);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft.getInstance().gameRenderer.displayItemActivation(itemStack);
    }
}