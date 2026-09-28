package com.wzz.lobotocraft.employee;

import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.block.entity.ElevatorEntryFloorPolicy;
import com.wzz.lobotocraft.employee.navigation.AStarPathfinder;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationAction;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationPath;
import com.wzz.lobotocraft.employee.navigation.NavigationPathStep;
import com.wzz.lobotocraft.employee.navigation.NavigationLineOfSight;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.employee.navigation.NavigationTransition;
import com.wzz.lobotocraft.employee.navigation.NodeEvaluator;
import com.wzz.lobotocraft.employee.navigation.PathSearchResult;
import com.wzz.lobotocraft.employee.navigation.TransitionGenerator;
import com.wzz.lobotocraft.world.structure.StructureUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Player-sized A* navigation with explicit bidirectional elevator legs. */
public final class EmployeeNavigator {
    private enum State { DIRECT, TO_ELEVATOR, WAITING_ELEVATOR }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEARCH_MARGIN = 16;
    private static final int SEARCH_VERTICAL_MARGIN = 6;
    private static final int PATH_NODE_BUDGET = 65_536;
    /**
     * Small vertical moves remain ordinary A* routes so employees use facility
     * stairs. Large inter-department height changes should not make the planner
     * explore every intervening floor before it considers an elevator.
     */
    /**
     * Central Command's internal stair route spans 38 blocks vertically. Keep that
     * transfer on normal geometry; elevator fallback still activates if direct A*
     * genuinely cannot reach the requested floor.
     */
    private static final double ELEVATOR_FIRST_VERTICAL_DISTANCE = 48.0D;
    private static final int SNAPSHOT_CELLS_PER_SERVER_TICK = 4_096;
    private static final int SNAPSHOT_CELLS_PER_NAVIGATOR_TICK = 512;
    private static final int FACILITY_FALLBACK_CELLS_PER_NAVIGATOR_TICK = 2_048;
    private static final int PATH_RESULTS_PER_SERVER_TICK = 4;
    private static final int UNREACHABLE_STRIKES = 4;
    private static final double DEFAULT_ARRIVAL_DISTANCE = 1.6D;
    private static final int WAYPOINT_NO_PROGRESS_LIMIT = 25;
    private static final double INTERACTION_GOAL_MARGIN = 0.35D;
    private static final int MAX_VISIBLE_WAYPOINT_SKIP = 0;

    private static final Map<MinecraftServer, PlanningBudget> PLANNING_BUDGETS = new IdentityHashMap<>();
    private static final AtomicInteger PLANNER_THREAD_ID = new AtomicInteger();
    private static final ExecutorService PATH_EXECUTOR = Executors.newFixedThreadPool(2,
            daemonThreadFactory());

    private final EmployeeServerPlayer player;
    private BlockPos target;
    private double arrivalDistance = DEFAULT_ARRIVAL_DISTANCE;
    private Vec3 interactionTarget;
    private double interactionRange;
    private Vec3 routedInteractionTarget;
    private boolean combatTarget;
    private double combatMinimumRange = 1.0D;
    private double combatPreferredRange = 1.0D;
    private Vec3 combatSightTarget;
    private double navigatorEyeHeight;
    private BlockPos elevator;
    private boolean elevatorUp;
    private List<NavigationPathStep> path = List.of();
    private int pathIndex;
    private boolean pathEndsAtActualGoal;
    private State state = State.DIRECT;
    private int waitTicks;
    private int replanDelay;
    private int stuckTicks;
    private int stuckStrikes;
    private int noPathStrikes;
    private Vec3 lastStuckCheck;
    private Vec3 trackedWaypoint;
    private double bestWaypointDistance = Double.MAX_VALUE;
    private int waypointNoProgressTicks;
    private boolean complete;
    private boolean unreachable;
    private String lastFailureReason = "none";
    private final Set<ElevatorTraversal> usedElevatorTraversals = new HashSet<>();
    private final Set<BlockPos> rejectedElevatorSources = new HashSet<>();
    private PlanningJob planningJob;
    /** Temporary target used while a closed iron door waits for its pressure plate. */
    private Vec3 pressurePlateTarget;

    public EmployeeNavigator(EmployeeServerPlayer player) {
        this.player = player;
    }

    public void moveTo(BlockPos target) {
        moveTo(target, DEFAULT_ARRIVAL_DISTANCE);
    }

    public void moveTo(BlockPos target, double arrivalDistance) {
        BlockPos requestedTarget = target.immutable();
        double requestedDistance = normalizeArrivalDistance(arrivalDistance);
        if (interactionTarget == null && NavigationExecutionPolicy.sameMoveRequest(
                this.target, this.arrivalDistance, requestedTarget, requestedDistance)) return;
        this.interactionTarget = null;
        this.interactionRange = 0.0D;
        clearCombatTarget();
        this.target = requestedTarget;
        this.arrivalDistance = requestedDistance;
        this.complete = false;
        invalidatePath();
        resetStuckTracking();
        usedElevatorTraversals.clear();
        rejectedElevatorSources.clear();
        evaluateRoute();
    }

    /** Refresh a moving target without erasing progress or unreachable history for the order. */
    public void retargetTo(BlockPos target) {
        retargetTo(target, DEFAULT_ARRIVAL_DISTANCE);
    }

    public void retargetTo(BlockPos target, double arrivalDistance) {
        BlockPos requestedTarget = target.immutable();
        double requestedDistance = normalizeArrivalDistance(arrivalDistance);
        if (interactionTarget == null && NavigationExecutionPolicy.sameMoveRequest(
                this.target, this.arrivalDistance, requestedTarget, requestedDistance)) return;
        this.interactionTarget = null;
        this.interactionRange = 0.0D;
        clearCombatTarget();
        this.target = requestedTarget;
        this.arrivalDistance = requestedDistance;
        this.complete = false;
        invalidatePath();
        evaluateRoute();
    }

    /** Navigate to a reachable standing position inside the required interaction range. */
    public void moveNear(Vec3 target, double range) {
        double requestedRange = normalizeInteractionRange(range);
        if (!combatTarget && NavigationExecutionPolicy.sameInteractionRequest(
                interactionTarget, interactionRange, target, requestedRange)) return;
        this.interactionTarget = target;
        this.interactionRange = requestedRange;
        clearCombatTarget();
        this.target = BlockPos.containing(target);
        this.complete = false;
        invalidatePath();
        resetStuckTracking();
        usedElevatorTraversals.clear();
        rejectedElevatorSources.clear();
        evaluateRoute();
    }

