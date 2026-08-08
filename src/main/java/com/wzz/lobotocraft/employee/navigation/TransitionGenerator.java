package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TransitionGenerator {
    private static final double HEIGHT_EPSILON = 1.0E-6D;
    /**
     * Employees may use stairs and slabs, but must not jump onto a full block
     * wall and treat its top as a second floor. A one-block rise is a player
     * jump, not a facility route transition.
     */
    private static final double MAX_NAVIGABLE_RISE = 0.75D;
    private static final double MAX_TIERED_SUPPORT_RISE = 1.0D;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int[][] DIAGONAL = {
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    private final NodeEvaluator evaluator;
    private final int maxStepUp;
    private final int maxDropDown;
    private final boolean allowBreaking;

    public TransitionGenerator(NodeEvaluator evaluator, int maxStepUp,
                               int maxDropDown, boolean allowBreaking) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.maxStepUp = Math.max(0, Math.min(maxStepUp, 1));
        this.maxDropDown = Math.max(0, Math.min(maxDropDown, 4));
        this.allowBreaking = allowBreaking;
    }

    public List<NavigationTransition> generate(NavigationNode from) {
        List<NavigationTransition> result = new ArrayList<>(8);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = from.feetPosition().relative(direction);
            for (NodeEvaluator.CandidateEvaluation candidate : evaluator.candidates(horizontal,
                    from.standingY(), maxStepUp, maxDropDown, allowBreaking)) {
                double rise = candidate.node().standingY() - from.standingY();
                if (rise > MAX_NAVIGABLE_RISE
                        && (rise > MAX_TIERED_SUPPORT_RISE
                        || !evaluator.hasTieredSupport(candidate.node()))) {
                    continue;
                }
                NodeEvaluator.Evaluation movement = evaluator.evaluateTransition(
                        from, candidate.node(), allowBreaking);
                if (movement.traversable()) result.add(toTransition(from, candidate.node(), movement));
            }
        }
        for (int[] offset : DIAGONAL) {
            addDiagonalTransitions(result, from, offset[0], offset[1]);
        }
        return List.copyOf(result);
    }

    private void addDiagonalTransitions(List<NavigationTransition> result, NavigationNode from,
                                        int offsetX, int offsetZ) {
        BlockPos targetColumn = from.feetPosition().offset(offsetX, 0, offsetZ);
        for (NodeEvaluator.CandidateEvaluation candidate : evaluator.candidates(targetColumn,
                from.standingY(), maxStepUp, maxDropDown, allowBreaking)) {
            NavigationNode target = candidate.node();
            if (Math.abs(target.standingY() - from.standingY()) > HEIGHT_EPSILON) continue;
            NavigationNode sideX = new NavigationNode(
                    from.feetPosition().offset(offsetX, 0, 0), from.standingY());
            NavigationNode sideZ = new NavigationNode(
                    from.feetPosition().offset(0, 0, offsetZ), from.standingY());
            if (!evaluator.evaluate(sideX, allowBreaking).traversable()
                    || !evaluator.evaluate(sideZ, allowBreaking).traversable()) continue;
            NodeEvaluator.Evaluation movement = evaluator.evaluateTransition(
                    from, target, allowBreaking);
            if (movement.traversable()) result.add(toTransition(from, target, movement));
        }
    }

    private NavigationTransition toTransition(NavigationNode from, NavigationNode target,
                                              NodeEvaluator.Evaluation evaluation) {
        List<BlockPos> affected = new ArrayList<>();
        affected.addAll(evaluation.doors());
        affected.addAll(evaluation.softObstacles());
        affected.addAll(evaluation.breakBlocks());
        double heightDelta = target.standingY() - from.standingY();
        NavigationAction action = heightDelta > HEIGHT_EPSILON
                ? NavigationAction.STEP_UP
                : heightDelta < -HEIGHT_EPSILON ? NavigationAction.DROP_DOWN : NavigationAction.WALK;
        int dx = target.feetPosition().getX() - from.feetPosition().getX();
        int dz = target.feetPosition().getZ() - from.feetPosition().getZ();
        double horizontalCost = dx != 0 && dz != 0 ? Math.sqrt(2.0D) : 1.0D;
        double cost = horizontalCost + Math.abs(heightDelta) * 0.35D
                + (evaluation.caution() ? 4.0D : 0.0D);
        if (!evaluation.breakBlocks().isEmpty()) {
            action = NavigationAction.BREAK_BLOCKS;
            cost += 8.0D + evaluation.breakCost();
        } else if (!evaluation.softObstacles().isEmpty()) {
            action = NavigationAction.CLEAR_SOFT_OBSTACLE;
            cost += 4.0D + evaluation.softObstacles().size();
        } else if (!evaluation.doors().isEmpty()) {
            action = NavigationAction.OPEN_DOOR;
            cost += 0.25D;
        }
        return new NavigationTransition(target, action, affected, cost);
    }
}
