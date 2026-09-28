package com.wzz.lobotocraft.employee;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.employee.navigation.AStarPathfinder;
import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationCandidatePolicy;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.employee.navigation.NavigationTransition;
import com.wzz.lobotocraft.employee.navigation.NodeEvaluator;
import com.wzz.lobotocraft.employee.navigation.PathSearchResult;
import com.wzz.lobotocraft.employee.navigation.TransitionGenerator;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.StructureUpdater;
import com.wzz.lobotocraft.world.structure.Structures;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import org.slf4j.Logger;

/**
 * End-to-end navigation checks against the facility structure shipped by the mod.
 *
 * <p>The ordinary navigation GameTests build small synthetic corridors. These checks instead
 * load the same multipart NBT used by a real company world, select player-sized standing points
 * from its collision data, and run {@link EmployeeNavigator} in the facility dimension. This
 * keeps regressions in the room geometry, stairs, elevator registration and live collision layer
 * visible to the test harness.</p>
 */
@GameTestHolder(ModMain.MODID + "_navigation")
public final class GeneratedFacilityNavigationGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROUTE_TIMEOUT_TICKS = 4_000;
    private static final int CONNECTED_PAIR_NODE_BUDGET = 65_536;

    private static final String CONTROL_MAIN_GROUP =
            "dept/control/passage/sefira-malkuth-1";
    private static final String CONTROL_TOP_LEFT_GROUP =
            "dept/control/passage/malkuth-1-1";
    private static final String INFORMATION_UPPER_LEFT_GROUP =
            "dept/information/passage/yesod-left-hall-leftdoor";
    private static final String INFORMATION_LOWER_LEFT_GROUP =
            "dept/information/passage/y-left-elevator-hub-leftdoor";
    private static final String INFORMATION_UPPER_RIGHT_GROUP =
            "dept/information/passage/yesod-right-hall-rightdoor";
    private static final String INFORMATION_LOWER_RIGHT_GROUP =
            "dept/information/passage/y-right-elevator-hub-rightdoor";
    private static final String SAFETY_MAIN_GROUP =
            "dept/safety/passage/sefira-netzach-1";
    private static final String SAFETY_UPPER_ELEVATOR_GROUP =
            "dept/safety/passage/n-leftup-elevator-hub";
    private static final String SAFETY_UPPER_CORRIDOR_GROUP =
            "dept/safety/passage/n-upper-way-rightdoor";
    private static final String SAFETY_UPPER_LEFT_CONTAINMENT_GROUP =
            "dept/safety/containment/n-upper-way4";
    private static final String SAFETY_UPPER_RIGHT_CONTAINMENT_GROUP =
            "dept/safety/containment/n-upper-way2";
    private static final String TRAINING_MAIN_GROUP =
            "dept/training/passage/sefira-hod-1";
    private static final String CENTRAL_UPPER_MAIN_GROUP =
            "dept/central/upper/passage/sefira-tiphereth1-1";
    private static final String CENTRAL_UPPER_SECONDARY_GROUP =
            "dept/central/upper/passage/dept-tiphereth1-1";
    private static final String CENTRAL_LOWER_SECONDARY_GROUP =
            "dept/central/lower/passage/dept-tiphereth2-1";
    private static final String WELFARE_MAIN_GROUP =
            "dept/welfare/passage/sefira-chesed-1";
    private static final String WELFARE_UPPER_MAIN_GROUP =
            "dept/welfare/passage/dept-chesed-1";
    private static final String DISCIPLINARY_MAIN_GROUP =
            "dept/disciplinary/passage/sefira-geburah-1";
    private static final String DISCIPLINARY_UPPER_MAIN_GROUP =
            "dept/disciplinary/passage/dept-geburah-1";
    private static final String DISCIPLINARY_UPPER_CORRIDOR_GROUP =
            "dept/disciplinary/passage/geburah-ru-way1";
    private static final String RECORD_MAIN_GROUP =
            "dept/record/passage/sefira-chokhmah-1";
    private static final String EXTRACTION_MAIN_GROUP =
            "dept/extraction/passage/sefira-binah-1";
    private static final String ARCHITECTURE_GROUP =
            "dept/architecture/passage/sefira-keter1";

    private static final Bounds CENTRAL_UPPER_MAIN = new Bounds(160, 129, 12, 230, 146, 64);
    private static final Bounds ARCHITECTURE_LEFT = new Bounds(38, 4, 47, 107, 9, 57);
    private static final Bounds ARCHITECTURE_RIGHT = new Bounds(283, 4, 46, 354, 9, 57);

    private GeneratedFacilityNavigationGameTests() {
    }

    @GameTest(template = "navigation_empty",
            templateNamespace = ModMain.MODID + "_navigation",
            timeoutTicks = 120_000, required = true, batch = "navigation_generated_facility")
    public static void generatedFacilitySupportsReportedEmployeeRoutes(GameTestHelper helper) {
        new Scenario().start(helper);
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID + "_navigation",
            timeoutTicks = 40, required = true, batch = "navigation_generation_callback")
    public static void headlessStructureCompletionInvokesCallback(GameTestHelper helper) throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        StructureLoader.LoadTask task = new StructureLoader.LoadTask(
                helper.getLevel(), null, "unused", BlockPos.ZERO,
                net.minecraft.world.level.block.Rotation.NONE, null, 0, 0, 0, 0,
                calls::incrementAndGet);
        java.lang.reflect.Method finish = StructureLoader.LoadTask.class.getDeclaredMethod(
                "finalizeLoading", int.class);
        finish.setAccessible(true);
        finish.invoke(task, 0);
        helper.assertTrue(calls.get() == 1, "headless structure completion omitted its callback");
        helper.succeed();
    }

    private static final class Scenario {
        private boolean generationStarted;
        private boolean staleEntitiesCleared;
        private boolean chunksLoaded;
        private List<Route> routes;
        private int routeIndex;
        private RouteRun active;

        private void start(GameTestHelper helper) {
            helper.onEachTick(() -> {
                try {
                    tick(helper);
                } catch (RuntimeException exception) {
                    LOGGER.error("Generated facility navigation crashed", exception);
                    helper.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                }
            });
        }

        private void tick(GameTestHelper helper) {
            ServerLevel facility = helper.getLevel().getServer().getLevel(ModDimensions.LOBOTO_KEY);
            if (facility == null) {
                helper.fail("facility dimension was not loaded");
                return;
            }
            // Use the real initialization transaction so spawning the employee cannot
            // trigger a second generation and teleport it away from the route's start.
            if (!generationStarted) {
                generationStarted = true;
                StructureUpdater.onPlayerEnter(facility, null);
            }
            if (StructureUpdater.isRunning()) return;
            if (!Structures.LOBOTO.isGenerated(facility)) {
                helper.fail("facility generation did not establish the generated state");
                return;
            }
            if (!staleEntitiesCleared) {
                int discardedClerks = discardStaleClerks(facility);
                LOGGER.info("Generated facility navigation discarded {} stale test clerks",
                        discardedClerks);
                staleEntitiesCleared = true;
            }

            if (!chunksLoaded) {
                loadFacilityChunks(facility);
                FacilityNavigationCache.prepare(facility);
                chunksLoaded = true;
                return;
            }
            if (FacilityNavigationCache.staticCellCount(facility) < 0) return;

            if (routes == null) {
                routes = buildRoutes(facility, helper);
                if (routes == null) return;
                String filter = routeFilter();
                if (!filter.isBlank()) {
                    routes = routes.stream()
                            .filter(route -> route.name().toLowerCase(java.util.Locale.ROOT)
                                    .contains(filter))
                            .toList();
                }
                routeIndex = Math.min(routeSkip(), routes.size());
                LOGGER.info("Generated facility navigation selected {} routes (skip={}, filter='{}')",
                        routes.size(), routeIndex, filter);
            }
            if (active == null) {
                if (routeIndex >= routes.size()) {
                    helper.succeed();
                    return;
                }
                Route route = routes.get(routeIndex);
                LOGGER.info("Starting generated facility navigation route {}/{}: {}",
                        routeIndex, routes.size(), route.name());
                active = route.isMonitorOrder()
                        ? RouteRun.monitorOrder(facility, route, routeIndex)
                        : RouteRun.direct(facility, route, routeIndex);
            }

            RouteRun.Result result = active.tick();
            if (result == RouteRun.Result.RUNNING) return;
            if (result == RouteRun.Result.FAILED) {
                String failure = active.failure();
                active.cleanup();
                helper.fail(failure);
                return;
            }
            active.cleanup();
            LOGGER.info("Passed generated facility navigation route {}/{}: {}",
                    routeIndex, routes.size(), active.routeName());
            active = null;
            routeIndex++;
        }
    }

    private static int routeSkip() {
        String value = System.getenv("LOBOTOCRAFT_NAV_SKIP");
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            LOGGER.warn("Ignoring invalid LOBOTOCRAFT_NAV_SKIP='{}'", value);
            return 0;
        }
    }

    private static String routeFilter() {
        String value = System.getenv("LOBOTOCRAFT_NAV_ROUTE_FILTER");
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static List<Route> buildRoutes(ServerLevel facility, GameTestHelper helper) {
        NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(facility,
                FacilityNavigationCache.COMPANY_MIN, FacilityNavigationCache.COMPANY_MAX);
        if (snapshot == null) return null;
        NodeEvaluator evaluator = new NodeEvaluator(snapshot, 0.6D, 1.8D);

        NavigationNode smallStart = nearestStandingPoint(evaluator, CENTRAL_UPPER_MAIN,
                new Vec3(172.5D, 136.0D, 38.5D));
        NavigationNode smallTarget = sampledStandingPoint(facility, evaluator,
                CENTRAL_UPPER_MAIN_GROUP, 0x5A11L);
        NavigationNode centralMain = sampledStandingPoint(facility, evaluator,
                CENTRAL_UPPER_MAIN_GROUP, 0xCE17A1L);
        NavigationNode centralSecondary = sampledStandingPoint(facility, evaluator,
                CENTRAL_UPPER_SECONDARY_GROUP, 0xCE17A2L);
        NavigationNode controlMain = sampledStandingPoint(facility, evaluator,
                CONTROL_MAIN_GROUP, 0xC01720L);
        NavigationNode controlTopLeft = sampledStandingPoint(facility, evaluator,
                CONTROL_TOP_LEFT_GROUP, 0xC01721L);
        NavigationNode informationUpperLeft = sampledStandingPoint(facility, evaluator,
                INFORMATION_UPPER_LEFT_GROUP, 0x1F001L);
        NavigationNode informationLowerLeft = sampledStandingPoint(facility, evaluator,
                INFORMATION_LOWER_LEFT_GROUP, 0x1F002L);
        NavigationNode informationUpperRight = sampledStandingPoint(facility, evaluator,
                INFORMATION_UPPER_RIGHT_GROUP, 0x1F003L);
        NavigationNode informationLowerRight = sampledStandingPoint(facility, evaluator,
                INFORMATION_LOWER_RIGHT_GROUP, 0x1F004L);
        NavigationNode safetyMain = sampledStandingPoint(facility, evaluator,
                SAFETY_MAIN_GROUP, 0x5AFE71L);
        NavigationNode safetyUpperElevator = sampledStandingPoint(facility, evaluator,
                SAFETY_UPPER_ELEVATOR_GROUP, 0x5AFE72L);
        NavigationNode safetyUpperCorridor = sampledStandingPoint(facility, evaluator,
                SAFETY_UPPER_CORRIDOR_GROUP, 0x5AFE73L);
        NavigationNode safetyUpperLeftContainment = sampledStandingPoint(facility, evaluator,
                SAFETY_UPPER_LEFT_CONTAINMENT_GROUP, 0x5AFE74L);
        NavigationNode safetyUpperRightContainment = sampledStandingPoint(facility, evaluator,
                SAFETY_UPPER_RIGHT_CONTAINMENT_GROUP, 0x5AFE75L);
        NavigationNode trainingMain = sampledStandingPointNearY(facility, evaluator,
                TRAINING_MAIN_GROUP, 0x7A1A11L,
                safetyMain == null ? 201.0D : safetyMain.standingY());
        NavigationNode centralLowerSecondary = sampledStandingPoint(facility, evaluator,
                CENTRAL_LOWER_SECONDARY_GROUP, 0xCE1702L);
        NodePair welfareRoute = sampledConnectedPair(facility, evaluator,
                WELFARE_MAIN_GROUP, 0x0E1FA1L,
                DISCIPLINARY_MAIN_GROUP, 0xD15C1L);
        NavigationNode welfareMain = welfareRoute == null ? null : welfareRoute.start();
        NavigationNode disciplinaryMain = welfareRoute == null ? null : welfareRoute.target();
        NavigationNode welfareUpperMain = sampledStandingPoint(facility, evaluator,
                WELFARE_UPPER_MAIN_GROUP, 0x0E1FA2L);
        NavigationNode disciplinaryUpperMain = sampledStandingPoint(facility, evaluator,
                DISCIPLINARY_UPPER_MAIN_GROUP, 0xD15C2L);
        NavigationNode disciplinaryUpperCorridor = sampledStandingPoint(facility, evaluator,
                DISCIPLINARY_UPPER_CORRIDOR_GROUP, 0xD15C3L);
        NavigationNode recordMain = sampledStandingPoint(facility, evaluator,
                RECORD_MAIN_GROUP, 0xAEC0ADL);
        NavigationNode extractionMain = sampledStandingPointNearY(facility, evaluator,
                EXTRACTION_MAIN_GROUP, 0xE47AC7L,
                recordMain == null ? 45.0D : recordMain.standingY());
        NavigationNode architectureLeft = nearestStandingPoint(evaluator, ARCHITECTURE_LEFT,
                new Vec3(42.5D, 6.0D, 52.5D));
        NavigationNode architectureRight = nearestStandingPoint(evaluator, ARCHITECTURE_RIGHT,
                new Vec3(350.5D, 6.0D, 52.5D));
        NavigationNode centralFarWest = nearestStandingPoint(evaluator, CENTRAL_UPPER_MAIN,
                new Vec3(163.5D, 136.0D, 16.5D));
        NavigationNode centralFarEast = nearestStandingPoint(evaluator, CENTRAL_UPPER_MAIN,
                new Vec3(227.5D, 136.0D, 60.5D));

        if (smallStart == null || smallTarget == null || centralMain == null
                || centralSecondary == null || controlMain == null || controlTopLeft == null
                || informationUpperLeft == null || informationLowerLeft == null
                || informationUpperRight == null || informationLowerRight == null
                || safetyMain == null || safetyUpperElevator == null
                || safetyUpperCorridor == null || safetyUpperLeftContainment == null
                || safetyUpperRightContainment == null || trainingMain == null
                || centralLowerSecondary == null || welfareMain == null
                || welfareUpperMain == null || disciplinaryMain == null
                || disciplinaryUpperMain == null || disciplinaryUpperCorridor == null
                || recordMain == null || extractionMain == null
                || architectureLeft == null || architectureRight == null
                || centralFarWest == null || centralFarEast == null) {
            helper.fail("could not select player-sized standing points from the generated facility: "
                    + "smallStart=" + smallStart + ", smallTarget=" + smallTarget
                    + ", centralMain=" + centralMain + ", centralSecondary=" + centralSecondary
                    + ", controlMain=" + controlMain + ", controlTopLeft=" + controlTopLeft
                    + ", informationUpperRight=" + informationUpperRight
                    + ", informationLowerRight=" + informationLowerRight
                    + ", welfareRoute=" + welfareRoute
                    + ", recordMain=" + recordMain
                    + ", extractionMain=" + extractionMain
                    + ", architectureLeft=" + architectureLeft
                    + ", architectureRight=" + architectureRight);
            return List.of();
        }
        List<Route> routes = new ArrayList<>();
        routes.add(Route.monitorOrder("EMP reported Safety upper elevator to upper corridor",
                safetyUpperElevator, safetyUpperCorridor, SAFETY_UPPER_CORRIDOR_GROUP, 0xE0401L));
        routes.add(Route.monitorOrder("EMP reported Safety upper elevator to left containment",
                safetyUpperElevator, safetyUpperLeftContainment,
                SAFETY_UPPER_LEFT_CONTAINMENT_GROUP, 0xE0402L));
        routes.add(Route.monitorOrder("EMP reported Safety upper elevator to right containment",
                safetyUpperElevator, safetyUpperRightContainment,
                SAFETY_UPPER_RIGHT_CONTAINMENT_GROUP, 0xE0403L));
        routes.add(Route.monitorOrder("EMP reported Central upper to Welfare upper",
                centralMain, welfareUpperMain, WELFARE_UPPER_MAIN_GROUP, 0xE0501L));
        routes.add(Route.monitorOrder("EMP reported Safety upper to Welfare upper",
                safetyUpperCorridor, welfareUpperMain, WELFARE_UPPER_MAIN_GROUP, 0xE0502L));
        routes.add(Route.monitorOrder("EMP reported Disciplinary upper main to Welfare upper",
                disciplinaryUpperMain, welfareUpperMain, WELFARE_UPPER_MAIN_GROUP, 0xE0503L));
        routes.add(Route.monitorOrder("EMP reported Disciplinary upper corridor to Welfare upper",
                disciplinaryUpperCorridor, welfareUpperMain, WELFARE_UPPER_MAIN_GROUP, 0xE0504L));
        routes.add(Route.monitorOrder("EMP reported Extraction to Welfare upper",
                extractionMain, welfareUpperMain, WELFARE_UPPER_MAIN_GROUP, 0xE0505L));
        addRoundTrip(routes, "Record to Extraction cross-wing", recordMain, extractionMain);
        addRoundTrip(routes, "Welfare to Disciplinary cross-wing", welfareMain,
                disciplinaryMain);
        addRoundTrip(routes, "Central upper-to-lower", centralMain, centralLowerSecondary);
        addRoundTrip(routes, "Safety to Training cross-wing", safetyMain, trainingMain);
        addRoundTrip(routes, "Architecture far ends", architectureLeft, architectureRight);
        addRoundTrip(routes, "Ultra-long Control top to Architecture west",
                controlTopLeft, architectureLeft);
        addRoundTrip(routes, "Ultra-long Control top to Architecture east",
                controlTopLeft, architectureRight);
        routes.add(Route.monitorOrder("Monitor order Ultra-long Control top to Architecture",
                controlTopLeft, architectureLeft, ARCHITECTURE_GROUP, 0xA11CE7L));
        routes.add(Route.monitorOrder("Monitor order Ultra-long Architecture to Control top",
                architectureLeft, controlTopLeft, CONTROL_TOP_LEFT_GROUP, 0xA11CE8L));
        routes.add(Route.journey("Continuous full-facility circuit", List.of(
                controlTopLeft,
                trainingMain,
                centralMain,
                disciplinaryMain,
                extractionMain,
                architectureRight,
                architectureLeft,
                recordMain,
                welfareMain,
                centralLowerSecondary,
                informationUpperRight,
                controlTopLeft)));
        addRoundTrip(routes, "Central main opposite corners", centralFarWest, centralFarEast);
        addRoundTrip(routes, "small same-floor sampled route", smallStart, smallTarget);
        addRoundTrip(routes, "Central upper-main staircase", centralMain, centralSecondary);
        routes.add(Route.monitorOrder("Monitor order Central upper-main staircase outbound",
                centralMain, centralSecondary, CENTRAL_UPPER_SECONDARY_GROUP, 0xA11CE1L));
        routes.add(Route.monitorOrder("Monitor order Central upper-main staircase return",
                centralSecondary, centralMain, CENTRAL_UPPER_MAIN_GROUP, 0xA11CE2L));
        addRoundTrip(routes, "Control to Central cross-floor", controlMain, centralMain);
        routes.add(Route.monitorOrder("Monitor order Control to Central outbound",
                controlMain, centralMain, CENTRAL_UPPER_MAIN_GROUP, 0xA11CE3L));
        routes.add(Route.monitorOrder("Monitor order Control to Central return",
                centralMain, controlMain, CONTROL_MAIN_GROUP, 0xA11CE4L));
        addRoundTrip(routes, "Information left elevator", informationLowerLeft,
                informationUpperLeft);
        addRoundTrip(routes, "Information right elevator", informationLowerRight,
                informationUpperRight);
        routes.add(Route.monitorOrder("Monitor order Information right elevator outbound",
                informationLowerRight, informationUpperRight,
                INFORMATION_UPPER_RIGHT_GROUP, 0xA11CE5L));
        routes.add(Route.monitorOrder("Monitor order Information right elevator return",
                informationUpperRight, informationLowerRight,
                INFORMATION_LOWER_RIGHT_GROUP, 0xA11CE6L));
        return List.copyOf(routes);
    }

    private static void addRoundTrip(List<Route> routes, String name,
                                     NavigationNode start, NavigationNode target) {
        routes.add(new Route(name + " outbound", start, target));
        routes.add(new Route(name + " return", target, start));
    }

    private static NavigationNode sampledStandingPoint(ServerLevel facility,
                                                        NodeEvaluator evaluator,
                                                        String groupId, long seed) {
        return sampledStandingPoint(facility, evaluator, groupId, seed, null);
    }

    private static NavigationNode sampledStandingPointNearY(ServerLevel facility,
                                                             NodeEvaluator evaluator,
                                                             String groupId, long seed,
                                                             double preferredY) {
        return sampledStandingPoint(facility, evaluator, groupId, seed, preferredY);
    }

    private static NavigationNode sampledStandingPoint(ServerLevel facility,
                                                        NodeEvaluator evaluator,
                                                        String groupId, long seed,
                                                        Double requestedY) {
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        return sampledStandingPoints(facility, evaluator, groupId, seed, requestedY)
                .stream()
                .filter(node -> isOnFacilityNetwork(transitions, node))
                .findFirst().orElse(null);
    }

    private static List<NavigationNode> sampledStandingPoints(ServerLevel facility,
                                                               NodeEvaluator evaluator,
                                                               String groupId, long seed,
                                                               Double requestedY) {
        List<FacilityWorldLayout.AreaDefinition> targetAreas =
                FacilityWorldLayout.targetAreas(facility, groupId);
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        List<BlockPos> sampled = FacilityMoveTargetSampler.sampleCandidates(targetAreas,
                RandomSource.create(seed), 256, 256, candidate ->
                        isConnectedStandingPoint(evaluator, transitions, candidate));
        double preferredY = requestedY != null ? requestedY : targetAreas.stream()
                .mapToDouble(area -> (area.minPos().getY() + area.maxPos().getY()) * 0.5D)
                .average().orElse(0.0D);
        return sampled.stream()
                .map(NavigationNode::new)
                .filter(node -> isLiveStandingPoint(facility, node))
                .sorted(java.util.Comparator.comparingDouble(node ->
                        Math.abs(node.standingY() - preferredY)))
                .toList();
    }

    private static NodePair sampledConnectedPair(ServerLevel facility,
                                                 NodeEvaluator evaluator,
                                                 String startGroup, long startSeed,
                                                 String targetGroup, long targetSeed) {
        List<NavigationNode> starts = sampledStandingPoints(
                facility, evaluator, startGroup, startSeed, null);
        List<NavigationNode> targets = sampledStandingPoints(
                facility, evaluator, targetGroup, targetSeed, null);
        if (starts.isEmpty() || targets.isEmpty()) return null;

        Set<NavigationNode> targetSet = new HashSet<>(targets);
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        AStarPathfinder pathfinder = new AStarPathfinder(transitions::generate,
                NavigationTransition::cost,
                (from, ignored) -> targets.stream()
                        .mapToDouble(target -> heuristic(from, target)).min().orElse(0.0D));
        for (NavigationNode start : oneCandidatePerLayer(starts)) {
            PathSearchResult result = pathfinder.search(start, targets.get(0), targetSet::contains,
                    new AStarPathfinder.SearchOptions(CONNECTED_PAIR_NODE_BUDGET, 1.0D));
            if (result.reachedGoal() && result.path() != null
                    && targetSet.contains(result.path().endpoint())) {
                return new NodePair(start, result.path().endpoint());
            }
        }
        return null;
    }

    private static List<NavigationNode> oneCandidatePerLayer(List<NavigationNode> candidates) {
        LinkedHashMap<Long, NavigationNode> layers = new LinkedHashMap<>();
        for (NavigationNode candidate : candidates) {
            layers.putIfAbsent(Double.doubleToLongBits(candidate.standingY()), candidate);
        }
        return List.copyOf(layers.values());
    }

    private static boolean isConnectedStandingPoint(NodeEvaluator evaluator,
                                                     TransitionGenerator transitions,
                                                     BlockPos candidate) {
        NavigationNode node = new NavigationNode(candidate);
        return evaluator.evaluate(node, false).traversable()
                && NavigationCandidatePolicy.hasLocalMobility(node, transitions::generate);
    }

    /**
     * Sealed surfaces inside a department cuboid -- notably the containment
     * cluster roofs -- are standable and locally mobile yet disconnected from
     * the corridor network, so route origins must additionally sit on a region
     * wide enough to reach the probe cap.  Applied after the cheap pruning and
     * the preferred-Y sort, so consumers pay for it lazily.
     */
    private static boolean isOnFacilityNetwork(TransitionGenerator transitions,
                                               NavigationNode node) {
        return NavigationCandidatePolicy.isFacilityConnectedRegion(
                node, transitions::generate);
    }

    private static boolean isLiveStandingPoint(ServerLevel facility, NavigationNode node) {
        Vec3 standing = node.standingPosition();
        double halfWidth = 0.3D;
        AABB body = new AABB(standing.x - halfWidth, standing.y,
                standing.z - halfWidth, standing.x + halfWidth,
                standing.y + 1.8D, standing.z + halfWidth);
        if (facility.getBlockCollisions(null, body).iterator().hasNext()) return false;
        AABB support = new AABB(standing.x - halfWidth, standing.y - 0.0625D,
                standing.z - halfWidth, standing.x + halfWidth,
                standing.y + 1.0E-6D, standing.z + halfWidth);
        return facility.getBlockCollisions(null, support).iterator().hasNext();
    }

    private static NavigationNode nearestStandingPoint(NodeEvaluator evaluator, Bounds bounds,
                                                        Vec3 hint) {
        NavigationNode best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos column = new BlockPos(x, bounds.maxY(), z);
                for (NodeEvaluator.CandidateEvaluation candidate : evaluator.candidates(column,
                        bounds.maxY() + 0.999D, 0.0D,
                        bounds.maxY() - bounds.minY() + 2.0D, false)) {
                    NavigationNode node = candidate.node();
                    if (node.standingY() < bounds.minY()
                            || node.standingY() > bounds.maxY() + 1.0D) continue;
                    double distance = node.standingPosition().distanceToSqr(hint);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = node;
                    }
                }
            }
        }
        return best;
    }

    private static void loadFacilityChunks(ServerLevel facility) {
        int minChunkX = FacilityNavigationCache.COMPANY_MIN.getX() >> 4;
        int maxChunkX = FacilityNavigationCache.COMPANY_MAX.getX() >> 4;
        int minChunkZ = FacilityNavigationCache.COMPANY_MIN.getZ() >> 4;
        int maxChunkZ = FacilityNavigationCache.COMPANY_MAX.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                facility.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static int discardStaleClerks(ServerLevel facility) {
        List<EntityClerk> staleClerks = new ArrayList<>();
        for (var entity : facility.getAllEntities()) {
            if (entity instanceof EntityClerk clerk) staleClerks.add(clerk);
        }
        int discarded = 0;
        for (EntityClerk clerk : staleClerks) {
            clerk.discard();
            discarded++;
        }
        return discarded;
    }

    private static EmployeeServerPlayer spawnEmployee(ServerLevel facility, NavigationNode node,
                                                       String name) {
        EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(), name,
                EmployeeDepartment.CONTROL);
        EmployeeServerPlayer employee = new EmployeeServerPlayer(facility,
                new GameProfile(record.id(), record.name()), record);
        employee.setInvulnerable(true);
        Vec3 position = node.standingPosition();
        employee.absMoveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        facility.addNewPlayer(employee);
        return employee;
    }

    private record Bounds(int minX, int minY, int minZ,
                          int maxX, int maxY, int maxZ) {
        private Bounds {
            int normalizedMinX = Math.min(minX, maxX);
            int normalizedMinY = Math.min(minY, maxY);
            int normalizedMinZ = Math.min(minZ, maxZ);
            int normalizedMaxX = Math.max(minX, maxX);
            int normalizedMaxY = Math.max(minY, maxY);
            int normalizedMaxZ = Math.max(minZ, maxZ);
            minX = normalizedMinX;
            minY = normalizedMinY;
            minZ = normalizedMinZ;
            maxX = normalizedMaxX;
            maxY = normalizedMaxY;
            maxZ = normalizedMaxZ;
        }
    }

    private record Route(String name, NavigationNode start, NavigationNode target,
                         String monitorTargetGroup, long monitorSeed,
                         List<NavigationNode> waypoints) {
        private Route(String name, NavigationNode start, NavigationNode target) {
            this(name, start, target, null, 0L, List.of(target));
        }

        private static Route monitorOrder(String name, NavigationNode start,
                                          NavigationNode target, String targetGroup,
                                          long seed) {
            return new Route(name, start, target, targetGroup, seed, List.of(target));
        }

        private static Route journey(String name, List<NavigationNode> stops) {
            if (stops.size() < 2) {
                throw new IllegalArgumentException("A navigation journey needs at least two stops");
            }
            List<NavigationNode> copiedStops = List.copyOf(stops);
            return new Route(name, copiedStops.get(0), copiedStops.get(copiedStops.size() - 1),
                    null, 0L, copiedStops.subList(1, copiedStops.size()));
        }

        private boolean isMonitorOrder() {
            return monitorTargetGroup != null && !monitorTargetGroup.isBlank();
        }
    }

    private record NodePair(NavigationNode start, NavigationNode target) {
    }

    private static final class RouteRun {
        private final Route route;
        private final EmployeeServerPlayer employee;
        private final EmployeeNavigator navigator;
        private final EmployeeRecord runtimeRecord;
        private int ticks;
        private int legTicks;
        private int waypointIndex;
        private String failure;
        private Vec3 firstMovementTarget;
        private Vec3 lastMovementTarget;

        private RouteRun(Route route, EmployeeServerPlayer employee,
                         EmployeeNavigator navigator, EmployeeRecord runtimeRecord) {
            this.route = route;
            this.employee = employee;
            this.navigator = navigator;
            this.runtimeRecord = runtimeRecord;
        }

        private static RouteRun direct(ServerLevel facility, Route route, int routeIndex) {
            EmployeeServerPlayer employee = spawnEmployee(facility,
                    route.start(), "generated_facility_route_" + routeIndex);
            EmployeeNavigator navigator = new EmployeeNavigator(employee);
            RouteRun run = new RouteRun(route, employee, navigator, null);
            run.navigateToCurrentWaypoint();
            return run;
        }

        private static RouteRun monitorOrder(ServerLevel facility, Route route, int routeIndex) {
            EmployeeRecord record = new EmployeeRecord(UUID.randomUUID(),
                    "generated_monitor_route_" + routeIndex, EmployeeDepartment.CONTROL);
            Vec3 start = route.start().standingPosition();
            record.setPosition(facility.dimension().location(), start.x, start.y, start.z);
            EmployeeRuntimeManager.spawn(facility.getServer(), record);
            EmployeeServerPlayer employee = EmployeeRuntimeManager.activePlayer(record.id());
            if (employee == null) {
                RouteRun run = new RouteRun(route,
                        spawnEmployee(facility, route.start(), "failed_monitor_route"),
                        null, record);
                run.failure = route.name() + " could not spawn a runtime employee";
                return run;
            }
            List<FacilityWorldLayout.AreaDefinition> targetAreas =
                    FacilityWorldLayout.targetAreas(facility, route.monitorTargetGroup());
            List<BlockPos> targets = FacilityMoveTargetSampler.sampleCandidates(targetAreas,
                    RandomSource.create(route.monitorSeed()), 128, 12,
                    candidate -> FacilityAreaMoveService.isNavigableTarget(
                            facility, employee, candidate));
            RouteRun run = new RouteRun(route, employee, null, record);
            if (targets.isEmpty()) {
                run.failure = route.name() + " did not sample any navigable monitor targets";
            } else if (!EmployeeRuntimeManager.assignManualMove(record, targets)) {
                run.failure = route.name() + " runtime rejected the monitor move order";
            }
            return run;
        }

        private Result tick() {
            ticks++;
            legTicks++;
            if (failure != null) return Result.FAILED;
            if (runtimeRecord != null) return tickRuntimeOrder();
            // GameTest's synthetic ServerPlayer is not in PlayerList, so it does not receive
            // the normal movement/physics tick that production employees receive.
            employee.doTick();
            navigator.tick();
            Vec3 movementTarget = navigator.testMovementTarget();
            if (movementTarget != null) {
                if (firstMovementTarget == null) firstMovementTarget = movementTarget;
                lastMovementTarget = movementTarget;
            }
            if (navigator.isComplete()) {
                if (waypointIndex + 1 >= route.waypoints().size()) return Result.COMPLETE;
                waypointIndex++;
                legTicks = 0;
                navigateToCurrentWaypoint();
                return Result.RUNNING;
            }
            if (navigator.isUnreachable() || legTicks >= ROUTE_TIMEOUT_TICKS) {
                NavigationNode currentTarget = currentWaypoint();
                failure = route.name() + " failed after " + ticks + " ticks (leg "
                        + (waypointIndex + 1) + "/" + route.waypoints().size() + " after "
                        + legTicks + " ticks): start=" + route.start()
                        + ", target=" + currentTarget
                        + ", employee=" + employee.blockPosition()
                        + ", next=" + movementTarget
                        + ", firstNext=" + firstMovementTarget
                        + ", lastNext=" + lastMovementTarget
                        + ", unreachable=" + navigator.isUnreachable()
                        + ", navigator=" + navigator.testFailureDiagnostics();
                return Result.FAILED;
            }
            return Result.RUNNING;
        }

        private NavigationNode currentWaypoint() {
            return route.waypoints().get(waypointIndex);
        }

        private void navigateToCurrentWaypoint() {
            NavigationNode waypoint = currentWaypoint();
            LOGGER.info("Generated facility route {} starting leg {}/{} from {} to {}",
                    route.name(), waypointIndex + 1, route.waypoints().size(),
                    employee.blockPosition(), waypoint);
            navigator.moveTo(waypoint.feetPosition(), 1.6D);
        }

        private Result tickRuntimeOrder() {
            if (runtimeRecord.taskType() == EmployeeTaskType.IDLE) {
                if (!runtimeRecord.taskSummary().isEmpty()) {
                    failure = route.name() + " failed: " + runtimeRecord.taskSummary()
                            + ", employee=" + employee.position()
                            + ", station=" + runtimeRecord.manualStationPos()
                            + ", navigator="
                            + EmployeeRuntimeManager.testNavigationDiagnostics(runtimeRecord);
                    return Result.FAILED;
                }
                BlockPos station = runtimeRecord.manualStationPos();
                if (station != null && employee.position().distanceToSqr(
                        Vec3.atBottomCenterOf(station)) <= 1.0D) {
                    return Result.COMPLETE;
                }
            }
            if (ticks >= ROUTE_TIMEOUT_TICKS) {
                failure = route.name() + " timed out: employee=" + employee.position()
                        + ", task=" + runtimeRecord.taskType() + "/"
                        + runtimeRecord.taskSummary()
                        + ", station=" + runtimeRecord.manualStationPos()
                        + ", navigator=" + EmployeeRuntimeManager.testNavigationDiagnostics(runtimeRecord);
                return Result.FAILED;
            }
            return Result.RUNNING;
        }

        private String failure() {
            return failure;
        }

        private String routeName() {
            return route.name();
        }

        private void cleanup() {
            if (runtimeRecord != null) {
                EmployeeRuntimeManager.remove(employee.server, runtimeRecord);
            } else {
                employee.kill();
            }
        }

        private enum Result { RUNNING, COMPLETE, FAILED }
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
}