    /** Refresh the geometry of the same interaction order while preserving its watchdog. */
    public void retargetNear(Vec3 target, double range) {
        double requestedRange = normalizeInteractionRange(range);
        if (!combatTarget && NavigationExecutionPolicy.sameInteractionRequest(
                interactionTarget, interactionRange, target, requestedRange)) return;
        boolean routeStillFits = routedInteractionTarget != null
                && routedInteractionTarget.distanceToSqr(target)
                <= INTERACTION_GOAL_MARGIN * INTERACTION_GOAL_MARGIN
                && Math.abs(interactionRange - requestedRange) <= 1.0E-6D;
        this.interactionTarget = target;
        this.interactionRange = requestedRange;
        clearCombatTarget();
        this.target = BlockPos.containing(target);
        if (routeStillFits && !complete) return;
        if (complete && hasArrived()) return;
        this.complete = false;
        invalidatePath();
        evaluateRoute();
    }

    /**
     * Tracks a moving combat target without discarding a route whose endpoint is
     * still inside the real weapon range. Planning also requires a clear attack line.
     */
    public void retargetCombat(LivingEntity entity, double attackRange) {
        retargetCombat(entity, 1.0D, attackRange, attackRange);
    }

    /** Keep a ranged employee inside a reachable firing band around a moving target. */
    public void retargetCombat(LivingEntity entity, double minimumRange,
                               double preferredRange, double maximumRange) {
        if (entity == null) return;
        Vec3 requestedTarget = entity.position();
        double requestedRange = normalizeInteractionRange(maximumRange);
        double reliableMaximum = Math.max(1.0D,
                requestedRange - NavigationExecutionPolicy.COMBAT_GOAL_MARGIN);
        double requestedMinimum = Mth.clamp(minimumRange, 1.0D, reliableMaximum);
        double requestedPreferred = Mth.clamp(preferredRange,
                requestedMinimum, reliableMaximum);
        boolean sameRange = Math.abs(interactionRange - requestedRange) <= 1.0E-6D
                && Math.abs(combatMinimumRange - requestedMinimum) <= 1.0E-6D
                && Math.abs(combatPreferredRange - requestedPreferred) <= 1.0E-6D;
        NavigationNode endpoint = currentPathEndpoint();
        boolean sameRouteFloor = combatTarget
                && NavigationExecutionPolicy.sameRouteFloor(
                player.getY(), interactionTarget, requestedTarget);
        boolean keepRoute = combatTarget && sameRange && !complete && endpoint != null
                && sameRouteFloor
                && (state == State.TO_ELEVATOR
                || NavigationExecutionPolicy.validCombatEndpoint(
                endpoint.standingPosition(), requestedTarget,
                requestedMinimum, requestedRange));
        boolean keepPlanning = combatTarget && sameRange && planningJob != null
                && NavigationExecutionPolicy.planningTargetStillRelevant(
                player.getY(), planningJob.request.interactionTarget(), requestedTarget);
        boolean keepRetryDelay = combatTarget && sameRange && !complete
                && planningJob == null && path.isEmpty()
                && NavigationExecutionPolicy.planningTargetStillRelevant(
                player.getY(), interactionTarget, requestedTarget);

        this.interactionTarget = requestedTarget;
        this.interactionRange = requestedRange;
        this.combatMinimumRange = requestedMinimum;
        this.combatPreferredRange = requestedPreferred;
        this.target = entity.blockPosition().immutable();
        this.combatTarget = true;
        this.combatSightTarget = entity.getEyePosition();
        this.navigatorEyeHeight = player.getEyeHeight();
        if (keepRoute || keepPlanning || keepRetryDelay) return;

        this.complete = false;
        invalidatePath();
        evaluateRoute();
    }

    public boolean isMovingNear(Vec3 target) {
        return interactionTarget != null && interactionTarget.distanceToSqr(target) <= 0.25D;
    }

    public void stop() {
        target = null;
        interactionTarget = null;
        interactionRange = 0.0D;
        clearCombatTarget();
        elevator = null;
        elevatorUp = false;
        usedElevatorTraversals.clear();
        rejectedElevatorSources.clear();
        complete = false;
        state = State.DIRECT;
        invalidatePath();
        resetStuckTracking();
        player.clearNavigationInput();
    }

