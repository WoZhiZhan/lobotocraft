package com.wzz.lobotocraft.entity.abnormality;

public final class SingingMachinePolicy {
    public static final int SONG_TICKS = 100;
    public static final int ATTACK_WINDUP_TICKS = 60;
    public static final int ATTACK_COOLDOWN_TICKS = 66;

    private SingingMachinePolicy() {
    }

    public static boolean consumesWorkerAfterWork(int fortitudeLevel, int temperanceLevel) {
        return fortitudeLevel >= 4 || temperanceLevel <= 2;
    }

    public static float controlledAttackDamage(int fortitudeLevel) {
        return 16.0F + 3.0F * Math.max(1, fortitudeLevel);
    }
}
