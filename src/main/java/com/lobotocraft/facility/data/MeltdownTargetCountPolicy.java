package com.lobotocraft.facility.data;

public enum MeltdownTargetCountPolicy {
    QLIPHOTH_LEVEL_TENTHS {
        @Override
        int requested(int eligibleContainments, int triggeredLevel, int callerLimit) {
            long scaled = (long) Math.max(0, eligibleContainments) * Math.max(1, triggeredLevel);
            return (int) Math.min(Integer.MAX_VALUE, (scaled + 9L) / 10L);
        }
    },
    CALLER_LIMIT {
        @Override
        int requested(int eligibleContainments, int triggeredLevel, int callerLimit) {
            return Math.max(0, callerLimit);
        }
    };

    abstract int requested(int eligibleContainments, int triggeredLevel, int callerLimit);
}
