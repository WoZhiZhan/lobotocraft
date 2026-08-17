package com.wzz.lobotocraft.core_suppression.geburah;

/** Camera and compositor feedback for one successful major-attack damage cue. */
public final class GeburahMajorHitFeedback {
    private GeburahMajorHitFeedback() {
    }

    public static Profile forCue(GeburahMajorMove move, GeburahMajorMove.Cue cue) {
        if (move == null || cue == null) return Profile.NONE;
        if (move == GeburahMajorMove.BODIES_EVERYWHERE) {
            return new Profile(12, 1.65F, 1.0F);
        }

        boolean ego = move == GeburahMajorMove.EGO_BLOOD_MIST;
        if (cue.maxDamage() >= 30) {
            return new Profile(ego ? 14 : 12, ego ? 1.78F : 1.45F, 1.0F);
        }
        if (cue.opening()) {
            return new Profile(6, ego ? 0.88F : 0.70F, 0.42F);
        }
        if (cue.maxDamage() >= 12) {
            return new Profile(8, ego ? 1.05F : 0.82F, ego ? 0.72F : 0.62F);
        }
        return new Profile(5, ego ? 0.55F : 0.45F, 0.18F);
    }

    public record Profile(int durationTicks, float amplitude, float visualIntensity) {
        private static final Profile NONE = new Profile(0, 0.0F, 0.0F);
    }
}
