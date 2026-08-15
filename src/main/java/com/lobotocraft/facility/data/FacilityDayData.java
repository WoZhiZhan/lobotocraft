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
                        entry.getString("TargetCode"), MeltdownCause.read(entry.getString("Cause")),
                        entry.contains("CreatedTick") ? entry.getLong("CreatedTick") : -1L,
                        entry.contains("RemainingTimeUnits") ? entry.getLong("RemainingTimeUnits") : -1L,
                        entry.contains("TimeRatePermille") ? entry.getInt("TimeRatePermille")
                                : ActiveMeltdown.NORMAL_TIME_RATE_PERMILLE,
                        entry.contains("LastProcessedTick") ? entry.getLong("LastProcessedTick") : -1L,
                        entry.contains("DefinitionVersion") ? entry.getInt("DefinitionVersion") : 0,
                        entry.hasUUID("InstanceId") ? entry.getUUID("InstanceId") : UUID.randomUUID(),
                        entry.hasUUID("BatchId") ? entry.getUUID("BatchId") : null,
                        entry.hasUUID("SourceEntity") ? entry.getUUID("SourceEntity") : null,
                        entry.getString("SourceType"),
                        new MeltdownStageContext(entry.getInt("FacilityDay"),
                            entry.contains("TriggeredQliphothLevel")
                                ? entry.getInt("TriggeredQliphothLevel")
                                : Math.max(1, entry.getInt("Level")),
                            entry.getBoolean("CoreSuppressionActive"),
                            entry.getString("CoreSuppressionType"),
                            entry.contains("CoreSuppressionPhase")
                                ? entry.getInt("CoreSuppressionPhase") : -1),
                        new MeltdownTargetLocator(entry.getString("TargetDimension"),
                            entry.getLong("TargetRoomMarker"),
                            entry.getBoolean("HasTargetRoomMarker"))));
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
            entry.putString("Cause", meltdown.cause().name());
            entry.putLong("CreatedTick", meltdown.createdTick());
            entry.putLong("RemainingTimeUnits", meltdown.remainingTimeUnits());
            entry.putInt("TimeRatePermille", meltdown.timeRatePermille());
            entry.putLong("LastProcessedTick", meltdown.lastProcessedTick());
            entry.putInt("DefinitionVersion", meltdown.definitionVersion());
            entry.putUUID("InstanceId", meltdown.instanceId());
            if (meltdown.batchId() != null) entry.putUUID("BatchId", meltdown.batchId());
            if (meltdown.sourceEntity() != null) entry.putUUID("SourceEntity", meltdown.sourceEntity());
            entry.putString("SourceType", meltdown.sourceType());
            entry.putInt("FacilityDay", meltdown.stageContext().facilityDay());
            entry.putInt("TriggeredQliphothLevel",
                meltdown.stageContext().triggeredQliphothLevel());
            entry.putBoolean("CoreSuppressionActive",
                meltdown.stageContext().coreSuppressionActive());
            entry.putString("CoreSuppressionType",
                meltdown.stageContext().coreSuppressionType());
            entry.putInt("CoreSuppressionPhase",
                meltdown.stageContext().coreSuppressionPhase());
            entry.putString("TargetDimension", meltdown.targetLocator().dimensionId());
            entry.putLong("TargetRoomMarker", meltdown.targetLocator().roomMarker());
            entry.putBoolean("HasTargetRoomMarker", meltdown.targetLocator().hasRoomMarker());
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
        MeltdownLifecycle.clear(this, MeltdownEndReason.DAY_CHANGED);
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
        addMeltdown(target, deadline, level, targetCode, MeltdownCause.LEGACY_OR_UNKNOWN);
    }

    public void addMeltdown(UUID target, long deadline, int level, String targetCode,
                             MeltdownCause cause) {
        activeMeltdowns.put(target, new ActiveMeltdown(deadline, level,
            targetCode == null ? "" : targetCode,
            cause == null ? MeltdownCause.LEGACY_OR_UNKNOWN : cause));
        setDirty();
    }

    public void addMeltdown(UUID target, long gameTime, int durationTicks, int level,
                             String targetCode, MeltdownCause cause, int timeRatePermille,
                             int definitionVersion, MeltdownStageContext stageContext,
                             UUID batchId, UUID sourceEntity, String sourceType,
                             MeltdownTargetLocator targetLocator) {
        activeMeltdowns.put(target, ActiveMeltdown.create(gameTime, durationTicks, level,
            targetCode, cause, timeRatePermille, definitionVersion, stageContext,
            batchId, sourceEntity, sourceType, targetLocator));
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

    public record ActiveMeltdown(long deadline, int level, String targetCode, MeltdownCause cause,
                                 long createdTick, long remainingTimeUnits,
                                 int timeRatePermille, long lastProcessedTick,
                                 int definitionVersion, UUID instanceId, UUID batchId,
                                 UUID sourceEntity, String sourceType,
                                 MeltdownStageContext stageContext,
                                 MeltdownTargetLocator targetLocator) {
        public static final int TIME_UNITS_PER_TICK = 1000;
        public static final int NORMAL_TIME_RATE_PERMILLE = 1000;

        public ActiveMeltdown {
            targetCode = targetCode == null ? "" : targetCode;
            cause = cause == null ? MeltdownCause.LEGACY_OR_UNKNOWN : cause;
            timeRatePermille = Math.max(0, timeRatePermille);
            definitionVersion = Math.max(0, definitionVersion);
            instanceId = instanceId == null ? UUID.randomUUID() : instanceId;
            sourceType = sourceType == null ? "" : sourceType;
            stageContext = stageContext == null ? MeltdownStageContext.legacy(level) : stageContext;
            targetLocator = targetLocator == null ? MeltdownTargetLocator.unknown() : targetLocator;
        }

        /** Compatibility constructor for callers that only know the old fields. */
        public ActiveMeltdown(long deadline, int level, String targetCode) {
            this(deadline, level, targetCode, MeltdownCause.LEGACY_OR_UNKNOWN);
        }

        /** Legacy absolute-deadline constructor. It migrates on the first server tick. */
        public ActiveMeltdown(long deadline, int level, String targetCode, MeltdownCause cause) {
            this(deadline, level, targetCode, cause, -1L, -1L,
                NORMAL_TIME_RATE_PERMILLE, -1L, 0, UUID.randomUUID(), null,
                null, "", MeltdownStageContext.legacy(level), MeltdownTargetLocator.unknown());
        }

        public static ActiveMeltdown create(long gameTime, int durationTicks, int level,
                                             String targetCode, MeltdownCause cause,
                                             int timeRatePermille, int definitionVersion,
                                             MeltdownStageContext stageContext, UUID batchId,
                                             UUID sourceEntity, String sourceType,
                                             MeltdownTargetLocator targetLocator) {
            long duration = Math.max(1, durationTicks);
            int rate = Math.max(0, timeRatePermille);
            long remaining = saturatedMultiply(duration, TIME_UNITS_PER_TICK);
            return new ActiveMeltdown(projectDeadline(gameTime, remaining, rate), level,
                targetCode, cause, gameTime, remaining, rate, gameTime, definitionVersion,
                UUID.randomUUID(), batchId, sourceEntity, sourceType, stageContext, targetLocator);
        }

        public boolean usesDynamicClock() {
            return remainingTimeUnits >= 0L && lastProcessedTick >= 0L;
        }

        public ActiveMeltdown advance(long gameTime) {
            long now = Math.max(0L, gameTime);
            long remaining = remainingTimeUnits;
            long created = createdTick;
            long last = lastProcessedTick;
            if (!usesDynamicClock()) {
                remaining = saturatedMultiply(Math.max(0L, deadline - now), TIME_UNITS_PER_TICK);
                created = now;
                last = now;
            } else if (now > last && remaining > 0L && timeRatePermille > 0) {
                long consumed = saturatedMultiply(now - last, timeRatePermille);
                remaining = Math.max(0L, remaining - Math.min(remaining, consumed));
                last = now;
            } else if (now > last) {
                last = now;
            }
            return new ActiveMeltdown(projectDeadline(now, remaining, timeRatePermille), level,
                targetCode, cause, created, remaining, timeRatePermille, last, definitionVersion,
                instanceId, batchId, sourceEntity, sourceType, stageContext, targetLocator);
        }

        public ActiveMeltdown withTimeRate(long gameTime, int newRatePermille) {
            ActiveMeltdown current = advance(gameTime);
            int rate = Math.max(0, newRatePermille);
            return new ActiveMeltdown(projectDeadline(Math.max(0L, gameTime),
                    current.remainingTimeUnits, rate), level, targetCode, cause,
                current.createdTick, current.remainingTimeUnits, rate,
                Math.max(0L, gameTime), definitionVersion, instanceId, batchId,
                sourceEntity, sourceType, stageContext, targetLocator);
        }

        public boolean expired() {
            return usesDynamicClock() && remainingTimeUnits <= 0L;
        }

        public long projectedRemainingTicks(long gameTime) {
            if (!usesDynamicClock()) return Math.max(0L, deadline - gameTime);
            if (remainingTimeUnits <= 0L) return 0L;
            if (timeRatePermille <= 0) return Long.MAX_VALUE;
            return ceilingDivide(remainingTimeUnits, timeRatePermille);
        }

        private static long projectDeadline(long gameTime, long remaining, int ratePermille) {
            if (remaining <= 0L) return gameTime;
            if (ratePermille <= 0) return Long.MAX_VALUE;
            long ticks = ceilingDivide(remaining, ratePermille);
            return gameTime > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : gameTime + ticks;
        }

        private static long ceilingDivide(long value, long divisor) {
            return value / divisor + (value % divisor == 0L ? 0L : 1L);
        }

        private static long saturatedMultiply(long left, long right) {
            if (left <= 0L || right <= 0L) return 0L;
            return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
        }
    }
}
