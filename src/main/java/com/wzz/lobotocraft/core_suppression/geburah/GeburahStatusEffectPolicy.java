package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahStatusEffectPolicy {
    public static final int SPEED_DURATION_TICKS = 30 * 20;

    private GeburahStatusEffectPolicy() {
    }

    public static int nextSpeedAmplifier(int currentAmplifier) {
        return Math.min(2, Math.max(-1, currentAmplifier) + 1);
    }
}
