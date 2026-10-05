package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

public final class NodeEvaluator {
    private static final double COLLISION_EPSILON = 1.0E-6D;
    private static final double SUPPORT_DEPTH = 0.0625D;
    private static final double SWEEP_SAMPLE_DISTANCE = 0.2D;

    private final NavigationSnapshot snapshot;
    private final double width;
    private final double height;
    private static final int MAX_MEMOIZED_NODES = 16_384;
    private final Map<NavigationNode, Evaluation> walkEvaluations;
    private final Map<NavigationNode, Evaluation> breakEvaluations;
    private final Map<VoxelShape, List<AABB>> collisionBoxes;

    public NodeEvaluator(NavigationSnapshot snapshot, double width, double height) {
        this(snapshot, width, height, false);
    }

    /** Opt-in memoization for repeated queries over this evaluator's immutable snapshot. */
    public NodeEvaluator(NavigationSnapshot snapshot, double width, double height, boolean memoize) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!Double.isFinite(width) || width <= 0.0D
                || !Double.isFinite(height) || height <= 0.0D) {
            throw new IllegalArgumentException("body dimensions must be finite and positive");
        }
        this.width = width;
        this.height = height;
        walkEvaluations = memoize ? new java.util.HashMap<>() : null;
        breakEvaluations = memoize ? new java.util.HashMap<>() : null;
        collisionBoxes = memoize ? new java.util.IdentityHashMap<>() : null;
    }

    public Evaluation evaluate(NavigationNode node, boolean allowBreaking) {
        Map<NavigationNode, Evaluation> cache = allowBreaking ? breakEvaluations : walkEvaluations;
        if (cache == null) return evaluateUncached(node, allowBreaking);
        Evaluation existing = cache.get(node);
        if (existing != null) return existing;
        Evaluation evaluation = evaluateUncached(node, allowBreaking);
        if (cache.size() < MAX_MEMOIZED_NODES) cache.put(node, evaluation);
        return evaluation;
    }

    private Evaluation evaluateUncached(NavigationNode node, boolean allowBreaking) {
        Evaluation body = evaluateBody(bodyBox(node.standingPosition().x, node.standingY(),
                node.standingPosition().z), allowBreaking);
        if (!body.traversable()) return body;
        Support support = findSupport(node);
        if (!support.present()) return Evaluation.blocked(support.failure());
        return body.withCaution(body.caution() || support.caution());
    }

    /** Returns every valid standing surface in the adjacent column, including stair/slab heights. */
    public List<CandidateEvaluation> candidates(BlockPos horizontal, double originY,
                                                double maxStepUp, double maxDropDown,
                                                boolean allowBreaking) {
        double minimumY = originY - Math.max(0.0D, maxDropDown);
        double maximumY = originY + Math.max(0.0D, maxStepUp);
        double centerX = horizontal.getX() + 0.5D;
        double centerZ = horizontal.getZ() + 0.5D;
        double halfWidth = width / 2.0D;
        double minX = centerX - halfWidth;
        double maxX = centerX + halfWidth;
        double minZ = centerZ - halfWidth;
        double maxZ = centerZ + halfWidth;
        Set<Double> surfaces = new TreeSet<>(Comparator.reverseOrder());

        int blockMinX = Mth.floor(minX + COLLISION_EPSILON);
        int blockMaxX = Mth.floor(maxX - COLLISION_EPSILON);
        int blockMinZ = Mth.floor(minZ + COLLISION_EPSILON);
        int blockMaxZ = Mth.floor(maxZ - COLLISION_EPSILON);
        int blockMinY = Mth.floor(minimumY) - 1;
        int blockMaxY = Mth.floor(maximumY);
        for (int y = blockMinY; y <= blockMaxY; y++) {
            for (int z = blockMinZ; z <= blockMaxZ; z++) {
                for (int x = blockMinX; x <= blockMaxX; x++) {
                    CellData cell = snapshot.cell(new BlockPos(x, y, z)).orElse(null);
                    if (cell == null || !cell.hasCollision() || isUnsafeNarrowSupport(cell)) continue;
                    for (AABB local : cell.collisionShape().toAabbs()) {
                        AABB box = local.move(x, y, z);
                        if (!overlaps(minX, maxX, box.minX, box.maxX)
                                || !overlaps(minZ, maxZ, box.minZ, box.maxZ)) continue;
                        double surface = box.maxY;
                        if (surface + COLLISION_EPSILON < minimumY
                                || surface - COLLISION_EPSILON > maximumY) continue;
                        surfaces.add(surface);
                    }
                }
            }
        }

        List<CandidateEvaluation> result = new ArrayList<>();
        for (double surface : surfaces) {
            BlockPos feet = new BlockPos(horizontal.getX(),
                    Mth.floor(surface), horizontal.getZ());
            NavigationNode node = new NavigationNode(feet, surface);
            Evaluation evaluation = evaluate(node, allowBreaking);
            if (evaluation.traversable()) result.add(new CandidateEvaluation(node, evaluation));
        }
        return List.copyOf(result);
    }

    /** Validates the complete player body motion, not just the destination node. */
    public Evaluation evaluateTransition(NavigationNode from, NavigationNode to,
                                         boolean allowBreaking) {
        Evaluation endpoint = evaluate(to, allowBreaking);
        if (!endpoint.traversable()) return endpoint;
        Vec3Like start = Vec3Like.of(from);
        Vec3Like end = Vec3Like.of(to);
        double rise = end.y - start.y;
        Evaluation swept;
        if (rise > COLLISION_EPSILON) {
            Vec3Like raised = new Vec3Like(start.x, end.y, start.z);
            swept = sweep(start, raised, allowBreaking);
            if (swept.traversable()) {
                swept = swept.merge(sweep(raised, end, allowBreaking));
            }
        } else if (rise < -COLLISION_EPSILON) {
            swept = sweep(start, new Vec3Like(end.x, start.y, end.z), allowBreaking);
            if (swept.traversable()) {
                swept = swept.merge(sweep(new Vec3Like(end.x, start.y, end.z), end,
                        allowBreaking));
            }
        } else {
            swept = sweep(start, end, allowBreaking);
        }
        return swept.traversable() ? swept.merge(endpoint) : swept;
    }

    /**
     * True when the node is supported by a slab/stair-like collision shape rather than a
     * complete cube. Some rotated facility stairs expose their next usable surface one full
     * block above the previous node even though the physical route is a normal staircase.
     */
    public boolean hasTieredSupport(NavigationNode node) {
        double standingY = node.standingY();
        int minY = Mth.floor(standingY) - 1;
        int maxY = Mth.floor(standingY + COLLISION_EPSILON);
        for (int y = minY; y <= maxY; y++) {
            int cellY = y;
            CellData cell = snapshot.cell(new BlockPos(
                    node.feetPosition().getX(), cellY, node.feetPosition().getZ())).orElse(null);
            if (cell == null || !cell.hasCollision()) continue;
            List<AABB> boxes = cell.collisionShape().toAabbs();
            boolean supportsNode = boxes.stream().anyMatch(box ->
                    Math.abs(cellY + box.maxY - standingY) <= COLLISION_EPSILON);
            if (!supportsNode) continue;
            if (boxes.size() > 1) return true;
            AABB box = boxes.get(0);
            if (box.minX > COLLISION_EPSILON || box.minY > COLLISION_EPSILON
                    || box.minZ > COLLISION_EPSILON
                    || box.maxX < 1.0D - COLLISION_EPSILON
                    || box.maxY < 1.0D - COLLISION_EPSILON
                    || box.maxZ < 1.0D - COLLISION_EPSILON) return true;
        }
        return false;
    }

    private Evaluation sweep(Vec3Like start, Vec3Like end, boolean allowBreaking) {
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / SWEEP_SAMPLE_DISTANCE));
        Evaluation combined = Evaluation.clear();
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            Evaluation evaluation = evaluateBody(bodyBox(
                    Mth.lerp(progress, start.x, end.x),
                    Mth.lerp(progress, start.y, end.y),
                    Mth.lerp(progress, start.z, end.z)), allowBreaking);
            combined = combined.merge(evaluation);
            if (!combined.traversable()) return combined;
        }
        return combined;
    }

    private Evaluation evaluateBody(AABB body, boolean allowBreaking) {
        Set<BlockPos> doors = new LinkedHashSet<>();
        Set<BlockPos> softObstacles = new LinkedHashSet<>();
        Set<BlockPos> breakBlocks = new LinkedHashSet<>();
        double breakCost = 0.0D;
        int minX = Mth.floor(body.minX + COLLISION_EPSILON);
        int maxX = Mth.floor(body.maxX - COLLISION_EPSILON);
        int minY = Mth.floor(body.minY + COLLISION_EPSILON);
        int maxY = Mth.floor(body.maxY - COLLISION_EPSILON);
        int minZ = Mth.floor(body.minZ + COLLISION_EPSILON);
        int maxZ = Mth.floor(body.maxZ - COLLISION_EPSILON);

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos position = new BlockPos(x, y, z);
                    CellData cell = snapshot.cell(position).orElse(null);
                    if (cell == null) return Evaluation.blocked(Failure.OUTSIDE_SNAPSHOT);
                    if (cell.hazard() == CellData.Hazard.DANGEROUS) {
                        return Evaluation.blocked(Failure.DANGEROUS);
                    }
                    if (!intersects(cell.collisionShape(), body, x, y, z)) continue;
                    if (cell.traversableClosedDoor()) {
                        doors.add(position);
                        continue;
                    }
                    if (allowBreaking && cell.softObstacle()) {
                        softObstacles.add(position);
                        continue;
                    }
                    if (allowBreaking && cell.breakable()) {
                        breakBlocks.add(position);
                        breakCost += Math.max(1.0D, cell.destroySpeed());
                        continue;
                    }
                    return Evaluation.blocked(Failure.SOLID_COLLISION);
                }
            }
        }
        return new Evaluation(true, Failure.NONE, List.copyOf(doors),
                List.copyOf(softObstacles), List.copyOf(breakBlocks), breakCost, false);
    }

    private Support findSupport(NavigationNode node) {
        double centerX = node.feetPosition().getX() + 0.5D;
        double centerZ = node.feetPosition().getZ() + 0.5D;
        double halfWidth = width / 2.0D;
        AABB probe = new AABB(centerX - halfWidth, node.standingY() - SUPPORT_DEPTH,
                centerZ - halfWidth, centerX + halfWidth,
                node.standingY() + COLLISION_EPSILON, centerZ + halfWidth);
        boolean found = false;
        boolean caution = false;
        for (int y = Mth.floor(probe.minY); y <= Mth.floor(probe.maxY); y++) {
            for (int z = Mth.floor(probe.minZ + COLLISION_EPSILON);
                 z <= Mth.floor(probe.maxZ - COLLISION_EPSILON); z++) {
                for (int x = Mth.floor(probe.minX + COLLISION_EPSILON);
                     x <= Mth.floor(probe.maxX - COLLISION_EPSILON); x++) {
                    BlockPos position = new BlockPos(x, y, z);
                    CellData cell = snapshot.cell(position).orElse(null);
                    if (cell == null) return new Support(false, false, Failure.OUTSIDE_SNAPSHOT);
                    if (!intersects(cell.collisionShape(), probe, x, y, z)) continue;
                    if (cell.hazard() == CellData.Hazard.DANGEROUS) {
                        return new Support(false, false, Failure.DANGEROUS);
                    }
                    found = true;
                    caution |= cell.hazard() == CellData.Hazard.CAUTION;
                }
            }
        }
        return new Support(found, caution, found ? Failure.NONE : Failure.NO_SUPPORT);
    }

    private AABB bodyBox(double centerX, double standingY, double centerZ) {
        double halfWidth = width / 2.0D;
        return new AABB(centerX - halfWidth, standingY, centerZ - halfWidth,
                centerX + halfWidth, standingY + height, centerZ + halfWidth);
    }

    private boolean intersects(VoxelShape shape, AABB body, int x, int y, int z) {
        if (collisionBoxes == null) return Shapes.joinIsNotEmpty(Shapes.create(body),
                shape.move(x, y, z), BooleanOp.AND);
        if (shape.isEmpty()) return false;
        List<AABB> boxes = collisionBoxes.computeIfAbsent(shape, VoxelShape::toAabbs);
        for (AABB local : boxes) {
            // Shapes' coordinate merger uses a 1e-7 intersection tolerance.
            // Do not reuse the larger standing-surface epsilon here.
            if (body.maxX > x + local.minX + 1.0E-7D && body.minX < x + local.maxX - 1.0E-7D
                    && body.maxY > y + local.minY + 1.0E-7D && body.minY < y + local.maxY - 1.0E-7D
                    && body.maxZ > z + local.minZ + 1.0E-7D && body.minZ < z + local.maxZ - 1.0E-7D) return true;
        }
        return false;
    }

    private static boolean overlaps(double firstMin, double firstMax,
                                    double secondMin, double secondMax) {
        return firstMax > secondMin + COLLISION_EPSILON
                && secondMax > firstMin + COLLISION_EPSILON;
    }

    private static boolean isUnsafeNarrowSupport(CellData cell) {
        return cell.traversableClosedDoor() || cell.unsafeNarrowSupport();
    }

    public enum Failure { NONE, OUTSIDE_SNAPSHOT, DANGEROUS, SOLID_COLLISION, NO_SUPPORT }

    public record CandidateEvaluation(NavigationNode node, Evaluation evaluation) {
    }

    public record Evaluation(boolean traversable, Failure failure, List<BlockPos> doors,
                             List<BlockPos> softObstacles, List<BlockPos> breakBlocks,
                             double breakCost, boolean caution) {
        private static Evaluation clear() {
            return new Evaluation(true, Failure.NONE, List.of(), List.of(), List.of(), 0.0D, false);
        }

        private static Evaluation blocked(Failure failure) {
            return new Evaluation(false, failure, List.of(), List.of(), List.of(), 0.0D, false);
        }

        private Evaluation withCaution(boolean value) {
            return new Evaluation(traversable, failure, doors, softObstacles,
                    breakBlocks, breakCost, value);
        }

        private Evaluation merge(Evaluation other) {
            if (!traversable) return this;
            if (!other.traversable) return other;
            Set<BlockPos> mergedDoors = new LinkedHashSet<>(doors);
            Set<BlockPos> mergedSoft = new LinkedHashSet<>(softObstacles);
            Set<BlockPos> mergedBreak = new LinkedHashSet<>(breakBlocks);
            mergedDoors.addAll(other.doors);
            mergedSoft.addAll(other.softObstacles);
            mergedBreak.addAll(other.breakBlocks);
            return new Evaluation(true, Failure.NONE, List.copyOf(mergedDoors),
                    List.copyOf(mergedSoft), List.copyOf(mergedBreak),
                    breakCost + other.breakCost, caution || other.caution);
        }
    }

    private record Support(boolean present, boolean caution, Failure failure) {
    }

    private record Vec3Like(double x, double y, double z) {
        private static Vec3Like of(NavigationNode node) {
            return new Vec3Like(node.feetPosition().getX() + 0.5D, node.standingY(),
                    node.feetPosition().getZ() + 0.5D);
        }

        private double distanceTo(Vec3Like other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
