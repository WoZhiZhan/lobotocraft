package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端：在监控界面里给楼层改名。
 * 名字存在存档级的 {@link CompanyRoomData} 里，所以那一层区块没加载也照样能改。
 */
public class SetFloorNamePacket implements IMessage {

    private ResourceLocation dimension;
    private BlockPos floorPos;
    private String name;

    public SetFloorNamePacket() {
    }

    public SetFloorNamePacket(ResourceLocation dimension, BlockPos floorPos, String name) {
        this.dimension = dimension;
        this.floorPos = floorPos;
        this.name = name;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.floorPos = buf.readBlockPos();
        this.name = buf.readUtf(48);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(floorPos);
        buf.writeUtf(name, 48);
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        if (!RequestThrottle.allow(player, "rename-floor", 250L)) return;

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 24) {
            trimmed = trimmed.substring(0, 24);
        }

        boolean ok = CompanyRoomData.get(player.serverLevel()).renameFloor(dimension, floorPos, trimmed);
        if (!ok) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.floor.marker_invalid"), true);
            return;
        }
        OpenRoomMapPacket.sendTo(player, true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
