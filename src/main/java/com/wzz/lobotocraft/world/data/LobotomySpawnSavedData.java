package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 存档级(世界全局)的特殊生成标记与计数。
 * 挂在主世界的 DimensionDataStorage 上, 保证整档唯一、随存档持久化。
 * 条件1: 力量玩家击杀"带虚弱的卫道士" -> 生成 一无所有 (nothingThereSpawned)。
 * 条件2: 饥饿玩家累计击杀 卫道士/村民 达 10 -> 生成 微笑的尸山 (smilingSpawned + hungerKillCount)。
 * 两者均"每个存档只执行一次"。
 */
public class LobotomySpawnSavedData extends SavedData {

    private static final String NAME = "lobotocraft_special_spawns";

    public boolean nothingThereSpawned = false;
    public boolean smilingSpawned = false;
    public int hungerKillCount = 0;

    public LobotomySpawnSavedData() {
    }

    /** 反序列化 */
    public static LobotomySpawnSavedData load(CompoundTag tag) {
        LobotomySpawnSavedData data = new LobotomySpawnSavedData();
        data.nothingThereSpawned = tag.getBoolean("nothingThereSpawned");
        data.smilingSpawned = tag.getBoolean("smilingSpawned");
        data.hungerKillCount = tag.getInt("hungerKillCount");
        return data;
    }

    /** 序列化 */
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("nothingThereSpawned", nothingThereSpawned);
        tag.putBoolean("smilingSpawned", smilingSpawned);
        tag.putInt("hungerKillCount", hungerKillCount);
        return tag;
    }

    /**
     * 取得(不存在则创建)世界全局实例。无论从哪个维度调用, 都统一挂到主世界, 保证整档唯一。
     */
    public static LobotomySpawnSavedData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                LobotomySpawnSavedData::load,
                LobotomySpawnSavedData::new,
                NAME);
    }
}