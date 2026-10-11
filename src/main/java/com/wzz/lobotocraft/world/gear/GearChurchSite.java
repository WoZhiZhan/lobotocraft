package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;

/** Bounded terrace probe. Only a small cut/fill budget, never terraforms an entire mesa. */
public final class GearChurchSite {
    public static final int MAX_CUT = 6, MAX_SUPPORT = 12, MAX_RELIEF = MAX_CUT + MAX_SUPPORT, SAMPLE_STEP = 16;
    public static final int SPACING = 96, SEPARATION = 48, SALT = 871231;
    private GearChurchSite() {}
    public interface Terrain {
        int surface(int x, int z);
        int oceanFloor(int x, int z);
        boolean badlands(int x, int y, int z);
        int minY();
        int maxY();
    }
    public static Optional<Site> findNear(Terrain terrain, int centerX, int centerZ, Rotation rotation) {
        // Neighboring probes overlap heavily. Keep exact heights, but do not recompute the same
        // noise column up to 25 times; this cache lives only for this bounded selection attempt.
        Map<Long, Integer> surfaces = new HashMap<>(), floors = new HashMap<>();
        Terrain memo = new Terrain() {
            public int surface(int x, int z) {
                return surfaces.computeIfAbsent(ChunkPos.asLong(x, z), key -> terrain.surface(x, z));
            }
            public int oceanFloor(int x, int z) {
                return floors.computeIfAbsent(ChunkPos.asLong(x, z), key -> terrain.oceanFloor(x, z));
            }
            public boolean badlands(int x, int y, int z) { return terrain.badlands(x, y, z); }
            public int minY() { return terrain.minY(); }
            public int maxY() { return terrain.maxY(); }
        };
        for (int radius : new int[]{0, 16, 32}) {
            int step = radius == 0 ? 1 : 16;
            for (int dx = -radius; dx <= radius; dx += step) for (int dz = -radius; dz <= radius; dz += step) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                var site = find(memo, centerX + dx, centerZ + dz, rotation);
                if (site.isPresent()) return site;
            }
        }
        return Optional.empty();
    }
    public static Optional<Site> find(Terrain terrain, int centerX, int centerZ, Rotation rotation) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, samples = 0, biomes = 0;
        for (int lx = 0; lx < GearChurchLayout.WIDTH; lx += SAMPLE_STEP) {
            for (int lz = 0; lz < GearChurchLayout.DEPTH; lz += SAMPLE_STEP) {
                if (!GearChurchLayout.foundation(lx, lz)) continue;
                BlockPos column = GearChurchLayout.world(new BlockPos(lx, 0, lz),
                        new BlockPos(centerX, 0, centerZ), rotation);
                int h = terrain.surface(column.getX(), column.getZ());
                if (h <= terrain.minY() + MAX_SUPPORT || h + GearChurchLayout.HEIGHT >= terrain.maxY()
                        || terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
                min = Math.min(min, h); max = Math.max(max, h);
                if (max - min > MAX_RELIEF) return Optional.empty();
                samples++;
                if (terrain.badlands(column.getX(), h - 1, column.getZ())) biomes++;
            }
        }
        if (samples == 0 || biomes * 5 < samples * 4 || !terrain.badlands(centerX, max - 1, centerZ)) return Optional.empty();
        // Porch ends in a bottom trapdoor at local Y=2 (top=anchor+2.1875). Untouched
        // exterior surface must be platformSurface+1 or +2: <=1 block step in either
        // direction, and dry. Check this cheap constraint BEFORE the full noise scan.
        int exitMin = Integer.MAX_VALUE, exitMax = Integer.MIN_VALUE;
        for (var exit : GearChurchLayout.ENTRANCE_EXITS) {
            var column = GearChurchLayout.world(exit, new BlockPos(centerX, 0, centerZ), rotation);
            int h = terrain.surface(column.getX(), column.getZ());
            if (terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
            exitMin = Math.min(exitMin, h); exitMax = Math.max(exitMax, h);
        }
        if (!fitsPlatformRange(min, max, exitMin, exitMax)) return Optional.empty();
        // Every authored column must still be dry and fit the budget. Refine the grid
        // 8->4->2->1 to discover interior ridges/gullies early, not after scanning one
        // entire edge first. Each authored column appears once across these passes.
        int fullMin = min, fullMax = max;
        for (int step : new int[]{8, 4, 2, 1}) {
            for (int lx = 0; lx < GearChurchLayout.WIDTH; lx += step)
                for (int lz = 0; lz < GearChurchLayout.DEPTH; lz += step) {
                    if (step < 8 && lx % (step * 2) == 0 && lz % (step * 2) == 0) continue;
                    if (GearChurchLayout.roofHeight(lx, lz) < 0) continue;
                    var column = GearChurchLayout.world(new BlockPos(lx, 0, lz),
                            new BlockPos(centerX, 0, centerZ), rotation);
                    int h = terrain.surface(column.getX(), column.getZ());
                    if (h <= terrain.minY() + MAX_SUPPORT
                            || terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
                    fullMin = Math.min(fullMin, h); fullMax = Math.max(fullMax, h);
                    // This intersection can only shrink as further extremes are found.
                    if (!fitsPlatformRange(fullMin, fullMax, exitMin, exitMax)
                            || fullMax + GearChurchLayout.HEIGHT >= terrain.maxY()) return Optional.empty();
                }
        }
        // Prefer not to raise above the original highest surface unless a higher natural
        // porch exit needs it; even then, the same twelve-block fill budget applies.
        int platformSurface = Math.min(Math.max(fullMax, exitMax - 2), Math.min(fullMin + MAX_SUPPORT, exitMin - 1));
        if (platformSurface + GearChurchLayout.HEIGHT >= terrain.maxY()) return Optional.empty();
        return Optional.of(new Site(new BlockPos(centerX, platformSurface - 1, centerZ), rotation, fullMin, fullMax, samples));
    }
    private static boolean fitsPlatformRange(int min, int max, int exitMin, int exitMax) {
        return Math.max(max - MAX_CUT, exitMax - 2) <= Math.min(min + MAX_SUPPORT, exitMin - 1);
    }
    public record Site(BlockPos anchor, Rotation rotation, int minSurface, int maxSurface, int samples) {}
}
