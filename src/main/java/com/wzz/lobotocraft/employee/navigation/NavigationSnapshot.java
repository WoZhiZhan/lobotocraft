package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Immutable, bounded world view used by the employee path planner. */
public final class NavigationSnapshot {
    private static final CellData AIR = new CellData(Shapes.empty(), false, false,
            false, false, 0.0F, CellData.Hazard.NONE);
    /**
     * A facility route is planned from the whole relevant corridor, not from
     * a succession of 20-block windows.  Keep a generous bound for malformed
     * targets while still preventing an accidental world-sized scan.
     */
    public static final int HARD_MAX_CELLS = 2_000_000;

    private final BlockPos minimum;
    private final BlockPos maximum;
    private final Map<BlockPos, CellData> cells;
    private final Set<BlockPos> unavailable;
    private final boolean complete;
    private final Function<BlockPos, Optional<CellData>> sharedLookup;

    NavigationSnapshot(BlockPos minimum, BlockPos maximum,
                       Map<BlockPos, CellData> cells, Set<BlockPos> unavailable,
                       boolean complete) {
        this.minimum = minimum.immutable();
        this.maximum = maximum.immutable();
        this.cells = Map.copyOf(cells);
        this.unavailable = Set.copyOf(unavailable);
        this.complete = complete;
        this.sharedLookup = null;
    }

    NavigationSnapshot(BlockPos minimum, BlockPos maximum,
                       Map<BlockPos, CellData> cells, boolean complete) {
        this(minimum, maximum, cells, Set.of(), complete);
    }

    public static NavigationSnapshot capture(Level level, BlockPos minimum, BlockPos maximum,
                                             int cellBudget) {
        Capture capture = beginCapture(level, minimum, maximum, cellBudget);
        int perCall = Math.max(1, Math.min(16_384, cellBudget));
        while (!capture.isDone()) capture.captureNext(perCall);
        return capture.finish();
    }

    public static Capture beginCapture(Level level, BlockPos minimum, BlockPos maximum,
                                       int cellBudget) {
        return new Capture(level, minimum, maximum, cellBudget);
    }

    /** Creates a bounded read-only view over an already published shared cache. */
    public static NavigationSnapshot shared(BlockPos minimum, BlockPos maximum,
            Function<BlockPos, Optional<CellData>> lookup) {
        return new NavigationSnapshot(minimum, maximum, lookup);
    }

    private NavigationSnapshot(BlockPos minimum, BlockPos maximum,
            Function<BlockPos, Optional<CellData>> lookup) {
        this.minimum = minimum.immutable();
        this.maximum = maximum.immutable();
        this.cells = Map.of();
        this.unavailable = Set.of();
        this.complete = true;
        this.sharedLookup = Objects.requireNonNull(lookup, "lookup");
    }

    public Optional<CellData> cell(BlockPos position) {
        if (position.getX() < minimum.getX() || position.getX() > maximum.getX()
                || position.getY() < minimum.getY() || position.getY() > maximum.getY()
                || position.getZ() < minimum.getZ() || position.getZ() > maximum.getZ()
                || unavailable.contains(position)) {
            return Optional.empty();
        }
        if (sharedLookup != null) {
            return Objects.requireNonNull(sharedLookup.apply(position),
                    "shared navigation lookup returned null");
        }
        CellData cell = cells.get(position);
        return Optional.of(cell == null ? AIR : cell);
    }

    public static CellData airCell() {
        return AIR;
    }

    /** Converts a known non-air state into immutable data safe for background A*. */
    public static CellData describeCell(BlockGetter level, BlockPos position, BlockState state) {
        FluidState fluid = state.getFluidState();
        float destroySpeed = state.getDestroySpeed(level, position);
        return new CellData(state.getCollisionShape(level, position),
                isTraversableClosedDoor(level, position, state), isSoftObstacle(state),
                destroySpeed >= 0.0F && !state.isAir(), isUnsafeNarrowSupport(state),
                destroySpeed, hazard(state, fluid));
    }

