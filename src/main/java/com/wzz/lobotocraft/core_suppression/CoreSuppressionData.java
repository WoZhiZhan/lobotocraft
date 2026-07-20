package com.wzz.lobotocraft.core_suppression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CoreSuppressionData extends SavedData {
    private static final String NAME = "lobotocraft_core_suppression";

    private int activeType = -1;
    private UUID ownerUuid;
    private String ownerName = "";
    private int challengeDay;
    private int completedMask;
    private int dawnCompleted;
    private int middayCompleted;
    private int seenDawnTriggerSerial;
    private int seenMiddayTriggerSerial;

    private final Map<UUID, int[]> hodPenalties = new HashMap<>();
    private final Map<UUID, int[]> pendingHodRestores = new HashMap<>();

    public static CoreSuppressionData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                CoreSuppressionData::load,
                CoreSuppressionData::new,
                NAME
        );
    }

    public boolean isActive() {
        return getActiveType() != null && ownerUuid != null;
    }

    public CoreSuppressionType getActiveType() {
        return CoreSuppressionType.byOrdinal(activeType);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getChallengeDay() {
        return challengeDay;
    }

    public int getCompletedMask() {
        return completedMask;
    }

    public int getDawnCompleted() {
        return dawnCompleted;
    }

    public int getMiddayCompleted() {
        return middayCompleted;
    }

    public int getSeenDawnTriggerSerial() {
        return seenDawnTriggerSerial;
    }

    public int getSeenMiddayTriggerSerial() {
        return seenMiddayTriggerSerial;
    }

    public boolean hasCompleted(CoreSuppressionType type) {
        return (completedMask & type.getCompletionBit()) != 0;
    }

    public boolean hasCompletedPrerequisites(CoreSuppressionType type) {
        int prerequisiteMask = type.getCompletionBit() - 1;
        return (completedMask & prerequisiteMask) == prerequisiteMask;
    }

    public void start(CoreSuppressionType type, UUID ownerUuid, String ownerName, int challengeDay,
                      int dawnTriggerSerial, int middayTriggerSerial) {
        this.activeType = type.ordinal();
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.challengeDay = challengeDay;
        this.dawnCompleted = 0;
        this.middayCompleted = 0;
        this.seenDawnTriggerSerial = dawnTriggerSerial;
        this.seenMiddayTriggerSerial = middayTriggerSerial;
        this.hodPenalties.clear();
        setDirty();
    }

    public void setProgress(int dawnCompleted, int middayCompleted) {
        int newDawn = Math.max(0, dawnCompleted);
        int newMidday = Math.max(0, middayCompleted);
        if (this.dawnCompleted == newDawn && this.middayCompleted == newMidday) {
            return;
        }
        this.dawnCompleted = newDawn;
        this.middayCompleted = newMidday;
        setDirty();
    }

    public void setSeenTriggerSerials(int dawn, int midday) {
        if (seenDawnTriggerSerial == dawn && seenMiddayTriggerSerial == midday) {
            return;
        }
        seenDawnTriggerSerial = Math.max(0, dawn);
        seenMiddayTriggerSerial = Math.max(0, midday);
        setDirty();
    }

    public void addHodPenalty(UUID playerUuid, int statIndex, int amount) {
        if (amount <= 0 || statIndex < 0 || statIndex >= 4) return;
        hodPenalties.computeIfAbsent(playerUuid, ignored -> new int[4])[statIndex] += amount;
        setDirty();
    }

    public void prepareHodRestores() {
        for (Map.Entry<UUID, int[]> entry : hodPenalties.entrySet()) {
            int[] pending = pendingHodRestores.computeIfAbsent(entry.getKey(), ignored -> new int[4]);
            int[] penalty = entry.getValue();
            for (int i = 0; i < pending.length; i++) {
                pending[i] += penalty[i];
            }
        }
        hodPenalties.clear();
        setDirty();
    }

    public int[] takePendingHodRestore(UUID playerUuid) {
        int[] restore = pendingHodRestores.remove(playerUuid);
        if (restore != null) setDirty();
        return restore;
    }

    public void finish(boolean success) {
        CoreSuppressionType type = getActiveType();
        if (success && type != null) {
            completedMask |= type.getCompletionBit();
        }
        activeType = -1;
        ownerUuid = null;
        ownerName = "";
        challengeDay = 0;
        dawnCompleted = 0;
        middayCompleted = 0;
        seenDawnTriggerSerial = 0;
        seenMiddayTriggerSerial = 0;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("ActiveType", activeType);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putString("OwnerName", ownerName);
        tag.putInt("ChallengeDay", challengeDay);
        tag.putInt("CompletedMask", completedMask);
        tag.putInt("DawnCompleted", dawnCompleted);
        tag.putInt("MiddayCompleted", middayCompleted);
        tag.putInt("SeenDawnTriggerSerial", seenDawnTriggerSerial);
        tag.putInt("SeenMiddayTriggerSerial", seenMiddayTriggerSerial);
        tag.put("HodPenalties", saveStatMap(hodPenalties));
        tag.put("PendingHodRestores", saveStatMap(pendingHodRestores));
        return tag;
    }

    public static CoreSuppressionData load(CompoundTag tag) {
        CoreSuppressionData data = new CoreSuppressionData();
        data.activeType = tag.contains("ActiveType") ? tag.getInt("ActiveType") : -1;
        if (tag.hasUUID("Owner")) data.ownerUuid = tag.getUUID("Owner");
        data.ownerName = tag.getString("OwnerName");
        data.challengeDay = tag.getInt("ChallengeDay");
        data.completedMask = tag.getInt("CompletedMask");
        data.dawnCompleted = tag.getInt("DawnCompleted");
        data.middayCompleted = tag.getInt("MiddayCompleted");
        data.seenDawnTriggerSerial = tag.getInt("SeenDawnTriggerSerial");
        data.seenMiddayTriggerSerial = tag.getInt("SeenMiddayTriggerSerial");
        loadStatMap(tag.getList("HodPenalties", Tag.TAG_COMPOUND), data.hodPenalties);
        loadStatMap(tag.getList("PendingHodRestores", Tag.TAG_COMPOUND), data.pendingHodRestores);
        return data;
    }

    private static ListTag saveStatMap(Map<UUID, int[]> source) {
        ListTag list = new ListTag();
        source.forEach((uuid, values) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", uuid);
            entry.putIntArray("Values", values);
            list.add(entry);
        });
        return list;
    }

    private static void loadStatMap(ListTag list, Map<UUID, int[]> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            int[] saved = entry.getIntArray("Values");
            int[] values = new int[4];
            System.arraycopy(saved, 0, values, 0, Math.min(saved.length, values.length));
            target.put(entry.getUUID("Player"), values);
        }
    }
}