    /** Rebuilds the current route after external world state changes such as a player respawn. */
    public void revalidateRoute() {
        if (target == null) return;
        complete = false;
        unreachable = false;
        noPathStrikes = 0;
        stuckStrikes = 0;
        invalidatePath();
        resetStuckTracking();
        evaluateRoute();
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isUnreachable() {
        return unreachable;
    }

    /** A complete snapshot proved this target unreachable at least once. */
    boolean hasConclusiveNoPath() {
        return noPathStrikes > 0;
    }

    public BlockPos target() {
        return target;
    }

    /** Test harness hook: exposes the next validated movement point. */
    Vec3 testMovementTarget() {
        if (pressurePlateTarget != null) return pressurePlateTarget;
        return pathIndex < path.size() ? path.get(pathIndex).node().standingPosition() : null;
    }

    /** Test harness hook for explaining a route that exhausted its retry budget. */
    String testFailureDiagnostics() {
        return "reason=" + lastFailureReason + ", state=" + state + ", elevator=" + elevator
                + ", elevatorUp=" + elevatorUp
                + ", stuckStrikes=" + stuckStrikes + ", noPathStrikes=" + noPathStrikes
                + ", path=" + pathIndex + "/" + path.size()
                + ", next=" + (pathIndex < path.size() ? path.get(pathIndex) : null)
                + ", planning=" + (planningJob != null);
    }

    public void tick() {
        tick(true);
    }

    /** Computes routes while withholding movement/elevator actions when paused. */
    public void tick(boolean allowMovement) {
        if (target == null || !player.isAlive()) {
            player.clearNavigationInput();
            return;
        }
        if (player.isNavigationInterrupted()) {
            player.clearNavigationInput();
            invalidatePath();
            replanDelay = 0;
            stuckTicks = 0;
            lastStuckCheck = player.position();
            return;
        }
        if (state == State.DIRECT && hasArrived()) {
            complete = true;
            invalidatePath();
            player.clearNavigationInput();
            return;
        }
        if (state == State.WAITING_ELEVATOR) {
            if (allowMovement) tickWaitingForElevator();
            return;
        }
        if (allowMovement && state == State.TO_ELEVATOR && tryUseElevator()) return;

        if (pathIndex >= path.size()) {
            player.clearNavigationInput();
            if (replanDelay > 0) {
                replanDelay--;
                return;
            }
            PlanResult result = planCurrentLeg();
            if (result == PlanResult.WORLD_DEFERRED) {
                replanDelay = 20;
                return;
            }
            if (result == PlanResult.BUDGET_DEFERRED || result == PlanResult.PLANNING_DEFERRED
                    || result == PlanResult.RETRY_EXPANDED) return;
            if (result == PlanResult.NO_PATH) {
                if (rejectUnreachableElevatorSource()) return;
                replanDelay = 40;
                lastFailureReason = "no-path@" + player.position();
                if (++noPathStrikes >= UNREACHABLE_STRIKES) unreachable = true;
                return;
            }
            noPathStrikes = 0;
            if (result == PlanResult.ALREADY_ARRIVED) {
                if (allowMovement && state == State.TO_ELEVATOR) tryUseElevator();
                else complete = true;
                return;
            }
        }

        if (!allowMovement) {
            player.clearNavigationInput();
            return;
        }

        int shortcutValidatedPathIndex = skipVisibleWaypoints();
        advanceReachedWaypoints();
        if (pathIndex >= path.size()) {
            player.clearNavigationInput();
            return;
        }
        NavigationPathStep step = path.get(pathIndex);
        boolean shortcutStillValid = shortcutValidatedPathIndex == pathIndex;
        boolean cardinalFlatWalk = NavigationExecutionPolicy.isCardinalFlatWalk(step);
        boolean transitionPrepared = prepareTransition(step);
        // The live noCollision probe can report a false negative for an adjacent
        // flat cell after a long server-player walk. A* already validated this
        // ordinary step; real movement collision plus the stuck watchdog handles
        // a block placed after planning. Keep strict revalidation for every
        // height-changing, diagonal and interactive transition.
        boolean waypointStillValid = shortcutStillValid || cardinalFlatWalk
                || isWaypointStillValid(step.node());
        boolean approachStillValid = shortcutStillValid || isApproachStillValid(step);
        if (!transitionPrepared || !waypointStillValid || !approachStillValid) {
            String invalidReason = "path-invalid@" + player.position()
                    + ", transitionPrepared=" + transitionPrepared
                    + ", waypointStillValid=" + waypointStillValid
                    + ", approachStillValid=" + approachStillValid
                    + ", shortcutStillValid=" + shortcutStillValid
                    + ", step=" + step;
            rejectBlockedPath(step);
            lastFailureReason = invalidReason;
            return;
        }
        if (!trackWaypointProgress(step.node())) {
            rejectCurrentPathForNoProgress();
            return;
        }
        applyInput(step);
        checkStuck();
    }

    private boolean hasArrived() {
        return interactionTarget != null
                ? combatTarget
                ? NavigationExecutionPolicy.validCombatEndpoint(
                player.position(), interactionTarget, combatMinimumRange, interactionRange)
                : player.position().distanceToSqr(interactionTarget) <= interactionRange * interactionRange
                : distanceTo(target) <= arrivalDistance;
    }

    private void evaluateRoute() {
        waitTicks = 0;
        replanDelay = 0;
        state = State.DIRECT;
        elevator = null;
        elevatorUp = false;
        // Keep nearby floors on normal geometry (notably Central Command's
        // internal stairs), but avoid a facility-height A* before even walking
        // toward an elevator for a genuinely long vertical transfer.
        if (shouldPreferElevatorFirst(player.getY(), target.getY())) {
            ElevatorChoice candidate = findBestElevator(false);
            if (candidate != null) {
                elevator = candidate.source();
                elevatorUp = candidate.upward();
                state = State.TO_ELEVATOR;
            }
        }
    }

    private ElevatorChoice findBestElevator(boolean allowVerticalDetour) {
        ServerLevel level = player.serverLevel();
        double currentVerticalGap = Math.abs(target.getY() - player.getY());
        boolean needsVerticalProgress = currentVerticalGap > 4.0D;
        boolean needsUp = target.getY() > player.getY();
        ElevatorChoice best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos position : ElevatorRegistry.knownPositions(level)) {
            if (!(level.getBlockEntity(position) instanceof ElevatorBlockEntity elevatorEntity)) continue;
            if (!ElevatorEntryFloorPolicy.isOnEntityFloor(level, player.blockPosition(), position)
                    && !elevatorEntity.isWithinInteractionRange(player)) continue;
            boolean[] directions = needsVerticalProgress
                    ? new boolean[]{needsUp} : new boolean[]{false, true};
            for (boolean upward : directions) {
                BlockPos destination = elevatorEntity.findDestinationElevator(level, upward);
                if (destination == null) continue;
                BlockPos sourcePosition = position.immutable();
                if (rejectedElevatorSources.contains(sourcePosition)) continue;
                ElevatorChoice choice = new ElevatorChoice(sourcePosition, upward);
                if (hasUsedElevatorTraversal(position, destination)) continue;
                double remaining = Math.abs(target.getY() - (destination.getY() + 1));
                if (needsVerticalProgress && remaining >= currentVerticalGap) continue;
                if (!needsVerticalProgress && !allowVerticalDetour) continue;
                double score = needsVerticalProgress
                        ? elevatorRouteScore(player.position(), target, position, destination)
                        : elevatorDetourScore(player.position(), target, position, destination);
                if (score < bestScore) {
                    bestScore = score;
                    best = choice;
                }
            }
        }
        return best;
    }

    private boolean rejectUnreachableElevatorSource() {
        if (state != State.TO_ELEVATOR || elevator == null) return false;
        rejectedElevatorSources.add(elevator.immutable());
        lastFailureReason = "unreachable-elevator-source@" + player.position()
                + " source=" + elevator + "/up=" + elevatorUp;
        state = State.DIRECT;
        elevator = null;
        elevatorUp = false;
        invalidatePath();
        replanDelay = 0;
        activateElevatorFallback();
        return true;
    }

    private boolean hasUsedElevatorTraversal(BlockPos source, BlockPos destination) {
        return usedElevatorTraversals.contains(new ElevatorTraversal(source, destination))
                || usedElevatorTraversals.contains(new ElevatorTraversal(destination, source));
    }

