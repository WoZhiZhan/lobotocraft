package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.client.room.RoomViewClient;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.PacketDecodeLimits;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.data.FloorEntry;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import com.wzz.lobotocraft.world.data.RoomEntry;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import com.wzz.lobotocraft.employee.RoomEmployeeSnapshotService;
import com.wzz.lobotocraft.employee.FacilityDangerSnapshotService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 服务端 -> 客户端：所有收容间 + 楼层的快照。
 * refresh = false 表示"打开监控视角"（会播放过渡动画），true 表示界面已经开着，只是刷新数据。
 */
public class OpenRoomMapPacket implements IMessage {
    private static final int MAX_ROOMS = 1024;
    private static final int MAX_FLOORS = 128;
    private static final int MAX_AREAS = 2048;
    private static final int MAX_EMPLOYEES = 512;
    private static final int MAX_DANGEROUS_AREAS = 512;

    private List<RoomEntry> rooms;
    private List<FloorEntry> floors;
    private List<FacilityAreaEntry> areas;
    private List<RoomEmployeeSnapshot> employees;
    private Set<String> dangerousAreaIds;
    private long serverGameTime;
    private boolean globalTt2Enabled;
    private boolean refresh;
    private ResourceLocation dimension;

    public OpenRoomMapPacket() {
    }

    public OpenRoomMapPacket(List<RoomEntry> rooms, List<FloorEntry> floors,
                             List<FacilityAreaEntry> areas,
                             List<RoomEmployeeSnapshot> employees,
                             Set<String> dangerousAreaIds, long serverGameTime,
                             boolean globalTt2Enabled,
                             boolean refresh, ResourceLocation dimension) {
        this.rooms = rooms;
        this.floors = floors;
        this.areas = areas;
        this.employees = employees;
        this.dangerousAreaIds = dangerousAreaIds;
        this.serverGameTime = serverGameTime;
        this.globalTt2Enabled = globalTt2Enabled;
        this.refresh = refresh;
        this.dimension = dimension;
    }

    public OpenRoomMapPacket(List<RoomEntry> rooms, List<FloorEntry> floors,
                             List<FacilityAreaEntry> areas,
                             List<RoomEmployeeSnapshot> employees,
                             Set<String> dangerousAreaIds, long serverGameTime,
                             boolean refresh, ResourceLocation dimension) {
        this(rooms, floors, areas, employees, dangerousAreaIds, serverGameTime,
                false, refresh, dimension);
    }

