package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahPosturePolicy {
    private GeburahPosturePolicy() {
    }

    public static boolean spendsComboPosture(boolean abnormalityTarget) {
        return !abnormalityTarget;
    }

    public static boolean restoresPostureOnHit(boolean abnormalityTarget) {
        return !abnormalityTarget;
    }

    public static boolean takesPostureDamageFrom(boolean playerResponsible) {
        return playerResponsible;
    }
}
