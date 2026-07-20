package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.client.ScreenDistortionState;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;

public final class CoreSuppressionClientState {
    private static boolean active;
    private static CoreSuppressionType type;
    private static String ownerName = "";
    private static int dawnCompleted;
    private static int middayCompleted;
    private static int workCompleted;
    private static int workRequired;
    private static int visualStage;
    private static int completedMask;
    private static int tick;

    private CoreSuppressionClientState() {
    }

    public static void update(boolean active, int typeOrdinal, String ownerName,
                              int dawnCompleted, int middayCompleted,
                              int workCompleted, int workRequired,
                              int visualStage, int completedMask) {
        CoreSuppressionClientState.active = active;
        CoreSuppressionClientState.type = CoreSuppressionType.byOrdinal(typeOrdinal);
        CoreSuppressionClientState.ownerName = ownerName;
        CoreSuppressionClientState.dawnCompleted = dawnCompleted;
        CoreSuppressionClientState.middayCompleted = middayCompleted;
        CoreSuppressionClientState.workCompleted = workCompleted;
        CoreSuppressionClientState.workRequired = workRequired;
        CoreSuppressionClientState.visualStage = visualStage;
        CoreSuppressionClientState.completedMask = completedMask;
        if (!active) tick = 0;
    }

    public static void tick() {
        if (!active || type != CoreSuppressionType.YESOD) return;
        tick++;
        if (tick % 20 == 0) {
            float intensity = switch (visualStage) {
                case 3 -> 0.78F;
                case 2 -> 0.48F;
                default -> 0.22F;
            };
            ScreenDistortionState.activate(intensity, 60);
        }
    }

    public static boolean isActive() {
        return active && type != null;
    }

    public static boolean isActive(CoreSuppressionType expected) {
        return isActive() && type == expected;
    }

    public static CoreSuppressionType getType() {
        return type;
    }

    public static String getOwnerName() {
        return ownerName;
    }

    public static int getDawnCompleted() {
        return dawnCompleted;
    }

    public static int getMiddayCompleted() {
        return middayCompleted;
    }

    public static int getWorkCompleted() {
        return workCompleted;
    }

    public static int getWorkRequired() {
        return workRequired;
    }

    public static int getCompletedMask() {
        return completedMask;
    }

    public static boolean requirementsMet() {
        return dawnCompleted >= 3 && middayCompleted >= 2
                && workRequired > 0 && workCompleted >= workRequired;
    }
}
