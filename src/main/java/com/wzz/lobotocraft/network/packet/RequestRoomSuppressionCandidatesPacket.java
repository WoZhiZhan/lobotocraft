package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.RoomEmployeeSnapshotService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;

public final class RequestRoomSuppressionCandidatesPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;

    public RequestRoomSuppressionCandidatesPacket() { }

    public RequestRoomSuppressionCandidatesPacket(ResourceLocation dimension, BlockPos roomPos) {
        this.dimension = dimension;
        this.roomPos = roomPos;
    }

    @Override public void fromBytes(FriendlyByteBuf buffer) {
        dimension = buffer.readResourceLocation();
        roomPos = buffer.readBlockPos();
    }
    @Override public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(roomPos);
    }
    @Override public boolean sendToServer() { return true; }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || !RequestThrottle.allow(sender, "room_suppression_candidates", 150L)) return;
        if (!AbnormalitySpawnHelper.isCompany(sender.level())
                || !sender.serverLevel().dimension().location().equals(dimension)) {
            reply(sender, List.of(), "请回到公司后再下达镇压任务");
            return;
        }
        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || FacilityAccessService.containmentAccess(sender.server, dimension, roomPos)
                != FacilityAccessService.ContainmentAccess.OPEN
                || !(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            reply(sender, List.of(), "收容单元当前不可用");
            return;
        }
        Entity entity = marker.getBoundAbnormality() == null ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()
                || !abnormality.hasEscape()) {
            reply(sender, List.of(), "异想体当前未处于出逃状态");
            return;
        }
        reply(sender, RoomEmployeeSnapshotService.collectForSuppression(sender, abnormality), "");
    }

    private void reply(ServerPlayer sender,
                       List<com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot> employees,
                       String error) {
        MessageLoader.getLoader().sendToPlayer(sender,
                new RoomSuppressionCandidatesPacket(roomPos, employees, error));
    }
}
