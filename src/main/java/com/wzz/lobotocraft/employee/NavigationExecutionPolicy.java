package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.employee.navigation.NavigationAction;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationPathStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Pure movement thresholds shared by the live navigator and deterministic tests. */
final class NavigationExecutionPolicy {
    static final double STRAIGHT_WAYPOINT_DISTANCE = 0.45D;
    static final double TURN_WAYPOINT_DISTANCE = 0.10D;
    static final double WAYPOINT_VERTICAL_DISTANCE = 0.20D;
    static final double STRAIGHT_OVERSHOOT_LATERAL_DISTANCE = 0.65D;
    static final double COMBAT_GOAL_MARGIN = 0.35D;
    static final double COMBAT_PLANNING_DRIFT = 1.5D;

    private NavigationExecutionPolicy() {
    }

    static boolean hasReachedWaypoint(Vec3 position, NavigationPathStep current,
                                      NavigationPathStep next, boolean finalActualGoal,
                                      boolean actualGoalReached) {
        if (finalActualGoal && next == null) return actualGoalReached;
        NavigationNode waypoint = current.node();
        boolean verticallyCentered = Math.abs(position.y - waypoint.standingY())
                <= WAYPOINT_VERTICAL_DISTANCE;
        double threshold = next != null && changesDirection(current, next)
                ? TURN_WAYPOINT_DISTANCE : STRAIGHT_WAYPOINT_DISTANCE;
        double dx = position.x - (waypoint.feetPosition().getX() + 0.5D);
        double dz = position.z - (waypoint.feetPosition().getZ() + 0.5D);
        if (verticallyCentered && dx * dx + dz * dz <= threshold * threshold) return true;
        return next != null && continuesDirection(current, next)
                && hasPassedStraightWaypoint(position, current)
                && isVerticallyBetweenSteps(position.y, current, next);
    }

    private static boolean hasPassedStraightWaypoint(Vec3 position,
                                                       NavigationPathStep current) {
        Vec3 from = current.from().standingPosition();
        Vec3 waypoint = current.node().standingPosition();
        double directionX = waypoint.x - from.x;
        double directionZ = waypoint.z - from.z;
        double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-6D) return false;
        directionX /= length;
        directionZ /= length;

