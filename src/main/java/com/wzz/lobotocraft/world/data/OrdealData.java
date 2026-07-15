package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全服共享的考验状态。
 */
public class OrdealData extends SavedData {
    private static final String NAME = "lobotocraft_ordeal_data";
    public static final int NO_DAWN_TYPE = 0;
    public static final int BLOOD_DAWN_TYPE = 1;
    public static final int GREEN_DAWN_TYPE = 2;
    public static final int VIOLET_DAWN_TYPE = 3;
    public static final int AMBER_DAWN_TYPE = 4;
    public static final int NO_MIDDAY_TYPE = 0;
    public static final int BLUE_MIDDAY_TYPE = 1;
    public static final int VIOLET_MIDDAY_TYPE = 2;
    public static final int GREEN_MIDDAY_TYPE = 3;

    private int trackedDay = 0;
    private int dawnChance = 0;
    private int dawnTriggersToday = 0;
    private int nextDawnType = NO_DAWN_TYPE;
    private boolean bloodDawnActive = false;
    private int bloodDawnRemaining = 0;
    private boolean greenDawnActive = false;
    private int greenDawnRemaining = 0;
    private boolean violetDawnActive = false;
    private int violetDawnRemaining = 0;
    private boolean amberDawnActive = false;
    private int amberDawnRemaining = 0;
    private int middayTriggersToday = 0;
    private int nextMiddayType = NO_MIDDAY_TYPE;
    private boolean blueMiddayActive = false;
    private int blueMiddayRemaining = 0;
    private boolean violetMiddayActive = false;
    private int violetMiddayRemaining = 0;
    private boolean greenMiddayActive = false;
    private int greenMiddayRemaining = 0;

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
            violetDawnActive = false;
            violetDawnRemaining = 0;
            amberDawnActive = false;
            amberDawnRemaining = 0;
            middayTriggersToday = 0;
            nextMiddayType = NO_MIDDAY_TYPE;
            blueMiddayActive = false;
            blueMiddayRemaining = 0;
            violetMiddayActive = false;
            violetMiddayRemaining = 0;
            greenMiddayActive = false;
            greenMiddayRemaining = 0;
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

