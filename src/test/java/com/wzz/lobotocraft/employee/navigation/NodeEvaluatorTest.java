package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NodeEvaluatorTest {
    private static final double PLAYER_WIDTH = 0.6D;
    private static final double PLAYER_HEIGHT = 1.8D;

    @Test
    void connectedTargetCheckRejectsAnIsolatedStandingPoint() {
        SnapshotBuilder isolated = new SnapshotBuilder();
        isolated.solid(new BlockPos(0, 0, 0));
        NavigationNode node = new NavigationNode(new BlockPos(0, 1, 0), 1.0D);
        assertTrue(generator(isolated).generate(node).isEmpty());

        SnapshotBuilder connected = new SnapshotBuilder();
        connected.solid(new BlockPos(0, 0, 0));
        connected.solid(new BlockPos(1, 0, 0));
        assertFalse(generator(connected).generate(node).isEmpty());
    }

    @Test
    void preservesLowerSlabStandingHeight() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.shape(new BlockPos(1, 0, 0),
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false);
        TransitionGenerator generator = generator(world);

        List<NavigationTransition> transitions = generator.generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        NavigationTransition slab = transitions.stream()
                .filter(step -> step.target().feetPosition().getX() == 1)
                .findFirst().orElseThrow();
        assertEquals(0.5D, slab.target().standingY());
        assertEquals(NavigationAction.DROP_DOWN, slab.action());
    }

    @Test
    void configuredOneBlockDropDoesNotRouteDownADeepShaft() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 3, 0));
        world.solid(new BlockPos(1, 0, 0));
        NodeEvaluator evaluator = new NodeEvaluator(world.build(), PLAYER_WIDTH, PLAYER_HEIGHT);
        TransitionGenerator generator = new TransitionGenerator(evaluator, 1, 1, false);

        List<NavigationTransition> transitions = generator.generate(
                new NavigationNode(new BlockPos(0, 4, 0), 4.0D));

        assertFalse(transitions.stream().anyMatch(step ->
                step.target().feetPosition().equals(new BlockPos(1, 1, 0))));
    }

    @Test
    void followsSuccessiveHalfBlockStairRises() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.solid(new BlockPos(1, 0, 0));
        world.shape(new BlockPos(1, 1, 0),
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false);
        world.solid(new BlockPos(2, 0, 0));
        world.solid(new BlockPos(2, 1, 0));

        TransitionGenerator generator = generator(world);
        NavigationNode start = new NavigationNode(new BlockPos(0, 1, 0), 1.0D);
        NavigationTransition firstRise = generator.generate(start).stream()
                .filter(step -> step.target().feetPosition().getX() == 1)
                .findFirst().orElseThrow();
        NavigationTransition secondRise = generator.generate(firstRise.target()).stream()
                .filter(step -> step.target().feetPosition().getX() == 2
                        && step.target().standingY() == 2.0D)
                .findFirst().orElseThrow();

        assertEquals(1.5D, firstRise.target().standingY());
        assertEquals(NavigationAction.STEP_UP, firstRise.action());
        assertEquals(NavigationAction.STEP_UP, secondRise.action());
    }

    @Test
    void rejectsFenceAsStepUpSurface() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.shape(new BlockPos(1, 0, 0),
                Shapes.box(0.375D, 0.0D, 0.0D, 0.625D, 1.5D, 1.0D), true);

        List<NavigationTransition> transitions = generator(world).generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        assertFalse(transitions.stream()
                .anyMatch(step -> step.target().feetPosition().getX() == 1));
    }

    @Test
    void rejectsStepWhenJumpSweepHitsLowCeiling() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.solid(new BlockPos(1, 1, 0));
        world.solid(new BlockPos(0, 2, 0));

        List<NavigationTransition> transitions = generator(world).generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        assertFalse(transitions.stream()
                .anyMatch(step -> step.target().feetPosition().getX() == 1
                        && step.target().standingY() == 2.0D));
    }

    @Test
    void rejectsOneBlockWallStepEvenWhenFullBodySweepIsClear() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.solid(new BlockPos(1, 1, 0));

        List<NavigationTransition> transitions = generator(world).generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        assertFalse(transitions.stream()
                .anyMatch(step -> step.target().feetPosition().getX() == 1
                        && step.target().standingY() == 2.0D
                        && step.action() == NavigationAction.STEP_UP));
    }

    @Test
    void allowsOneBlockRiseOntoBroadLandingInEveryDirection() {
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int side : new int[]{-1, 1}) {
                SnapshotBuilder world = new SnapshotBuilder();
                world.solid(new BlockPos(0, 0, 0));
                for (int forward = 1; forward <= 2; forward++) {
                    for (int lateral = 0; lateral <= 1; lateral++) {
                        world.solid(new BlockPos(
                                direction[0] * forward - direction[1] * lateral * side, 1,
                                direction[1] * forward + direction[0] * lateral * side));
                    }
                }
                BlockPos landing = new BlockPos(direction[0], 2, direction[1]);
                assertTrue(generator(world).generate(new NavigationNode(new BlockPos(0, 1, 0)))
                                .stream().anyMatch(step -> step.target().feetPosition().equals(landing)),
                        "a supported two-by-two landing was mistaken for a wall: " + landing);
            }
        }
    }

    @Test
    void rejectsLongNarrowWallTopDespiteItsSidewaysMobility() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        for (int z = -2; z <= 2; z++) world.solid(new BlockPos(1, 1, z));
        assertFalse(generator(world).generate(new NavigationNode(new BlockPos(0, 1, 0)))
                .stream().anyMatch(step -> step.target().feetPosition().equals(new BlockPos(1, 2, 0))));
    }

    @Test
    void rejectsNarrowForwardLedgeWithoutLateralLandingRoom() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        for (int x = 1; x <= 3; x++) world.solid(new BlockPos(x, 1, 0));
        assertFalse(generator(world).generate(new NavigationNode(new BlockPos(0, 1, 0)))
                .stream().anyMatch(step -> step.target().feetPosition().equals(new BlockPos(1, 2, 0))));
    }

    @Test
    void broadLandingDoesNotBypassJumpHeadroom() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        for (int x = 1; x <= 2; x++) {
            for (int z = 0; z <= 1; z++) world.solid(new BlockPos(x, 1, z));
        }
        world.solid(new BlockPos(0, 3, 0));
        assertFalse(generator(world).generate(new NavigationNode(new BlockPos(0, 1, 0)))
                .stream().anyMatch(step -> step.target().feetPosition().equals(new BlockPos(1, 2, 0))));
    }

    @Test
    void allowsFullHeightRiseWhenTheSupportIsARealStairShape() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.shape(new BlockPos(1, 1, 0), Shapes.or(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                Shapes.box(0.5D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D)), false);

        List<NavigationTransition> transitions = generator(world).generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        assertTrue(transitions.stream()
                .anyMatch(step -> step.target().feetPosition().getX() == 1
                        && step.target().standingY() == 2.0D
                        && step.action() == NavigationAction.STEP_UP));
    }

    @Test
    void addsOpenDiagonalWithEuclideanCost() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.solid(new BlockPos(1, 0, 0));
        world.solid(new BlockPos(0, 0, 1));
        world.solid(new BlockPos(1, 0, 1));

        NavigationTransition diagonal = generator(world).generate(
                        new NavigationNode(new BlockPos(0, 1, 0), 1.0D)).stream()
                .filter(step -> step.target().feetPosition().equals(new BlockPos(1, 1, 1)))
                .findFirst().orElseThrow();

        assertEquals(Math.sqrt(2.0D), diagonal.cost(), 1.0E-9D);
    }

    @Test
    void diagonalDoesNotCutABlockedCorner() {
        SnapshotBuilder world = new SnapshotBuilder();
        world.solid(new BlockPos(0, 0, 0));
        world.solid(new BlockPos(1, 0, 0));
        world.solid(new BlockPos(0, 0, 1));
        world.solid(new BlockPos(1, 0, 1));
        world.solid(new BlockPos(1, 1, 0));

        List<NavigationTransition> transitions = generator(world).generate(
                new NavigationNode(new BlockPos(0, 1, 0), 1.0D));

        assertFalse(transitions.stream()
                .anyMatch(step -> step.target().feetPosition().equals(new BlockPos(1, 1, 1))));
    }

    private static TransitionGenerator generator(SnapshotBuilder world) {
        NodeEvaluator evaluator = new NodeEvaluator(world.build(), PLAYER_WIDTH, PLAYER_HEIGHT);
        return new TransitionGenerator(evaluator, 1, 4, false);
    }

    private static final class SnapshotBuilder {
        private static final BlockPos MINIMUM = new BlockPos(-3, -5, -3);
        private static final BlockPos MAXIMUM = new BlockPos(3, 5, 3);
        private final Map<BlockPos, CellData> cells = new HashMap<>();

        private SnapshotBuilder() {
            for (int y = MINIMUM.getY(); y <= MAXIMUM.getY(); y++) {
                for (int z = MINIMUM.getZ(); z <= MAXIMUM.getZ(); z++) {
                    for (int x = MINIMUM.getX(); x <= MAXIMUM.getX(); x++) {
                        cells.put(new BlockPos(x, y, z), cell(Shapes.empty(), false));
                    }
                }
            }
        }

        private void solid(BlockPos position) {
            shape(position, Shapes.block(), false);
        }

        private void shape(BlockPos position, VoxelShape shape, boolean unsafeNarrowSupport) {
            cells.put(position.immutable(), cell(shape, unsafeNarrowSupport));
        }

        private NavigationSnapshot build() {
            return new NavigationSnapshot(MINIMUM, MAXIMUM, cells, true);
        }

        private static CellData cell(VoxelShape shape, boolean unsafeNarrowSupport) {
            return new CellData(shape, false, false, !shape.isEmpty(), unsafeNarrowSupport,
                    shape.isEmpty() ? 0.0F : 1.0F, CellData.Hazard.NONE);
        }
    }
}
