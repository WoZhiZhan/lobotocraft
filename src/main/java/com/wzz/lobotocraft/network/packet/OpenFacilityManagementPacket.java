package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.FacilityManagementScreen;
import com.wzz.lobotocraft.client.screen.DepartmentAssignmentScreen;
import com.wzz.lobotocraft.client.screen.EgoAssignmentScreen;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class OpenFacilityManagementPacket implements IMessage {
    private CompoundTag snapshot;

    public OpenFacilityManagementPacket() {
    }

    public OpenFacilityManagementPacket(CompoundTag snapshot) {
        this.snapshot = snapshot.copy();
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        snapshot = buf.readNbt();
        if (snapshot == null) snapshot = new CompoundTag();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(snapshot);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot.getBoolean("OpenDepartmentAssignment")) {
            if (minecraft.screen instanceof DepartmentAssignmentScreen deployment) {
                deployment.updateSnapshot(snapshot);
            } else {
                minecraft.setScreen(new DepartmentAssignmentScreen(snapshot));
            }
        } else if (snapshot.getBoolean("OpenEgoAssignment")) {
            if (minecraft.screen instanceof EgoAssignmentScreen assignment) {
                assignment.updateSnapshot(snapshot);
            } else {
                net.minecraft.client.gui.screens.Screen returnScreen =
                        minecraft.screen instanceof DepartmentAssignmentScreen
                                || minecraft.screen instanceof FacilityManagementScreen
                                ? minecraft.screen : null;
                minecraft.setScreen(new EgoAssignmentScreen(snapshot, returnScreen));
            }
        } else {
            if (minecraft.screen instanceof FacilityManagementScreen management) {
                management.updateSnapshot(snapshot);
            } else {
                minecraft.setScreen(new FacilityManagementScreen(snapshot));
            }
        }
    }
}