    private PlanResult planCurrentLeg() {
        if (planningJob == null) planningJob = beginPlanningJob();
        if (planningJob == null) return PlanResult.NO_PATH;
        if (planningJob.future == null) {
            NavigationSnapshot snapshot;
            if (planningJob.sharedFacilityCache) {
                snapshot = FacilityNavigationCache.snapshot(player.serverLevel(),
                        FacilityNavigationCache.COMPANY_MIN, FacilityNavigationCache.COMPANY_MAX);
                if (snapshot == null) {
                    if (StructureUpdater.isRunning()) return PlanResult.PLANNING_DEFERRED;
                    // The whole facility resource is decoded asynchronously at startup.
                    // Nearby orders can use loaded live chunks while that cache warms up.
                    int allowance = reserveSnapshotCells(player.server,
                            player.serverLevel().getGameTime(),
                            FACILITY_FALLBACK_CELLS_PER_NAVIGATOR_TICK);
                    if (allowance <= 0) return PlanResult.BUDGET_DEFERRED;
                    planningJob.capture.captureNext(allowance);
                    if (!planningJob.capture.isDone()) return PlanResult.PLANNING_DEFERRED;
                    snapshot = planningJob.capture.finish();
                }
            } else {
                int allowance = reserveSnapshotCells(player.server,
                        player.serverLevel().getGameTime(), SNAPSHOT_CELLS_PER_NAVIGATOR_TICK);
                if (allowance <= 0) return PlanResult.BUDGET_DEFERRED;
                planningJob.capture.captureNext(allowance);
                if (!planningJob.capture.isDone()) return PlanResult.PLANNING_DEFERRED;
                snapshot = planningJob.capture.finish();
            }
            PlanningRequest request = planningJob.request;
            AtomicBoolean cancelled = planningJob.cancelled;
            NavigationSnapshot planningSnapshot = snapshot;
            planningJob.future = CompletableFuture.supplyAsync(
                    () -> computePath(planningSnapshot, request, cancelled), PATH_EXECUTOR);
            return PlanResult.PLANNING_DEFERRED;
        }
        if (!planningJob.future.isDone()) return PlanResult.PLANNING_DEFERRED;
        if (!reservePathResult(player.server, player.serverLevel().getGameTime())) {
            return PlanResult.BUDGET_DEFERRED;
        }

        PlanningRequest completedRequest = planningJob.request;
        PlanningOutcome outcome;
        try {
            outcome = planningJob.future.join();
        } catch (RuntimeException exception) {
            LOGGER.error("Employee path planning failed for {} -> {}",
                    player.blockPosition(), target, exception);
            planningJob = null;
            return PlanResult.NO_PATH;
        }
        planningJob = null;
        PathSearchResult result = outcome.result;
        NavigationPath candidate = result.path();
        if (!result.reachedGoal()) {
            // A bounded search can still provide a useful route segment. Walking
            // it and replanning from its endpoint prevents a remote room order
            // from leaving the employee motionless while preserving exact A*
            // routes whenever the goal fits inside one search budget.
            if (isExecutablePartial(result)) {
                acceptPath(candidate, false, outcome.startNode);
                return PlanResult.PLANNED;
            }
            if (outcome.snapshotComplete && activateElevatorFallback()) {
                return PlanResult.RETRY_EXPANDED;
            }
            return outcome.snapshotComplete ? PlanResult.NO_PATH : PlanResult.WORLD_DEFERRED;
        }
        if (candidate == null) return PlanResult.NO_PATH;
        if (combatTarget && !completedRequest.toElevator
                && !NavigationExecutionPolicy.validCombatEndpoint(
                candidate.endpoint().standingPosition(), interactionTarget,
                combatMinimumRange, interactionRange)) {
            return PlanResult.RETRY_EXPANDED;
        }
        if (candidate.steps().isEmpty()) {
            if (hasReachedCurrentLeg()) return PlanResult.ALREADY_ARRIVED;
            if (outcome.startTraversable) {
                NavigationTransition alignment = new NavigationTransition(
                        outcome.startNode, NavigationAction.WALK, List.of(), 1.0D);
                path = List.of(new NavigationPathStep(
                        outcome.startNode, outcome.startNode, alignment, 1.0D, 1.0D));
                pathIndex = 0;
                pathEndsAtActualGoal = true;
                routedInteractionTarget = interactionTarget;
                resetWaypointProgress();
                return PlanResult.PLANNED;
            }
            return PlanResult.NO_PATH;
        }
        acceptPath(candidate, true, outcome.startNode);
        return PlanResult.PLANNED;
    }

    static boolean shouldPreferElevatorFirst(double currentY, double targetY) {
        return Double.isFinite(currentY) && Double.isFinite(targetY)
                && Math.abs(targetY - currentY) > ELEVATOR_FIRST_VERTICAL_DISTANCE;
    }

    static boolean isExecutablePartial(PathSearchResult result) {
        return result != null
                && result.status() == PathSearchResult.Status.PARTIAL_BUDGET_EXHAUSTED
                && result.path() != null
                && !result.path().steps().isEmpty();
    }

    private boolean activateElevatorFallback() {
        if (state != State.DIRECT || target == null) return false;
        ElevatorChoice candidate = findBestElevator(true);
        if (candidate == null) return false;
        elevator = candidate.source();
        elevatorUp = candidate.upward();
        state = State.TO_ELEVATOR;
        return true;
    }

    static double elevatorRouteScore(Vec3 playerPosition, BlockPos target,
                                     BlockPos source, BlockPos destination) {
        double entryDistance = playerPosition.distanceTo(Vec3.atCenterOf(source.above()));
        double exitDistance = Vec3.atCenterOf(destination.above())
                .distanceTo(Vec3.atBottomCenterOf(target));
        return entryDistance * 0.25D + exitDistance;
    }

    static double elevatorDetourScore(Vec3 playerPosition, BlockPos target,
                                      BlockPos source, BlockPos destination) {
        double entryDistance = playerPosition.distanceTo(Vec3.atCenterOf(source.above()));
        double continuationHint = Vec3.atCenterOf(destination.above())
                .distanceTo(Vec3.atBottomCenterOf(target));
        return entryDistance + continuationHint * 0.01D;
    }

