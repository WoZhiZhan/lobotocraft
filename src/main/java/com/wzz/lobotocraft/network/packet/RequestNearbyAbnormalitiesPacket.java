package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import com.wzz.lobotocraft.world.data.AbnormalityCandidate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 客户端 -> 服务端：Ctrl+右键房间，要一份附近异想体的清单，用来手动改这个房间的住户。
 * <p>
 * 自动识别在绝大多数情况下够用，这个只是兜底 —— 比如玩家把两只异想体放进了同一个房间、
 * 或者收容单元把 A 放进了 B 的房间。
 */
public class RequestNearbyAbnormalitiesPacket implements IMessage {

    /** 搜索范围 */
    private static final int RADIUS_XZ = 64;
    private static final int RADIUS_Y = 32;
    private static final int MAX_RESULTS = 20;

    private ResourceLocation dimension;
    private BlockPos roomPos;

    public RequestNearbyAbnormalitiesPacket() {
    }

    public RequestNearbyAbnormalitiesPacket(ResourceLocation dimension, BlockPos roomPos) {
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
    public void run(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            player.displayClientMessage(Component.literal("§c目标维度未加载。"), true);
            return;
        }
        RoomChunkTickets.keepAlive(target, roomPos);
        target.getChunk(roomPos.getX() >> 4, roomPos.getZ() >> 4);

        if (!(target.getBlockEntity(roomPos) instanceof RoomMarkerBlockEntity be)) {
            player.displayClientMessage(
                    Component.literal("§7该房间的信标读取失败，请稍后再试。"), true);
            return;
        }

        UUID bound = be.getBoundAbnormality();
        AABB box = new AABB(roomPos).inflate(RADIUS_XZ, RADIUS_Y, RADIUS_XZ);
        AABB roomBox = be.getScanBox();

        List<AbnormalityCandidate> candidates = new ArrayList<>();
        for (AbstractAbnormality abnormality : target.getEntitiesOfClass(
                AbstractAbnormality.class, box, e -> e.isAlive() && !e.isRemoved())) {
            AbnormalityCandidate candidate = new AbnormalityCandidate();
            candidate.uuid = abnormality.getUUID();
            candidate.code = abnormality.getAbnormalityCode() == null ? "" : abnormality.getAbnormalityCode();
            candidate.name = abnormality.getAbnormalityName() == null ? "" : abnormality.getAbnormalityName();
            RiskLevel risk = abnormality.getRiskLevel();
            if (risk != null) {
                candidate.riskName = risk.name();
                candidate.riskColor = 0xFF000000 | (risk.getColor() & 0xFFFFFF);
            }
            candidate.distance = (int) Math.sqrt(abnormality.distanceToSqr(
                    roomPos.getX() + 0.5, roomPos.getY() + 0.5, roomPos.getZ() + 0.5));
            candidate.inRoom = roomBox.contains(abnormality.position());
            candidate.alreadyBound = bound != null && bound.equals(abnormality.getUUID());
            candidates.add(candidate);
        }

        // 房间里的排前面，其余按距离
        candidates.sort(Comparator
                .comparing((AbnormalityCandidate c) -> !c.inRoom)
                .thenComparingInt(c -> c.distance));
        if (candidates.size() > MAX_RESULTS) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_RESULTS));
        }

        MessageLoader.getLoader().sendToPlayer(player,
                new NearbyAbnormalitiesPacket(roomPos, candidates));
    }

    @Override
    public boolean sendToServer() {
        return true;
    }
}
