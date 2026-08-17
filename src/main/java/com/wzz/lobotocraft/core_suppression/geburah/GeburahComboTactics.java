package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.function.IntUnaryOperator;

public final class GeburahComboTactics {
    private GeburahComboTactics() {
    }

    public static GeburahMove chooseNext(GeburahMove previous, Snapshot snapshot,
                                         IntUnaryOperator nextInt) {
        GeburahMove best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (GeburahMove candidate : GeburahMove.values()) {
            if (candidate == previous) continue;
            double score = score(candidate, snapshot)
                    + Math.floorMod(nextInt.applyAsInt(5), 5) * 0.05D;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? GeburahMove.SLASH : best;
    }

    private static double score(GeburahMove move, Snapshot snapshot) {
        return switch (move) {
            case SLASH -> 8.0D - Math.abs(snapshot.distance() - 3.2D) * 1.5D
                    + Math.min(3.0D, snapshot.lateralOffset()) * 2.0D
                    + Math.min(4, snapshot.nearbyEnemies());
            case STAB -> 4.0D + snapshot.distance() * 1.5D
                    + Math.max(0.0D, snapshot.awaySpeed()) * 12.0D
                    + (snapshot.forwardClear() ? 3.0D : -6.0D)
                    - snapshot.lateralOffset() * 0.8D;
            case VERTICAL_SLASH -> 7.0D - Math.abs(snapshot.distance() - 3.5D)
                    + Math.min(4, snapshot.nearbyEnemies()) * 2.5D
                    - Math.max(0.0D, snapshot.awaySpeed()) * 4.0D;
        };
    }

    public record Snapshot(double distance, double lateralOffset, double awaySpeed,
                           int nearbyEnemies, boolean forwardClear) {
    }
}
