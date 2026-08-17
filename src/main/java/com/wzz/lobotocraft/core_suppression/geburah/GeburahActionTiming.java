package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahActionTiming {
    public static final double NORMAL_ACTION_SPEED = 1.0D;
    public static final double EGO_COMBO_SPEED = 1.60D;

    private final boolean ego;
    private final double animationSpeed;

    private GeburahActionTiming(boolean ego, double animationSpeed) {
        this.ego = ego;
        this.animationSpeed = animationSpeed;
    }

    public static GeburahActionTiming forPhase(boolean ego) {
        return new GeburahActionTiming(ego,
                ego ? EGO_COMBO_SPEED : NORMAL_ACTION_SPEED);
    }

    public int windupTicks() {
        return scaleDuration(GeburahMove.WINDUP_TICKS);
    }

    public int strikeTicks(GeburahMove move) {
        return scaleDuration(move.strikeTicks(ego));
    }

    public int hitTick(GeburahMove move) {
        int strikeTicks = strikeTicks(move);
        int scaled = Math.max(1, (int) Math.round(move.hitTick(ego) / animationSpeed));
        return Math.min(strikeTicks - 1, scaled);
    }

    public int recoveryTicks(GeburahMove move) {
        return scaleDuration(move.recoveryTicks());
    }

    public double animationSpeed() {
        return animationSpeed;
    }

    private int scaleDuration(int sourceTicks) {
        return Math.max(1, (int) Math.ceil(sourceTicks / animationSpeed));
    }
}