        double offsetX = position.x - waypoint.x;
        double offsetZ = position.z - waypoint.z;
        double forward = offsetX * directionX + offsetZ * directionZ;
        double lateral = Math.abs(offsetX * directionZ - offsetZ * directionX);
        return forward >= 0.0D && lateral <= STRAIGHT_OVERSHOOT_LATERAL_DISTANCE;
    }

    static float forwardInput(Vec3 position, NavigationNode waypoint) {
        double dx = position.x - (waypoint.feetPosition().getX() + 0.5D);
        double dz = position.z - (waypoint.feetPosition().getZ() + 0.5D);
        return dx * dx + dz * dz < 1.0D ? 0.9F : 1.0F;
    }

    static boolean needsStartAlignment(Vec3 position, NavigationNode start) {
        double dx = position.x - (start.feetPosition().getX() + 0.5D);
        double dz = position.z - (start.feetPosition().getZ() + 0.5D);
        return dx * dx + dz * dz > TURN_WAYPOINT_DISTANCE * TURN_WAYPOINT_DISTANCE
                || Math.abs(position.y - start.standingY()) > WAYPOINT_VERTICAL_DISTANCE;
    }

    static boolean sameMoveRequest(BlockPos currentTarget, double currentRange,
                                   BlockPos requestedTarget, double requestedRange) {
        return currentTarget != null && currentTarget.equals(requestedTarget)
                && Math.abs(currentRange - requestedRange) <= 1.0E-6D;
    }

    static boolean sameInteractionRequest(Vec3 currentTarget, double currentRange,
                                          Vec3 requestedTarget, double requestedRange) {
        return currentTarget != null && currentTarget.distanceToSqr(requestedTarget) <= 1.0E-8D
                && Math.abs(currentRange - requestedRange) <= 1.0E-6D;
    }

    static boolean validCombatEndpoint(Vec3 endpoint, Vec3 target, double attackRange) {
        return validCombatEndpoint(endpoint, target, 1.0D, attackRange);
    }

    static boolean validCombatEndpoint(Vec3 endpoint, Vec3 target,
                                       double minimumRange, double maximumRange) {
        if (endpoint == null || target == null) return false;
        if (!Double.isFinite(minimumRange) || !Double.isFinite(maximumRange)) return false;
        double reliableRange = Math.max(1.0D, maximumRange - COMBAT_GOAL_MARGIN);
        double reliableMinimum = Math.max(1.0D, Math.min(minimumRange, reliableRange));
        double distanceSqr = endpoint.distanceToSqr(target);
        return distanceSqr >= reliableMinimum * reliableMinimum
                && distanceSqr <= reliableRange * reliableRange;
    }

    static boolean sameRouteFloor(double originY, Vec3 firstTarget, Vec3 secondTarget) {
        if (firstTarget == null || secondTarget == null) return false;
        int firstDirection = floorDirection(originY, firstTarget.y);
        int secondDirection = floorDirection(originY, secondTarget.y);
        return firstDirection == secondDirection;
    }

    static boolean planningTargetStillRelevant(double originY, Vec3 plannedTarget,
                                                Vec3 latestTarget) {
        return sameRouteFloor(originY, plannedTarget, latestTarget)
                && plannedTarget.distanceToSqr(latestTarget)
                <= COMBAT_PLANNING_DRIFT * COMBAT_PLANNING_DRIFT;
    }

    /** True only when two adjacent walk steps continue along the same straight line. */
    static boolean continuesStraight(NavigationPathStep current, NavigationPathStep next) {
        if (current == null || next == null
                || current.transition().action()
                != NavigationAction.WALK
                || next.transition().action()
                != NavigationAction.WALK
                || Math.abs(current.node().standingY() - next.node().standingY()) > 1.0E-6D) {
            return false;
        }
        BlockPos firstFrom = current.from().feetPosition();
        BlockPos firstTo = current.node().feetPosition();
        BlockPos secondTo = next.node().feetPosition();
        int firstX = Integer.signum(firstTo.getX() - firstFrom.getX());
        int firstZ = Integer.signum(firstTo.getZ() - firstFrom.getZ());
        int secondX = Integer.signum(secondTo.getX() - firstTo.getX());
        int secondZ = Integer.signum(secondTo.getZ() - firstTo.getZ());
        return (firstX != 0 || firstZ != 0) && firstX == secondX && firstZ == secondZ;
    }

    static boolean isCardinalFlatWalk(NavigationPathStep step) {
        if (step == null || step.transition().action() != NavigationAction.WALK
                || Math.abs(step.from().standingY() - step.node().standingY()) > 1.0E-6D) {
            return false;
        }
        BlockPos from = step.from().feetPosition();
        BlockPos to = step.node().feetPosition();
        return Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ()) == 1;
    }

    private static boolean continuesDirection(NavigationPathStep current,
                                              NavigationPathStep next) {
        if (current == null || next == null) return false;
        BlockPos firstFrom = current.from().feetPosition();
        BlockPos firstTo = current.node().feetPosition();
        BlockPos secondTo = next.node().feetPosition();
        int firstX = Integer.signum(firstTo.getX() - firstFrom.getX());
        int firstZ = Integer.signum(firstTo.getZ() - firstFrom.getZ());
        int secondX = Integer.signum(secondTo.getX() - firstTo.getX());
        int secondZ = Integer.signum(secondTo.getZ() - firstTo.getZ());
        return (firstX != 0 || firstZ != 0) && firstX == secondX && firstZ == secondZ;
    }

    private static boolean isVerticallyBetweenSteps(double y, NavigationPathStep current,
                                                    NavigationPathStep next) {
        double first = current.node().standingY();
        double second = next.node().standingY();
        double minimum = Math.min(first, second) - WAYPOINT_VERTICAL_DISTANCE;
        double maximum = Math.max(first, second) + WAYPOINT_VERTICAL_DISTANCE;
        return y >= minimum && y <= maximum;
    }

    private static int floorDirection(double originY, double targetY) {
        double difference = targetY - originY;
        if (Math.abs(difference) <= 4.0D) return 0;
        return difference > 0.0D ? 1 : -1;
    }

    private static boolean changesDirection(NavigationPathStep current, NavigationPathStep next) {
        return !continuesStraight(current, next);
    }
}