    /** Reads one live cell. Air is returned as the shared air sentinel. */
    public static CellData describeLiveCell(Level level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        FluidState fluid = state.getFluidState();
        return state.isAir() && fluid.isEmpty()
                ? AIR : describeCell(level, position, state);
    }

    public boolean complete() {
        return complete;
    }

    private static boolean isTraversableClosedDoor(BlockGetter level, BlockPos position,
                                                   BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock)
                || !state.hasProperty(DoorBlock.OPEN) || state.getValue(DoorBlock.OPEN)) {
            return false;
        }
        if (!state.is(Blocks.IRON_DOOR)) return true;
        BlockPos lower = state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
        for (BlockPos adjacent : new BlockPos[]{
                lower.north(), lower.south(), lower.west(), lower.east()}) {
            if (level.getBlockState(adjacent).getBlock() instanceof PressurePlateBlock) return true;
        }
        return false;
    }

    private static boolean isSoftObstacle(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(Blocks.COBWEB) || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN);
    }

    private static boolean isUnsafeNarrowSupport(BlockState state) {
        return state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof WallBlock;
    }

    private static CellData.Hazard hazard(BlockState state, FluidState fluid) {
        if (fluid.is(FluidTags.LAVA) || state.is(Blocks.LAVA) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return CellData.Hazard.DANGEROUS;
        }
        if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)) {
            return CellData.Hazard.CAUTION;
        }
        return CellData.Hazard.NONE;
    }

    /** Main-thread cursor that copies a large world window over multiple server ticks. */
    public static final class Capture {
        private final Level level;
        private final BlockPos minimum;
        private final BlockPos maximum;
        private final int totalBudget;
        private final Map<BlockPos, CellData> cells = new HashMap<>();
        private final Set<BlockPos> unavailable = new HashSet<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int x;
        private int y;
        private int z;
        private int inspected;
        private boolean complete = true;
        private boolean done;

        private Capture(Level level, BlockPos minimum, BlockPos maximum, int cellBudget) {
            this.level = Objects.requireNonNull(level, "level");
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.getX() > maximum.getX() || minimum.getY() > maximum.getY()
                    || minimum.getZ() > maximum.getZ()) {
                throw new IllegalArgumentException("invalid snapshot bounds");
            }
            this.minimum = minimum.immutable();
            this.maximum = maximum.immutable();
            this.totalBudget = Math.max(1, Math.min(cellBudget, HARD_MAX_CELLS));
            this.x = minimum.getX();
            this.y = minimum.getY();
            this.z = minimum.getZ();
        }

        /** Returns the number of world cells copied by this call. */
        public int captureNext(int cellBudget) {
            if (done || cellBudget <= 0) return 0;
            int copied = 0;
            while (!done && copied < cellBudget) {
                if (inspected >= totalBudget) {
                    complete = false;
                    done = true;
                    break;
                }
                cursor.set(x, y, z);
                inspected++;
                copied++;
                if (!level.hasChunkAt(cursor)) {
                    complete = false;
                    unavailable.add(cursor.immutable());
                } else {
                    BlockState state = level.getBlockState(cursor);
                    FluidState fluid = state.getFluidState();
                    if (state.isAir() && fluid.isEmpty()) {
                        advance();
                        continue;
                    }
                    cells.put(cursor.immutable(), describeCell(level, cursor, state));
                }
                advance();
            }
            return copied;
        }

        public boolean isDone() {
            return done;
        }

        public NavigationSnapshot finish() {
            if (!done) throw new IllegalStateException("snapshot capture is not complete");
            return new NavigationSnapshot(minimum, maximum, cells, unavailable, complete);
        }

        private void advance() {
            if (++x <= maximum.getX()) return;
            x = minimum.getX();
            if (++z <= maximum.getZ()) return;
            z = minimum.getZ();
            if (++y <= maximum.getY()) return;
            done = true;
        }
    }
}
