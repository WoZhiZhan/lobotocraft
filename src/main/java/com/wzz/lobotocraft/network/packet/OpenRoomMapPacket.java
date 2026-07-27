package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.client.room.RoomViewClient;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FloorEntry;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：所有收容间 + 楼层的快照。
 * refresh = false 表示"打开监控视角"（会播放过渡动画），true 表示界面已经开着，只是刷新数据。
 */
public class OpenRoomMapPacket implements IMessage {

    private List<RoomEntry> rooms;
    private List<FloorEntry> floors;
    private long serverGameTime;
    private boolean refresh;
    private ResourceLocation dimension;

    public OpenRoomMapPacket() {
    }

    public OpenRoomMapPacket(List<RoomEntry> rooms, List<FloorEntry> floors,
                             long serverGameTime, boolean refresh, ResourceLocation dimension) {
        this.rooms = rooms;
        this.floors = floors;
        this.serverGameTime = serverGameTime;
        this.refresh = refresh;
        this.dimension = dimension;
    }

    /**
     * 收集玩家应该看到的房间并发送。优先取玩家所在维度；如果当前维度没有房间，
     * 就回退到公司维度，方便在外面也能远程看监控。
     */
    public static void sendTo(ServerPlayer player, boolean refresh) {
        ServerLevel level = player.serverLevel();
        CompanyRoomData data = CompanyRoomData.get(level);

        ResourceLocation dim = level.dimension().location();
        List<RoomEntry> rooms = data.getRooms(dim);
        if (rooms.isEmpty()) {
            ResourceLocation fallback = ModDimensions.LOBOTO_KEY.location();
            List<RoomEntry> companyRooms = data.getRooms(fallback);
            if (!companyRooms.isEmpty()) {
                dim = fallback;
                rooms = companyRooms;
            }
        }

        // 给这些房间的区块挂/续强制加载票，这样远处房间也能有实时数据。
        // 超过上限时优先保离玩家近的（rooms 是排过序的副本，不影响存档里的顺序）
        ServerLevel roomLevel = player.server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, dim));
        if (roomLevel != null) {
            List<RoomEntry> byDistance = new ArrayList<>(rooms);
            byDistance.sort(java.util.Comparator.comparingDouble(
                    r -> r.pos.distToCenterSqr(player.getX(), player.getY(), player.getZ())));
            for (RoomEntry room : byDistance) {
                RoomChunkTickets.keepAlive(roomLevel, room.pos);
            }
        }

        // 按楼层标记方块给每个房间归层（楼层列表已按 Y 从高到低排好）
        List<FloorEntry> floors = data.getFloors(dim);
        for (RoomEntry room : rooms) {
            int index = CompanyRoomData.resolveFloorIndex(floors, room);
            room.floorIndex = index;
            if (index >= 0) {
                FloorEntry floor = floors.get(index);
                room.floorName = floor.displayName();
                room.floorY = floor.y();
            } else {
                room.floorName = "";
                room.floorY = room.pos.getY();
            }
        }

        MessageLoader.getLoader().sendToPlayer(player,
                new OpenRoomMapPacket(rooms, floors, level.getGameTime(), refresh, dim));
    }

    public List<RoomEntry> getRooms() {
        return rooms;
    }

    public List<FloorEntry> getFloors() {
        return floors;
    }

    public long getServerGameTime() {
        return serverGameTime;
    }

    public boolean isRefresh() {
        return refresh;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        int roomCount = buf.readVarInt();
        this.rooms = new ArrayList<>(roomCount);
        for (int i = 0; i < roomCount; i++) {
            this.rooms.add(RoomEntry.read(buf));
        }
        int floorCount = buf.readVarInt();
        this.floors = new ArrayList<>(floorCount);
        for (int i = 0; i < floorCount; i++) {
            this.floors.add(FloorEntry.read(buf));
        }
        this.serverGameTime = buf.readLong();
        this.refresh = buf.readBoolean();
        this.dimension = buf.readResourceLocation();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(rooms.size());
        for (RoomEntry entry : rooms) {
            entry.write(buf);
        }
        buf.writeVarInt(floors.size());
        for (FloorEntry entry : floors) {
            entry.write(buf);
        }
        buf.writeLong(serverGameTime);
        buf.writeBoolean(refresh);
        buf.writeResourceLocation(dimension);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void runClient(NetworkEvent.Context ctx) {
        RoomViewClient.onRoomData(this);
    }

    @Override
    public boolean sendToClient() {
        return true;
    }
}
