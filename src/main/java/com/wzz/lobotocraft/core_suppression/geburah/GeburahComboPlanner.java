package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public final class GeburahComboPlanner {
    public static final int NORMAL_MIN_SEGMENTS = 1;
    public static final int NORMAL_MAX_SEGMENTS = 3;
    public static final int EGO_MIN_SEGMENTS = 3;
    public static final int EGO_MAX_SEGMENTS = 6;

    private GeburahComboPlanner() {
    }

    public static AttackPlan planForPosture(boolean ego, boolean preferLong, float posture,
                                            IntUnaryOperator nextInt) {
        if (posture <= GeburahCombatState.COMBO_POSTURE_COST) {
            GeburahMove[] moves = GeburahMove.values();
            GeburahMove recoveryMove = moves[bounded(nextInt, moves.length)];
            return new AttackPlan(List.of(recoveryMove), false);
        }
        return new AttackPlan(plan(ego, preferLong, nextInt), true);
    }

    public static List<GeburahMove> plan(boolean ego, IntUnaryOperator nextInt) {
        return plan(ego, false, nextInt);
    }

    public static List<GeburahMove> plan(boolean ego, boolean preferLong,
                                         IntUnaryOperator nextInt) {
        int min = ego ? EGO_MIN_SEGMENTS : NORMAL_MIN_SEGMENTS;
        int max = ego ? EGO_MAX_SEGMENTS : NORMAL_MAX_SEGMENTS;
        int count = min + bounded(nextInt, max - min + 1);
        if (preferLong) count = Math.min(max, count + 1);
        GeburahMove[] moves = GeburahMove.values();
        List<GeburahMove> result = new ArrayList<>(count);
        GeburahMove previous = null;
        for (int i = 0; i < count; i++) {
            int choice = bounded(nextInt, previous == null ? moves.length : moves.length - 1);
            GeburahMove selected;
            if (previous == null) {
                selected = moves[choice];
            } else {
                selected = moves[choice >= previous.ordinal() ? choice + 1 : choice];
            }
            result.add(selected);
            previous = selected;
        }
        return List.copyOf(result);
    }

    private static int bounded(IntUnaryOperator nextInt, int bound) {
        if (bound <= 1) return 0;
        return Math.floorMod(nextInt.applyAsInt(bound), bound);
    }

    public record AttackPlan(List<GeburahMove> moves, boolean spendsComboPosture) {
        public AttackPlan {
            moves = List.copyOf(moves);
        }
    }
}
