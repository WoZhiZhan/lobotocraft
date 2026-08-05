package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Center-first point sampling shared by monitor movement and deterministic tests. */
public final class FacilityMoveTargetSampler {

    private FacilityMoveTargetSampler() {
    }

    public static Optional<BlockPos> sample(List<FacilityWorldLayout.AreaDefinition> sourceAreas,
                                            RandomSource random, int attempts,
                                            Predicate<BlockPos> acceptable) {
        if (sourceAreas == null || sourceAreas.isEmpty() || random == null
                || attempts <= 0 || acceptable == null) return Optional.empty();

        SamplingPool pool = prepare(sourceAreas, random);
        if (pool.areas().isEmpty() || pool.totalWeight() <= 0) return Optional.empty();

        for (int attempt = 0; attempt < attempts; attempt++) {
            WeightedArea selected = choose(pool.areas(), random.nextInt(pool.totalWeight()));
            Column column = selected.nextColumn();
            if (column == null) continue;
            for (int y = selected.area.minPos().getY(); y <= selected.area.maxPos().getY(); y++) {
                BlockPos candidate = new BlockPos(column.x, y, column.z);
                if (acceptable.test(candidate)) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns several standable alternatives, including different surfaces in the same column.
     * Facility room cuboids can contain decorative lower layers, so movement orders must not
     * permanently commit to the first geometrically standable Y coordinate.
     */
    public static List<BlockPos> sampleCandidates(
            List<FacilityWorldLayout.AreaDefinition> sourceAreas, RandomSource random,
            int columnAttempts, int maxResults, Predicate<BlockPos> acceptable) {
        if (sourceAreas == null || sourceAreas.isEmpty() || random == null
                || columnAttempts <= 0 || maxResults <= 0 || acceptable == null) return List.of();
        SamplingPool pool = prepare(sourceAreas, random);
        if (pool.areas().isEmpty() || pool.totalWeight() <= 0) return List.of();

        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        int attempts = 0;
        WeightedArea primary = pool.areas().stream()
                .max(java.util.Comparator.comparingInt(area -> area.weight))
                .orElseThrow();
        int primaryIndex = pool.areas().indexOf(primary);
        for (int index = 0; index < pool.areas().size()
                && attempts < columnAttempts && result.size() < maxResults; index++) {
            WeightedArea selected = pool.areas().get((primaryIndex + index) % pool.areas().size());
            Column column = selected.nextColumn();
            attempts++;
            addColumnCandidates(result, selected, column, maxResults, acceptable,
                    pool.areas().size() > 1);
        }
        while (attempts < columnAttempts && result.size() < maxResults) {
            WeightedArea selected = choose(pool.areas(), random.nextInt(pool.totalWeight()));
            Column column = selected.nextColumn();
            attempts++;
            addColumnCandidates(result, selected, column, maxResults, acceptable, false);
        }
        return List.copyOf(result);
    }

    private static void addColumnCandidates(Set<BlockPos> result, WeightedArea selected,
                                            Column column, int maxResults,
                                            Predicate<BlockPos> acceptable,
                                            boolean stopAfterFirst) {
        if (column == null) return;
        int minY = selected.area.minPos().getY();
        int maxY = selected.area.maxPos().getY();
        int centerY = minY + (maxY - minY) / 2;
        for (int offset = 0; offset <= maxY - minY && result.size() < maxResults; offset++) {
            int y = centerOut(centerY, offset);
            if (y < minY || y > maxY) continue;
            BlockPos candidate = new BlockPos(column.x, y, column.z);
            if (acceptable.test(candidate) && result.add(candidate) && stopAfterFirst) return;
        }
    }

    private static SamplingPool prepare(List<FacilityWorldLayout.AreaDefinition> sourceAreas,
                                        RandomSource random) {
        List<WeightedArea> areas = new ArrayList<>();
        int totalWeight = 0;
        for (FacilityWorldLayout.AreaDefinition area : sourceAreas) {
            if (area == null) continue;
            int remainingWeight = Integer.MAX_VALUE - totalWeight;
            if (remainingWeight <= 0) break;
            int minX = insetMin(area.minPos().getX(), area.maxPos().getX());
            int maxX = insetMax(area.minPos().getX(), area.maxPos().getX());
            int minZ = insetMin(area.minPos().getZ(), area.maxPos().getZ());
            int maxZ = insetMax(area.minPos().getZ(), area.maxPos().getZ());
            long width = (long) maxX - minX + 1L;
            long depth = (long) maxZ - minZ + 1L;
            int weight = (int) Math.min(remainingWeight, Math.max(1L, width * depth));
            areas.add(new WeightedArea(area, minX, maxX, minZ, maxZ, weight, random));
            totalWeight += weight;
            if (totalWeight == Integer.MAX_VALUE) break;
        }
        return new SamplingPool(List.copyOf(areas), totalWeight);
    }

    private static int centerOut(int center, int index) {
        if (index == 0) return center;
        int distance = (index + 1) / 2;
        return (index & 1) == 1 ? center - distance : center + distance;
    }

    private static WeightedArea choose(List<WeightedArea> areas, int ticket) {
        int offset = ticket;
        for (WeightedArea area : areas) {
            if (offset < area.weight) return area;
            offset -= area.weight;
        }
        return areas.get(areas.size() - 1);
    }

    private static int insetMin(int min, int max) {
        return min + inset(max - min + 1);
    }

    private static int insetMax(int min, int max) {
        return max - inset(max - min + 1);
    }

    private static int inset(int extent) {
        if (extent >= 5) return 2;
        if (extent >= 3) return 1;
        return 0;
    }

    private static int centeredCoordinate(int min, int max, RandomSource random) {
        int lowerCenter = min + (max - min) / 2;
        return ((max - min + 1) & 1) == 0 && random.nextBoolean()
                ? lowerCenter + 1 : lowerCenter;
    }

    private static Column spiralOffset(int index) {
        if (index == 0) return new Column(0, 0);
        int remaining = index - 1;
        int ring = 1;
        while (remaining >= 8 * ring) {
            remaining -= 8 * ring;
            ring++;
        }

        int topLength = 2 * ring + 1;
        if (remaining < topLength) return new Column(-ring + remaining, -ring);
        remaining -= topLength;
        if (remaining < 2 * ring) return new Column(ring, -ring + 1 + remaining);
        remaining -= 2 * ring;
        if (remaining < 2 * ring) return new Column(ring - 1 - remaining, ring);
        remaining -= 2 * ring;
        return new Column(-ring, ring - 1 - remaining);
    }

    private static Column transform(Column offset, int rotation, boolean mirror) {
        int x = mirror ? -offset.x : offset.x;
        int z = offset.z;
        return switch (rotation) {
            case 1 -> new Column(-z, x);
            case 2 -> new Column(-x, -z);
            case 3 -> new Column(z, -x);
            default -> new Column(x, z);
        };
    }

    private static final class WeightedArea {
        private final FacilityWorldLayout.AreaDefinition area;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int weight;
        private final int centerX;
        private final int centerZ;
        private final int rotation;
        private final boolean mirror;
        private final long rawCandidateCount;
        private int spiralIndex;

        private WeightedArea(FacilityWorldLayout.AreaDefinition area,
                             int minX, int maxX, int minZ, int maxZ, int weight,
                             RandomSource random) {
            this.area = area;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.weight = weight;
            this.centerX = centeredCoordinate(minX, maxX, random);
            this.centerZ = centeredCoordinate(minZ, maxZ, random);
            this.rotation = random.nextInt(4);
            this.mirror = random.nextBoolean();
            int radius = Math.max(
                    Math.max(centerX - minX, maxX - centerX),
                    Math.max(centerZ - minZ, maxZ - centerZ));
            long diameter = radius * 2L + 1L;
            this.rawCandidateCount = diameter * diameter;
        }

        private Column nextColumn() {
            while (spiralIndex < rawCandidateCount) {
                Column offset = transform(spiralOffset(spiralIndex++), rotation, mirror);
                int x = centerX + offset.x;
                int z = centerZ + offset.z;
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    return new Column(x, z);
                }
            }
            return null;
        }
    }

    private record Column(int x, int z) {
    }

    private record SamplingPool(List<WeightedArea> areas, int totalWeight) {
    }
}