    public void decrementDawnTriggersToday() {
        if (dawnTriggersToday <= 0) return;
        dawnTriggersToday--;
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

    public boolean isNextVioletDawn() {
        return nextDawnType == VIOLET_DAWN_TYPE;
    }

    public int getNextDawnType() {
        return nextDawnType;
    }

    public void setNextDawnType(int dawnType) {
        if (dawnType < BLOOD_DAWN_TYPE || dawnType > AMBER_DAWN_TYPE) {
            nextDawnType = NO_DAWN_TYPE;
        } else {
            nextDawnType = dawnType;
        }
        setDirty();
    }

    public void setRandomNextDawnType(RandomSource random) {
        setNextDawnType(BLOOD_DAWN_TYPE + random.nextInt(AMBER_DAWN_TYPE));
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

    public boolean isVioletDawnActive() {
        return violetDawnActive;
    }

    public void startVioletDawn(int entityCount) {
        violetDawnActive = true;
        violetDawnRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementVioletDawnRemaining() {
        if (violetDawnRemaining > 0) {
            violetDawnRemaining--;
            setDirty();
        }
        return violetDawnRemaining;
    }

    public void finishVioletDawn() {
        violetDawnActive = false;
        violetDawnRemaining = 0;
        setDirty();
    }

    public boolean hasActiveDawn() {
        return bloodDawnActive || greenDawnActive || violetDawnActive || amberDawnActive;
    }

    public boolean isAmberDawnActive() {
        return amberDawnActive;
    }

    public void startAmberDawn(int entityCount) {
        amberDawnActive = true;
        amberDawnRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementAmberDawnRemaining() {
        if (amberDawnRemaining > 0) {
            amberDawnRemaining--;
            setDirty();
        }
        return amberDawnRemaining;
    }

    public void finishAmberDawn() {
        amberDawnActive = false;
        amberDawnRemaining = 0;
        setDirty();
    }

    public int getMiddayTriggersToday() {
        return middayTriggersToday;
    }

    public void incrementMiddayTriggersToday() {
        middayTriggersToday++;
        setDirty();
    }

    public void decrementMiddayTriggersToday() {
        if (middayTriggersToday <= 0) return;
        middayTriggersToday--;
        setDirty();
    }

    public boolean hasNextMiddayType() {
        return nextMiddayType != NO_MIDDAY_TYPE;
    }

    public int getNextMiddayType() {
        return nextMiddayType;
    }

    public void setNextMiddayType(int middayType) {
        if (middayType < BLUE_MIDDAY_TYPE || middayType > GREEN_MIDDAY_TYPE) {
            nextMiddayType = NO_MIDDAY_TYPE;
        } else {
            nextMiddayType = middayType;
        }
        setDirty();
    }

    public void setRandomNextMiddayType(RandomSource random) {
        setNextMiddayType(BLUE_MIDDAY_TYPE + random.nextInt(GREEN_MIDDAY_TYPE));
    }

    public boolean isBlueMiddayActive() {
        return blueMiddayActive;
    }

    public void startBlueMidday(int entityCount) {
        blueMiddayActive = true;
        blueMiddayRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementBlueMiddayRemaining() {
        if (blueMiddayRemaining > 0) {
            blueMiddayRemaining--;
            setDirty();
        }
        return blueMiddayRemaining;
    }

    public void finishBlueMidday() {
        blueMiddayActive = false;
        blueMiddayRemaining = 0;
        setDirty();
    }

    public boolean isVioletMiddayActive() {
        return violetMiddayActive;
    }

    public void startVioletMidday(int entityCount) {
        violetMiddayActive = true;
        violetMiddayRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementVioletMiddayRemaining() {
        if (violetMiddayRemaining > 0) {
            violetMiddayRemaining--;
            setDirty();
        }
        return violetMiddayRemaining;
    }

    public void finishVioletMidday() {
        violetMiddayActive = false;
        violetMiddayRemaining = 0;
        setDirty();
    }

    public boolean isGreenMiddayActive() {
        return greenMiddayActive;
    }

    public void startGreenMidday(int entityCount) {
        greenMiddayActive = true;
        greenMiddayRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public int decrementGreenMiddayRemaining() {
        if (greenMiddayRemaining > 0) {
            greenMiddayRemaining--;
            setDirty();
        }
        return greenMiddayRemaining;
    }

    public void finishGreenMidday() {
        greenMiddayActive = false;
        greenMiddayRemaining = 0;
        setDirty();
    }

    public boolean hasActiveMidday() {
        return blueMiddayActive || violetMiddayActive || greenMiddayActive;
    }

    public boolean hasActiveOrdeal() {
        return hasActiveDawn() || hasActiveMidday();
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
        tag.putBoolean("VioletDawnActive", violetDawnActive);
        tag.putInt("VioletDawnRemaining", violetDawnRemaining);
        tag.putBoolean("AmberDawnActive", amberDawnActive);
        tag.putInt("AmberDawnRemaining", amberDawnRemaining);
        tag.putInt("MiddayTriggersToday", middayTriggersToday);
        tag.putInt("NextMiddayType", nextMiddayType);
        tag.putBoolean("BlueMiddayActive", blueMiddayActive);
        tag.putInt("BlueMiddayRemaining", blueMiddayRemaining);
        tag.putBoolean("VioletMiddayActive", violetMiddayActive);
        tag.putInt("VioletMiddayRemaining", violetMiddayRemaining);
        tag.putBoolean("GreenMiddayActive", greenMiddayActive);
        tag.putInt("GreenMiddayRemaining", greenMiddayRemaining);
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
        data.violetDawnActive = tag.getBoolean("VioletDawnActive");
        data.violetDawnRemaining = tag.getInt("VioletDawnRemaining");
        data.amberDawnActive = tag.getBoolean("AmberDawnActive");
        data.amberDawnRemaining = tag.getInt("AmberDawnRemaining");
        data.middayTriggersToday = tag.getInt("MiddayTriggersToday");
        data.nextMiddayType = tag.getInt("NextMiddayType");
        data.blueMiddayActive = tag.getBoolean("BlueMiddayActive");
        data.blueMiddayRemaining = tag.getInt("BlueMiddayRemaining");
        data.violetMiddayActive = tag.getBoolean("VioletMiddayActive");
        data.violetMiddayRemaining = tag.getInt("VioletMiddayRemaining");
        data.greenMiddayActive = tag.getBoolean("GreenMiddayActive");
        data.greenMiddayRemaining = tag.getInt("GreenMiddayRemaining");
        return data;
    }
}
