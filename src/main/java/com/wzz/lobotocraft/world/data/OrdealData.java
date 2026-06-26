package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全服共享的考验状态。
 */
public class OrdealData extends SavedData {
    private static final String NAME = "lobotocraft_ordeal_data";
    private static final int NO_DAWN_TYPE = 0;
    private static final int BLOOD_DAWN_TYPE = 1;
    private static final int GREEN_DAWN_TYPE = 2;

    private int trackedDay = 0;
    private int dawnChance = 0;
    private int dawnTriggersToday = 0;
    private int nextDawnType = NO_DAWN_TYPE;
    private boolean bloodDawnActive = false;
    private int bloodDawnRemaining = 0;
    private boolean greenDawnActive = false;
    private int greenDawnRemaining = 0;

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
            nextDawnType = NO_DAWN_TYPE;
            bloodDawnActive = false;
            bloodDawnRemaining = 0;
            greenDawnActive = false;
            greenDawnRemaining = 0;
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

    public void resetDawnTriggersToday() {
        dawnTriggersToday = 0;
        setDirty();
    }

    public boolean hasNextDawnType() {
        return nextDawnType != NO_DAWN_TYPE;
    }

    public boolean isNextGreenDawn() {
        return nextDawnType == GREEN_DAWN_TYPE;
    }

    public void setNextDawnType(boolean greenDawn) {
        nextDawnType = greenDawn ? GREEN_DAWN_TYPE : BLOOD_DAWN_TYPE;
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

    public boolean isGreenDawnActive() {
        return greenDawnActive;
    }

    public void startGreenDawn(int entityCount) {
        greenDawnActive = true;
        greenDawnRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementGreenDawnRemaining() {
        if (greenDawnRemaining > 0) {
            greenDawnRemaining--;
            setDirty();
        }
        return greenDawnRemaining;
    }

    public void finishGreenDawn() {
        greenDawnActive = false;
        greenDawnRemaining = 0;
        setDirty();
    }

    public boolean hasActiveDawn() {
        return bloodDawnActive || greenDawnActive;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("TrackedDay", trackedDay);
        tag.putInt("DawnChance", dawnChance);
        tag.putInt("DawnTriggersToday", dawnTriggersToday);
        tag.putInt("NextDawnType", nextDawnType);
        tag.putBoolean("BloodDawnActive", bloodDawnActive);
        tag.putInt("BloodDawnRemaining", bloodDawnRemaining);
        tag.putBoolean("GreenDawnActive", greenDawnActive);
        tag.putInt("GreenDawnRemaining", greenDawnRemaining);
        return tag;
    }

    public static OrdealData load(CompoundTag tag) {
        OrdealData data = new OrdealData();
        data.trackedDay = tag.getInt("TrackedDay");
        data.dawnChance = tag.getInt("DawnChance");
        data.dawnTriggersToday = tag.getInt("DawnTriggersToday");
        data.nextDawnType = tag.getInt("NextDawnType");
        data.bloodDawnActive = tag.getBoolean("BloodDawnActive");
        data.bloodDawnRemaining = tag.getInt("BloodDawnRemaining");
        data.greenDawnActive = tag.getBoolean("GreenDawnActive");
        data.greenDawnRemaining = tag.getInt("GreenDawnRemaining");
        return data;
    }
}