    /**
     * 收集玩家应该看到的房间并发送。优先取玩家所在维度；如果当前维度没有房间，
     * 就回退到公司维度，方便在外面也能远程看监控。
     */
    public static void sendTo(ServerPlayer player, boolean refresh) {
        ServerLevel level = player.serverLevel();
        CompanyRoomData data = CompanyRoomData.get(level);

        ResourceLocation dim = level.dimension().location();
        List<RoomEntry> candidateRooms = data.getRooms(dim);
        if (candidateRooms.isEmpty()) {
            ResourceLocation fallback = ModDimensions.LOBOTO_KEY.location();
            List<RoomEntry> companyRooms = data.getRooms(fallback);
            if (!companyRooms.isEmpty()) {
                dim = fallback;
                candidateRooms = companyRooms;
            }
        }

        boolean suppressionActive = CoreSuppressionManager.isSuppressionActive(player.server.overworld());
        for (RoomEntry room : candidateRooms) {
            FacilityWorldLayout.applyRoomBinding(room);
            if (!suppressionActive) {
                room.meltdownActive = false;
                room.meltdownRemainingTicks = 0;
            }
        }
        final ResourceLocation visibleDimension = dim;
        List<RoomEntry> rooms = candidateRooms.stream()
                .filter(room -> isContainmentVisible(FacilityAccessService.containmentAccess(
                        player.server, visibleDimension, room.pos)))
                .toList();

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
                reconcileEscapedStatus(roomLevel, room);
            }
        }

        // 按楼层标记方块给每个房间归层（楼层列表已按 Y 从高到低排好）
        List<FloorEntry> allFloors = data.getFloors(dim);
        Set<Integer> usedFloorIndices = new TreeSet<>();
        for (RoomEntry room : rooms) {
            int index = CompanyRoomData.resolveFloorIndex(allFloors, room);
            room.floorIndex = index;
            if (index >= 0) {
                usedFloorIndices.add(index);
                FloorEntry floor = allFloors.get(index);
                room.floorName = floor.displayName();
                room.floorY = floor.y();
            } else {
                room.floorName = "";
                room.floorY = room.pos.getY();
            }
        }

        List<FloorEntry> floors = new ArrayList<>();
        Map<Integer, Integer> remappedFloorIndices = new HashMap<>();
        for (int originalIndex : usedFloorIndices) {
            remappedFloorIndices.put(originalIndex, floors.size());
            floors.add(allFloors.get(originalIndex));
        }
        for (RoomEntry room : rooms) {
            room.floorIndex = remappedFloorIndices.getOrDefault(room.floorIndex, -1);
        }

        List<FacilityAreaEntry> areas = FacilityWorldLayout.mergeAreas(dim, data.getAreas(dim)).stream()
                .filter(area -> area.type == FacilityAreaType.CONTAINMENT
                        ? isContainmentVisible(FacilityAccessService.containmentAccess(
                                player.server, visibleDimension, area.markerPos))
                        : FacilityAccessService.isGroupOpen(
                                player.server, visibleDimension, area.groupId))
                .toList();
        Set<String> dangerousAreaIds = roomLevel == null
                ? Set.of() : FacilityDangerSnapshotService.collect(roomLevel, areas);
        MessageLoader.getLoader().sendToPlayer(player,
                new OpenRoomMapPacket(rooms, floors, areas, RoomEmployeeSnapshotService.collect(player, dim),
                        dangerousAreaIds, level.getGameTime(),
                        com.lobotocraft.facility.data.FacilityDayData.get(player.server.overworld())
                                .isTt2ProtocolEnabled(), refresh, dim));
    }

    static boolean isContainmentVisible(FacilityAccessService.ContainmentAccess access) {
        return access == FacilityAccessService.ContainmentAccess.OPEN
                || access == FacilityAccessService.ContainmentAccess.UNEXPANDED;
    }

    private static void reconcileEscapedStatus(ServerLevel level, RoomEntry room) {
        if (room.status != RoomEntry.STATUS_ESCAPED) return;
        if (!level.hasChunkAt(room.pos)
                || !(level.getBlockEntity(room.pos) instanceof RoomMarkerBlockEntity marker)) {
            room.status = RoomEntry.STATUS_LOST;
            room.escapeDistance = 0;
            return;
        }
        Entity entity = marker.getBoundAbnormality() == null
                ? null : level.getEntity(marker.getBoundAbnormality());
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()
                || !abnormality.hasEscape()) {
            room.status = RoomEntry.STATUS_LOST;
            room.escapeDistance = 0;
        }
    }

    public List<RoomEntry> getRooms() {
        return rooms;
    }

    public List<FloorEntry> getFloors() {
        return floors;
    }

    public List<FacilityAreaEntry> getAreas() {
        return areas;
    }

    public List<RoomEmployeeSnapshot> getEmployees() {
        return employees;
    }

    public Set<String> getDangerousAreaIds() {
        return dangerousAreaIds;
    }

    public long getServerGameTime() {
        return serverGameTime;
    }

    public boolean isGlobalTt2Enabled() {
        return globalTt2Enabled;
    }

    public boolean isRefresh() {
        return refresh;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        int roomCount = PacketDecodeLimits.readVarIntCount(buf, MAX_ROOMS, "monitor room");
        this.rooms = new ArrayList<>(roomCount);
        for (int i = 0; i < roomCount; i++) {
            this.rooms.add(RoomEntry.read(buf));
        }
        int floorCount = PacketDecodeLimits.readVarIntCount(buf, MAX_FLOORS, "monitor floor");
        this.floors = new ArrayList<>(floorCount);
        for (int i = 0; i < floorCount; i++) {
            this.floors.add(FloorEntry.read(buf));
        }
        int areaCount = PacketDecodeLimits.readVarIntCount(buf, MAX_AREAS, "monitor area");
        this.areas = new ArrayList<>(areaCount);
        for (int i = 0; i < areaCount; i++) {
            this.areas.add(FacilityAreaEntry.read(buf));
        }
        int employeeCount = PacketDecodeLimits.readVarIntCount(buf, MAX_EMPLOYEES, "monitor employee");
        this.employees = new ArrayList<>(employeeCount);
        for (int i = 0; i < employeeCount; i++) {
            this.employees.add(RoomEmployeeSnapshot.read(buf));
        }
        int dangerCount = PacketDecodeLimits.readVarIntCount(buf, MAX_DANGEROUS_AREAS, "monitor danger");
        this.dangerousAreaIds = new java.util.LinkedHashSet<>(dangerCount);
        for (int i = 0; i < dangerCount; i++) dangerousAreaIds.add(buf.readUtf(128));
        this.serverGameTime = buf.readLong();
        this.globalTt2Enabled = buf.readBoolean();
        this.refresh = buf.readBoolean();
        this.dimension = buf.readResourceLocation();
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        int roomCount = Math.min(rooms.size(), MAX_ROOMS);
        buf.writeVarInt(roomCount);
        for (int index = 0; index < roomCount; index++) {
            rooms.get(index).write(buf);
        }
        int floorCount = Math.min(floors.size(), MAX_FLOORS);
        buf.writeVarInt(floorCount);
        for (int index = 0; index < floorCount; index++) {
            floors.get(index).write(buf);
        }
        int areaCount = Math.min(areas.size(), MAX_AREAS);
        buf.writeVarInt(areaCount);
        for (int index = 0; index < areaCount; index++) {
            areas.get(index).write(buf);
        }
        int employeeCount = Math.min(employees.size(), MAX_EMPLOYEES);
        buf.writeVarInt(employeeCount);
        for (int index = 0; index < employeeCount; index++) employees.get(index).write(buf);
        int dangerCount = Math.min(dangerousAreaIds.size(), MAX_DANGEROUS_AREAS);
        buf.writeVarInt(dangerCount);
        int writtenDangerCount = 0;
        for (String areaId : dangerousAreaIds) {
            if (writtenDangerCount++ >= dangerCount) break;
            buf.writeUtf(areaId, 128);
        }
        buf.writeLong(serverGameTime);
        buf.writeBoolean(globalTt2Enabled);
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
