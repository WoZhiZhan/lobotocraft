package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.gametest.*;

@GameTestHolder("lobotocraft_gear_church_terrain")
@PrefixGameTestTemplate(false)
public final class GearChurchTerrainGameTests {
    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_terrain", timeoutTicks = 2400)
    public static void knownLegacyTerrainSiteRetainsItsExactSafePlatformAfterOptimization(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(GearChurchGameTests.NOISE_LEVEL);
        h.assertTrue(level != null && level.getSeed() == 0, "fixture requires the documented seed0 terrain");
        var generator = level.getChunkSource().getGenerator();
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(GearChurchLayout.ID);
        var chunk = new ChunkPos(-5313, -24308);
        var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level,
                structure.biomes()::contains);
        long began = System.nanoTime();
        // Pin the original safe terrain footprint, not the earlier accepted offset in an
        // old-budget search order. Higher cut budgets can intentionally accept earlier offsets.
        var site = GearChurchSite.find(GearChurchStructure.terrain(ctx), -85016,
                -388952, net.minecraft.world.level.block.Rotation.CLOCKWISE_90).orElseThrow();
        h.assertTrue(site.anchor().equals(new net.minecraft.core.BlockPos(-85016, 99, -388952))
                && site.minSurface() == 88 && site.maxSurface() == 106,
                "optimization changed the documented full-column accepted site");
        System.out.println("GEAR-TERRAIN LEGACY-SITE PASS anchor=" + site.anchor() + " heights=88..106 cut=6 fill=12"
                + " selectionMillis=" + (System.nanoTime() - began) / 1_000_000
                + "; old-grid terrain fixture only, NOT a 96/48 natural-generation witness");
        h.succeed();
    }

    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_terrain", timeoutTicks = 2400)
    public static void batchTilesMatchVanillaAcrossEveryColumnOfWaterLandAndNegativeTiles(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(GearChurchGameTests.NOISE_LEVEL);
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(GearChurchLayout.ID);
        int compared = 0, wet = 0;
        for (long seed : new long[]{0, 1, 2}) {
            var random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                    random, level.getStructureManager(), seed, new ChunkPos(0, 0), level, structure.biomes()::contains);
            var terrain = GearChurchStructure.terrain(ctx);
            terrain.prepareDetailedSampling();
            for (var tile : new int[][]{{0, 0}, {-85024, -388960}, {100000, 100000}, {-100000, -100000}}) {
                for (int dx = 0; dx < GearChurchNoiseHeights.TILE_SIZE; dx++)
                    for (int dz = 0; dz < GearChurchNoiseHeights.TILE_SIZE; dz++) {
                    int x = tile[0] + dx, z = tile[1] + dz;
                    int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    h.assertTrue(terrain.surface(x, z) == surface && terrain.oceanFloor(x, z) == floor,
                            "batch differs seed=" + seed + " pos=" + x + "," + z
                                    + " actual=" + terrain.surface(x, z) + "/" + terrain.oceanFloor(x, z)
                                    + " vanilla=" + surface + "/" + floor);
                    terrain.prepareCoarseSampling();
                    h.assertTrue(terrain.surface(x, z) == surface && terrain.oceanFloor(x, z) == floor,
                            "cached tile changed after returning to coarse mode");
                    terrain.prepareDetailedSampling();
                    compared++;
                    if (surface != floor) wet++;
                }
            }
        }
        h.assertTrue(wet > 0 && wet < compared, "batch fixture must cover dry and wet tiles");
        System.out.println("GEAR-TERRAIN BATCH PASS seeds=0,1,2 allColumns=" + compared + " wetColumns=" + wet);
        h.succeed();
    }

    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_terrain", timeoutTicks = 100)
    public static void emptyNoiseRangeRetainsVanillaMinimumHeightInsteadOfCrashing(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(GearChurchGameTests.NOISE_LEVEL);
        var generator = level.getChunkSource().getGenerator();
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(GearChurchLayout.ID);
        for (var accessor : new net.minecraft.world.level.LevelHeightAccessor[]{
                net.minecraft.world.level.LevelHeightAccessor.create(320, 16),
                net.minecraft.world.level.LevelHeightAccessor.create(80, 1),
                net.minecraft.world.level.LevelHeightAccessor.create(80, 10),
                net.minecraft.world.level.LevelHeightAccessor.create(81, 16)}) {
            var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                    new ChunkPos(0, 0), accessor, structure.biomes()::contains);
            var terrain = GearChurchStructure.terrain(ctx);
            h.assertTrue(terrain.surface(0, 0) == generator.getBaseHeight(0, 0,
                    Heightmap.Types.WORLD_SURFACE_WG, accessor, ctx.randomState())
                    && terrain.oceanFloor(0, 0) == generator.getBaseHeight(0, 0,
                    Heightmap.Types.OCEAN_FLOOR_WG, accessor, ctx.randomState()),
                    "empty/clipped noise range changed vanilla minimum-height behavior");
        }
        System.out.println("GEAR-TERRAIN PASS emptyNoiseRange/minimumHeight");
        h.succeed();
    }

    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_terrain", timeoutTicks = 2400)
    public static void pairedProductionHeightsMatchBothVanillaQueriesAcrossThreeSeeds(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(GearChurchGameTests.NOISE_LEVEL);
        h.assertTrue(level != null, "real noise dimension required");
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(GearChurchLayout.ID);
        int[][] anchors = {{0, 0}, {-42552, -47464}, {129112, 9832}, {-85016, -388952},
                {100000, 100000}, {-100000, -100000}};
        int compared = 0, wet = 0;
        for (long seed : new long[]{0, 1, 2}) {
            var random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                    random, level.getStructureManager(), seed, new ChunkPos(0, 0), level, structure.biomes()::contains);
            var terrain = GearChurchStructure.terrain(ctx);
            for (int round = 0; round < 4; round++) for (var anchor : anchors) for (int i = 0; i < 16; i++) {
                int x = anchor[0] + i * 7, z = anchor[1] + round * 11;
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                h.assertTrue(terrain.centerSurface(x, z) == surface, "center fast query changed exact surface height");
                h.assertTrue(terrain.surface(x, z) == surface && terrain.oceanFloor(x, z) == floor,
                        "paired production heights differ at seed=" + seed + " x=" + x + " z=" + z);
                h.assertTrue(terrain.oceanFloor(x, z) == floor && terrain.surface(x, z) == surface,
                        "cached reverse-order reads changed heights");
                compared++;
                if (surface != floor) wet++;
            }
        }
        h.assertTrue(wet > 0 && wet < compared, "differential fixture must cover both dry and wet columns");
        var custom = new NoiseBasedChunkGenerator(generator.getBiomeSource(), generator.generatorSettings()) {
            @Override public int getBaseHeight(int x, int z, Heightmap.Types type,
                    net.minecraft.world.level.LevelHeightAccessor accessor, RandomState random) {
                return type == Heightmap.Types.WORLD_SURFACE_WG ? 151 : 73;
            }
        };
        var customContext = new Structure.GenerationContext(level.registryAccess(), custom, custom.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                new ChunkPos(0, 0), level, structure.biomes()::contains);
        var customTerrain = GearChurchStructure.terrain(customContext);
        h.assertTrue(customTerrain.surface(0, 0) == 151 && customTerrain.oceanFloor(0, 0) == 73,
                "optimized path bypassed custom generator height overrides");
        System.out.println("GEAR-TERRAIN PASS seeds=0,1,2 comparisons=" + compared + " wetColumns=" + wet
                + " customGeneratorOverride=preserved");
        h.succeed();
    }
}
