package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahMajorAttackController {
    private GeburahMajorMove move;
    private GeburahMajorMove.Cue currentCue;
    private int elapsedTicks;
    private int cueIndex;
    private boolean active;

    public boolean start(GeburahMajorMove move) {
        if (active || move == null) return false;
        this.move = move;
        currentCue = null;
        elapsedTicks = 0;
        cueIndex = 0;
        active = true;
        return true;
    }

    public Event tick() {
        if (!active || move == null) return Event.NONE;
        elapsedTicks++;
        if (cueIndex < move.cues().size()
                && elapsedTicks >= move.cues().get(cueIndex).tick()) {
            currentCue = move.cues().get(cueIndex++);
            return Event.HIT;
        }
        if (elapsedTicks >= move.durationTicks()) {
            active = false;
            return Event.FINISHED;
        }
        return Event.NONE;
    }

    public void abort() {
        active = false;
        currentCue = null;
    }

    public void reset() {
        move = null;
        currentCue = null;
        elapsedTicks = 0;
        cueIndex = 0;
        active = false;
    }

    public Stage stage() {
        if (!active || move == null) return Stage.READY;
        int first = move.cues().get(0).tick();
        int last = move.cues().get(move.cues().size() - 1).tick();
        if (elapsedTicks < first) return Stage.WINDUP;
        if (elapsedTicks <= last) return Stage.ACTIVE;
        return Stage.RECOVERY;
    }

    public boolean isActive() {
        return active;
    }

    public GeburahMajorMove move() {
        return move;
    }

    public GeburahMajorMove.Cue currentCue() {
        return currentCue;
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public enum Event {
        NONE,
        HIT,
        FINISHED
    }

    public enum Stage {
        READY,
        WINDUP,
        ACTIVE,
        RECOVERY
    }
}
