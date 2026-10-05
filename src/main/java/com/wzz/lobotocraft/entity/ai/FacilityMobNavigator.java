package com.wzz.lobotocraft.entity.ai;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.block.entity.ElevatorEntryFloorPolicy;
import com.wzz.lobotocraft.employee.ElevatorRegistry;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.employee.navigation.AStarPathfinder;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationAction;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationPath;
import com.wzz.lobotocraft.employee.navigation.NavigationPathStep;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.employee.navigation.NavigationTransition;
import com.wzz.lobotocraft.employee.navigation.NodeEvaluator;
import com.wzz.lobotocraft.employee.navigation.PathSearchResult;
import com.wzz.lobotocraft.employee.navigation.TransitionGenerator;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Facility A* route executor for escaped ground mobs, including explicit elevator legs. */
public final class FacilityMobNavigator {
    private enum State { DIRECT, TO_ELEVATOR, WAITING_ELEVATOR }

    private static final int PATH_NODE_BUDGET = 65_536;
    private static final int UNREACHABLE_STRIKES = 4;
    private static final double ARRIVAL_DISTANCE = 1.6D;
    private static final double WAYPOINT_DISTANCE = 1.15D;
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService PATH_EXECUTOR = Executors.newFixedThreadPool(2,
            daemonThreadFactory());

    private final PathfinderMob mob;
    private final double speed;
    private final Set<BlockPos> rejectedElevators = new HashSet<>();
    private BlockPos target;
    private BlockPos elevator;
    private boolean elevatorUp;
    private boolean elevatorExitVerified;
    private State state = State.DIRECT;
    private List<NavigationPathStep> path = List.of();
    private int pathIndex;
    private int replanDelay;
    private int noPathStrikes;
    private int stuckChecks;
    private int waypointNoProgressTicks;
    private Vec3 trackedWaypoint;
    private double bestWaypointDistance = Double.MAX_VALUE;
    private boolean complete;
    private boolean unreachable;
    private PlanningJob planningJob;

    public FacilityMobNavigator(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
    }

    public void moveTo(BlockPos target) {
        stop();
        this.target = target.immutable();
        evaluateRoute(false);
    }

