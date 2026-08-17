package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahCombatPolicy {
    private GeburahCombatPolicy() {
    }

    public static Action choose(Snapshot snapshot) {
        double comboScore = 48.0D;
        if (!snapshot.targetFullHealth()) comboScore += 32.0D;
        if (snapshot.distance() <= 5.0D) comboScore += 12.0D;
        if (snapshot.postureRatio() < 0.35F) comboScore -= 18.0D;

        double willScore = Double.NEGATIVE_INFINITY;
        if (snapshot.willOfBattleReady() && snapshot.safeLanding()
                && snapshot.distance() >= 2.0D && snapshot.distance() <= 24.0D) {
            willScore = 42.0D;
            if (snapshot.targetFullHealth()) willScore += 48.0D;
            if (snapshot.distance() >= 7.0D) willScore += 18.0D;
            if (snapshot.distance() <= 3.0D) willScore -= 24.0D;
            if (snapshot.willKillPriority()) willScore += 100.0D;
        }
        return willScore > comboScore ? Action.WILL_OF_BATTLE : Action.COMBO;
    }

    public enum Action {
        COMBO,
        WILL_OF_BATTLE
    }

    public record Snapshot(boolean targetFullHealth, double distance, float postureRatio,
                           boolean willOfBattleReady, boolean safeLanding,
                           boolean willKillPriority) {
    }
}
