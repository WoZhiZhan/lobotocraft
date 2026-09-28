package com.lobotocraft.facility.data;

public enum MeltdownTargetCountPolicy {
    QLIPHOTH_LEVEL_TENTHS {
        @Override
        int requested(int statisticalPopulation, int workablePopulation,
                      int triggeredLevel, int callerLimit) {
            long scaled = (long) Math.max(0, statisticalPopulation)
                    * Math.max(1, triggeredLevel);
            int levelScaled = (int) Math.min(Integer.MAX_VALUE, (scaled + 9L) / 10L);
            return Math.min(Math.max(0, workablePopulation), levelScaled);
        }
    },
    CALLER_LIMIT {
        @Override
        int requested(int statisticalPopulation, int workablePopulation,
                      int triggeredLevel, int callerLimit) {
            return Math.max(0, callerLimit);
        }
    };

    abstract int requested(int statisticalPopulation, int workablePopulation,
                           int triggeredLevel, int callerLimit);
}
