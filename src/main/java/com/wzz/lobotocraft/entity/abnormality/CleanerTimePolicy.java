package com.wzz.lobotocraft.entity.abnormality;

public final class CleanerTimePolicy {
    private static final long DAY_LENGTH = 24_000L;
    private static final long NIGHT_START = 13_000L;
    private static final long NIGHT_END = 23_000L;

    private CleanerTimePolicy() {
    }

    public static boolean isNaturalSpawnTime(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, DAY_LENGTH);
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
    }
}