    public void tick() {
        if (target == null || !mob.isAlive()) return;
        if (state == State.DIRECT && distanceTo(target) <= ARRIVAL_DISTANCE) {
            complete = true;
            haltMovement();
            cancelPlanning();
            return;
        }
        if (state == State.WAITING_ELEVATOR) {
            tickWaitingForElevator();
            return;
        }
        if (state == State.TO_ELEVATOR && tryUseElevator()) return;

        if (pathIndex >= path.size()) {
            haltMovement();
            if (replanDelay > 0) {
                replanDelay--;
                return;
            }
            PlanResult result = planCurrentLeg();
            if (result == PlanResult.PENDING || result == PlanResult.RETRY) return;
            if (result == PlanResult.NO_PATH) {
                if (state == State.TO_ELEVATOR) {
                    rejectedElevators.add(elevator);
                    evaluateRoute(true);
                    return;
                }
                if (activateElevatorFallback()) return;
                replanDelay = 40;
                if (++noPathStrikes >= UNREACHABLE_STRIKES) unreachable = true;
                return;
            }
            noPathStrikes = 0;
        }

        advanceReachedWaypoints();
        if (pathIndex >= path.size()) return;
        NavigationPathStep step = path.get(pathIndex);
        if (!prepareTransition(step)) {
            invalidatePath();
            replanDelay = 10;
            recordBlockedLeg();
            return;
        }
        Vec3 waypoint = step.node().standingPosition();
        if (!trackProgress(waypoint)) {
            invalidatePath();
            replanDelay = 5;
            recordBlockedLeg();
            return;
        }
        mob.getLookControl().setLookAt(waypoint.x, waypoint.y + mob.getEyeHeight(), waypoint.z);
        // The facility planner has already validated this full-body transition.
        // Asking vanilla navigation to plan it again can return a stationary
        // partial path on custom facility floors, silently discarding our route.
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, speed);
        if (waypoint.y - mob.getY() > mob.getStepHeight()
                && mob.onGround()) mob.getJumpControl().jump();
    }

    public void stop() {
        target = null;
        elevator = null;
        elevatorUp = false;
        elevatorExitVerified = false;
        state = State.DIRECT;
        complete = false;
        unreachable = false;
        noPathStrikes = 0;
        stuckChecks = 0;
        replanDelay = 0;
        rejectedElevators.clear();
        invalidatePath();
        haltMovement();
    }

    private void haltMovement() {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isUnreachable() {
        return unreachable;
    }

    private PlanResult planCurrentLeg() {
        if (!(mob.level() instanceof ServerLevel level)) return PlanResult.NO_PATH;
        BlockPos legTarget = state == State.TO_ELEVATOR && elevator != null
                ? elevator.above() : target;
        if (planningJob == null) {
            NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(level,
                    FacilityNavigationCache.COMPANY_MIN, FacilityNavigationCache.COMPANY_MAX);
            if (snapshot == null) return PlanResult.PENDING;
            AtomicBoolean cancelled = new AtomicBoolean();
            NavigationNode landing = null;
            if (state == State.TO_ELEVATOR && elevator != null) {
                if (!(level.getBlockEntity(elevator) instanceof ElevatorBlockEntity source))
                    return PlanResult.NO_PATH;
                BlockPos destination = source.findDestinationElevator(level, elevatorUp);
                Vec3 safeLanding = destination == null ? null
                        : ElevatorBlockEntity.findSafeLanding(level, mob, destination);
                if (safeLanding == null) return PlanResult.NO_PATH;
                if (Math.abs(safeLanding.y - target.getY()) <= 5.0D)
                    landing = new NavigationNode(BlockPos.containing(safeLanding), safeLanding.y);
            }
            PlanningRequest request = new PlanningRequest(
                    new NavigationNode(mob.blockPosition(), mob.getY()), legTarget,
                    mob.getBbWidth(), mob.getBbHeight(), openNavigationAreas(level), landing, target);
            CompletableFuture<PathSearchResult> future = CompletableFuture.supplyAsync(
                    () -> computePath(snapshot, request, cancelled), PATH_EXECUTOR);
            planningJob = new PlanningJob(cancelled, future);
            return PlanResult.PENDING;
        }
        if (!planningJob.future.isDone()) return PlanResult.PENDING;
        PathSearchResult result;
        try {
            result = planningJob.future.join();
        } catch (RuntimeException exception) {
            planningJob = null;
            return PlanResult.NO_PATH;
        }
        planningJob = null;
        if (result.status() == PathSearchResult.Status.UNREACHABLE
                || result.status() == PathSearchResult.Status.PARTIAL_UNREACHABLE)
            return PlanResult.NO_PATH;
        if (state == State.TO_ELEVATOR) elevatorExitVerified = true;
        NavigationPath candidate = result.path();
        if (candidate == null || candidate.steps().isEmpty()) {
            return isAtCurrentLeg() ? PlanResult.RETRY : PlanResult.NO_PATH;
        }
        if (!result.reachedGoal()
                && result.status() != PathSearchResult.Status.PARTIAL_BUDGET_EXHAUSTED) {
            return PlanResult.NO_PATH;
        }
        path = candidate.steps();
        pathIndex = 0;
        resetWaypointProgress();
        return PlanResult.PLANNED;
    }

    private void recordBlockedLeg() {
        if (++stuckChecks < UNREACHABLE_STRIKES) return;
        if (state == State.TO_ELEVATOR && elevator != null) {
            rejectedElevators.add(elevator);
            stuckChecks = 0;
            evaluateRoute(true);
        } else {
            unreachable = true;
        }
    }

    private static PathSearchResult computePath(NavigationSnapshot snapshot,
                                                PlanningRequest request,
                                                AtomicBoolean cancelled) {
        // A geometrically nearby lift can land in a disconnected room on the same
        // target floor. Check that continuation in the existing worker, not on the
        // server thread and not only after the passenger has become trapped there.
        if (request.landing != null) {
            PathSearchResult exit = computePath(snapshot,
                    new PlanningRequest(request.landing, request.finalTarget,
                            request.width, request.height, request.openAreas, null, null), cancelled);
            // A partial path is not evidence that boarding this lift is safe.
            if (!exit.reachedGoal())
                return new PathSearchResult(PathSearchResult.Status.UNREACHABLE, null,
                        exit.visitedNodes(), exit.generatedTransitions());
        }
        NodeEvaluator evaluator = new NodeEvaluator(snapshot, request.width, request.height);
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        NavigationNode start = resolveStart(evaluator, transitions, request.start);
        NavigationNode goal = new NavigationNode(request.target);
        Predicate<NavigationNode> reached = node -> node.standingPosition()
                .distanceTo(Vec3.atBottomCenterOf(request.target)) <= ARRIVAL_DISTANCE;
        AStarPathfinder pathfinder = new AStarPathfinder(node -> transitions.generate(node).stream()
                .filter(transition -> isInsideOpenArea(
                        transition.target().feetPosition(), request.openAreas))
                .toList(),
                NavigationTransition::cost, FacilityMobNavigator::heuristic);
        return pathfinder.search(start, goal, reached,
                new AStarPathfinder.SearchOptions(PATH_NODE_BUDGET, 1.0D), cancelled::get);
    }

    private static List<FacilityWorldLayout.AreaDefinition> openNavigationAreas(
            ServerLevel level) {
        return FacilityWorldLayout.fixedAreas(level.dimension().location()).stream()
                .filter(area -> FacilityAccessService.isGroupOpen(level.getServer(),
                        level.dimension().location(), area.groupId()))
                .toList();
    }

    private static boolean isInsideOpenArea(
            BlockPos position, List<FacilityWorldLayout.AreaDefinition> openAreas) {
        return openAreas.stream().anyMatch(area ->
                position.getX() >= area.minPos().getX() - 2
                        && position.getX() <= area.maxPos().getX() + 2
                        && position.getY() >= area.minPos().getY() - 2
                        && position.getY() <= area.maxPos().getY() + 2
                        && position.getZ() >= area.minPos().getZ() - 2
                        && position.getZ() <= area.maxPos().getZ() + 2);
    }

    private static NavigationNode resolveStart(NodeEvaluator evaluator,
                                               TransitionGenerator transitions,
                                               NavigationNode requested) {
        if (evaluator.evaluate(requested, false).traversable()
                && !transitions.generate(requested).isEmpty()) return requested;
        NavigationNode best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = requested.feetPosition();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos column = origin.offset(x, 0, z);
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

    private void evaluateRoute(boolean allowVerticalDetour) {
        state = State.DIRECT;
        elevator = null;
        elevatorUp = false;
        elevatorExitVerified = false;
        invalidatePath();
        if (target == null) return;
        double verticalGap = Math.abs(target.getY() - mob.getY());
        if (verticalGap > 5.0D || allowVerticalDetour) activateElevatorFallback();
    }

    private boolean activateElevatorFallback() {
        ElevatorChoice choice = findBestElevator();
        if (choice == null) return false;
        elevator = choice.source;
        elevatorUp = choice.upward;
        elevatorExitVerified = false;
        state = State.TO_ELEVATOR;
        invalidatePath();
        return true;
    }

    private ElevatorChoice findBestElevator() {
        if (!(mob.level() instanceof ServerLevel level) || target == null) return null;
        boolean needsUp = target.getY() > mob.getY();
        double currentGap = Math.abs(target.getY() - mob.getY());
        ElevatorChoice best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos source : ElevatorRegistry.knownPositions(level)) {
            if (rejectedElevators.contains(source)
                    || !(level.getBlockEntity(source) instanceof ElevatorBlockEntity elevatorEntity)
                    || !ElevatorEntryFloorPolicy.isOnEntityFloor(level, mob.blockPosition(), source)) {
                continue;
            }
            for (boolean upward : currentGap > 5.0D
                    ? new boolean[]{needsUp} : new boolean[]{false, true}) {
                BlockPos destination = elevatorEntity.findDestinationElevator(level, upward);
                if (destination == null) continue;
                double remaining = Math.abs(target.getY() - (destination.getY() + 1.0D));
                if (currentGap > 5.0D && remaining >= currentGap) continue;
                double score = mob.position().distanceTo(Vec3.atCenterOf(source.above()))
                        + Vec3.atCenterOf(destination.above())
                        .distanceTo(Vec3.atBottomCenterOf(target));
                if (score < bestScore) {
                    bestScore = score;
                    best = new ElevatorChoice(source.immutable(), upward);
                }
            }
        }
        return best;
    }

    private boolean tryUseElevator() {
        if (!elevatorExitVerified || !(mob.level() instanceof ServerLevel level) || elevator == null
                || !(level.getBlockEntity(elevator) instanceof ElevatorBlockEntity source)
                || !source.isWithinInteractionRange(mob)) return false;
        ElevatorBlockEntity.TravelResult result = source.tryTravel(mob, elevatorUp);
        if (result == ElevatorBlockEntity.TravelResult.COOLDOWN) {
            haltMovement();
            return true;
        }
        if (result != ElevatorBlockEntity.TravelResult.SUCCESS) {
            rejectedElevators.add(elevator);
            evaluateRoute(true);
            return true;
        }
        state = State.WAITING_ELEVATOR;
        invalidatePath();
        haltMovement();
        return true;
    }

    private void tickWaitingForElevator() {
        if (target == null) return;
        if (Math.abs(target.getY() - mob.getY()) <= 5.0D) {
            state = State.DIRECT;
            elevator = null;
            elevatorUp = false;
            invalidatePath();
            return;
        }
        evaluateRoute(false);
    }

    private void advanceReachedWaypoints() {
        while (pathIndex < path.size()) {
            Vec3 waypoint = path.get(pathIndex).node().standingPosition();
            if (mob.position().distanceToSqr(waypoint)
                    > WAYPOINT_DISTANCE * WAYPOINT_DISTANCE) return;
            pathIndex++;
            stuckChecks = 0;
            resetWaypointProgress();
        }
        if (pathIndex >= path.size()) invalidatePath();
    }

    private boolean prepareTransition(NavigationPathStep step) {
        NavigationAction action = step.transition().action();
        if (action != NavigationAction.OPEN_DOOR) {
            return action == NavigationAction.WALK
                    || action == NavigationAction.STEP_UP
                    || action == NavigationAction.DROP_DOWN;
        }
        if (!(mob.level() instanceof ServerLevel level)) return false;
        for (BlockPos position : step.transition().affectedBlocks()) {
            BlockState state = level.getBlockState(position);
            if (!(state.getBlock() instanceof DoorBlock door)
                    || !state.hasProperty(DoorBlock.OPEN) || state.getValue(DoorBlock.OPEN)) {
                continue;
            }
            BlockPos lower = lowerDoorPosition(position, state);
            if (state.is(Blocks.IRON_DOOR) && findAdjacentPressurePlate(level, lower) == null) {
                return false;
            }
            BlockState lowerState = level.getBlockState(lower);
            door.setOpen(mob, level, lowerState, lower, true);
        }
        return true;
    }

    private static BlockPos findAdjacentPressurePlate(ServerLevel level, BlockPos door) {
        for (BlockPos candidate : new BlockPos[]{
                door.north(), door.south(), door.west(), door.east()}) {
            if (level.getBlockState(candidate).getBlock() instanceof PressurePlateBlock) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos lowerDoorPosition(BlockPos position, BlockState state) {
        return state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
    }

    private boolean trackProgress(Vec3 waypoint) {
        if (trackedWaypoint == null || trackedWaypoint.distanceToSqr(waypoint) > 0.01D) {
            trackedWaypoint = waypoint;
            bestWaypointDistance = mob.position().distanceToSqr(waypoint);
            waypointNoProgressTicks = 0;
            return true;
        }
        double distance = mob.position().distanceToSqr(waypoint);
        if (distance + 0.01D < bestWaypointDistance) {
            bestWaypointDistance = distance;
            waypointNoProgressTicks = 0;
        } else {
            waypointNoProgressTicks++;
        }
        return waypointNoProgressTicks < 40;
    }

    private boolean isAtCurrentLeg() {
        BlockPos legTarget = state == State.TO_ELEVATOR && elevator != null
                ? elevator.above() : target;
        return legTarget != null && distanceTo(legTarget) <= ARRIVAL_DISTANCE;
    }

    private double distanceTo(BlockPos position) {
        return mob.position().distanceTo(Vec3.atBottomCenterOf(position));
    }

    private void invalidatePath() {
        cancelPlanning();
        path = List.of();
        pathIndex = 0;
        resetWaypointProgress();
    }

    private void cancelPlanning() {
        if (planningJob != null) {
            planningJob.cancelled.set(true);
            planningJob.future.cancel(false);
            planningJob = null;
        }
    }

    private void resetWaypointProgress() {
        trackedWaypoint = null;
        bestWaypointDistance = Double.MAX_VALUE;
        waypointNoProgressTicks = 0;
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

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "lobotocraft-abnormality-path-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private enum PlanResult { PENDING, PLANNED, RETRY, NO_PATH }

    private record ElevatorChoice(BlockPos source, boolean upward) {
    }

    private record PlanningRequest(NavigationNode start, BlockPos target,
                                   double width, double height,
                                   List<FacilityWorldLayout.AreaDefinition> openAreas,
                                   NavigationNode landing, BlockPos finalTarget) {
    }

    private record PlanningJob(AtomicBoolean cancelled,
                               CompletableFuture<PathSearchResult> future) {
    }
}
