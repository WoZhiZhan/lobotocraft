package com.wzz.lobotocraft.world.gear;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraftforge.gametest.*;

/** Bounded multi-seed distribution survey, not a substitute for actual natural placement. */
@GameTestHolder("lobotocraft_gear_church_distribution")
@PrefixGameTestTemplate(false)
public final class GearChurchDistributionGameTests {
    private static final int HALF_WIDTH = 100_000;
    private static final long[] SEEDS = {0, 1, 2};

    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_distribution", timeoutTicks = 12000)
    public static void compareTrialAndPreviousPlacementInSameAreaAcrossThreeSeeds(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(GearChurchGameTests.NOISE_LEVEL);
        h.assertTrue(level != null, "Real vanilla noise dimension is required");
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(GearChurchLayout.ID);
        var current = (RandomSpreadStructurePlacement) level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(GearChurchLayout.ID).placement();
        h.assertTrue(current.spacing() == 96 && current.separation() == 48, "production trial did not load");
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var oldJson = StructurePlacement.CODEC.encodeStart(ops, current).result().orElseThrow().getAsJsonObject();
        oldJson.addProperty("spacing", 256);
        oldJson.addProperty("separation", 144);
        var previous = (RandomSpreadStructurePlacement) StructurePlacement.CODEC.parse(ops, oldJson)
                .result().orElseThrow();
        List<Survey> surveys = new ArrayList<>();
        for (long seed : SEEDS) {
            // Real vanilla router/biome sampler is recreated for each seed; no biome fixture.
            var random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var state = generator.createState(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                    random, seed);
            surveys.add(new Survey(level, structure, generator, random, state, seed, previous));
            surveys.add(new Survey(level, structure, generator, random, state, seed, current));
        }
        int[] cursor = {0};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            var survey = surveys.get(cursor[0]);
            for (int budget = 0; budget < 12 && survey.index < survey.candidates.size(); budget++)
                survey.inspect(survey.candidates.get(survey.index++));
            if (survey.index == survey.candidates.size()) {
                survey.report();
                cursor[0]++;
            }
            if (cursor[0] < surveys.size()) h.runAfterDelay(1, () -> step[0].run());
            else {
                h.assertTrue(surveys.stream().allMatch(s -> s.index == s.candidates.size()),
                        "sampling stopped at first success");
                System.out.println("GEAR-DISTRIBUTION COMPLETE seeds=0,1,2 square=[-100000,100000]^2"
                        + " noise-policy-survey-only; no actual StructureStart/resident validation in this survey");
                h.succeed();
            }
        };
        step[0].run();
    }

    private static final class Survey {
        final ServerLevel level;
        final GearChurchStructure structure;
        final NoiseBasedChunkGenerator generator;
        final RandomState random;
        final ChunkGeneratorStructureState structureState;
        final long seed;
        final RandomSpreadStructurePlacement placement;
        final List<ChunkPos> candidates = new ArrayList<>();
        final long started = System.nanoTime();
        int index, badlands, eligible;
        double nearest = Double.POSITIVE_INFINITY, maxMillis, totalMillis;
        ChunkPos nearestChunk;
        Survey(ServerLevel level, GearChurchStructure structure, NoiseBasedChunkGenerator generator,
                RandomState random, ChunkGeneratorStructureState state, long seed,
                RandomSpreadStructurePlacement placement) {
            this.level = level; this.structure = structure; this.generator = generator;
            this.random = random; this.structureState = state; this.seed = seed; this.placement = placement;
            int radius = (int) Math.ceil((double) HALF_WIDTH / (placement.spacing() * 16)) + 1;
            for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
                var chunk = placement.getPotentialStructureChunk(seed, cx * placement.spacing(), cz * placement.spacing());
                if (Math.abs(chunk.getMiddleBlockX()) <= HALF_WIDTH && Math.abs(chunk.getMiddleBlockZ()) <= HALF_WIDTH)
                    candidates.add(chunk);
            }
        }
        void inspect(ChunkPos chunk) {
            int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
            if (!generator.getBiomeSource().getNoiseBiome(x >> 2, 80 >> 2, z >> 2, random.sampler())
                    .is(GearChurchStructure.BIOMES)
                    || !placement.isStructureChunk(structureState, chunk.x, chunk.z)) return;
            badlands++;
            var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                    random, level.getStructureManager(), seed, chunk, level, structure.biomes()::contains);
            long start = System.nanoTime();
            var site = structure.findValidGenerationPoint(ctx);
            double millis = (System.nanoTime() - start) / 1_000_000.0;
            maxMillis = Math.max(maxMillis, millis); totalMillis += millis;
            if (site.isPresent()) {
                eligible++;
                double distance = Math.hypot(x, z);
                if (distance < nearest) { nearest = distance; nearestChunk = chunk; }
                System.out.println("GEAR-DISTRIBUTION WITNESS seed=" + seed + " spacing=" + placement.spacing()
                        + " chunk=" + chunk + " distance=" + Math.round(distance) + " selectionMillis=" + Math.round(millis));
            }
            if (badlands % 10 == 0)
                System.out.println("GEAR-DISTRIBUTION PROGRESS seed=" + seed + " spacing=" + placement.spacing()
                        + " scanned=" + index + "/" + candidates.size() + " badlands=" + badlands + " eligible=" + eligible);
        }
        void report() {
            System.out.println("GEAR-DISTRIBUTION RESULT seed=" + seed + " spacing=" + placement.spacing()
                    + " separation=" + placement.separation() + " candidates=" + candidates.size()
                    + " badlandsExclusionPass=" + badlands + " eligible=" + eligible
                    + " nearestDistance=" + (nearestChunk == null ? "NONE" : Math.round(nearest))
                    + " nearestChunk=" + nearestChunk + " maxSelectionMillis=" + Math.round(maxMillis)
                    + " totalSelectionMillis=" + Math.round(totalMillis));
        }
    }
}
