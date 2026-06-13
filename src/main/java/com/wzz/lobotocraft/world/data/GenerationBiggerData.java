package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class GenerationBiggerData extends SavedData {

    public static final String ID = "structure_generation";

    private final Map<String, Entry> entries = new HashMap<>();

    public static GenerationBiggerData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                GenerationBiggerData::load,
                GenerationBiggerData::new,
                ID
        );
    }

    public Entry getOrCreate(String name) {
        return entries.computeIfAbsent(name, k -> new Entry());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (var e : entries.entrySet()) {
            all.put(e.getKey(), e.getValue().save());
        }
        tag.put("entries", all);
        return tag;
    }

    public static GenerationBiggerData load(CompoundTag tag) {
        GenerationBiggerData data = new GenerationBiggerData();
        CompoundTag all = tag.getCompound("entries");
        for (String key : all.getAllKeys()) {
            data.entries.put(key, Entry.load(all.getCompound(key)));
        }
        return data;
    }

    public static class Entry {
        public boolean generated;
        public int centerX, centerZ;
        public int currentRadius;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("generated", generated);
            tag.putInt("centerX", centerX);
            tag.putInt("centerZ", centerZ);
            tag.putInt("currentRadius", currentRadius);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            Entry e = new Entry();
            e.generated = tag.getBoolean("generated");
            e.centerX = tag.getInt("centerX");
            e.centerZ = tag.getInt("centerZ");
            e.currentRadius = tag.getInt("currentRadius");
            return e;
        }
    }
}