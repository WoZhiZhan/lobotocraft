package com.wzz.lobotocraft.world.gear;

import com.wzz.lobotocraft.mixin.NoiseGeneratorFluidPickerAccessor;
import java.util.Arrays;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact height tile using vanilla interpolation. No chunk loading or world mutation. */
final class GearChurchNoiseHeights {
    // Vanilla aquifer caches are sized for a chunk; larger tiles are not supported.
    static final int TILE_SIZE = 16;
    private GearChurchNoiseHeights() {}

    static int[] sample(NoiseBasedChunkGenerator generator, LevelHeightAccessor accessor,
            RandomState random, int tileX, int tileZ) {
        var settings = generator.generatorSettings().value();
        var range = settings.noiseSettings().clampToHeightAccessor(accessor);
        int width = range.getCellWidth(), height = range.getCellHeight();
        int area = TILE_SIZE * TILE_SIZE;
        int[] heights = new int[area * 2]; // surface then ocean-floor planes
        Arrays.fill(heights, accessor.getMinBuildHeight());
        if (range.height() / height <= 0) return heights;
        var picker = ((NoiseGeneratorFluidPickerAccessor) generator).lobotocraft$getFluidPicker().get();
        var noise = new HeightNoiseChunk(TILE_SIZE / width, random, tileX, tileZ, range, settings, picker);
        boolean[] floorsFound = new boolean[area];
        noise.initializeForFirstCellX();
        try {
            for (int cellX = 0; cellX < TILE_SIZE / width; cellX++) {
                noise.advanceCellX(cellX);
                for (int cellZ = 0; cellZ < TILE_SIZE / width; cellZ++) {
                    int remaining = width * width;
                    for (int cellY = range.height() / height - 1; cellY >= 0 && remaining > 0; cellY--) {
                        noise.selectCellYZ(cellY, cellZ);
                        for (int dy = height - 1; dy >= 0 && remaining > 0; dy--) {
                            int y = range.minY() + cellY * height + dy;
                            noise.updateForY(y, (double) dy / height);
                            for (int dx = 0; dx < width; dx++) {
                                int x = tileX + cellX * width + dx;
                                noise.updateForX(x, (double) dx / width);
                                for (int dz = 0; dz < width; dz++) {
                                    int index = (cellZ * width + dz) * TILE_SIZE + cellX * width + dx;
                                    if (floorsFound[index]) continue;
                                    noise.updateForZ(tileZ + cellZ * width + dz, (double) dz / width);
                                    var state = noise.state();
                                    if (state == null) state = settings.defaultBlock();
                                    if (heights[index] == accessor.getMinBuildHeight()
                                            && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state))
                                        heights[index] = y + 1;
                                    if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
                                        heights[index + area] = y + 1;
                                        floorsFound[index] = true;
                                        remaining--;
                                    }
                                }
                            }
                        }
                    }
                }
                noise.swapSlices();
            }
        } finally {
            noise.stopInterpolation();
        }
        return heights;
    }

    private static final class HeightNoiseChunk extends NoiseChunk {
        HeightNoiseChunk(int cellCount, RandomState random, int x, int z, NoiseSettings range,
                NoiseGeneratorSettings settings, Aquifer.FluidPicker picker) {
            // Same beardifier marker / empty blender as vanilla getBaseHeight/getBaseColumn,
            // not the actual world's structure beardifier or post-carver terrain.
            super(cellCount, random, x, z, range, DensityFunctions.BeardifierMarker.INSTANCE,
                    settings, picker, Blender.empty());
        }
        BlockState state() { return getInterpolatedState(); }
    }
}
