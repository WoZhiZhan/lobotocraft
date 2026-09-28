package com.lobotocraft.facility.data;

/** Immutable rules for one timed meltdown cause. */
public record MeltdownDefinition(MeltdownCause cause, int version, int durationTicks,
                                 int defaultTimeRatePermille,
                                 boolean requiresCoreSuppression,
                                 MeltdownStackingPolicy stackingPolicy,
                                 MeltdownResolutionPolicy resolutionPolicy,
                                 MeltdownFailurePolicy failurePolicy,
                                 MeltdownTargetCountPolicy targetCountPolicy,
                                 boolean excludeWorkingTargets) {
    public MeltdownDefinition {
        cause = cause == null ? MeltdownCause.LEGACY_OR_UNKNOWN : cause;
        version = Math.max(1, version);
        durationTicks = Math.max(1, durationTicks);
        defaultTimeRatePermille = Math.max(0, defaultTimeRatePermille);
        stackingPolicy = stackingPolicy == null ? MeltdownStackingPolicy.IGNORE_NEW : stackingPolicy;
        resolutionPolicy = resolutionPolicy == null
            ? MeltdownResolutionPolicy.WORK_COMPLETION : resolutionPolicy;
        failurePolicy = failurePolicy == null
            ? MeltdownFailurePolicy.ENERGY_AND_COUNTER : failurePolicy;
        targetCountPolicy = targetCountPolicy == null
            ? MeltdownTargetCountPolicy.CALLER_LIMIT : targetCountPolicy;
    }

    public static MeltdownDefinition forCause(MeltdownCause cause) {
        MeltdownCause normalized = cause == null ? MeltdownCause.LEGACY_OR_UNKNOWN : cause;
        MeltdownTargetCountPolicy countPolicy = normalized == MeltdownCause.QLIPHOTH_OVERLOAD
            ? MeltdownTargetCountPolicy.QLIPHOTH_LEVEL_TENTHS
            : MeltdownTargetCountPolicy.CALLER_LIMIT;
        boolean excludeWorking = normalized != MeltdownCause.QLIPHOTH_OVERLOAD
            && normalized != MeltdownCause.APOCALYPSE_BIRD_EGG
            && normalized != MeltdownCause.DEBUG;
        return new MeltdownDefinition(normalized, 1, MeltdownLifecycle.DEFAULT_DURATION_TICKS,
            MeltdownLifecycle.NORMAL_TIME_RATE_PERMILLE, normalized.requiresCoreSuppression(),
            MeltdownStackingPolicy.IGNORE_NEW, MeltdownResolutionPolicy.WORK_COMPLETION,
            MeltdownFailurePolicy.ENERGY_AND_COUNTER, countPolicy, excludeWorking);
    }

    public int requestedTargetCount(int statisticalPopulation, int workablePopulation,
                                    int triggeredLevel, int callerLimit) {
        return targetCountPolicy.requested(statisticalPopulation, workablePopulation,
            triggeredLevel, callerLimit);
    }
}