    private PlanningJob beginPlanningJob() {
        if (target == null) return null;
        BlockPos start = player.blockPosition();
        BlockPos legTarget = state == State.TO_ELEVATOR && elevator != null ? elevator.above() : target;
        int sightY = combatSightTarget == null ? legTarget.getY() : Mth.floor(combatSightTarget.y);
        int minX = Math.min(start.getX(), legTarget.getX()) - SEARCH_MARGIN;
        int maxX = Math.max(start.getX(), legTarget.getX()) + SEARCH_MARGIN;
        int minY = Math.min(Math.min(start.getY(), legTarget.getY()), sightY)
                - SEARCH_VERTICAL_MARGIN;
        int maxY = Math.max(Math.max(start.getY(), legTarget.getY()), sightY)
                + SEARCH_VERTICAL_MARGIN;
        int minZ = Math.min(start.getZ(), legTarget.getZ()) - SEARCH_MARGIN;
        int maxZ = Math.max(start.getZ(), legTarget.getZ()) + SEARCH_MARGIN;
        BlockPos minimum = new BlockPos(minX, minY, minZ);
        BlockPos maximum = new BlockPos(maxX, maxY, maxZ);
        boolean sharedFacilityCache = FacilityNavigationCache.manages(player.serverLevel());
        NavigationSnapshot.Capture capture = NavigationSnapshot.beginCapture(
                player.serverLevel(), minimum, maximum,
                NavigationSnapshot.HARD_MAX_CELLS);
        PlanningRequest request = new PlanningRequest(new NavigationNode(start, player.getY()),
                legTarget, state == State.TO_ELEVATOR, elevator, interactionTarget,
                interactionRange, combatMinimumRange, combatPreferredRange,
                arrivalDistance, combatTarget, combatSightTarget,
                navigatorEyeHeight, player.getBbWidth(), player.getBbHeight());
        return new PlanningJob(capture, request, minimum, maximum, sharedFacilityCache);
    }

    private static PlanningOutcome computePath(NavigationSnapshot snapshot, PlanningRequest request,
                                               AtomicBoolean cancelled) {
        NodeEvaluator evaluator = new NodeEvaluator(snapshot, request.width, request.height);
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        AStarPathfinder pathfinder = new AStarPathfinder(transitions::generate,
                NavigationTransition::cost, EmployeeNavigator::heuristic);
        Predicate<NavigationNode> goal;
        if (request.toElevator) {
            goal = node -> isWithinElevatorRange(node, request.elevator);
        } else if (request.interactionTarget != null) {
            goal = request.combatTarget
                    ? node -> NavigationExecutionPolicy.validCombatEndpoint(
                    node.standingPosition(), request.interactionTarget,
                    request.combatMinimumRange, request.interactionRange)
                    && NavigationLineOfSight.isClear(snapshot,
                    node.standingPosition().add(0.0D, request.navigatorEyeHeight, 0.0D),
                    request.combatSightTarget)
                    : node -> isValidInteractionGoal(node, request.interactionTarget,
                    request.interactionRange);
        } else {
            goal = node -> isWithin(node, request.legTarget, request.arrivalDistance);
        }
        NavigationNode searchStart = resolveSearchStart(evaluator, transitions, request.startNode);
        NavigationNode heuristicGoal = combatHeuristicGoal(request);
        PathSearchResult result = pathfinder.search(searchStart, heuristicGoal, goal,
                new AStarPathfinder.SearchOptions(PATH_NODE_BUDGET, 1.0D), cancelled::get);
        boolean startTraversable = goal.test(searchStart)
                && evaluator.evaluate(searchStart, false).traversable();
        return new PlanningOutcome(result, snapshot.complete(), searchStart, startTraversable);
    }

