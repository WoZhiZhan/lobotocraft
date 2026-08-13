package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public static final int CRIMSON_MIDDAY_TYPE = 4;

    private int trackedDay = 0;
    private int dawnChance = 0;
    private int dawnTriggersToday = 0;
    private int dawnTriggerSerialToday = 0;
    private int dawnCompletionsToday = 0;
    private int nextDawnType = NO_DAWN_TYPE;
    private boolean bloodDawnActive = false;
    private int bloodDawnRemaining = 0;
    private boolean greenDawnActive = false;
    private int greenDawnRemaining = 0;
    private boolean violetDawnActive = false;
    private int violetDawnRemaining = 0;
    private boolean amberDawnActive = false;
    private int amberDawnRemaining = 0;
    private final Map<UUID, AmberDawnRecord> trackedAmberDawns = new LinkedHashMap<>();
    private int middayTriggersToday = 0;
    private int middayTriggerSerialToday = 0;
    private int middayCompletionsToday = 0;
    private int nextMiddayType = NO_MIDDAY_TYPE;
    private boolean blueMiddayActive = false;
    private int blueMiddayRemaining = 0;
    private boolean violetMiddayActive = false;
    private int violetMiddayRemaining = 0;
    private boolean greenMiddayActive = false;
    private int greenMiddayRemaining = 0;
    private boolean crimsonMiddayActive = false;
    private int crimsonMiddayRemaining = 0;
    private int lateOrdealTier = 0;
    private int lateOrdealColor = 0;
    private final Map<UUID, LateOrdealRecord> trackedLateOrdeals = new LinkedHashMap<>();

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
            dawnTriggerSerialToday = 0;
            dawnCompletionsToday = 0;
            nextDawnType = NO_DAWN_TYPE;
            bloodDawnActive = false;
            bloodDawnRemaining = 0;
            greenDawnActive = false;
            greenDawnRemaining = 0;
            violetDawnActive = false;
            violetDawnRemaining = 0;
            amberDawnActive = false;
            amberDawnRemaining = 0;
            trackedAmberDawns.clear();
            middayTriggersToday = 0;
            middayTriggerSerialToday = 0;
            middayCompletionsToday = 0;
            nextMiddayType = NO_MIDDAY_TYPE;
            blueMiddayActive = false;
            blueMiddayRemaining = 0;
            violetMiddayActive = false;
            violetMiddayRemaining = 0;
            greenMiddayActive = false;
            greenMiddayRemaining = 0;
            crimsonMiddayActive = false;
            crimsonMiddayRemaining = 0;
            lateOrdealTier = 0;
            lateOrdealColor = 0;
            trackedLateOrdeals.clear();
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
        dawnTriggerSerialToday++;
        setDirty();
    }

    public int getDawnTriggerSerialToday() {
        return dawnTriggerSerialToday;
    }

    public int getDawnCompletionsToday() {
        return dawnCompletionsToday;
    }

    public void setCompletionsToday(int dawnCompletions, int middayCompletions) {
        dawnCompletionsToday = Math.max(0, dawnCompletions);
        middayCompletionsToday = Math.max(0, middayCompletions);
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
        if (bloodDawnActive) dawnCompletionsToday++;
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
        if (greenDawnActive) dawnCompletionsToday++;
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
        if (violetDawnActive) dawnCompletionsToday++;
        violetDawnActive = false;
        violetDawnRemaining = 0;
        setDirty();
    }

    public boolean hasActiveDawn() {
        return bloodDawnActive || greenDawnActive || violetDawnActive || amberDawnActive;
    }

    public void abortAllActiveOrdeals() {
        bloodDawnActive = false;
        bloodDawnRemaining = 0;
        greenDawnActive = false;
        greenDawnRemaining = 0;
        violetDawnActive = false;
        violetDawnRemaining = 0;
        amberDawnActive = false;
        amberDawnRemaining = 0;
        trackedAmberDawns.clear();
        blueMiddayActive = false;
        blueMiddayRemaining = 0;
        violetMiddayActive = false;
        violetMiddayRemaining = 0;
        greenMiddayActive = false;
        greenMiddayRemaining = 0;
        crimsonMiddayActive = false;
        crimsonMiddayRemaining = 0;
        lateOrdealTier = 0;
        lateOrdealColor = 0;
        trackedLateOrdeals.clear();
        setDirty();
    }

    public boolean isAmberDawnActive() {
        return amberDawnActive;
    }

    public void startAmberDawn(int entityCount) {
        amberDawnActive = true;
        amberDawnRemaining = Math.max(0, entityCount);
        trackedAmberDawns.clear();
        setDirty();
    }

    public void trackAmberDawn(UUID uuid, String dimension, int chunkX, int chunkZ) {
        if (!amberDawnActive || uuid == null || dimension == null || dimension.isBlank()) return;
        AmberDawnRecord next = new AmberDawnRecord(uuid, dimension, chunkX, chunkZ);
        AmberDawnRecord previous = trackedAmberDawns.put(uuid, next);
        amberDawnRemaining = trackedAmberDawns.size();
        if (!next.equals(previous)) {
            setDirty();
        }
    }

    public int removeTrackedAmberDawn(UUID uuid) {
        if (!amberDawnActive) return amberDawnRemaining;
        if (!trackedAmberDawns.isEmpty()) {
            if (trackedAmberDawns.remove(uuid) != null) {
                amberDawnRemaining = trackedAmberDawns.size();
                setDirty();
            }
            return amberDawnRemaining;
        }

        // Legacy saves only stored a raw remaining count.
        if (amberDawnRemaining > 0) {
            amberDawnRemaining--;
            setDirty();
        }
        return amberDawnRemaining;
    }

    public List<AmberDawnRecord> getTrackedAmberDawns() {
        return List.copyOf(trackedAmberDawns.values());
    }

    public void replaceTrackedAmberDawns(Collection<AmberDawnRecord> records) {
        if (!amberDawnActive) return;
        trackedAmberDawns.clear();
        for (AmberDawnRecord record : records) {
            if (record != null && record.uuid() != null
                    && record.dimension() != null && !record.dimension().isBlank()) {
                trackedAmberDawns.put(record.uuid(), record);
            }
        }
        amberDawnRemaining = trackedAmberDawns.size();
        setDirty();
    }

    public void cancelAmberDawn() {
        amberDawnActive = false;
        amberDawnRemaining = 0;
        trackedAmberDawns.clear();
        setDirty();
    }

    public void finishAmberDawn() {
        if (amberDawnActive) dawnCompletionsToday++;
        amberDawnActive = false;
        amberDawnRemaining = 0;
        trackedAmberDawns.clear();
        setDirty();
    }

    public int getMiddayTriggersToday() {
        return middayTriggersToday;
    }

    public void incrementMiddayTriggersToday() {
        middayTriggersToday++;
        middayTriggerSerialToday++;
        setDirty();
    }

    public int getMiddayTriggerSerialToday() {
        return middayTriggerSerialToday;
    }

    public int getMiddayCompletionsToday() {
        return middayCompletionsToday;
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
        if (middayType < BLUE_MIDDAY_TYPE || middayType > CRIMSON_MIDDAY_TYPE) {
            nextMiddayType = NO_MIDDAY_TYPE;
        } else {
            nextMiddayType = middayType;
        }
        setDirty();
    }

    public void setRandomNextMiddayType(RandomSource random) {
        setNextMiddayType(BLUE_MIDDAY_TYPE + random.nextInt(CRIMSON_MIDDAY_TYPE));
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
        if (blueMiddayActive) middayCompletionsToday++;
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
        if (violetMiddayActive) middayCompletionsToday++;
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
        if (greenMiddayActive) middayCompletionsToday++;
        greenMiddayActive = false;
        greenMiddayRemaining = 0;
        setDirty();
    }

    public boolean isCrimsonMiddayActive() {
        return crimsonMiddayActive;
    }

    public void startCrimsonMidday(int entityCount) {
        crimsonMiddayActive = true;
        crimsonMiddayRemaining = Math.max(0, entityCount);
        setDirty();
    }

    public void addCrimsonMiddayRemaining(int entityCount) {
        if (entityCount <= 0) return;
        crimsonMiddayRemaining += entityCount;
        setDirty();
    }

    public int decrementCrimsonMiddayRemaining() {
        if (crimsonMiddayRemaining > 0) {
            crimsonMiddayRemaining--;
            setDirty();
        }
        return crimsonMiddayRemaining;
    }

    public void finishCrimsonMidday() {
        if (crimsonMiddayActive) middayCompletionsToday++;
        crimsonMiddayActive = false;
        crimsonMiddayRemaining = 0;
        setDirty();
    }

    public boolean hasActiveMidday() {
        return blueMiddayActive || violetMiddayActive || greenMiddayActive || crimsonMiddayActive;
    }

    public boolean hasActiveOrdeal() {
        return hasActiveDawn() || hasActiveMidday() || hasActiveLateOrdeal();
    }

    public boolean hasActiveLateOrdeal() {
        return lateOrdealTier != 0 && !trackedLateOrdeals.isEmpty();
    }

    public int getLateOrdealTier() {
        return lateOrdealTier;
    }

    public int getLateOrdealColor() {
        return lateOrdealColor;
    }

    public void startLateOrdeal(int tier, int color, Collection<LateOrdealRecord> records) {
        trackedLateOrdeals.clear();
        if (records != null) {
            for (LateOrdealRecord record : records) {
                if (record != null && record.uuid() != null && record.dimension() != null
                        && !record.dimension().isBlank()) {
                    trackedLateOrdeals.put(record.uuid(), record);
                }
            }
        }
        if (trackedLateOrdeals.isEmpty()) {
            lateOrdealTier = 0;
            lateOrdealColor = 0;
        } else {
            lateOrdealTier = Math.max(1, tier);
            lateOrdealColor = Math.max(1, color);
        }
        setDirty();
    }

    public void trackLateOrdeal(LateOrdealRecord record) {
        if (lateOrdealTier == 0 || record == null || record.uuid() == null
                || record.dimension() == null || record.dimension().isBlank()) return;
        if (!record.equals(trackedLateOrdeals.put(record.uuid(), record))) setDirty();
    }

    public int removeTrackedLateOrdeal(UUID uuid) {
        if (uuid != null && trackedLateOrdeals.remove(uuid) != null) setDirty();
        return trackedLateOrdeals.size();
    }

    public List<LateOrdealRecord> getTrackedLateOrdeals() {
        return List.copyOf(trackedLateOrdeals.values());
    }

    public void finishLateOrdeal() {
        lateOrdealTier = 0;
        lateOrdealColor = 0;
        trackedLateOrdeals.clear();
        setDirty();
    }

    public void resetForChallenge(int day) {
        trackedDay = Math.max(1, day);
        dawnChance = 0;
        dawnTriggersToday = 0;
        dawnTriggerSerialToday = 0;
        dawnCompletionsToday = 0;
        nextDawnType = NO_DAWN_TYPE;
        bloodDawnActive = false;
        bloodDawnRemaining = 0;
        greenDawnActive = false;
        greenDawnRemaining = 0;
        violetDawnActive = false;
        violetDawnRemaining = 0;
        amberDawnActive = false;
        amberDawnRemaining = 0;
        trackedAmberDawns.clear();
        middayTriggersToday = 0;
        middayTriggerSerialToday = 0;
        middayCompletionsToday = 0;
        nextMiddayType = NO_MIDDAY_TYPE;
        blueMiddayActive = false;
        blueMiddayRemaining = 0;
        violetMiddayActive = false;
        violetMiddayRemaining = 0;
        greenMiddayActive = false;
        greenMiddayRemaining = 0;
        crimsonMiddayActive = false;
        crimsonMiddayRemaining = 0;
        lateOrdealTier = 0;
        lateOrdealColor = 0;
        trackedLateOrdeals.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("TrackedDay", trackedDay);
        tag.putInt("DawnChance", dawnChance);
        tag.putInt("DawnTriggersToday", dawnTriggersToday);
        tag.putInt("DawnTriggerSerialToday", dawnTriggerSerialToday);
        tag.putInt("DawnCompletionsToday", dawnCompletionsToday);
        tag.putInt("NextDawnType", nextDawnType);
        tag.putBoolean("BloodDawnActive", bloodDawnActive);
        tag.putInt("BloodDawnRemaining", bloodDawnRemaining);
        tag.putBoolean("GreenDawnActive", greenDawnActive);
        tag.putInt("GreenDawnRemaining", greenDawnRemaining);
        tag.putBoolean("VioletDawnActive", violetDawnActive);
        tag.putInt("VioletDawnRemaining", violetDawnRemaining);
        tag.putBoolean("AmberDawnActive", amberDawnActive);
        tag.putInt("AmberDawnRemaining", amberDawnRemaining);
        ListTag amberDawns = new ListTag();
        for (AmberDawnRecord record : trackedAmberDawns.values()) {
            amberDawns.add(record.save());
        }
        tag.put("TrackedAmberDawns", amberDawns);
        tag.putInt("MiddayTriggersToday", middayTriggersToday);
        tag.putInt("MiddayTriggerSerialToday", middayTriggerSerialToday);
        tag.putInt("MiddayCompletionsToday", middayCompletionsToday);
        tag.putInt("NextMiddayType", nextMiddayType);
        tag.putBoolean("BlueMiddayActive", blueMiddayActive);
        tag.putInt("BlueMiddayRemaining", blueMiddayRemaining);
        tag.putBoolean("VioletMiddayActive", violetMiddayActive);
        tag.putInt("VioletMiddayRemaining", violetMiddayRemaining);
        tag.putBoolean("GreenMiddayActive", greenMiddayActive);
        tag.putInt("GreenMiddayRemaining", greenMiddayRemaining);
        tag.putBoolean("CrimsonMiddayActive", crimsonMiddayActive);
        tag.putInt("CrimsonMiddayRemaining", crimsonMiddayRemaining);
        tag.putInt("LateOrdealTier", lateOrdealTier);
        tag.putInt("LateOrdealColor", lateOrdealColor);
        ListTag lateOrdeals = new ListTag();
        for (LateOrdealRecord record : trackedLateOrdeals.values()) {
            lateOrdeals.add(record.save());
        }
        tag.put("TrackedLateOrdeals", lateOrdeals);
        return tag;
    }

    public static OrdealData load(CompoundTag tag) {
        OrdealData data = new OrdealData();
        data.trackedDay = tag.getInt("TrackedDay");
        data.dawnChance = tag.getInt("DawnChance");
        data.dawnTriggersToday = tag.getInt("DawnTriggersToday");
        data.dawnTriggerSerialToday = tag.getInt("DawnTriggerSerialToday");
        data.dawnCompletionsToday = tag.getInt("DawnCompletionsToday");
        data.nextDawnType = tag.getInt("NextDawnType");
        data.bloodDawnActive = tag.getBoolean("BloodDawnActive");
        data.bloodDawnRemaining = tag.getInt("BloodDawnRemaining");
        data.greenDawnActive = tag.getBoolean("GreenDawnActive");
        data.greenDawnRemaining = tag.getInt("GreenDawnRemaining");
        data.violetDawnActive = tag.getBoolean("VioletDawnActive");
        data.violetDawnRemaining = tag.getInt("VioletDawnRemaining");
        data.amberDawnActive = tag.getBoolean("AmberDawnActive");
        data.amberDawnRemaining = tag.getInt("AmberDawnRemaining");
        ListTag amberDawns = tag.getList("TrackedAmberDawns", Tag.TAG_COMPOUND);
        for (Tag entry : amberDawns) {
            AmberDawnRecord record = AmberDawnRecord.load((CompoundTag) entry);
            if (record != null) {
                data.trackedAmberDawns.put(record.uuid(), record);
            }
        }
        if (!data.trackedAmberDawns.isEmpty()) {
            data.amberDawnRemaining = data.trackedAmberDawns.size();
        }
        data.middayTriggersToday = tag.getInt("MiddayTriggersToday");
        data.middayTriggerSerialToday = tag.getInt("MiddayTriggerSerialToday");
        data.middayCompletionsToday = tag.getInt("MiddayCompletionsToday");
        data.nextMiddayType = tag.getInt("NextMiddayType");
        data.blueMiddayActive = tag.getBoolean("BlueMiddayActive");
        data.blueMiddayRemaining = tag.getInt("BlueMiddayRemaining");
        data.violetMiddayActive = tag.getBoolean("VioletMiddayActive");
        data.violetMiddayRemaining = tag.getInt("VioletMiddayRemaining");
        data.greenMiddayActive = tag.getBoolean("GreenMiddayActive");
        data.greenMiddayRemaining = tag.getInt("GreenMiddayRemaining");
        data.crimsonMiddayActive = tag.getBoolean("CrimsonMiddayActive");
        data.crimsonMiddayRemaining = tag.getInt("CrimsonMiddayRemaining");
        data.lateOrdealTier = tag.getInt("LateOrdealTier");
        data.lateOrdealColor = tag.getInt("LateOrdealColor");
        ListTag lateOrdeals = tag.getList("TrackedLateOrdeals", Tag.TAG_COMPOUND);
        for (Tag entry : lateOrdeals) {
            LateOrdealRecord record = LateOrdealRecord.load((CompoundTag) entry);
            if (record != null) data.trackedLateOrdeals.put(record.uuid(), record);
        }
        if (data.trackedLateOrdeals.isEmpty()) {
            data.lateOrdealTier = 0;
            data.lateOrdealColor = 0;
        }
        return data;
    }

    public record AmberDawnRecord(UUID uuid, String dimension, int chunkX, int chunkZ) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Uuid", uuid);
            tag.putString("Dimension", dimension);
            tag.putInt("ChunkX", chunkX);
            tag.putInt("ChunkZ", chunkZ);
            return tag;
        }

        private static AmberDawnRecord load(CompoundTag tag) {
            if (!tag.hasUUID("Uuid") || !tag.contains("Dimension", Tag.TAG_STRING)) {
                return null;
            }
            String dimension = tag.getString("Dimension");
            if (dimension.isBlank()) return null;
            return new AmberDawnRecord(
                    tag.getUUID("Uuid"), dimension, tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }

    public record LateOrdealRecord(UUID uuid, String dimension, int chunkX, int chunkZ) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Uuid", uuid);
            tag.putString("Dimension", dimension);
            tag.putInt("ChunkX", chunkX);
            tag.putInt("ChunkZ", chunkZ);
            return tag;
        }

        private static LateOrdealRecord load(CompoundTag tag) {
            if (!tag.hasUUID("Uuid") || !tag.contains("Dimension", Tag.TAG_STRING)) return null;
            String dimension = tag.getString("Dimension");
            if (dimension.isBlank()) return null;
            return new LateOrdealRecord(tag.getUUID("Uuid"), dimension,
                    tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }
}
