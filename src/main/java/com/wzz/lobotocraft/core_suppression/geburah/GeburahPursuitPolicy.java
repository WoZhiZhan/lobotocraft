package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahPursuitPolicy {
    private static final double NORMAL_MOVEMENT_SPEED = 0.24D;
    private static final double EGO_MOVEMENT_SPEED = 0.54D;
    private static final double DESIRED_ATTACK_DISTANCE = 3.25D;
    private static final double NORMAL_MAX_STEP = 2.25D;
    private static final double EGO_MAX_STEP = 3.0D;
    private static final double ELEVATOR_FLOOR_THRESHOLD = 5.0D;
    private static final float NORMAL_MAX_TURN = 55.0F;
    private static final float EGO_MAX_TURN = 75.0F;

    private GeburahPursuitPolicy() {
    }

    public static double movementSpeed(boolean ego) {
        return ego ? EGO_MOVEMENT_SPEED : NORMAL_MOVEMENT_SPEED;
    }

    public static boolean shouldFaceMovement(boolean combatActionActive,
                                             double deltaX, double deltaZ) {
        return !combatActionActive && Double.isFinite(deltaX) && Double.isFinite(deltaZ)
                && deltaX * deltaX + deltaZ * deltaZ > 1.0E-6D;
    }

    public static float movementYaw(double deltaX, double deltaZ) {
        return wrapDegrees((float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D));
    }

    public static double stepDistance(double targetDistance, boolean ego) {
        if (!Double.isFinite(targetDistance) || targetDistance <= DESIRED_ATTACK_DISTANCE) {
            return 0.0D;
        }
        return Math.min(ego ? EGO_MAX_STEP : NORMAL_MAX_STEP,
                targetDistance - DESIRED_ATTACK_DISTANCE);
    }

    public static float turnToward(float currentYaw, float desiredYaw, boolean ego) {
        float maximum = ego ? EGO_MAX_TURN : NORMAL_MAX_TURN;
        float difference = wrapDegrees(desiredYaw - currentYaw);
        float change = Math.max(-maximum, Math.min(maximum, difference));
        return wrapDegrees(currentYaw + change);
    }

    public static boolean shouldUseElevator(double currentY, double targetY,
                                            boolean targetValid,
                                            boolean combatActionActive) {
        return targetValid && !combatActionActive
                && Double.isFinite(currentY) && Double.isFinite(targetY)
                && Math.abs(targetY - currentY) > ELEVATOR_FLOOR_THRESHOLD;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
