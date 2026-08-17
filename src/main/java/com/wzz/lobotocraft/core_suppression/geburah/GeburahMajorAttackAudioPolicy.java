package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahMajorAttackAudioPolicy {
    private static final int WINDUP_REPLAY_INTERVAL_TICKS = 18;

    private GeburahMajorAttackAudioPolicy() {
    }

    public static HitSound forCue(GeburahMajorMove move, GeburahMajorMove.Cue cue,
                                  int elapsedTicks, int lastWindupTick) {
        if (move == null || cue == null) return HitSound.NONE;
        if (move == GeburahMajorMove.BODIES_EVERYWHERE || cue.maxDamage() >= 30) {
            return HitSound.IMPACT;
        }
        if (move.bloodMist()
                && elapsedTicks - lastWindupTick >= WINDUP_REPLAY_INTERVAL_TICKS) {
            return HitSound.WINDUP;
        }
        return HitSound.NONE;
    }

    public enum HitSound {
        NONE,
        WINDUP,
        IMPACT
    }
}