    private static NavigationNode combatHeuristicGoal(PlanningRequest request) {
        if (request.toElevator || !request.combatTarget || request.interactionTarget == null) {
            return new NavigationNode(request.legTarget);
        }
        Vec3 away = request.startNode.standingPosition().subtract(request.interactionTarget);
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-6D) away = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 preferred = request.interactionTarget.add(
                away.normalize().scale(request.combatPreferredRange));
        return new NavigationNode(BlockPos.containing(
                preferred.x, request.interactionTarget.y, preferred.z));
    }

    /**
     * Physics can leave an employee on the edge of a stair or slab at a height that is
     * valid in the live world but has no grid neighbours. Re-enter the nearest connected
     * standing surface; acceptPath adds a live-validated alignment step when needed.
     */
    static NavigationNode resolveSearchStart(NodeEvaluator evaluator,
                                             TransitionGenerator transitions,
                                             NavigationNode requested) {
        if (evaluator.evaluate(requested, false).traversable()
                && !transitions.generate(requested).isEmpty()) {
            return requested;
        }
        NavigationNode best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = requested.feetPosition();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                BlockPos column = origin.offset(offsetX, 0, offsetZ);
                for (NodeEvaluator.CandidateEvaluation candidate : evaluator.candidates(
                        column, requested.standingY(), 1.0D, 1.0D, false)) {
                    NavigationNode node = candidate.node();
                    if (transitions.generate(node).isEmpty()) continue;
                    double distance = node.standingPosition()
                            .distanceToSqr(requested.standingPosition());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = node;
                    }
                }
            }
        }
        return best == null ? requested : best;
    }

    private void acceptPath(NavigationPath candidate, boolean endsAtActualGoal,
                            NavigationNode startNode) {
        if (NavigationExecutionPolicy.needsStartAlignment(player.position(), startNode)) {
            NavigationTransition alignment = new NavigationTransition(
                    startNode, NavigationAction.WALK, List.of(), 1.0D);
            List<NavigationPathStep> aligned = new ArrayList<>(candidate.steps().size() + 1);
            aligned.add(new NavigationPathStep(
                    startNode, startNode, alignment, 1.0D, 1.0D));
            aligned.addAll(candidate.steps());
            path = List.copyOf(aligned);
        } else {
            path = candidate.steps();
        }
        pathIndex = 0;
        pathEndsAtActualGoal = endsAtActualGoal;
        routedInteractionTarget = interactionTarget;
        resetWaypointProgress();
    }

    private static boolean isValidInteractionGoal(NavigationNode node, Vec3 interactionTarget,
                                                  double interactionRange) {
        double distance = node.standingPosition().distanceTo(interactionTarget);
        double reliableRange = Math.max(1.0D, interactionRange - INTERACTION_GOAL_MARGIN);
        return distance >= 1.0D && distance <= reliableRange;
    }

    private void advanceReachedWaypoints() {
        while (pathIndex < path.size()) {
            NavigationPathStep current = path.get(pathIndex);
            NavigationPathStep next = pathIndex + 1 < path.size() ? path.get(pathIndex + 1) : null;
            if (!NavigationExecutionPolicy.hasReachedWaypoint(player.position(), current, next,
                    pathEndsAtActualGoal, hasReachedCurrentLeg())) return;
            pathIndex++;
            stuckTicks = 0;
            stuckStrikes = 0;
            lastStuckCheck = player.position();
            resetWaypointProgress();
        }
    }

    private int skipVisibleWaypoints() {
        if (pathIndex >= path.size()) return -1;
        int furthest = Math.min(path.size() - 1, pathIndex + MAX_VISIBLE_WAYPOINT_SKIP);
        int lastWalkCandidate = pathIndex;
        for (int candidate = pathIndex + 1; candidate <= furthest; candidate++) {
            NavigationPathStep step = path.get(candidate);
            if (!NavigationExecutionPolicy.continuesStraight(
                    path.get(candidate - 1), step)) break;
            lastWalkCandidate = candidate;
        }
        // Test the longest shortcut first. Open corridors normally need one live
        // collision sweep instead of repeating almost the same sweep eight times.
        for (int candidate = lastWalkCandidate; candidate > pathIndex; candidate--) {
            NavigationPathStep step = path.get(candidate);
            if (isLiveShortcutSafe(player.position(), step.node().standingPosition())) {
                pathIndex = candidate;
                resetWaypointProgress();
                return candidate;
            }
        }
        return -1;
    }

    private boolean isLiveShortcutSafe(Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / 0.2D));
        AABB currentBody = player.getBoundingBox();
        Vec3 playerPosition = player.position();
        double halfWidth = player.getBbWidth() / 2.0D;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;
            AABB body = currentBody.move(
                    x - playerPosition.x, y - playerPosition.y, z - playerPosition.z);
            if (!player.serverLevel().noCollision(player, body)) {
                FacilityNavigationCache.refreshVolume(player.serverLevel(), body);
                return false;
            }
            AABB support = new AABB(x - halfWidth, y - 0.0625D, z - halfWidth,
                    x + halfWidth, y + 1.0E-6D, z + halfWidth);
            if (!player.serverLevel().getBlockCollisions(player, support).iterator().hasNext()) {
                FacilityNavigationCache.refreshVolume(player.serverLevel(), support);
                return false;
            }
        }
        return true;
    }

    private boolean prepareTransition(NavigationPathStep step) {
        pressurePlateTarget = null;
        if (step.transition().action() != NavigationAction.OPEN_DOOR) {
            return step.transition().action() == NavigationAction.WALK
                    || step.transition().action() == NavigationAction.STEP_UP
                    || step.transition().action() == NavigationAction.DROP_DOWN;
        }
        for (BlockPos position : step.transition().affectedBlocks()) {
            BlockState state = player.serverLevel().getBlockState(position);
            if (state.getBlock() instanceof DoorBlock door && state.hasProperty(DoorBlock.OPEN)
                    && !state.getValue(DoorBlock.OPEN)) {
                if (state.getBlock() == Blocks.IRON_DOOR) {
                    BlockPos lower = lowerDoorPosition(position, state);
                    BlockState lowerState = player.serverLevel().getBlockState(lower);
                    BlockPos plate = findAdjacentPressurePlate(lower);
                    if (plate == null) return false;
                    // The route has proven that the adjacent plate is the only
                    // control for this door. Trigger its equivalent state change
                    // directly; this avoids a one-tick redstone timing race for
                    // server-side employees while still rejecting doors without a plate.
                    door.setOpen(player, player.serverLevel(), lowerState, lower, true);
                } else {
                    door.setOpen(player, player.serverLevel(), state, position, true);
                }
            }
        }
        return true;
    }

    private BlockPos findAdjacentPressurePlate(BlockPos door) {
        BlockState state = player.serverLevel().getBlockState(door);
        BlockPos lower = lowerDoorPosition(door, state);
        for (BlockPos candidate : new BlockPos[]{
                lower.north(), lower.south(), lower.west(), lower.east()}) {
            BlockState candidateState = player.serverLevel().getBlockState(candidate);
            if (candidateState.getBlock() instanceof PressurePlateBlock) return candidate;
        }
        return null;
    }

    private static BlockPos lowerDoorPosition(BlockPos position, BlockState state) {
        return state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
    }

    private boolean isWaypointStillValid(NavigationNode waypoint) {
        double halfWidth = player.getBbWidth() / 2.0D;
        double centerX = waypoint.feetPosition().getX() + 0.5D;
        double centerZ = waypoint.feetPosition().getZ() + 0.5D;
        AABB body = new AABB(centerX - halfWidth, waypoint.standingY(), centerZ - halfWidth,
                centerX + halfWidth, waypoint.standingY() + player.getBbHeight(),
                centerZ + halfWidth);
        if (!player.serverLevel().noCollision(player, body)
                && !(isClosedIronDoor(waypoint.feetPosition())
                && findAdjacentPressurePlate(waypoint.feetPosition()) != null)) {
            FacilityNavigationCache.refreshVolume(player.serverLevel(), body);
            return false;
        }
        AABB support = new AABB(centerX - halfWidth, waypoint.standingY() - 0.0625D,
                centerZ - halfWidth, centerX + halfWidth, waypoint.standingY() + 1.0E-6D,
                centerZ + halfWidth);
        boolean supported = player.serverLevel().getBlockCollisions(player, support)
                .iterator().hasNext();
        if (!supported) FacilityNavigationCache.refreshVolume(player.serverLevel(), support);
        return supported;
    }

    /** Revalidate the path from the employee's real off-center position, not the A* cell center. */
    private boolean isApproachStillValid(NavigationPathStep step) {
        if (step.transition().action() == NavigationAction.OPEN_DOOR
                && step.transition().affectedBlocks().stream().anyMatch(this::isClosedIronDoor)
                && step.transition().affectedBlocks().stream()
                .anyMatch(position -> findAdjacentPressurePlate(position) != null)) return true;
        Vec3 start = player.position();
        Vec3 end = step.node().standingPosition();
        if (NavigationExecutionPolicy.isCardinalFlatWalk(step)) return true;
        // Stair collision can place the live player on the lower half of the same
        // block while the center-sampled A* node is on its upper half. Treat that
        // sub-step mismatch like vanilla auto-step movement instead of rejecting
        // an otherwise valid descent path and replanning from an invalid center node.
        double liveRise = end.y - start.y;
        if (liveRise > 1.0E-4D && liveRise <= player.maxUpStep() + 0.1D) {
            Vec3 raised = new Vec3(start.x, end.y, start.z);
            return isLiveSweepClear(start, raised) && isLiveSweepClear(raised, end);
        }
        // Start-alignment steps are represented as WALK even when live stair
        // collision has left the entity half a block above the sampled node.
        // Clear the stair horizontally before descending into the flat surface.
        if (liveRise < -1.0E-4D && liveRise >= -1.0D) {
            Vec3 edge = new Vec3(end.x, start.y, end.z);
            return isLiveSweepClear(start, edge) && isLiveSweepClear(edge, end);
        }
        if (step.transition().action() == NavigationAction.STEP_UP) {
            Vec3 raised = new Vec3(start.x, end.y, start.z);
            return isLiveSweepClear(start, raised) && isLiveSweepClear(raised, end);
        }
        if (step.transition().action() == NavigationAction.DROP_DOWN) {
            Vec3 edge = new Vec3(end.x, start.y, end.z);
            return isLiveSweepClear(start, edge) && isLiveSweepClear(edge, end);
        }
        return isLiveSweepClear(start, end);
    }

    private boolean isLiveSweepClear(Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / 0.2D));
        AABB currentBody = player.getBoundingBox();
        Vec3 playerPosition = player.position();
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;
            AABB body = currentBody.move(
                    x - playerPosition.x, y - playerPosition.y, z - playerPosition.z);
            if (!player.serverLevel().noCollision(player, body)) {
                FacilityNavigationCache.refreshVolume(player.serverLevel(), body);
                return false;
            }
        }
        return true;
    }

    private boolean trackWaypointProgress(NavigationNode waypoint) {
        Vec3 position = waypoint.standingPosition();
        if (trackedWaypoint == null || trackedWaypoint.distanceToSqr(position) > 0.01D) {
            trackedWaypoint = position;
            bestWaypointDistance = player.position().distanceToSqr(position);
            waypointNoProgressTicks = 0;
            return true;
        }
        double distance = player.position().distanceToSqr(position);
        if (distance + 0.01D < bestWaypointDistance) {
            bestWaypointDistance = distance;
            waypointNoProgressTicks = 0;
        } else {
            waypointNoProgressTicks++;
        }
        return waypointNoProgressTicks < WAYPOINT_NO_PROGRESS_LIMIT;
    }

    private void applyInput(NavigationPathStep step) {
        NavigationNode waypoint = step.node();
        Vec3 targetPosition = pressurePlateTarget != null
                ? pressurePlateTarget : waypoint.standingPosition();
        double dx = targetPosition.x - player.getX();
        double dz = targetPosition.z - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        boolean jump = NavigationExecutionPolicy.needsJump(
                player.getY(), targetPosition.y, player.maxUpStep(), player.onGround());
        player.setNavigationInput(yaw,
                NavigationExecutionPolicy.forwardInput(player.position(), waypoint), jump);
    }

    private boolean isClosedIronDoor(BlockPos position) {
        BlockState state = player.serverLevel().getBlockState(position);
        return state.is(Blocks.IRON_DOOR) && state.hasProperty(DoorBlock.OPEN)
                && !state.getValue(DoorBlock.OPEN);
    }

    private boolean tryUseElevator() {
        if (elevator == null
                || !(player.serverLevel().getBlockEntity(elevator) instanceof ElevatorBlockEntity source)
                || !source.isWithinInteractionRange(player)) {
            return false;
        }
        player.clearNavigationInput();
        ElevatorBlockEntity.TravelResult result = source.tryTravel(player, elevatorUp);
        if (result == ElevatorBlockEntity.TravelResult.COOLDOWN) return true;
        if (result != ElevatorBlockEntity.TravelResult.SUCCESS) {
            lastFailureReason = "elevator-" + result + "@" + player.position()
                    + " source=" + elevator + "/up=" + elevatorUp;
            if (result == ElevatorBlockEntity.TravelResult.NO_DESTINATION
                    || result == ElevatorBlockEntity.TravelResult.BLOCKED_DESTINATION) {
                rejectUnreachableElevatorSource();
                return true;
            }
            invalidatePath();
            evaluateRoute();
            return false;
        }
        BlockPos destination = source.findDestinationElevator(player.serverLevel(), elevatorUp);
        if (destination != null) {
            usedElevatorTraversals.add(new ElevatorTraversal(
                    elevator.immutable(), destination.immutable()));
        }
        resetStuckTracking();
        state = State.WAITING_ELEVATOR;
        waitTicks = 0;
        invalidatePath();
        return true;
    }

    private void tickWaitingForElevator() {
        waitTicks++;
        player.clearNavigationInput();
        if (ElevatorEntryFloorPolicy.isOnSameFloor(
                player.serverLevel(), player.blockPosition(), target)) {
            state = State.DIRECT;
            elevator = null;
            elevatorUp = false;
            invalidatePath();
            return;
        }
        if (elevator == null
                || !(player.serverLevel().getBlockEntity(elevator) instanceof ElevatorBlockEntity source)
                || !source.isWithinInteractionRange(player)) {
            invalidatePath();
            evaluateRoute();
            return;
        }
        if (waitTicks % 20 == 0) source.tryTravel(player, elevatorUp);
        if (waitTicks > 160) {
            invalidatePath();
            evaluateRoute();
        }
    }

    private void rejectCurrentPath() {
        player.clearNavigationInput();
        invalidatePath();
        replanDelay = 2;
    }

    private void rejectCurrentPathForNoProgress() {
        lastFailureReason = "waypoint-no-progress@" + player.position()
                + " toward " + trackedWaypoint;
        rejectCurrentPath();
        if (++stuckStrikes >= UNREACHABLE_STRIKES) unreachable = true;
    }

    private void rejectBlockedPath(NavigationPathStep step) {
        lastFailureReason = "live-path-blocked@" + player.position()
                + " step=" + step.from() + "->" + step.node()
                + "/" + step.transition().action()
                + "/" + step.transition().affectedBlocks();
        player.clearNavigationInput();
        invalidatePath();
        replanDelay = 10;
        if (++stuckStrikes >= UNREACHABLE_STRIKES) unreachable = true;
    }

    private void checkStuck() {
        if (++stuckTicks < 40) return;
        if (lastStuckCheck != null && player.position().distanceToSqr(lastStuckCheck) < 0.16D) {
            lastFailureReason = "movement-stuck@" + player.position();
            invalidatePath();
            replanDelay = 2;
            if (++stuckStrikes >= UNREACHABLE_STRIKES) unreachable = true;
        }
        lastStuckCheck = player.position();
        stuckTicks = 0;
    }

    private void invalidatePath() {
        cancelPlanningJob();
        path = List.of();
        pathIndex = 0;
        pathEndsAtActualGoal = false;
        routedInteractionTarget = null;
        resetWaypointProgress();
    }

    private NavigationNode currentPathEndpoint() {
        return path.isEmpty() ? null : path.get(path.size() - 1).node();
    }

    private void clearCombatTarget() {
        combatTarget = false;
        combatMinimumRange = 1.0D;
        combatPreferredRange = 1.0D;
        combatSightTarget = null;
        navigatorEyeHeight = 0.0D;
    }

    private void resetWaypointProgress() {
        trackedWaypoint = null;
        bestWaypointDistance = Double.MAX_VALUE;
        waypointNoProgressTicks = 0;
    }

    private void resetStuckTracking() {
        stuckTicks = 0;
        stuckStrikes = 0;
        noPathStrikes = 0;
        lastStuckCheck = null;
        resetWaypointProgress();
        unreachable = false;
    }

    private double distanceTo(BlockPos position) {
        return player.position().distanceTo(Vec3.atBottomCenterOf(position));
    }

    private boolean hasReachedCurrentLeg() {
        if (state == State.TO_ELEVATOR && elevator != null
                && player.serverLevel().getBlockEntity(elevator) instanceof ElevatorBlockEntity source) {
            return source.isWithinInteractionRange(player);
        }
        return state == State.DIRECT && hasArrived();
    }

    private static boolean isWithin(NavigationNode node, BlockPos goal, double radius) {
        return node.standingPosition().distanceToSqr(Vec3.atBottomCenterOf(goal))
                <= radius * radius;
    }

    private static boolean isWithinElevatorRange(NavigationNode node, BlockPos elevator) {
        if (elevator == null) return false;
        return node.standingPosition().distanceToSqr(Vec3.atCenterOf(elevator.above()))
                <= ElevatorBlockEntity.INTERACTION_RADIUS * ElevatorBlockEntity.INTERACTION_RADIUS;
    }

    private double transitionCost(NavigationTransition transition) {
        return transition.cost();
    }

    static boolean isSearchBoundary(BlockPos node, BlockPos center, int radius) {
        return isSearchBoundary(node, center, radius, Integer.MAX_VALUE);
    }

    static boolean isSearchBoundary(BlockPos node, BlockPos center,
                                    int horizontalRadius, int verticalRadius) {
        return isSearchBoundary(node, center, horizontalRadius, verticalRadius, true);
    }

    static boolean isSearchBoundary(BlockPos node, BlockPos center,
                                    int horizontalRadius, int verticalRadius,
                                    boolean allowVerticalBoundary) {
        int edge = Math.max(1, horizontalRadius - 1);
        int verticalEdge = Math.max(1, verticalRadius - 2);
        return Math.abs(node.getX() - center.getX()) >= edge
                || Math.abs(node.getZ() - center.getZ()) >= edge
                || allowVerticalBoundary && Math.abs(node.getY() - center.getY()) >= verticalEdge;
    }

    private static double heuristic(NavigationNode from, NavigationNode to) {
        BlockPos a = from.feetPosition();
        BlockPos b = to.feetPosition();
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        int diagonal = Math.min(dx, dz);
        int straight = Math.max(dx, dz) - diagonal;
        return diagonal * Math.sqrt(2.0D) + straight
                + Math.abs(from.standingY() - to.standingY()) * 0.35D;
    }

    private static int reserveSnapshotCells(MinecraftServer server, long gameTick, int requested) {
        PlanningBudget budget = planningBudget(server, gameTick);
        int granted = Math.min(Math.max(0, requested), budget.snapshotCells);
        budget.snapshotCells -= granted;
        return granted;
    }

    private static boolean reservePathResult(MinecraftServer server, long gameTick) {
        PlanningBudget budget = planningBudget(server, gameTick);
        if (budget.pathResults <= 0) return false;
        budget.pathResults--;
        return true;
    }

    private static PlanningBudget planningBudget(MinecraftServer server, long gameTick) {
        PlanningBudget budget = PLANNING_BUDGETS.computeIfAbsent(server,
                ignored -> new PlanningBudget());
        if (budget.tick != gameTick) {
            budget.tick = gameTick;
            budget.snapshotCells = SNAPSHOT_CELLS_PER_SERVER_TICK;
            budget.pathResults = PATH_RESULTS_PER_SERVER_TICK;
        }
        return budget;
    }

    static void clearPlanningBudget(MinecraftServer server) {
        PLANNING_BUDGETS.remove(server);
    }

    private static final class PlanningBudget {
        private long tick = Long.MIN_VALUE;
        private int snapshotCells;
        private int pathResults;
    }

    private void cancelPlanningJob() {
        if (planningJob == null) return;
        planningJob.cancelled.set(true);
        if (planningJob.future != null) planningJob.future.cancel(false);
        planningJob = null;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "lobotocraft-path-planner-" + PLANNER_THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class PlanningJob {
        private final NavigationSnapshot.Capture capture;
        private final PlanningRequest request;
        private final BlockPos minimum;
        private final BlockPos maximum;
        private final boolean sharedFacilityCache;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private CompletableFuture<PlanningOutcome> future;

        private PlanningJob(NavigationSnapshot.Capture capture, PlanningRequest request,
                            BlockPos minimum, BlockPos maximum,
                            boolean sharedFacilityCache) {
            this.capture = capture;
            this.request = request;
            this.minimum = minimum;
            this.maximum = maximum;
            this.sharedFacilityCache = sharedFacilityCache;
        }
    }

    private record PlanningRequest(NavigationNode startNode, BlockPos legTarget,
                                   boolean toElevator, BlockPos elevator,
                                   Vec3 interactionTarget, double interactionRange,
                                   double combatMinimumRange, double combatPreferredRange,
                                   double arrivalDistance, boolean combatTarget,
                                   Vec3 combatSightTarget, double navigatorEyeHeight,
                                   double width, double height) {
    }

    private record PlanningOutcome(PathSearchResult result, boolean snapshotComplete,
                                   NavigationNode startNode, boolean startTraversable) {
    }

    private record ElevatorChoice(BlockPos source, boolean upward) {
    }

    private record ElevatorTraversal(BlockPos source, BlockPos destination) {
    }

    private static double normalizeArrivalDistance(double distance) {
        return Math.max(0.6D, Math.min(4.0D, distance));
    }

    private static double normalizeInteractionRange(double range) {
        return Math.max(1.0D, range);
    }

    private enum PlanResult {
        PLANNED,
        ALREADY_ARRIVED,
        NO_PATH,
        BUDGET_DEFERRED,
        PLANNING_DEFERRED,
        WORLD_DEFERRED,
        RETRY_EXPANDED
    }
}
