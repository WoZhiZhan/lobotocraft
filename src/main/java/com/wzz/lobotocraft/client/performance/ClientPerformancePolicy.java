package com.wzz.lobotocraft.client.performance;

import java.util.concurrent.TimeUnit;

final class ClientPerformancePolicy {
    static final int NORMAL = 0;
    static final int SLOW = 1;
    static final int SEVERE = 2;
    static final int FREEZE = 3;

    static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    static final long SEVERE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    static final long FREEZE_NANOS = TimeUnit.MILLISECONDS.toNanos(1_000);

    private ClientPerformancePolicy() {
    }

    static int classify(long elapsedNanos) {
        if (elapsedNanos >= FREEZE_NANOS) return FREEZE;
        if (elapsedNanos >= SEVERE_NANOS) return SEVERE;
        if (elapsedNanos >= SLOW_NANOS) return SLOW;
        return NORMAL;
    }

    static String name(int severity) {
        return switch (severity) {
            case SLOW -> "SLOW";
            case SEVERE -> "SEVERE";
            case FREEZE -> "FREEZE";
            default -> "NORMAL";
        };
    }

    static long reportThrottleNanos(int severity) {
        return switch (severity) {
            case SLOW -> TimeUnit.SECONDS.toNanos(5);
            case SEVERE -> TimeUnit.SECONDS.toNanos(2);
            case FREEZE -> TimeUnit.SECONDS.toNanos(1);
            default -> Long.MAX_VALUE;
        };
    }
}
