package com.lobotocraft.facility.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class FacilityDayData extends SavedData {
    private static final String NAME = "lobotocraft_facility_day";
    private static final int SCHEDULE_VERSION = 2;
    private static final int[] ENERGY_QUOTAS = {
        15, 30, 45, 60, 80, 80, 100, 120, 140, 170,
        170, 190, 210, 230, 260, 260, 280, 300, 330, 360,
        420, 460, 500, 550, 600, 630, 660, 700, 740, 800,
        840, 880, 930, 980, 1050, 1090, 1130, 1180, 1230, 1300,
        1350, 1400, 1460, 1520, 1600, 1680, 1780, 1880, 2000, 1500
    };
    private static final int[] OVERLOAD_BASE = {
        30, 30, 30, 30, 30, 35, 35, 35, 35, 35,
        50, 50, 50, 50, 50, 55, 55, 55, 55, 55,
        70, 70, 70, 70, 70, 80, 80, 80, 80, 80,
        80, 80, 80, 90, 90, 90, 90, 90, 95, 95,
        95, 95, 95, 100, 105
    };

    private int day = 1;
    private int energy;
    /** Fractional global energy used by original-style continuous tools. */
    private double energyRemainder;
    private int meltdownLevel = 1;
    private int meltdownGauge;
    private OrdealTier nextOrdeal = OrdealTier.NONE;
    private int nextOrdealColor;
    private int noonStage;
    private boolean dawnTriggered;
    private boolean noonTriggered;
    /** Server-global TT2 protocol switch, controlled from the facility monitor. */
    private boolean tt2ProtocolEnabled;
    /** Daily ordeal schedule. Index is the meltdown stage (I..IX). */
    private final OrdealTier[] scheduledTiers = new OrdealTier[10];
    private final int[] scheduledColors = new int[10];
    private boolean schedulePrepared;
    private long notificationSerial;
    private int notificationType;
    private final Map<UUID, ActiveMeltdown> activeMeltdowns = new HashMap<>();
    private final Map<UUID, Integer> consecutiveWorks = new HashMap<>();

    public static FacilityDayData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
            .computeIfAbsent(FacilityDayData::load, FacilityDayData::new, NAME);
    }

    public static FacilityDayData load(CompoundTag tag) {
        FacilityDayData data = new FacilityDayData();
        data.day = Math.max(1, tag.getInt("Day"));
        data.energy = Math.max(0, tag.getInt("Energy"));
        data.energyRemainder = Math.max(0.0D,
                Math.min(Math.nextDown(1.0D), tag.getDouble("EnergyRemainder")));
        if (data.energy >= data.getEnergyQuota()) {
            data.energy = data.getEnergyQuota();
            data.energyRemainder = 0.0D;
        }
        data.meltdownLevel = Math.max(1, tag.getInt("MeltdownLevel"));
        data.meltdownGauge = Math.max(0, tag.getInt("MeltdownGauge"));
        data.nextOrdeal = OrdealTier.byId(tag.getInt("NextOrdeal"));
        data.nextOrdealColor = tag.getInt("NextOrdealColor");
        data.noonStage = tag.getInt("NoonStage");
        data.dawnTriggered = tag.getBoolean("DawnTriggered");
        data.noonTriggered = tag.getBoolean("NoonTriggered");
        data.tt2ProtocolEnabled = tag.getBoolean("TT2ProtocolEnabled");
        data.schedulePrepared = tag.getBoolean("SchedulePrepared")
                && tag.getInt("ScheduleVersion") >= SCHEDULE_VERSION;
        for (int i = 0; i < data.scheduledTiers.length; i++) {
            data.scheduledTiers[i] = OrdealTier.byId(tag.getInt("ScheduleTier" + i));
            data.scheduledColors[i] = Math.max(0, tag.getInt("ScheduleColor" + i));
        }
        // Saves written before the deterministic schedule was introduced are rebuilt
        // by FacilityManager on the next day boundary.
        if (!data.schedulePrepared) data.clearSchedule();
        else data.removeLockedOrdeals();
        data.notificationSerial = tag.getLong("NotificationSerial");
        data.notificationType = tag.getInt("NotificationType");

        ListTag meltdowns = tag.getList("ActiveMeltdowns", 10);
        for (int i = 0; i < meltdowns.size(); i++) {
            CompoundTag entry = meltdowns.getCompound(i);
                if (entry.hasUUID("Target")) {
                    data.activeMeltdowns.put(entry.getUUID("Target"),
                    new ActiveMeltdown(entry.getLong("Deadline"), Math.max(1, entry.getInt("Level")),
                        entry.getString("TargetCode")));
            }
        }
        ListTag streaks = tag.getList("ConsecutiveWorks", 10);
        for (int i = 0; i < streaks.size(); i++) {
            CompoundTag entry = streaks.getCompound(i);
            if (entry.hasUUID("Target")) {
                data.consecutiveWorks.put(entry.getUUID("Target"), Math.max(0, entry.getInt("Count")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Day", day);
        tag.putInt("Energy", energy);
        tag.putDouble("EnergyRemainder", energyRemainder);
        tag.putInt("MeltdownLevel", meltdownLevel);
        tag.putInt("MeltdownGauge", meltdownGauge);
        tag.putInt("NextOrdeal", nextOrdeal.ordinal());
        tag.putInt("NextOrdealColor", nextOrdealColor);
        tag.putInt("NoonStage", noonStage);
        tag.putBoolean("DawnTriggered", dawnTriggered);
        tag.putBoolean("NoonTriggered", noonTriggered);
        tag.putBoolean("TT2ProtocolEnabled", tt2ProtocolEnabled);
        tag.putBoolean("SchedulePrepared", schedulePrepared);
        tag.putInt("ScheduleVersion", SCHEDULE_VERSION);
        for (int i = 0; i < scheduledTiers.length; i++) {
            tag.putInt("ScheduleTier" + i,
                scheduledTiers[i] == null ? OrdealTier.NONE.ordinal() : scheduledTiers[i].ordinal());
            tag.putInt("ScheduleColor" + i, scheduledColors[i]);
        }
        tag.putLong("NotificationSerial", notificationSerial);
        tag.putInt("NotificationType", notificationType);

        ListTag meltdowns = new ListTag();
        activeMeltdowns.forEach((target, meltdown) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", target);
            entry.putLong("Deadline", meltdown.deadline());
            entry.putInt("Level", meltdown.level());
            entry.putString("TargetCode", meltdown.targetCode());
            meltdowns.add(entry);
        });
        tag.put("ActiveMeltdowns", meltdowns);

        ListTag streaks = new ListTag();
        consecutiveWorks.forEach((target, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", target);
            entry.putInt("Count", count);
            streaks.add(entry);
        });
        tag.put("ConsecutiveWorks", streaks);
        return tag;
    }

    public void beginDay(int newDay) {
        day = Math.max(1, newDay);
        energy = 0;
        energyRemainder = 0.0D;
        meltdownLevel = 1;
        meltdownGauge = 0;
        nextOrdeal = OrdealTier.NONE;
        nextOrdealColor = 0;
        noonStage = 0;
        dawnTriggered = false;
        noonTriggered = false;
        clearSchedule();
        activeMeltdowns.clear();
        consecutiveWorks.clear();
        notifyState(4);
    }

    public int getDay() { return day; }
    public int getEnergy() { return energy; }
    public double getExactEnergy() { return energy + energyRemainder; }
    public int getMeltdownLevel() { return meltdownLevel; }
    public int getMeltdownGauge() { return meltdownGauge; }
    public OrdealTier getNextOrdeal() {
        return OrdealSchedulePlanner.isUnlocked(day, nextOrdeal) ? nextOrdeal : OrdealTier.NONE;
    }
    public int getNextOrdealColor() {
        return getNextOrdeal() == OrdealTier.NONE ? 0 : nextOrdealColor;
    }
    public int getNoonStage() { return noonStage; }
    public boolean isDawnTriggered() { return dawnTriggered; }
    public boolean isNoonTriggered() { return noonTriggered; }
    public boolean isTt2ProtocolEnabled() { return tt2ProtocolEnabled; }

    public void setTt2ProtocolEnabled(boolean enabled) {
        if (tt2ProtocolEnabled == enabled) return;
        tt2ProtocolEnabled = enabled;
        setDirty();
    }

    public boolean toggleTt2Protocol() {
        tt2ProtocolEnabled = !tt2ProtocolEnabled;
        setDirty();
        return tt2ProtocolEnabled;
    }
    public long getNotificationSerial() { return notificationSerial; }
    public int getNotificationType() { return notificationType; }
    public Map<UUID, ActiveMeltdown> getActiveMeltdowns() { return activeMeltdowns; }

    public int getEnergyQuota() {
        int index = day - 1;
        if (index < ENERGY_QUOTAS.length) return ENERGY_QUOTAS[index];
        // 表尾之后每天 +30 递增。必须锚定数组真正的末项：早期表只有 20 项时这里写死了 [19]，
        // 表扩到 50 项后未同步，导致 Day 51 跌回 1290（低于 Day 50 的 1500）。
        int last = ENERGY_QUOTAS.length - 1;
        return ENERGY_QUOTAS[last] + (index - last) * 30;
    }

    public int getMeltdownThreshold() {
        int index = Math.min(day - 1, OVERLOAD_BASE.length - 1);
        return Math.max(1, (int) (OVERLOAD_BASE[index] * 0.15F));
    }

    public void addEnergy(int amount) {
        addEnergy((double) Math.max(0, amount));
    }

    public void addEnergy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        setExactEnergy(Math.min(getEnergyQuota(), getExactEnergy() + amount));
    }

    /** Server-authoritative debug setter; intentionally not used by normal gameplay. */
    public void debugSetEnergy(int amount) {
        energy = Math.max(0, Math.min(getEnergyQuota(), amount));
        energyRemainder = 0.0D;
        setDirty();
    }

    public void subtractEnergy(int amount) {
        setExactEnergy(Math.max(0.0D, getExactEnergy() - Math.max(0, amount)));
    }

    private void setExactEnergy(double amount) {
        double clamped = Math.max(0.0D, Math.min(getEnergyQuota(), amount));
        energy = (int) Math.floor(clamped + 1.0E-9D);
        energyRemainder = energy >= getEnergyQuota()
                ? 0.0D : Math.max(0.0D, clamped - energy);
        setDirty();
    }

    public boolean advanceGauge() {
        if (meltdownGauge < Integer.MAX_VALUE) meltdownGauge++;
        setDirty();
        boolean stageChanged = meltdownGauge >= getMeltdownThreshold();
        if (stageChanged) {
            // The original game resets every containment unit's repeated-work
            // counter when the Qliphoth overload level changes, even if this
            // mod's ordinary day does not spawn a visible meltdown.
            consecutiveWorks.clear();
            setDirty();
        }
        return stageChanged;
    }

    /** Server-authoritative debug setter; intentionally not used by normal gameplay. */
    public void debugSetMeltdownGauge(int value) {
        meltdownGauge = Math.max(0, value);
        setDirty();
    }

    public void finishMeltdownStage() {
        meltdownGauge = 0;
        meltdownLevel = Math.min(10, meltdownLevel + 1);
        consecutiveWorks.clear();
        setDirty();
    }

    public void setNextOrdeal(OrdealTier tier, int color) {
        nextOrdeal = tier;
        nextOrdealColor = color;
        setDirty();
    }

    /** Clears the current day's preselected ordeal table. */
    public void clearSchedule() {
        for (int i = 0; i < scheduledTiers.length; i++) {
            scheduledTiers[i] = OrdealTier.NONE;
            scheduledColors[i] = 0;
        }
        schedulePrepared = false;
        setDirty();
    }

    /**
     * Creates the complete stage table exactly once for a day. The table is
     * deterministic for a world seed/day pair and is persisted in SavedData,
     * so a reload cannot reroll an ordeal color.
     */
    public void prepareSchedule(long worldSeed) {
        if (schedulePrepared) return;
        OrdealSchedulePlanner.Plan plan = OrdealSchedulePlanner.create(day, worldSeed);
        for (int i = 0; i < scheduledTiers.length; i++) {
            scheduledTiers[i] = plan.tierAt(i);
            scheduledColors[i] = plan.colorAt(i);
        }
        schedulePrepared = true;
        setDirty();
    }

    public boolean isSchedulePrepared() {
        return schedulePrepared;
    }

    public OrdealTier getScheduledOrdeal(int stage) {
        if (stage < 0 || stage >= scheduledTiers.length || !schedulePrepared) return OrdealTier.NONE;
        OrdealTier tier = scheduledTiers[stage];
        return tier == null || !OrdealSchedulePlanner.isUnlocked(day, tier)
                ? OrdealTier.NONE : tier;
    }

    public int getScheduledOrdealColor(int stage) {
        if (stage < 0 || stage >= scheduledColors.length || !schedulePrepared) return 0;
        return getScheduledOrdeal(stage) == OrdealTier.NONE ? 0 : scheduledColors[stage];
    }

    private void removeLockedOrdeals() {
        boolean changed = false;
        for (int i = 0; i < scheduledTiers.length; i++) {
            if (OrdealSchedulePlanner.isUnlocked(day, scheduledTiers[i])) continue;
            scheduledTiers[i] = OrdealTier.NONE;
            scheduledColors[i] = 0;
            changed = true;
        }
        if (!OrdealSchedulePlanner.isUnlocked(day, nextOrdeal)) {
            nextOrdeal = OrdealTier.NONE;
            nextOrdealColor = 0;
            changed = true;
        }
        if (changed) setDirty();
    }

    /** Returns a defensive copy for server-side UI/network projection. */
    public OrdealScheduleEntry[] getSchedule() {
        OrdealScheduleEntry[] result = new OrdealScheduleEntry[scheduledTiers.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new OrdealScheduleEntry(getScheduledOrdeal(i), getScheduledOrdealColor(i));
        }
        return result;
    }

    public record OrdealScheduleEntry(OrdealTier tier, int color) {
    }

    public void setNoonStage(int stage) { noonStage = stage; setDirty(); }
    public void markDawnTriggered() { dawnTriggered = true; setDirty(); }
    public void markNoonTriggered() { noonTriggered = true; setDirty(); }

    public void addMeltdown(UUID target, long deadline, int level) {
        addMeltdown(target, deadline, level, "");
    }

    public void addMeltdown(UUID target, long deadline, int level, String targetCode) {
        activeMeltdowns.put(target, new ActiveMeltdown(deadline, level,
            targetCode == null ? "" : targetCode));
        setDirty();
    }

    public boolean resolveMeltdown(UUID target) {
        boolean removed = activeMeltdowns.remove(target) != null;
        if (removed) setDirty();
        return removed;
    }

    public int clearActiveMeltdowns() {
        int count = activeMeltdowns.size();
        if (count > 0) {
            activeMeltdowns.clear();
            setDirty();
        }
        return count;
    }

    public int getConsecutiveWorks(UUID target) {
        return consecutiveWorks.getOrDefault(target, 0);
    }

    public void incrementConsecutiveWorks(UUID target) {
        consecutiveWorks.compute(target, (ignored, count) ->
                count == null || count < Integer.MAX_VALUE ? (count == null ? 1 : count + 1)
                        : Integer.MAX_VALUE);
        setDirty();
    }

    public void incrementConsecutiveWorks(UUID target, int maximum) {
        int cap = Math.max(0, maximum);
        consecutiveWorks.compute(target, (ignored, count) ->
                Math.min(cap, count == null ? 1 : count + 1));
        setDirty();
    }

    public void notifyState(int type) {
        notificationType = type;
        notificationSerial++;
        setDirty();
    }

    public record ActiveMeltdown(long deadline, int level, String targetCode) {
    }
}
