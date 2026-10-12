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
        int halfWidth = Integer.getInteger("lobotocraft.gearChurchSurveyHalfWidth", 100_000);
        boolean exhaustive = Boolean.getBoolean("lobotocraft.gearChurchSurveyExhaustive");
        boolean currentOnly = Boolean.getBoolean("lobotocraft.gearChurchSurveyCurrentOnly");
        h.assertTrue(halfWidth > 0 && halfWidth <= 100_000, "survey area must remain bounded");
        List<Survey> surveys = new ArrayList<>();
        for (long seed : SEEDS) {
            // Real vanilla router/biome sampler is recreated for each seed; no biome fixture.
            var random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var state = generator.createState(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                    random, seed);
            if (!currentOnly) surveys.add(new Survey(level, structure, generator, random, state, seed, previous, halfWidth, exhaustive));
            surveys.add(new Survey(level, structure, generator, random, state, seed, current, halfWidth, exhaustive));
        }
        int[] cursor = {0};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            var survey = surveys.get(cursor[0]);
            // One attempt can still exceed the tick budget. Measure it, do not obscure it
            // by bundling twelve expensive attempts into one tick during acceptance.
            for (int budget = 0; budget < (exhaustive ? 1 : 12) && survey.index < survey.candidates.size(); budget++)
                survey.inspect(survey.candidates.get(survey.index++));
            if (survey.index == survey.candidates.size()) {
                survey.report();
                cursor[0]++;
            }
            if (cursor[0] < surveys.size()) h.runAfterDelay(1, () -> step[0].run());
            else {
                h.assertTrue(surveys.stream().allMatch(s -> s.index == s.candidates.size()),
                        "sampling stopped at first success");
                System.out.println("GEAR-DISTRIBUTION COMPLETE seeds=0,1,2 halfWidth=" + halfWidth
                        + " exhaustiveGrid=" + exhaustive + " currentOnly=" + currentOnly
                        + (exhaustive ? "; every production grid candidate/exclusion/terrain tested"
                            : "; centerY80-prefiltered-lower-bound-only; not exhaustive production acceptance")
                        + "; no actual StructureStart/resident validation in this survey");
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
        final int halfWidth;
        final boolean exhaustive;
        final List<ChunkPos> candidates = new ArrayList<>();
        final List<Double> timings = new ArrayList<>();
        final List<Double> acceptedTimings = new ArrayList<>();
        int excluded, overSecond, overTick;
        int index, badlands, eligible;
        double nearest = Double.POSITIVE_INFINITY, maxMillis, totalMillis;
        ChunkPos nearestChunk;
        Survey(ServerLevel level, GearChurchStructure structure, NoiseBasedChunkGenerator generator,
                RandomState random, ChunkGeneratorStructureState state, long seed,
                RandomSpreadStructurePlacement placement, int halfWidth, boolean exhaustive) {
            this.level = level; this.structure = structure; this.generator = generator;
            this.random = random; this.structureState = state; this.seed = seed; this.placement = placement;
            this.halfWidth = halfWidth; this.exhaustive = exhaustive;
            int radius = (int) Math.ceil((double) halfWidth / (placement.spacing() * 16)) + 1;
            for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
                var chunk = placement.getPotentialStructureChunk(seed, cx * placement.spacing(), cz * placement.spacing());
                if (Math.abs(chunk.getMiddleBlockX()) <= halfWidth && Math.abs(chunk.getMiddleBlockZ()) <= halfWidth)
                    candidates.add(chunk);
            }
        }
        void inspect(ChunkPos chunk) {
            int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
            if (!exhaustive && !generator.getBiomeSource().getNoiseBiome(x >> 2, 80 >> 2, z >> 2, random.sampler())
                    .is(GearChurchStructure.BIOMES)) return;
            if (!placement.isStructureChunk(structureState, chunk.x, chunk.z)) { excluded++; return; }
            badlands++;
            var ctx = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                    random, level.getStructureManager(), seed, chunk, level, structure.biomes()::contains);
            long start = System.nanoTime();
            var site = structure.findValidGenerationPoint(ctx);
            double millis = (System.nanoTime() - start) / 1_000_000.0;
            maxMillis = Math.max(maxMillis, millis); totalMillis += millis;
            timings.add(millis);
            if (millis > 1000) overSecond++;
            if (millis > 50) overTick++;
            if (site.isPresent()) {
                eligible++;
                acceptedTimings.add(millis);
                var anchor = site.get().position();
                double distance = Math.hypot(anchor.getX(), anchor.getZ());
                if (distance < nearest) { nearest = distance; nearestChunk = chunk; }
                System.out.println("GEAR-DISTRIBUTION WITNESS seed=" + seed + " spacing=" + placement.spacing()
                        + " chunk=" + chunk + " distance=" + Math.round(distance) + " selectionMillis=" + Math.round(millis));
            }
            if (badlands % 10 == 0)
                System.out.println("GEAR-DISTRIBUTION PROGRESS seed=" + seed + " spacing=" + placement.spacing()
                        + " scanned=" + index + "/" + candidates.size() + " badlands=" + badlands + " eligible=" + eligible);
        }
        void report() {
            timings.sort(Double::compareTo); acceptedTimings.sort(Double::compareTo);
            System.out.println("GEAR-DISTRIBUTION RESULT seed=" + seed + " spacing=" + placement.spacing()
                    + " separation=" + placement.separation() + " candidates=" + candidates.size()
                    + " halfWidth=" + halfWidth + " exhaustiveGrid=" + exhaustive
                    + " selectedExclusionPass=" + badlands + " excluded=" + excluded + " eligible=" + eligible
                    + " nearestDistance=" + (nearestChunk == null ? "NONE" : Math.round(nearest))
                    + " nearestChunk=" + nearestChunk + " maxSelectionMillis=" + Math.round(maxMillis)
                    + " totalSelectionMillis=" + Math.round(totalMillis)
                    + " p50Millis=" + percentile(timings, .5) + " p95Millis=" + percentile(timings, .95)
                    + " p99Millis=" + percentile(timings, .99) + " over50Millis=" + overTick
                    + " over1000Millis=" + overSecond + " acceptedP50Millis=" + percentile(acceptedTimings, .5)
                    + " acceptedMaxMillis=" + percentile(acceptedTimings, 1)
                    + " selectionPerformance=" + (overSecond > 0 ? "FAIL_1S_GUARD" : "NO_1S_EXCEEDANCE_NOT_50MS_ACCEPTANCE"));
        }
        private long percentile(List<Double> values, double fraction) {
            return values.isEmpty() ? -1 : Math.round(values.get(Math.max(0, (int)Math.ceil(values.size()*fraction)-1)));
        }
    }
}
