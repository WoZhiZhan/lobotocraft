package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahSpecialController {
    public static final int WINDUP_TICKS = 18;
    public static final int STRIKE_TICKS = 17;
    public static final int HIT_TICK = 1;
    public static final int RECOVERY_TICKS = 18;

    private Stage stage = Stage.READY;
    private int ticksRemaining;
    private boolean hitEmitted;

    public Event start() {
        if (isActive()) return Event.NONE;
        stage = Stage.WINDUP;
        ticksRemaining = WINDUP_TICKS;
        hitEmitted = false;
        return Event.START_WINDUP;
    }

    public Event tick() {
        if (stage == Stage.READY) return Event.NONE;
        ticksRemaining--;
        if (stage == Stage.WINDUP && ticksRemaining <= 0) {
            stage = Stage.STRIKE;
            ticksRemaining = STRIKE_TICKS;
            hitEmitted = false;
            return Event.START_STRIKE;
        }
        if (stage == Stage.STRIKE) {
            int elapsed = STRIKE_TICKS - ticksRemaining;
            if (!hitEmitted && elapsed >= HIT_TICK) {
                hitEmitted = true;
                return Event.HIT;
            }
            if (ticksRemaining <= 0) return beginRecovery();
        }
        if (stage == Stage.RECOVERY && ticksRemaining <= 0) {
            reset();
            return Event.FINISHED;
        }
        return Event.NONE;
    }

    public Event abortToRecovery() {
        return isActive() ? beginRecovery() : Event.NONE;
    }

    private Event beginRecovery() {
        stage = Stage.RECOVERY;
        ticksRemaining = RECOVERY_TICKS;
        return Event.START_RECOVERY;
    }

    public void reset() {
        stage = Stage.READY;
        ticksRemaining = 0;
        hitEmitted = false;
    }

    public Stage stage() {
        return stage;
    }

    public boolean isActive() {
        return stage != Stage.READY;
    }

    public float strikeProgress() {
        if (stage != Stage.STRIKE) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F,
                (STRIKE_TICKS - ticksRemaining) / (float) STRIKE_TICKS));
    }

    public enum Stage {
        READY,
        WINDUP,
        STRIKE,
        RECOVERY
    }

    public enum Event {
        NONE,
        START_WINDUP,
        START_STRIKE,
        HIT,
        START_RECOVERY,
        FINISHED
    }
}
