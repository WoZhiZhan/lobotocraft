package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.world.RoomBindingAuthorizations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * 客户端 -> 服务端：手动把某只异想体指定为某个房间的住户。
 * uuid 为 null 表示"清空这个房间的登记"（清空之后下次扫描仍会自动认一只）。
 */
public class BindRoomAbnormalityPacket implements IMessage {

    private ResourceLocation dimension;
    private BlockPos roomPos;
    private UUID uuid;

    public BindRoomAbnormalityPacket() {
    }

    public BindRoomAbnormalityPacket(ResourceLocation dimension, BlockPos roomPos, UUID uuid) {
        this.dimension = dimension;
        this.roomPos = roomPos;
        this.uuid = uuid;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.roomPos = buf.readBlockPos();
        this.uuid = buf.readBoolean() ? buf.readUUID() : null;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(roomPos);
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUUID(uuid);
        }
    }

    @Override
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        if (!RequestThrottle.allow(player, "bind-room", 250L)) return;

        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.dimension_unloaded"), true);
            return;
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                player.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            player.displayClientMessage(Component.translatable(access
                    == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "message.lobotocraft.room.unexpanded"
                    : "message.lobotocraft.room.unavailable"), true);
            return;
        }
        if (!target.isLoaded(roomPos)
                || !(target.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity be)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.marker_invalid_or_unloaded"), true);
            return;
        }
        if (!RoomBindingAuthorizations.consume(player, dimension, roomPos, uuid)) return;

        if (uuid == null) {
            be.clearBinding();
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.binding_cleared"), true);
        } else if (be.bindByUuid(target, uuid)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.binding_changed",
                    be.getBoundNameComponent()), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.abnormality_missing"), true);
            return;
        }

        OpenRoomMapPacket.sendTo(player, true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
