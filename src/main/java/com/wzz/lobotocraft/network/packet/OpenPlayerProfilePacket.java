package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.screen.FacilityManagementScreen;
import com.wzz.lobotocraft.client.screen.PlayerDepartmentScreen;
import com.wzz.lobotocraft.client.screen.SpecialEgoScreen;
import com.wzz.lobotocraft.employee.PlayerProfileService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

public final class OpenPlayerProfilePacket implements IMessage {
    private String viewId;
    private CompoundTag snapshot;

    public OpenPlayerProfilePacket() {
    }

    public OpenPlayerProfilePacket(PlayerProfileService.View view, CompoundTag snapshot) {
        this.viewId = view.id();
        this.snapshot = snapshot.copy();
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        viewId = buffer.readUtf(24);
        snapshot = buffer.readNbt();
        if (snapshot == null) snapshot = new CompoundTag();
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(viewId, 24);
        buffer.writeNbt(snapshot);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerProfileService.View view = PlayerProfileService.View.byId(viewId);
        if (view == PlayerProfileService.View.DEPARTMENT) {
            if (minecraft.screen instanceof PlayerDepartmentScreen departmentScreen) {
                departmentScreen.updateSnapshot(snapshot);
            } else {
                Screen parent = minecraft.screen instanceof FacilityManagementScreen
                        ? minecraft.screen : null;
                minecraft.setScreen(new PlayerDepartmentScreen(snapshot, parent));
            }
        } else if (view == PlayerProfileService.View.SPECIAL_EGO) {
            if (minecraft.screen instanceof SpecialEgoScreen specialEgoScreen) {
                specialEgoScreen.updateSnapshot(snapshot);
            } else {
                Screen parent = minecraft.screen instanceof FacilityManagementScreen
                        ? minecraft.screen : null;
                minecraft.setScreen(new SpecialEgoScreen(snapshot, parent));
            }
        }
    }
}
