package com.wzz.lobotocraft.world.data;

import com.wzz.lobotocraft.world.structure.StructureVersions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 记录每个结构在这个存档里已经生成到哪个版本了。
 * <p>
 * 单独一份 SavedData，不去动你原有的 {@link GenerationBiggerData} —— 那个负责"生成没生成"，
 * 这个只负责"生成的是哪一版"，两边互不干扰，老存档也不会因为多了个字段读不出来。
 */
public class StructureVersionData extends SavedData {

    public static final String ID = "lobotocraft_structure_versions";

    private final CompoundTag versions = new CompoundTag();

    public static StructureVersionData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                StructureVersionData::load,
                StructureVersionData::new,
                ID
        );
    }

    public static StructureVersionData load(CompoundTag tag) {
        StructureVersionData data = new StructureVersionData();
        CompoundTag stored = tag.getCompound("Versions");
        for (String key : stored.getAllKeys()) {
            data.versions.putInt(key, stored.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Versions", versions.copy());
        return tag;
    }

    /** 没有记录的当成 {@link StructureVersions#LEGACY} */
    public int getVersion(String structureName) {
        return versions.contains(structureName)
                ? versions.getInt(structureName)
                : StructureVersions.LEGACY;
    }

    public boolean hasRecord(String structureName) {
        return versions.contains(structureName);
    }

    public void setVersion(String structureName, int version) {
        versions.putInt(structureName, version);
        setDirty();
    }
}
