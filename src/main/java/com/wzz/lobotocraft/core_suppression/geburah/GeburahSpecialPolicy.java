package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahSpecialPolicy {
    private GeburahSpecialPolicy() {
    }

    public static Action choose(Snapshot snapshot) {
        if (snapshot.ego() && snapshot.bodiesOpeningPending()
                && snapshot.bodiesReady()) {
            return Action.BODIES_EVERYWHERE;
        }
        if (snapshot.ego() && snapshot.bodiesReady()
                && snapshot.nearbyEnemies() >= 2) {
            return Action.BODIES_EVERYWHERE;
        }

        double bestScore = 0.0D;
        Action best = Action.COMBO;
        if (snapshot.playerTarget() && snapshot.bloodMistReady()
                && snapshot.distance() <= 7.0D) {
            double score = 35.0D + (snapshot.fullHealth() ? 45.0D : 10.0D)
                    + Math.max(0.0D, 7.0D - snapshot.distance());
            if (score > bestScore) {
                bestScore = score;
                best = Action.BLOOD_MIST;
            }
        }
        if (snapshot.willReady() && snapshot.willLandingSafe()) {
            double score = 30.0D + (snapshot.fullHealth() ? 35.0D : 0.0D)
                    + Math.min(20.0D, snapshot.distance());
            if (score > bestScore) best = Action.WILL_OF_BATTLE;
        }
        return best;
    }

    public enum Action {
        COMBO,
        WILL_OF_BATTLE,
        BLOOD_MIST,
        BODIES_EVERYWHERE
    }

    public record Snapshot(boolean ego, boolean playerTarget, boolean fullHealth,
                           double distance, int nearbyEnemies,
                           boolean bodiesOpeningPending, boolean bodiesReady,
                           boolean bloodMistReady, boolean willReady,
                           boolean willLandingSafe) {
    }
}
