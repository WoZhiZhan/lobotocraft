package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.work.AbnormalityWorkHandler;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * 客户端 -> 服务端：在监控界面里右键某个房间，打开那只异想体的图鉴。
 * <p>
 * 直接复用你原有的 {@link AbnormalityWorkHandler#openAbnormalityEncyclopediaScreen}，
 * 所以解锁进度、观察等级、管理须知这些全都跟平时点异想体打开的是同一套。
 * <p>
 * 需要异想体实体是加载着的（图鉴数据是从实体身上取的）；区块没加载就直接告诉玩家查不了。
 */
public class OpenRoomAbnormalityInfoPacket implements IMessage {

    private ResourceLocation dimension;
    private BlockPos roomPos;

    public OpenRoomAbnormalityInfoPacket() {
    }

    public OpenRoomAbnormalityInfoPacket(ResourceLocation dimension, BlockPos roomPos) {
        this.dimension = dimension;
        this.roomPos = roomPos;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.roomPos = buf.readBlockPos();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(roomPos);
    }

    @Override
    public void run(net.minecraftforge.network.NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        if (!RequestThrottle.allow(player, "room-abnormality-info", 250L)) return;
        if (FacilityAccessService.containmentAccess(player.server, dimension, roomPos)
                != FacilityAccessService.ContainmentAccess.OPEN) return;

        if (CompanyRoomData.get(player.serverLevel()).findRoom(dimension, roomPos) == null) return;
        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.dimension_unloaded"), true);
            return;
        }
        // 先把区块拉起来，这样离得再远也能查资料
        if (!target.isLoaded(roomPos)) return;

        if (!(target.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity be)) {
            player.displayClientMessage(
                    Component.translatable("message.lobotocraft.room.marker_read_failed"), true);
            return;
        }

        UUID bound = be.getBoundAbnormality();
        if (bound == null) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.no_occupant"), true);
            return;
        }
        Entity entity = target.getEntity(bound);
        if (!(entity instanceof IAbnormality abnormality)) {
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.room.abnormality_unavailable"), true);
            return;
        }

        String code = abnormality.getAbnormalityCode();
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data ->
                AbnormalityWorkHandler.openAbnormalityEncyclopediaScreen(player, abnormality, data, code, 0.0D));
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
