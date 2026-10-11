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
