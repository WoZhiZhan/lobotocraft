package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahMajorAttackAudioPolicy {
    private static final int SOFT_HIT_INTERVAL_TICKS = 18;

    private GeburahMajorAttackAudioPolicy() {
    }

    public static HitSound forCue(GeburahMajorMove move, GeburahMajorMove.Cue cue,
                                  int elapsedTicks, int lastSoftHitTick) {
        if (move == null || cue == null) return HitSound.NONE;
        if (move == GeburahMajorMove.BODIES_EVERYWHERE || cue.maxDamage() >= 30) {
            return HitSound.IMPACT;
        }
        if (move.bloodMist()
                && elapsedTicks - lastSoftHitTick >= SOFT_HIT_INTERVAL_TICKS) {
            return HitSound.SOFT_HIT;
        }
        return HitSound.NONE;
    }

    public enum HitSound {
        NONE,
        SOFT_HIT,
        IMPACT
    }
}
