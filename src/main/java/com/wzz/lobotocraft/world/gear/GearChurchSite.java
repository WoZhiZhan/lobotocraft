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
    public static final int SPACING = 256, SEPARATION = 144, SALT = 871231;
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
        // Only rare coarse-pass candidates pay for the bounded full foundation check.
        // Every authored column (including template air above a non-foundation column)
        // must be dry and fit the cut budget, not just the sparse sample or filled supports.
        int fullMin = min, fullMax = max;
        for (int lx = 0; lx < GearChurchLayout.WIDTH; lx++) for (int lz = 0; lz < GearChurchLayout.DEPTH; lz++) {
            if (GearChurchLayout.roofHeight(lx, lz) < 0) continue;
            BlockPos column = GearChurchLayout.world(new BlockPos(lx, 0, lz),
                    new BlockPos(centerX, 0, centerZ), rotation);
            int h = terrain.surface(column.getX(), column.getZ());
            if (h <= terrain.minY() + MAX_SUPPORT
                    || terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
            fullMin = Math.min(fullMin, h); fullMax = Math.max(fullMax, h);
            if (fullMax - fullMin > MAX_RELIEF || fullMax + GearChurchLayout.HEIGHT >= terrain.maxY()) return Optional.empty();
        }
        // Porch ends in a bottom trapdoor at local Y=2 (top=anchor+2.1875). Untouched
        // exterior surface must be platformSurface+1 or +2: <=1 block step in either
        // direction, and dry. Choose the highest platform in that intersection.
        int exitMin = Integer.MAX_VALUE, exitMax = Integer.MIN_VALUE;
        for (var exit : GearChurchLayout.ENTRANCE_EXITS) {
            var column = GearChurchLayout.world(exit, new BlockPos(centerX, 0, centerZ), rotation);
            int h = terrain.surface(column.getX(), column.getZ());
            if (terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
            exitMin = Math.min(exitMin, h); exitMax = Math.max(exitMax, h);
        }
        int platformSurface = Math.min(Math.min(fullMax, fullMin + MAX_SUPPORT), exitMin - 1);
        if (platformSurface < Math.max(fullMax - MAX_CUT, exitMax - 2)) return Optional.empty();
        return Optional.of(new Site(new BlockPos(centerX, platformSurface - 1, centerZ), rotation, fullMin, fullMax, samples));
    }
    public record Site(BlockPos anchor, Rotation rotation, int minSurface, int maxSurface, int samples) {}
}
