package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** The monitor requests an action on a room, never names a sacrifice victim. */
public final class RequestUniqueRoomWorkPacket implements IMessage {
    private ResourceLocation dimension;
    private BlockPos roomPos;
    private int slot;
    public RequestUniqueRoomWorkPacket() {}
    public RequestUniqueRoomWorkPacket(ResourceLocation dimension, BlockPos roomPos) {
        this(dimension, roomPos, 3);
    }
    public RequestUniqueRoomWorkPacket(ResourceLocation dimension, BlockPos roomPos, int slot) {
        this.dimension = dimension;
        this.roomPos = roomPos;
        this.slot = slot;
    }
    @Override public void fromBytes(FriendlyByteBuf buf) {
        dimension = buf.readResourceLocation();
        roomPos = buf.readBlockPos();
        slot = buf.readUnsignedByte();
    }
    @Override public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(roomPos);
        buf.writeByte(slot);
    }
    @Override public boolean sendToServer() { return true; }
    @Override public void run(NetworkEvent.Context context) {
        var player = context.getSender();
        if (player == null || player.level().dimension() != ModDimensions.LOBOTO_KEY
                || !player.level().dimension().location().equals(dimension)
                || !RequestThrottle.allow(player, "unique_room_work", 250L)) return;
        if (FacilityAccessService.containmentAccess(player.server, dimension, roomPos)
                != FacilityAccessService.ContainmentAccess.OPEN) return;
        if (!(player.serverLevel().getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity marker)
                || marker.getBoundAbnormality() == null) return;
        if (slot < 0 || slot > 3) return;
        if (player.serverLevel().getEntity(marker.getBoundAbnormality()) instanceof AbstractAbnormality source) {
            if (com.wzz.lobotocraft.work.AbnormalityCommandPolicy.action(source.getAbnormalityCode(), slot)
                    == com.wzz.lobotocraft.work.AbnormalityCommandPolicy.Action.PROTECTION) return;
            com.wzz.lobotocraft.work.AbnormalityCommandPolicy.dispatch(
                    player, source, slot);
        }
    }
}
