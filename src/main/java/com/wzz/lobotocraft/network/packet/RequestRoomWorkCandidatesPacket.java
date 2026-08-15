package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.RoomEmployeeSnapshotService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.work.WorkType;
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
import java.util.UUID;

public class RequestRoomWorkCandidatesPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;
    private WorkType workType;

    public RequestRoomWorkCandidatesPacket() {
    }

    public RequestRoomWorkCandidatesPacket(ResourceLocation dimension, BlockPos roomPos,
                                           WorkType workType) {
        this.dimension = dimension;
        this.roomPos = roomPos;
        this.workType = workType;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buffer) {
        dimension = buffer.readResourceLocation();
        roomPos = buffer.readBlockPos();
        workType = buffer.readEnum(WorkType.class);
    }

    @Override
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(roomPos);
        buffer.writeEnum(workType);
    }

    @Override
    public void run(NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null
                || !com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(sender.level())) return;
        if (!RequestThrottle.allow(sender, "room_work_candidates", 150L)) return;
        ServerLevel level = sender.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            reply(sender, List.of(), "message.lobotocraft.monitor.dispatch.room_invalid");
            return;
        }
        FacilityAccessService.ContainmentAccess access = FacilityAccessService.containmentAccess(
                sender.server, dimension, roomPos);
        if (access != FacilityAccessService.ContainmentAccess.OPEN) {
            reply(sender, List.of(), access == FacilityAccessService.ContainmentAccess.UNEXPANDED
                    ? "message.lobotocraft.monitor.dispatch.room_unexpanded"
                    : "message.lobotocraft.monitor.dispatch.room_unavailable");
            return;
        }
        if (!(level.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)) {
            reply(sender, List.of(), "message.lobotocraft.monitor.dispatch.room_invalid");
            return;
        }
        UUID bound = marker.getBoundAbnormality();
        Entity entity = bound == null ? null : level.getEntity(bound);
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()) {
            reply(sender, List.of(), "message.lobotocraft.monitor.dispatch.abnormality_unavailable");
            return;
        }
        if (false && abnormality.isToolType()) {
            reply(sender, List.of(), "message.lobotocraft.monitor.dispatch.tool_not_workable");
            return;
        }
        reply(sender, RoomEmployeeSnapshotService.collect(sender, abnormality, workType), "");
    }

    private void reply(ServerPlayer sender,
                       List<com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot> employees,
                       String error) {
        MessageLoader.getLoader().sendToPlayer(sender,
                new RoomWorkCandidatesPacket(roomPos, workType, employees, error));
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
