package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * 追踪终末鸟的三个蛋的状态
 */
public class EndBirdEggTracker extends SavedData {
    private static final String NAME = "end_bird_egg_tracker";

    // 蛋类型常量
    public static final String EGG_SMALL = "small";   // 小喙
    public static final String EGG_HIGH = "high";     // 长臂
    public static final String EGG_EYE = "eye";       // 大眼

    // 记录蛋的UUID和类型
    private final Map<String, String> eggTypes = new HashMap<>(); // UUID -> 类型
    private final Set<String> destroyedEggs = new HashSet<>();   // 已击杀的蛋UUID
    private UUID endBirdUUID = null;  // 终末鸟的UUID

    public static EndBirdEggTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            EndBirdEggTracker::load, 
            EndBirdEggTracker::new, 
            NAME
        );
    }

    /**
     * 注册蛋
     */
    public void registerEgg(String eggUUID, String eggType) {
        eggTypes.put(eggUUID, eggType);
        setDirty();
    }

    /**
     * 注册终末鸟
     */
    public void registerEndBird(String uuid) {
        this.endBirdUUID = UUID.fromString(uuid);
        setDirty();
    }

    /**
     * 标记蛋已被摧毁
     * @return 返回摧毁后剩余的存活蛋数量
     */
    public int markEggDestroyed(String eggUUID) {
        destroyedEggs.add(eggUUID);
        setDirty();
        return getAliveEggCount();
    }

    /**
     * 获取已被摧毁的蛋数量
     */
    public int getDestroyedEggCount() {
        return destroyedEggs.size();
    }

    /**
     * 获取存活的蛋数量
     */
    public int getAliveEggCount() {
        return (int) eggTypes.keySet().stream()
                .filter(uuid -> !destroyedEggs.contains(uuid))
                .count();
    }

    /**
     * 获取已摧毁的蛋类型列表
     */
    public List<String> getDestroyedEggTypes() {
        List<String> types = new ArrayList<>();
        for (String uuid : destroyedEggs) {
            String type = eggTypes.get(uuid);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    /**
     * 检查是否所有蛋都被摧毁
     */
    public boolean areAllEggsDestroyed() {
        return destroyedEggs.size() >= eggTypes.size() && !eggTypes.isEmpty();
    }

    /**
     * 获取终末鸟UUID
     */
    public UUID getEndBirdUUID() {
        return endBirdUUID;
    }

    /**
     * 清理所有数据
     */
    public void clear() {
        eggTypes.clear();
        destroyedEggs.clear();
        endBirdUUID = null;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        // 保存蛋类型映射
        ListTag eggList = new ListTag();
        for (Map.Entry<String, String> entry : eggTypes.entrySet()) {
            CompoundTag eggTag = new CompoundTag();
            eggTag.putString("UUID", entry.getKey());
            eggTag.putString("Type", entry.getValue());
            eggList.add(eggTag);
        }
        tag.put("EggTypes", eggList);

        // 保存已摧毁的蛋
        ListTag destroyedList = new ListTag();
        for (String uuid : destroyedEggs) {
            destroyedList.add(StringTag.valueOf(uuid));
        }
        tag.put("DestroyedEggs", destroyedList);

        // 保存终末鸟UUID
        if (endBirdUUID != null) {
            tag.putUUID("EndBirdUUID", endBirdUUID);
        }

        return tag;
    }

    public static EndBirdEggTracker load(CompoundTag tag) {
        EndBirdEggTracker tracker = new EndBirdEggTracker();

        // 加载蛋类型映射
        ListTag eggList = tag.getList("EggTypes", 10); // 10 = TAG_Compound
        for (int i = 0; i < eggList.size(); i++) {
            CompoundTag eggTag = eggList.getCompound(i);
            String uuid = eggTag.getString("UUID");
            String type = eggTag.getString("Type");
            if (!uuid.isEmpty() && !type.isEmpty()) {
                tracker.eggTypes.put(uuid, type);
            }
        }

        // 加载已摧毁的蛋
        ListTag destroyedList = tag.getList("DestroyedEggs", 8); // 8 = TAG_String
        for (int i = 0; i < destroyedList.size(); i++) {
            tracker.destroyedEggs.add(destroyedList.getString(i));
        }

        // 加载终末鸟UUID
        if (tag.hasUUID("EndBirdUUID")) {
            tracker.endBirdUUID = tag.getUUID("EndBirdUUID");
        }

        return tracker;
    }
}