package com.wzz.lobotocraft.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档级（世界全局）的收容间 + 楼层登记表，统一挂在主世界的 DimensionDataStorage 上。
 * <p>
 * 存在的意义：公司维度建筑非常大，右键监控终端时不可能去遍历所有区块寻找标记方块
 * （未加载的区块根本拿不到 BlockEntity）。所以信标自己把快照"上报"到这里，终端只读这张表。
 */
public class CompanyRoomData extends SavedData {

    private static final String NAME = "lobotocraft_company_rooms";

    /** 楼层匹配容差：楼层标记允许比房间信标高出这么多格，仍算同一层 */
    private static final int FLOOR_UP_TOLERANCE = 4;

    private final Map<String, RoomEntry> rooms = new LinkedHashMap<>();
    private final Map<String, FloorEntry> floors = new LinkedHashMap<>();
    private final Map<String, FacilityAreaEntry> areas = new LinkedHashMap<>();

    public static CompanyRoomData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                CompanyRoomData::load,
                CompanyRoomData::new,
                NAME);
    }

    public static CompanyRoomData load(CompoundTag tag) {
        CompanyRoomData data = new CompanyRoomData();
        ListTag roomList = tag.getList("Rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomList.size(); i++) {
            RoomEntry entry = RoomEntry.load(roomList.getCompound(i));
            if (entry.dimension != null && entry.pos != null) {
                data.rooms.put(entry.key(), entry);
            }
        }
        ListTag floorList = tag.getList("Floors", Tag.TAG_COMPOUND);
        for (int i = 0; i < floorList.size(); i++) {
            FloorEntry entry = FloorEntry.load(floorList.getCompound(i));
            if (entry.dimension != null && entry.pos != null) {
                data.floors.put(entry.key(), entry);
            }
        }
        ListTag areaList = tag.getList("Areas", Tag.TAG_COMPOUND);
        for (int i = 0; i < areaList.size(); i++) {
            FacilityAreaEntry entry = FacilityAreaEntry.load(areaList.getCompound(i));
            if (entry.dimension != null && !entry.key.isEmpty()) {
                data.areas.put(entry.key, entry);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag roomList = new ListTag();
        for (RoomEntry entry : rooms.values()) {
            roomList.add(entry.save());
        }
        tag.put("Rooms", roomList);

        ListTag floorList = new ListTag();
        for (FloorEntry entry : floors.values()) {
            floorList.add(entry.save());
        }
        tag.put("Floors", floorList);

        ListTag areaList = new ListTag();
        for (FacilityAreaEntry entry : areas.values()) {
            areaList.add(entry.save());
        }
        tag.put("Areas", areaList);
        return tag;
    }

    // ------------------------------------------------------------ 收容间

    public RoomEntry getOrCreateRoom(ResourceLocation dimension, BlockPos pos) {
        String key = RoomEntry.makeKey(dimension, pos);
        RoomEntry entry = rooms.get(key);
        if (entry == null) {
            entry = new RoomEntry(dimension, pos);
            rooms.put(key, entry);
            setDirty();
        }
        return entry;
    }

    public void removeRoom(ResourceLocation dimension, BlockPos pos) {
        if (rooms.remove(RoomEntry.makeKey(dimension, pos)) != null) {
            setDirty();
        }
    }

    public void putRoom(RoomEntry entry) {
        rooms.put(entry.key(), entry);
        setDirty();
    }

    public RoomEntry findRoom(ResourceLocation dimension, BlockPos pos) {
        return rooms.get(RoomEntry.makeKey(dimension, pos));
    }

    /** 取某个维度里的所有房间，按 Z 再按 X 排序 */
    public List<RoomEntry> getRooms(ResourceLocation dimension) {
        List<RoomEntry> result = new ArrayList<>();
        for (RoomEntry entry : rooms.values()) {
            if (entry.dimension != null && entry.dimension.equals(dimension)) {
                result.add(entry);
            }
        }
        result.sort(Comparator
                .comparingInt((RoomEntry e) -> e.pos.getZ())
                .thenComparingInt(e -> e.pos.getX()));
        return result;
    }

    // -------------------------------------------------------------- 区域

    public FacilityAreaEntry getOrCreateArea(ResourceLocation dimension, BlockPos markerPos,
                                             FacilityAreaType type) {
        String key = FacilityAreaEntry.makeKey(type, dimension, markerPos);
        FacilityAreaEntry entry = areas.get(key);
        if (entry == null) {
            entry = new FacilityAreaEntry(dimension, key, markerPos, markerPos, markerPos,
                    "", type, "");
            areas.put(key, entry);
            setDirty();
        }
        return entry;
    }

    public void putArea(FacilityAreaEntry entry) {
        if (entry == null || entry.dimension == null || entry.key == null || entry.key.isEmpty()) return;
        areas.put(entry.key, entry);
        setDirty();
    }

    public FacilityAreaEntry findArea(String key) {
        return key == null ? null : areas.get(key);
    }

    public void removeArea(String key) {
        if (key != null && areas.remove(key) != null) {
            setDirty();
        }
    }

    public void removeArea(FacilityAreaType type, ResourceLocation dimension, BlockPos markerPos) {
        removeArea(FacilityAreaEntry.makeKey(type, dimension, markerPos));
    }

    public List<FacilityAreaEntry> getAreas(ResourceLocation dimension) {
        List<FacilityAreaEntry> result = new ArrayList<>();
        for (FacilityAreaEntry entry : areas.values()) {
            if (entry.dimension != null && entry.dimension.equals(dimension)) {
                result.add(entry);
            }
        }
        result.sort(Comparator
                .comparingInt((FacilityAreaEntry e) -> e.markerPos.getY()).reversed()
                .thenComparingInt(e -> e.markerPos.getZ())
                .thenComparingInt(e -> e.markerPos.getX())
                .thenComparing(e -> e.key));
        return result;
    }

    // -------------------------------------------------------------- 楼层

    public FloorEntry getOrCreateFloor(ResourceLocation dimension, BlockPos pos) {
        String key = FloorEntry.makeKey(dimension, pos);
        FloorEntry entry = floors.get(key);
        if (entry == null) {
            entry = new FloorEntry(dimension, pos);
            floors.put(key, entry);
            setDirty();
        }
        return entry;
    }

    public void removeFloor(ResourceLocation dimension, BlockPos pos) {
        if (floors.remove(FloorEntry.makeKey(dimension, pos)) != null) {
            setDirty();
        }
    }

    public FloorEntry findFloor(ResourceLocation dimension, BlockPos pos) {
        return floors.get(FloorEntry.makeKey(dimension, pos));
    }

    public boolean renameFloor(ResourceLocation dimension, BlockPos pos, String name) {
        FloorEntry entry = floors.get(FloorEntry.makeKey(dimension, pos));
        if (entry == null) return false;
        entry.name = name == null ? "" : name;
        setDirty();
        return true;
    }

    /** 取某个维度里的所有楼层，按 Y 从高到低 */
    public List<FloorEntry> getFloors(ResourceLocation dimension) {
        List<FloorEntry> result = new ArrayList<>();
        for (FloorEntry entry : floors.values()) {
            if (entry.dimension != null && entry.dimension.equals(dimension)) {
                result.add(entry);
            }
        }
        result.sort(Comparator.comparingInt(FloorEntry::y).reversed());
        return result;
    }

    /**
     * 把房间归属到楼层：取"高度在房间信标之上不超过 {@value #FLOOR_UP_TOLERANCE} 格、
     * 或位于其下方"的那个最高的楼层标记。楼层列表必须已按 Y 从高到低排好序。
     *
     * @return floors 里的下标，没有楼层标记时返回 -1
     */
    public static int resolveFloorIndex(List<FloorEntry> sortedFloors, RoomEntry room) {
        return resolveFloorIndex(sortedFloors, room.pos.getY());
    }

    public static int resolveFloorIndex(List<FloorEntry> sortedFloors, FacilityAreaEntry area) {
        return resolveFloorIndex(sortedFloors, area.markerPos.getY());
    }

    public static int resolveFloorIndex(List<FloorEntry> sortedFloors, BlockPos position) {
        return position == null ? -1 : resolveFloorIndex(sortedFloors, position.getY());
    }

    private static int resolveFloorIndex(List<FloorEntry> sortedFloors, int markerY) {
        if (sortedFloors.isEmpty()) return -1;
        for (int i = 0; i < sortedFloors.size(); i++) {
            if (sortedFloors.get(i).y() <= markerY + FLOOR_UP_TOLERANCE) {
                return i;
            }
        }
        // 所有楼层标记都在这个房间上方（房间比最低层还低），归给最低那层
        return sortedFloors.size() - 1;
    }
}
