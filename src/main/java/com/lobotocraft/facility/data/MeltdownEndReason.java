package com.lobotocraft.facility.data;

/** Terminal reason reported when an active meltdown leaves the lifecycle. */
public enum MeltdownEndReason {
    WORK_COMPLETED,
    EXPIRED,
    DAY_CHANGED,
    SUPPRESSION_ENDED,
    TARGET_INVALID,
    EXHIBITION_RECOVERY,
    RULE_PROTECTED,
    LEGACY_MIGRATION
}
