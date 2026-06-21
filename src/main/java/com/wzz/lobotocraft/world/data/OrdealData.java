package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全服共享的考验状态。
 */
public class OrdealData extends SavedData {
    private static final String NAME = "lobotocraft_ordeal_data";

    private int trackedDay = 0;
    private int dawnChance = 0;
    private int dawnTriggersToday = 0;
    private boolean bloodDawnActive = false;
    private int bloodDawnRemaining = 0;

    public static OrdealData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                OrdealData::load,
                OrdealData::new,
                NAME
        );
    }

    public void syncDay(int day) {
        if (day <= 0) return;
        if (trackedDay == 0) {
            trackedDay = day;
            setDirty();
            return;
        }
        if (day > trackedDay) {
            trackedDay = day;
            dawnChance = 0;
            dawnTriggersToday = 0;
            bloodDawnActive = false;
            bloodDawnRemaining = 0;
            setDirty();
        }
    }

    public int getDawnChance() {
        return dawnChance;
    }

    public void setDawnChance(int chance) {
        dawnChance = Math.max(0, Math.min(100, chance));
        setDirty();
    }

    public int getDawnTriggersToday() {
        return dawnTriggersToday;
    }

    public void incrementDawnTriggersToday() {
        dawnTriggersToday++;
        setDirty();
    }

    public boolean isBloodDawnActive() {
        return bloodDawnActive;
    }

    public void startBloodDawn(int entityCount) {
        bloodDawnActive = true;
        bloodDawnRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementBloodDawnRemaining() {
        if (bloodDawnRemaining > 0) {
            bloodDawnRemaining--;
            setDirty();
        }
        return bloodDawnRemaining;
    }

    public void finishBloodDawn() {
        bloodDawnActive = false;
        bloodDawnRemaining = 0;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("TrackedDay", trackedDay);
        tag.putInt("DawnChance", dawnChance);
        tag.putInt("DawnTriggersToday", dawnTriggersToday);
        tag.putBoolean("BloodDawnActive", bloodDawnActive);
        tag.putInt("BloodDawnRemaining", bloodDawnRemaining);
        return tag;
    }

    public static OrdealData load(CompoundTag tag) {
        OrdealData data = new OrdealData();
        data.trackedDay = tag.getInt("TrackedDay");
        data.dawnChance = tag.getInt("DawnChance");
        data.dawnTriggersToday = tag.getInt("DawnTriggersToday");
        data.bloodDawnActive = tag.getBoolean("BloodDawnActive");
        data.bloodDawnRemaining = tag.getInt("BloodDawnRemaining");
        return data;
    }
}
