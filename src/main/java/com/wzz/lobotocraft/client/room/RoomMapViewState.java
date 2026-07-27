package com.wzz.lobotocraft.client.room;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控界面的视图状态：每层楼各自的平移 / 缩放，以及每个房间被手动拖开的偏移。
 * <p>
 * 纯客户端数据，跟存档无关，写在 <code>config/lobotocraft_room_map.dat</code>，
 * 所以关掉游戏再进来，你摆好的布局还在。
 * <p>
 * 房间偏移用的是<b>世界坐标单位</b>（格）而不是屏幕像素，这样缩放的时候相对位置不会乱。
 */
public final class RoomMapViewState {

    private static final String FILE_NAME = "lobotocraft_room_map.dat";

    /** 楼层标签 -> {panX, panY, zoom} */
    private static final Map<String, double[]> FLOOR_VIEW = new HashMap<>();
    /** 房间 key -> {偏移X, 偏移Z}，单位是格 */
    private static final Map<String, double[]> ROOM_OFFSET = new HashMap<>();

    private static String lastFloorLabel = null;
    private static boolean loaded = false;
    private static boolean dirty = false;

    private RoomMapViewState() {
    }

    // ------------------------------------------------------------ 读写接口

    public static double[] getFloorView(String floorLabel) {
        ensureLoaded();
        return FLOOR_VIEW.get(floorLabel);
    }

    public static void setFloorView(String floorLabel, double panX, double panY, double zoom) {
        ensureLoaded();
        FLOOR_VIEW.put(floorLabel, new double[]{panX, panY, zoom});
        dirty = true;
    }

    public static double[] getRoomOffset(String roomKey) {
        ensureLoaded();
        return ROOM_OFFSET.getOrDefault(roomKey, new double[]{0, 0});
    }

    public static void setRoomOffset(String roomKey, double dx, double dz) {
        ensureLoaded();
        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            ROOM_OFFSET.remove(roomKey);
        } else {
            ROOM_OFFSET.put(roomKey, new double[]{dx, dz});
        }
        dirty = true;
    }

    public static void clearRoomOffsets() {
        ensureLoaded();
        ROOM_OFFSET.clear();
        dirty = true;
    }

    public static String getLastFloorLabel() {
        ensureLoaded();
        return lastFloorLabel;
    }

    public static void setLastFloorLabel(String label) {
        ensureLoaded();
        lastFloorLabel = label;
        dirty = true;
    }

    // ------------------------------------------------------------ 持久化

    private static File file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME).toFile();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (!f.exists()) return;
            CompoundTag root = NbtIo.readCompressed(f);

            ListTag views = root.getList("Views", Tag.TAG_COMPOUND);
            for (int i = 0; i < views.size(); i++) {
                CompoundTag tag = views.getCompound(i);
                FLOOR_VIEW.put(tag.getString("Label"), new double[]{
                        tag.getDouble("PanX"), tag.getDouble("PanY"), tag.getDouble("Zoom")});
            }
            ListTag offsets = root.getList("Offsets", Tag.TAG_COMPOUND);
            for (int i = 0; i < offsets.size(); i++) {
                CompoundTag tag = offsets.getCompound(i);
                ROOM_OFFSET.put(tag.getString("Key"), new double[]{
                        tag.getDouble("X"), tag.getDouble("Z")});
            }
            if (root.contains("LastFloor")) {
                lastFloorLabel = root.getString("LastFloor");
            }
        } catch (Throwable ignored) {
            // 配置坏了就当没有，不要因为这个崩游戏
        }
    }

    /** 关闭界面时调用 */
    public static void save() {
        if (!dirty) return;
        dirty = false;
        try {
            CompoundTag root = new CompoundTag();

            ListTag views = new ListTag();
            for (Map.Entry<String, double[]> entry : FLOOR_VIEW.entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Label", entry.getKey());
                tag.putDouble("PanX", entry.getValue()[0]);
                tag.putDouble("PanY", entry.getValue()[1]);
                tag.putDouble("Zoom", entry.getValue()[2]);
                views.add(tag);
            }
            root.put("Views", views);

            ListTag offsets = new ListTag();
            for (Map.Entry<String, double[]> entry : ROOM_OFFSET.entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Key", entry.getKey());
                tag.putDouble("X", entry.getValue()[0]);
                tag.putDouble("Z", entry.getValue()[1]);
                offsets.add(tag);
            }
            root.put("Offsets", offsets);

            if (lastFloorLabel != null) {
                root.putString("LastFloor", lastFloorLabel);
            }
            NbtIo.writeCompressed(root, file());
        } catch (Throwable ignored) {
            // 写不进去就算了
        }
    }
}
