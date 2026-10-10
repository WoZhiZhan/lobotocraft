package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import java.util.Optional;

/** Fixed, bounded terrain probe. Rejects unsuitable sites rather than terraforming an entire mesa. */
public final class GearChurchSite {
    public static final int MAX_RELIEF = 8, MAX_SUPPORT = 12, SAMPLE_STEP = 16;
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
        for (int radius : new int[]{0, 16, 32}) {
            int step = radius == 0 ? 1 : 16;
            for (int dx = -radius; dx <= radius; dx += step) for (int dz = -radius; dz <= radius; dz += step) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                var site = find(terrain, centerX + dx, centerZ + dz, rotation);
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
        // Every actual column must be dry and fit the same eight-block relief/support budget.
        int fullMin = min, fullMax = max;
        for (int lx = 0; lx < GearChurchLayout.WIDTH; lx++) for (int lz = 0; lz < GearChurchLayout.DEPTH; lz++) {
            if (!GearChurchLayout.foundation(lx, lz)) continue;
            BlockPos column = GearChurchLayout.world(new BlockPos(lx, 0, lz),
                    new BlockPos(centerX, 0, centerZ), rotation);
            int h = terrain.surface(column.getX(), column.getZ());
            if (terrain.oceanFloor(column.getX(), column.getZ()) != h) return Optional.empty();
            fullMin = Math.min(fullMin, h); fullMax = Math.max(fullMax, h);
            if (fullMax - fullMin > MAX_RELIEF || fullMax + GearChurchLayout.HEIGHT >= terrain.maxY()) return Optional.empty();
        }
        return Optional.of(new Site(new BlockPos(centerX, fullMax - 1, centerZ), rotation, fullMin, fullMax, samples));
    }
    public record Site(BlockPos anchor, Rotation rotation, int minSurface, int maxSurface, int samples) {}
}
