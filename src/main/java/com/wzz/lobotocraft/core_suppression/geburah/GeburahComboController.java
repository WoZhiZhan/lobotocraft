package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.List;
import java.util.ArrayList;

public final class GeburahComboController {
    private List<GeburahMove> moves = List.of();
    private Stage stage = Stage.READY;
    private boolean ego;
    private int moveIndex;
    private int ticksRemaining;
    private boolean hitEmitted;
    private boolean recoveryHit;
    private boolean finishedCleanRecovery;
    private GeburahActionTiming timing = GeburahActionTiming.forPhase(false);

    public Event start(List<GeburahMove> plan, boolean ego) {
        if (isAttacking() || plan == null || plan.isEmpty()) return Event.NONE;
        moves = List.copyOf(plan);
        this.ego = ego;
        timing = GeburahActionTiming.forPhase(ego);
        moveIndex = 0;
        stage = Stage.WINDUP;
        ticksRemaining = timing.windupTicks();
        hitEmitted = false;
        recoveryHit = false;
        finishedCleanRecovery = false;
        return Event.START_WINDUP;
    }

    public Event tick() {
        if (stage == Stage.READY) return Event.NONE;
        ticksRemaining--;
        if (stage == Stage.WINDUP && ticksRemaining <= 0) {
            beginStrike();
            return Event.START_STRIKE;
        }
        if (stage == Stage.STRIKE) {
            int elapsed = timing.strikeTicks(move()) - ticksRemaining;
            if (!hitEmitted && elapsed >= timing.hitTick(move())) {
                hitEmitted = true;
                return Event.HIT;
            }
            if (ticksRemaining <= 0) {
                if (moveIndex + 1 < moves.size()) {
                    moveIndex++;
                    beginStrike();
                    return Event.START_CHAIN_STRIKE;
                }
                stage = Stage.RECOVERY;
                ticksRemaining = timing.recoveryTicks(move());
                return Event.START_RECOVERY;
            }
        }
        if (stage == Stage.RECOVERY && ticksRemaining <= 0) {
            finishedCleanRecovery = !recoveryHit;
            stage = Stage.READY;
            ticksRemaining = 0;
            return Event.FINISHED;
        }
        return Event.NONE;
    }

    private void beginStrike() {
        stage = Stage.STRIKE;
        ticksRemaining = timing.strikeTicks(move());
        hitEmitted = false;
    }

    public void markRecoveryHit() {
        if (stage == Stage.RECOVERY) recoveryHit = true;
    }

    public boolean consumeCleanRecovery() {
        boolean clean = finishedCleanRecovery;
        finishedCleanRecovery = false;
        timing = GeburahActionTiming.forPhase(false);
        return clean;
    }

    public void reset() {
        moves = List.of();
        stage = Stage.READY;
        moveIndex = 0;
        ticksRemaining = 0;
        hitEmitted = false;
        recoveryHit = false;
        finishedCleanRecovery = false;
    }

    public GeburahMove move() {
        return moves.isEmpty() ? null : moves.get(moveIndex);
    }

    public GeburahMove previousMove() {
        return moveIndex <= 0 || moves.isEmpty() ? null : moves.get(moveIndex - 1);
    }

    public int moveIndex() {
        return moveIndex;
    }

    public int moveCount() {
        return moves.size();
    }

    public Stage stage() {
        return stage;
    }

    public boolean isAttacking() {
        return stage != Stage.READY;
    }

    public double animationSpeed() {
        return timing.animationSpeed();
    }

    public float strikeProgress() {
        if (stage != Stage.STRIKE || move() == null) return 0.0F;
        int total = timing.strikeTicks(move());
        return Math.max(0.0F, Math.min(1.0F, (total - ticksRemaining) / (float) total));
    }

    public boolean replaceCurrentMove(GeburahMove replacement) {
        if (replacement == null || stage != Stage.STRIKE || moveIndex <= 0
                || replacement == moves.get(moveIndex - 1)) {
            return false;
        }
        ArrayList<GeburahMove> adjusted = new ArrayList<>(moves);
        adjusted.set(moveIndex, replacement);
        moves = List.copyOf(adjusted);
        ticksRemaining = timing.strikeTicks(replacement);
        hitEmitted = false;
        return true;
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
        START_CHAIN_STRIKE,
        HIT,
        START_RECOVERY,
        FINISHED
    }
}
