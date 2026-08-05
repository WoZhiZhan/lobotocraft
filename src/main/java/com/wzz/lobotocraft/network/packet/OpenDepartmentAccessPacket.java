package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.DepartmentAccessScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class OpenDepartmentAccessPacket implements IMessage {
    private CompoundTag snapshot = new CompoundTag();
    private boolean forceOpen;

    public OpenDepartmentAccessPacket() {
    }

    public OpenDepartmentAccessPacket(CompoundTag snapshot) {
        this(snapshot, true);
    }

    public OpenDepartmentAccessPacket(CompoundTag snapshot, boolean forceOpen) {
        this.snapshot = snapshot.copy();
        this.forceOpen = forceOpen;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        CompoundTag received = buffer.readNbt();
        snapshot = received == null ? new CompoundTag() : received;
        forceOpen = buffer.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeNbt(snapshot);
        buffer.writeBoolean(forceOpen);
    }

    @Override public boolean sendToClient() { return true; }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DepartmentAccessScreen screen) {
            screen.updateSnapshot(snapshot);
        } else if (forceOpen) {
            minecraft.setScreen(new DepartmentAccessScreen(snapshot));
        }
    }
}
