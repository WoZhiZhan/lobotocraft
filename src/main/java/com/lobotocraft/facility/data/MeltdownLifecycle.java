package com.lobotocraft.facility.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small lifecycle seam for timed containment-unit meltdowns.
 * Runtime persistence remains owned by FacilityDayData; this module owns the
 * cause-specific invariants used by creation and ticking.
 */
public final class MeltdownLifecycle {
    public static final int DEFAULT_DURATION_TICKS = 20 * 60;
    public static final int NORMAL_TIME_RATE_PERMILLE = 1000;
    public static final int MAX_TIME_RATE_PERMILLE = 16_000;

    private MeltdownLifecycle() {
    }

    public static long deadline(long gameTime, int durationTicks) {
        return gameTime + Math.max(1, durationTicks);
    }

    public static MeltdownDefinition definition(MeltdownCause cause) {
        return MeltdownDefinition.forCause(cause);
    }

    public static boolean start(FacilityDayData data, UUID target, long gameTime, int level,
                                String targetCode, MeltdownCause cause) {
        MeltdownStageContext context = new MeltdownStageContext(
            data == null ? 0 : data.getDay(), level, false, "", -1);
        return start(data, target, gameTime, level, targetCode, cause, context,
            UUID.randomUUID(), null, cause == null ? "" : cause.name().toLowerCase(java.util.Locale.ROOT),
            MeltdownTargetLocator.unknown());
    }

    public static boolean start(FacilityDayData data, UUID target, long gameTime, int level,
                                String targetCode, MeltdownCause cause,
                                MeltdownStageContext stageContext, UUID batchId,
                                UUID sourceEntity, String sourceType) {
        return start(data, target, gameTime, level, targetCode, cause, stageContext,
            batchId, sourceEntity, sourceType, MeltdownTargetLocator.unknown());
    }

    public static boolean start(FacilityDayData data, UUID target, long gameTime, int level,
                                String targetCode, MeltdownCause cause,
                                MeltdownStageContext stageContext, UUID batchId,
                                UUID sourceEntity, String sourceType,
                                MeltdownTargetLocator targetLocator) {
        if (data == null || target == null) return false;
        MeltdownDefinition definition = definition(cause);
        FacilityDayData.ActiveMeltdown existing = data.getActiveMeltdowns().get(target);
        if (existing != null && definition.stackingPolicy() == MeltdownStackingPolicy.IGNORE_NEW) {
            return false;
        }
        data.addMeltdown(target, gameTime, definition.durationTicks(), level, targetCode,
            definition.cause(), definition.defaultTimeRatePermille(), definition.version(),
            stageContext, batchId == null ? UUID.randomUUID() : batchId,
            sourceEntity, sourceType, targetLocator);
        return true;
    }

    public static boolean resolve(FacilityDayData data, UUID target) {
        if (data == null || target == null) return false;
        FacilityDayData.ActiveMeltdown instance = data.getActiveMeltdowns().get(target);
        if (instance == null || definition(instance.cause()).resolutionPolicy()
                != MeltdownResolutionPolicy.WORK_COMPLETION) return false;
        return end(data, target, MeltdownEndReason.WORK_COMPLETED) != null;
    }

    public static MeltdownResolution end(FacilityDayData data, UUID target,
                                          MeltdownEndReason reason) {
        if (data == null || target == null || reason == null) return null;
        FacilityDayData.ActiveMeltdown instance = data.getActiveMeltdowns().get(target);
        if (instance == null || !data.resolveMeltdown(target)) return null;
        return new MeltdownResolution(target, instance, reason);
    }

    /** Advances every active instance and returns targets whose time reached zero. */
    public static List<UUID> advance(FacilityDayData data, long gameTime) {
        if (data == null || data.getActiveMeltdowns().isEmpty()) return List.of();
        List<UUID> expired = new ArrayList<>();
        boolean changed = false;
        for (Map.Entry<UUID, FacilityDayData.ActiveMeltdown> entry
                : data.getActiveMeltdowns().entrySet()) {
            FacilityDayData.ActiveMeltdown previous = entry.getValue();
            FacilityDayData.ActiveMeltdown advanced = previous.advance(gameTime);
            if (!advanced.equals(previous)) {
                entry.setValue(advanced);
                changed = true;
            }
            if (advanced.expired()) expired.add(entry.getKey());
        }
        if (changed) data.setDirty();
        return List.copyOf(expired);
    }

    /** 0 pauses the timer; 1000 is normal speed; 2000 is double speed. */
    public static boolean setTimeRate(FacilityDayData data, UUID target, long gameTime,
                                      int ratePermille) {
        if (data == null || target == null) return false;
        FacilityDayData.ActiveMeltdown current = data.getActiveMeltdowns().get(target);
        if (current == null) return false;
        int rate = Math.max(0, Math.min(MAX_TIME_RATE_PERMILLE, ratePermille));
        FacilityDayData.ActiveMeltdown updated = current.withTimeRate(gameTime, rate);
        if (updated.equals(current)) return false;
        data.getActiveMeltdowns().put(target, updated);
        data.setDirty();
        return true;
    }

    public static boolean survivesWithoutSuppression(FacilityDayData.ActiveMeltdown meltdown) {
        return meltdown != null && !definition(meltdown.cause()).requiresCoreSuppression();
    }

    public static int endSuppressionBound(FacilityDayData data) {
        if (data == null || data.getActiveMeltdowns().isEmpty()) return 0;
        List<UUID> targets = data.getActiveMeltdowns().entrySet().stream()
            .filter(entry -> entry.getValue().cause().requiresCoreSuppression())
            .map(Map.Entry::getKey)
            .toList();
        for (UUID target : targets) end(data, target, MeltdownEndReason.SUPPRESSION_ENDED);
        return targets.size();
    }

    public static int clear(FacilityDayData data, MeltdownEndReason reason) {
        if (data == null || reason == null || data.getActiveMeltdowns().isEmpty()) return 0;
        List<UUID> targets = List.copyOf(data.getActiveMeltdowns().keySet());
        for (UUID target : targets) end(data, target, reason);
        return targets.size();
    }
}
