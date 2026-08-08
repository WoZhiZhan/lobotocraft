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
            player.displayClientMessage(Component.literal("§c目标维度未加载。"), true);
            return;
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                player.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            player.displayClientMessage(Component.literal(access == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "§7该收容单元尚未扩建。" : "§c该收容单元当前不可用。"), true);
            return;
        }
        if (!target.isLoaded(roomPos)
                || !(target.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity be)) {
            player.displayClientMessage(Component.literal("§c该收容间信标已失效或未加载。"), true);
            return;
        }
        if (!RoomBindingAuthorizations.consume(player, dimension, roomPos, uuid)) return;

        if (uuid == null) {
            be.clearBinding();
            player.displayClientMessage(Component.literal("§7已清空该收容间的登记。"), true);
        } else if (be.bindByUuid(target, uuid)) {
            player.displayClientMessage(Component.literal(
                    "§b[收容间信标] §7住户已改为：§f" + be.getBoundName()), true);
        } else {
            player.displayClientMessage(Component.literal("§c目标异想体已不存在。"), true);
            return;
        }

        OpenRoomMapPacket.sendTo(player, true);
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
