package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityManagementService;
import com.wzz.lobotocraft.network.IMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class RequestFacilityManagementPacket implements IMessage {
    private boolean openEgoAssignment;
    private boolean returnNavigation;

    public RequestFacilityManagementPacket() {
    }

    public RequestFacilityManagementPacket(boolean openEgoAssignment) {
        this.openEgoAssignment = openEgoAssignment;
    }

    private RequestFacilityManagementPacket(boolean openEgoAssignment, boolean returnNavigation) {
        this.openEgoAssignment = openEgoAssignment;
        this.returnNavigation = returnNavigation;
    }

    public static RequestFacilityManagementPacket returnToFacility() {
        return new RequestFacilityManagementPacket(false, true);
    }

    public static RequestFacilityManagementPacket returnToEgoAssignment() {
        return new RequestFacilityManagementPacket(true, true);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        openEgoAssignment = buf.readBoolean();
        returnNavigation = buf.readBoolean();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(openEgoAssignment);
        buf.writeBoolean(returnNavigation);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        // 快照构建是重活（遍历全部玩家/员工/E.G.O 仓库并打包 NBT），加冷却防刷
        if (player == null) return;
        boolean allowed = returnNavigation
                ? com.wzz.lobotocraft.network.RequestThrottle.allowNavigation(player,
                        "facility_return_navigation")
                : com.wzz.lobotocraft.network.RequestThrottle.allowSnapshot(player, "facility");
        if (!allowed) {
            return;
        }
        if (openEgoAssignment) FacilityManagementService.openEgoAssignment(player);
        else FacilityManagementService.open(player);
    }

    boolean opensEgoAssignment() {
        return openEgoAssignment;
    }

    boolean isReturnNavigation() {
        return returnNavigation;
    }
}
