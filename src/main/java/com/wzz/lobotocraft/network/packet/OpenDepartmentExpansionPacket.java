package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.DepartmentExpansionScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class OpenDepartmentExpansionPacket implements IMessage {
    private CompoundTag snapshot = new CompoundTag();
    private boolean forceOpen;

    public OpenDepartmentExpansionPacket() {
    }

    public OpenDepartmentExpansionPacket(CompoundTag snapshot, boolean forceOpen) {
        this.snapshot = snapshot.copy();
        this.forceOpen = forceOpen;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        CompoundTag decoded = buffer.readNbt();
        snapshot = decoded == null ? new CompoundTag() : decoded;
        forceOpen = buffer.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeNbt(snapshot);
        buffer.writeBoolean(forceOpen);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DepartmentExpansionScreen screen) {
            screen.updateSnapshot(snapshot);
        } else if (forceOpen) {
            minecraft.setScreen(new DepartmentExpansionScreen(snapshot));
        }
    }
}
