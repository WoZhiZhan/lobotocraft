package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.RequestThrottle;
import com.wzz.lobotocraft.work.UniqueWorkRequest;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

public final class CommitUniqueCommandPacket implements IMessage {
    private UUID source;
    private UUID target;
    public CommitUniqueCommandPacket() {}
    public CommitUniqueCommandPacket(UUID source, UUID target) { this.source = source; this.target = target; }
    @Override public void toBytes(FriendlyByteBuf buf) { buf.writeUUID(source); buf.writeUUID(target); }
    @Override public void fromBytes(FriendlyByteBuf buf) { source = buf.readUUID(); target = buf.readUUID(); }
    @Override public boolean sendToServer() { return true; }
    @Override public void run(NetworkEvent.Context context) {
        var player = context.getSender();
        if (player == null || !RequestThrottle.allow(player, "commit_unique_command", 250L)) return;
        if (!(player.serverLevel().getEntity(source) instanceof EntityRedHoodMercenary hood)
                || hood.getHomeRoomMarker() == null || hood.getHomeRoomDimension() == null) return;
        if (FacilityAccessService.containmentAccess(player.server, hood.getHomeRoomDimension(),
                hood.getHomeRoomMarker()) != FacilityAccessService.ContainmentAccess.OPEN) return;
        if (!(player.serverLevel().getEntity(target) instanceof LivingEntity living)) return;
        String error = UniqueWorkRequest.commitCommission(player, hood, living);
        player.sendSystemMessage(Component.translatable(
                error == null ? "message.lobotocraft.commission.started" : error));
    }
}
