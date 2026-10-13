package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.*;
import com.wzz.lobotocraft.entity.gear.EntityGearFollower;
import java.util.*;

@GameTestHolder("lobotocraft_gear_church")
@PrefixGameTestTemplate(false)
public final class GearChurchGameTests {
    private static final String T = "workcloseregressiongametests.navigation_empty";
    public static final ResourceKey<Level> NOISE_LEVEL = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation("lobotocraft", "gear_church_test"));

    @GameTest(template = T, templateNamespace = "lobotocraft_gear_church", timeoutTicks = 12000,
            batch = "gear_church_real_badlands")
    public static void naturalCandidateFitsRealBadlandsAndCreatesSixteenResidentsExactlyOnce(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(NOISE_LEVEL);
        h.assertTrue(level != null, "Real noise test dimension missing; use tools/gametest-data, not a flat fixture");
        h.assertTrue(level.getSeed() == 0, "Use tools/gear-church-gametest.properties for reproducible seed=0 terrain");
        var structure = (GearChurchStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(GearChurchLayout.ID);
        var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(GearChurchLayout.ID);
        h.assertTrue(structure != null && set != null, "production church registry data did not load");
        var placement = (RandomSpreadStructurePlacement) set.placement();
        h.assertTrue(placement.spacing() == GearChurchSite.SPACING && placement.separation() == GearChurchSite.SEPARATION,
                "production placement differs from tested spacing");
        var encodedPlacement = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                .encodeStart(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                        level.registryAccess()), placement).result().orElseThrow().getAsJsonObject();
        h.assertTrue(!encodedPlacement.has("exclusion_zone"),
                "user removed other-structure exclusion; production registry must not retain it");
        var state = level.getChunkSource().getGenerator().createState(
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                level.getChunkSource().randomState(), level.getSeed());
        // A seed0 church candidate previously rejected solely by vanilla village placement.
        // Check the decoded placement decision, not only the packaged JSON.
        h.assertTrue(placement.isStructureChunk(state, -6222, 3745),
                "former village-veto candidate must now reach terrain selection");
        var candidates = new ArrayList<ChunkPos>();
        // Seeded production candidate grid, spiral ordering; no manual convenient world coordinates.
        for (int radius = 0; radius <= 160; radius++) {
            for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
                if (radius != 0 && Math.abs(cx) != radius && Math.abs(cz) != radius) continue;
                candidates.add(placement.getPotentialStructureChunk(level.getSeed(),
                        cx * placement.spacing(), cz * placement.spacing()));
            }
        }
        String witness = System.getProperty("lobotocraft.gearChurchWitnessChunk", "");
        if (!witness.isBlank()) {
            String[] fields = witness.split(",");
            h.assertTrue(fields.length == 2, "invalid replay witness chunk");
            var chunk = new ChunkPos(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
            h.assertTrue(candidates.remove(chunk) && placement.isStructureChunk(
                    level.getChunkSource().getGenerator().createState(
                            level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                            level.getChunkSource().randomState(), level.getSeed()), chunk.x, chunk.z),
                    "replay witness must belong to the same seed0 production grid and exclusion rules");
            candidates.add(0, chunk);
            System.out.println("GEAR-CHURCH REPLAY natural-grid witness=" + chunk
                    + "; all production terrain, exclusion and natural generation checks retained");
        }
        new Search(h, level, structure, placement, candidates).step();
    }

    private static Structure.GenerationContext context(ServerLevel level, GearChurchStructure structure, ChunkPos chunk) {
        var generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level,
                structure.biomes()::contains);
    }

    private static final class Search {
        final GameTestHelper h;
        final ServerLevel level;
        final GearChurchStructure structure;
        final RandomSpreadStructurePlacement placement;
        final List<ChunkPos> candidates;
        final net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState;
        int index, badlands, bestRelief = Integer.MAX_VALUE, bestDryBadlandsRelief = Integer.MAX_VALUE;
        Search(GameTestHelper h, ServerLevel level, GearChurchStructure structure,
               RandomSpreadStructurePlacement placement, List<ChunkPos> candidates) {
            this.h = h; this.level = level; this.structure = structure; this.placement = placement; this.candidates = candidates;
            this.structureState = level.getChunkSource().getGenerator().createState(
                    level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                    level.getChunkSource().randomState(), level.getSeed());
        }
        void step() {
            // Bound work per test tick so a large search does not turn into a server watchdog hang.
            for (int budget = 0; budget < 12 && index < candidates.size(); budget++) {
                var chunk = candidates.get(index++);
                var ctx = context(level, structure, chunk);
                int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
                if (!ctx.biomeSource().getNoiseBiome(x >> 2, 80 >> 2, z >> 2,
                        ctx.randomState().sampler()).is(GearChurchStructure.BIOMES)) continue;
                if (!placement.isStructureChunk(structureState, chunk.x, chunk.z)) continue;
                badlands++;
                if (badlands % 10 == 0) System.out.println("GEAR-CHURCH SEARCH candidates=" + index
                        + " badlands=" + badlands + " bestRawRelief=" + bestRelief
                        + " bestDryBadlandsRelief=" + bestDryBadlandsRelief);
                var terrain = GearChurchStructure.terrain(ctx);
                int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
                for (int lx = 0; lx < GearChurchLayout.WIDTH; lx += GearChurchSite.SAMPLE_STEP)
                    for (int lz = 0; lz < GearChurchLayout.DEPTH; lz += GearChurchSite.SAMPLE_STEP) {
                        if (!GearChurchLayout.foundation(lx, lz)) continue;
                        int height = terrain.surface(x + lx - GearChurchLayout.CENTER.getX(),
                                z + lz - GearChurchLayout.CENTER.getZ());
                        minimum = Math.min(minimum, height); maximum = Math.max(maximum, height);
                    }
                bestRelief = Math.min(bestRelief, maximum - minimum);
                {
                    int water = 0, validBiomes = 0, diagnosticSamples = 0;
                    for (int lx = 0; lx < GearChurchLayout.WIDTH; lx += GearChurchSite.SAMPLE_STEP)
                        for (int lz = 0; lz < GearChurchLayout.DEPTH; lz += GearChurchSite.SAMPLE_STEP) {
                            if (!GearChurchLayout.foundation(lx, lz)) continue;
                            int px = x + lx - GearChurchLayout.CENTER.getX(), pz = z + lz - GearChurchLayout.CENTER.getZ();
                            int surface = terrain.surface(px, pz);
                            diagnosticSamples++;
                            if (terrain.oceanFloor(px, pz) != surface) water++;
                            if (terrain.badlands(px, surface - 1, pz)) validBiomes++;
                        }
                    if (water == 0 && validBiomes * 5 >= diagnosticSamples * 4
                            && terrain.badlands(x, maximum - 1, z))
                        bestDryBadlandsRelief = Math.min(bestDryBadlandsRelief, maximum - minimum);
                    if (maximum - minimum <= GearChurchSite.MAX_RELIEF)
                        System.out.println("GEAR-CHURCH LOW-RELIEF chunk=" + chunk + " heights=" + minimum + ".." + maximum
                                + " waterSamples=" + water + " biomeSamples=" + validBiomes + "/" + diagnosticSamples
                                + " centerBiomeAtSurface=" + terrain.badlands(x, maximum - 1, z));
                }
                var stub = structure.findValidGenerationPoint(ctx);
                if (stub.isEmpty()) continue;
                var pieces = stub.get().getPiecesBuilder().build();
                var start = new StructureStart(structure, chunk, 0, pieces);
                h.assertTrue(start.isValid() && start.getPieces().size() == GearChurchLayout.PARTS.size(),
                        "candidate did not produce the complete registered fixed structure");
                System.out.println("GEAR-CHURCH CANDIDATE seed=" + level.getSeed() + " chunk=" + chunk
                        + " searched=" + index + " badlandsCandidates=" + badlands);
                validateAndPlace(h, level, start);
                return;
            }
            if (index >= candidates.size()) {
                h.fail("No eligible site in " + candidates.size() + " production grid candidates; badlands=" + badlands
                        + " bestRawRelief=" + bestRelief
                        + " bestDryBadlandsRelief=" + bestDryBadlandsRelief
                        + ". Do not relax relief/biome checks to turn this green.");
            } else h.runAfterDelay(1, this::step);
        }
    }

    private static void validateAndPlace(GameTestHelper h, ServerLevel level, StructureStart start) {
        var piece = (GearChurchPiece) start.getPieces().get(0);
        BlockPos anchor = piece.anchor();
        var bounds = start.getBoundingBox();
        // Generate real vanilla surface chunks first, with normal structures/decorations enabled.
        int minCx = bounds.minX() >> 4, maxCx = bounds.maxX() >> 4;
        int minCz = bounds.minZ() >> 4, maxCz = bounds.maxZ() >> 4;
        var chunks = new ArrayList<ChunkPos>();
        for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++)
            chunks.add(new ChunkPos(cx, cz));
        // Normal player view supplies these neighbors; a remote test fixture otherwise
        // leaves ENTITY_TICKING's radius-two FULL futures unresolved.
        var preparation = new ArrayList<>(chunks);
        for (int cx = minCx - 2; cx <= maxCx + 2; cx++)
            for (int cz = minCz - 2; cz <= maxCz + 2; cz++)
                if (cx < minCx || cx > maxCx || cz < minCz || cz > maxCz)
                    preparation.add(new ChunkPos(cx, cz));
        var forced = new ArrayList<ChunkPos>();
        registerTicketCleanup(h, level, forced);
        // Snapshot post-carver terrain before FEATURES places the church. This independent
        // observation prevents a new terracotta support column being mistaken for native ground.
        Map<BlockPos, NativeSupport> nativeSupports = new HashMap<>();
        int[] carvingCursor = {0};
        final int[] cursor = {0};
        Runnable[] generate = new Runnable[1];
        generate[0] = () -> {
            for (int budget = 0; budget < 2 && cursor[0] < preparation.size(); budget++) {
                var chunk = preparation.get(cursor[0]++);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(chunk.x, chunk.z, true); forced.add(chunk);
                }
                level.getChunk(chunk.x, chunk.z);
            }
            // GameTest's scheduler keys entries by Runnable identity; rescheduling the running
            // object would be removed by its current iterator after run() returns.
            if (cursor[0] < preparation.size()) h.runAfterDelay(1, () -> generate[0].run());
            else h.runAfterDelay(2, () -> {
                boolean[] deferred = {false};
                try {
                    h.assertTrue(level.getBiome(anchor).is(GearChurchStructure.BIOMES), "actual chunk biome is not badlands");
                    // Natural generation may already have placed this structure at its production candidate.
                    var natural = level.structureManager().getStructureAt(anchor.above(2), start.getStructure());
                    h.assertTrue(natural.isValid(), "production candidate did not naturally generate a StructureStart");
                    h.assertTrue(natural.getPieces().size() == 25, "natural start lost template parts");
                    var actualPiece = (GearChurchPiece) natural.getPieces().get(0);
                    var ctx = context(level, (GearChurchStructure) start.getStructure(), start.getChunkPos());
                    var site = GearChurchSite.find(GearChurchStructure.terrain(ctx), anchor.getX(), anchor.getZ(),
                            actualPiece.rotation()).orElseThrow();
                    h.assertTrue(site.maxSurface() - site.minSurface() <= GearChurchSite.MAX_RELIEF,
                            "test relaxed production relief");
                    int platformSurface = anchor.getY() + 1;
                    h.assertTrue(site.maxSurface() - platformSurface <= GearChurchSite.MAX_CUT
                            && platformSurface - site.minSurface() <= GearChurchSite.MAX_SUPPORT,
                            "natural terrace exceeded cut/fill budget");
                    h.assertTrue(natural.getBoundingBox().getXSpan() <= 145
                            && natural.getBoundingBox().getZSpan() <= 145, "template rotation split the building apart");
                    // Confirm continuous supports against the pre-placement post-carver snapshot.
                    int unsupported = 0;
                    int deepestSupport = 0, caveColumnsBeyondSurfaceBudget = 0;
                    for (int lx = 0; lx < GearChurchLayout.WIDTH; lx++)
                        for (int lz = 0; lz < GearChurchLayout.DEPTH; lz++) {
                            if (!GearChurchLayout.foundation(lx, lz)) continue;
                            var base = GearChurchLayout.world(new BlockPos(lx, 0, lz), anchor, actualPiece.rotation());
                            var observed = nativeSupports.get(base);
                            boolean connected = observed != null && observed.depth() > 0;
                            if (connected) {
                                deepestSupport = Math.max(deepestSupport, observed.depth() - 1);
                                if (observed.depth() - 1 > GearChurchSite.MAX_SUPPORT) caveColumnsBeyondSurfaceBudget++;
                                var terminal = base.below(observed.depth());
                                connected = level.getBlockState(terminal).equals(observed.state())
                                        && GearChurchPiece.isStableSupport(level.getBlockState(terminal), level, terminal);
                                for (int down = 1; down < observed.depth() && connected; down++) {
                                    var pos = base.below(down);
                                    connected = level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
                                }
                            }
                            if (!connected) unsupported++;
                        }
                    h.assertTrue(unsupported == 0, "actual terrain left unsupported foundation columns: " + unsupported);
                    System.out.println("GEAR-CHURCH SUPPORT verifiedBeforeFeatures=" + nativeSupports.size()
                            + " maxActualFill=" + deepestSupport + " caveColumnsBeyondSurfaceBudget=" + caveColumnsBeyondSurfaceBudget);
                    assertEntrance(h, level, anchor, actualPiece.rotation());
                    for (var exit : GearChurchLayout.ENTRANCE_EXITS) {
                        var column = GearChurchLayout.world(exit, anchor, actualPiece.rotation());
                        int originalSurface = ctx.chunkGenerator().getBaseHeight(column.getX(), column.getZ(),
                                Heightmap.Types.WORLD_SURFACE_WG, level, ctx.randomState());
                        var foot = new BlockPos(column.getX(), originalSurface, column.getZ());
                        h.assertTrue(Math.abs(originalSurface - (anchor.getY() + 2.1875)) <= 1,
                                "natural exterior is an impassable step at the authored porch");
                        h.assertTrue(level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)
                                && level.getFluidState(foot).isEmpty() && level.noCollision(
                                        new net.minecraft.world.phys.AABB(foot.getX() + 0.2, foot.getY(),
                                                foot.getZ() + 0.2, foot.getX() + 0.8, foot.getY() + 1.8,
                                                foot.getZ() + 0.8)), "actual exterior path is blocked or unsupported");
                    }
                    int spawned = GearChurchResidents.populateNear(level, natural, Vec3.atCenterOf(anchor));
                    h.assertTrue(spawned == 16, "church failed to populate all sixteen authored safe points: " + spawned);
                    h.assertTrue(GearChurchResidents.populateNear(level, natural, Vec3.atCenterOf(anchor)) == 0,
                            "repeated population duplicated initial batch");
                    var data = GearChurchResidents.data(level);
                    var restored = GearChurchResidents.Data.load(data.save(new CompoundTag()));
                    h.assertTrue(restored.get(anchor, actualPiece.rotation()).spawnedCount() == 16,
                            "batch slot count did not survive save/load");
                    level.getDataStorage().set(GearChurchResidents.DATA_ID, restored);
                    h.assertTrue(GearChurchResidents.populateNear(level, natural, Vec3.atCenterOf(anchor)) == 0,
                            "reload duplicated residents");
                    deferred[0] = true;
                    finishWhenResidentsVisible(h, level, natural, actualPiece, site, chunks.size(), forced);
                } finally {
                    if (!deferred[0]) forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                }
            });
        };
        Runnable[] carve = new Runnable[1];
        carve[0] = () -> {
            for (int budget = 0; budget < 2 && carvingCursor[0] < chunks.size(); budget++) {
                var chunk = chunks.get(carvingCursor[0]++);
                var terrainChunk = level.getChunk(chunk.x, chunk.z,
                        net.minecraft.world.level.chunk.ChunkStatus.CARVERS, true);
                h.assertTrue(!terrainChunk.getStatus().isOrAfter(net.minecraft.world.level.chunk.ChunkStatus.FEATURES),
                        "cannot snapshot native support after church placement");
            }
            if (carvingCursor[0] < chunks.size()) h.runAfterDelay(1, () -> carve[0].run());
            else {
                for (int lx = 0; lx < GearChurchLayout.WIDTH; lx++) for (int lz = 0; lz < GearChurchLayout.DEPTH; lz++) {
                    if (!GearChurchLayout.foundation(lx, lz)) continue;
                    var base = GearChurchLayout.world(new BlockPos(lx, 0, lz), anchor, piece.rotation());
                    var terrainChunk = level.getChunk(base.getX() >> 4, base.getZ() >> 4,
                            net.minecraft.world.level.chunk.ChunkStatus.CARVERS, true);
                    int depth = -1;
                    net.minecraft.world.level.block.state.BlockState nativeState = Blocks.AIR.defaultBlockState();
                    for (int down = 1; down <= GearChurchSite.MAX_CAVE_SUPPORT + 1; down++) {
                        var pos = base.below(down);
                        if (pos.getY() < level.getMinBuildHeight()) break;
                        var state = terrainChunk.getBlockState(pos);
                        if (GearChurchPiece.isStableSupport(state, net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos)) {
                            depth = down; nativeState = state; break;
                        }
                    }
                    nativeSupports.put(base, new NativeSupport(depth, nativeState));
                }
                generate[0].run();
            }
        };
        carve[0].run();
    }
    private record NativeSupport(int depth, net.minecraft.world.level.block.state.BlockState state) {}

    /** Vanilla exposes listeners on GameTestInfo but no public Helper->Info getter.
     * Read only that test owner; never read/write production entity/chunk internals. */
    private static void registerTicketCleanup(GameTestHelper h, ServerLevel level, List<ChunkPos> forced) {
        try {
            var field = Arrays.stream(GameTestHelper.class.getDeclaredFields())
                    .filter(candidate -> candidate.getType() == GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            var info = (GameTestInfo) field.get(h);
            info.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo test) {}
                public void testPassed(GameTestInfo test) { cleanup(); }
                public void testFailed(GameTestInfo test) { cleanup(); }
                private void cleanup() {
                    forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                    forced.clear();
                }
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register test ticket cleanup before terrain preparation", failure);
        }
    }

    private static void finishWhenResidentsVisible(GameTestHelper h, ServerLevel level, StructureStart natural,
            GearChurchPiece piece, GearChurchSite.Site site, int realChunks, List<ChunkPos> forced) {
        int[] tries = {0};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            try {
                // Only advance vanilla pending tasks, never force entity-manager visibility.
                for (int budget = 0; budget < 64 && level.getChunkSource().pollTask(); budget++) {}
                var residents = new ArrayList<EntityGearFollower>();
                for (int slot = 0; slot < 16; slot++) {
                    var entity = level.getEntity(GearChurchResidents.residentId(level, piece.anchor(), slot));
                    if (entity == null) {
                        if (++tries[0] >= 100) h.fail("resident UUID never became queryable within 100 fixture ticks: " + slot);
                        h.runAfterDelay(1, () -> poll[0].run());
                        return;
                    }
                    h.assertTrue(entity instanceof EntityGearFollower, "wrong actual gear resident type " + slot);
                    var resident = (EntityGearFollower) entity;
                    h.assertTrue(resident.churchSlot() == slot && resident.getMaxHealth() == 86
                            && resident.getHealth() == 86 && !resident.isRemoved()
                            && level.noCollision(resident), "resident slot/health/collision mismatch");
                    h.assertTrue(GearChurchLayout.indoors(GearChurchLayout.local(resident.blockPosition(),
                            piece.anchor(), piece.rotation())), "resident spawned outside actual church interior");
                    residents.add(resident);
                }
                residents.forEach(EntityGearFollower::discard);
                h.assertTrue(GearChurchResidents.populateNear(level, natural, Vec3.atCenterOf(piece.anchor())) == 0,
                        "discarded initial batch was silently respawned");
                System.out.println("GEAR-CHURCH PASS seed=" + level.getSeed() + " anchor=" + piece.anchor()
                        + " rotation=" + piece.rotation() + " sampleHeights=" + site.minSurface()
                        + ".." + site.maxSurface() + " realChunks=" + realChunks
                        + " unsupported=0 residents=16 reloadDuplicate=0"
                        + " cut=" + (site.maxSurface() - piece.anchor().getY() - 1)
                        + " fill=" + (piece.anchor().getY() + 1 - site.minSurface()));
                forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                h.succeed();
            } catch (RuntimeException | Error failure) {
                forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                throw failure;
            }
        };
        h.runAfterDelay(1, () -> poll[0].run());
    }

    @GameTest(template = T, templateNamespace = "lobotocraft_gear_church", timeoutTicks = 100,
            batch = "gear_church_templates")
    public static void authoredTemplatesKeepStatesSignsAirAndAllRotations(GameTestHelper h) {
        var manager = h.getLevel().getStructureManager();
        int blocks = 0, signs = 0, authoredSoil = 0;
        for (var part : GearChurchLayout.PARTS) {
            var template = manager.getOrCreate(part.template());
            h.assertTrue(template.getSize().getY() == GearChurchLayout.HEIGHT, "template height changed");
            var saved = template.save(new CompoundTag());
            var palettes = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var t : saved.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var b = (CompoundTag) t; String id = palettes.getCompound(b.getInt("state")).getString("Name");
                if (!id.equals("minecraft:air")) blocks++;
                if (id.equals("minecraft:dirt") || id.equals("minecraft:grass_block")) authoredSoil++;
                if (b.contains("nbt") && b.getCompound("nbt").getString("id").equals("minecraft:sign")) signs++;
            }
        }
        h.assertTrue(blocks == 163233 && authoredSoil == 10837 && blocks - authoredSoil == 152396 && signs == 105,
                "asset extraction lost source blocks/authored soil/signs: " + blocks + "/" + authoredSoil + "/" + signs);
        var anchor = h.absolutePos(new BlockPos(0, 3, 0));
        for (var rotation : Rotation.values()) {
            var site = new GearChurchSite.Site(anchor, rotation, anchor.getY(), anchor.getY(), 90);
            var p = new GearChurchPiece(manager, GearChurchLayout.PARTS.get(0), site);
            var serialization = StructurePieceSerializationContext.fromLevel(h.getLevel());
            var reloaded = new GearChurchPiece(serialization, p.createTag(serialization));
            h.assertTrue(reloaded.anchor().equals(anchor) && reloaded.rotation() == rotation
                    && reloaded.getBoundingBox().equals(p.getBoundingBox()), "piece save/load lost rotation or bounds");
        }
        h.succeed();
    }

    public static void manualChunkClippedPlacementPopulatesSixteenSafeSlotsAndPersists(GameTestHelper h) {
        // This fixture verifies placement/lifecycle only, NOT the separate real-badlands suitability requirement.
        var level = h.getLevel();
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(GearChurchLayout.ID);
        var anchor = new BlockPos(1608, 70, 1608);
        var builder = new net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder();
        var site = new GearChurchSite.Site(anchor, Rotation.CLOCKWISE_90, 71, 71, 90);
        for (var part : GearChurchLayout.PARTS) builder.addPiece(new GearChurchPiece(level.getStructureManager(), part, site));
        var start = new StructureStart(structure, new ChunkPos(anchor), 0, builder.build());
        var bb = start.getBoundingBox();
        var chunks = new ArrayList<ChunkPos>();
        for (int cx = bb.minX() >> 4; cx <= bb.maxX() >> 4; cx++)
            for (int cz = bb.minZ() >> 4; cz <= bb.maxZ() >> 4; cz++) chunks.add(new ChunkPos(cx, cz));
        List<ChunkPos> forced = new ArrayList<>();
        registerTicketCleanup(h, level, forced);
        int[] cursor = {0};
        Runnable[] place = new Runnable[1];
        place[0] = () -> {
            if (cursor[0] % 10 == 0) System.out.println("GEAR-CHURCH MANUAL STEP " + cursor[0] + "/" + chunks.size());
            for (int budget = 0; budget < 2 && cursor[0] < chunks.size(); budget++) {
                var chunk = chunks.get(cursor[0]++);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(chunk.x, chunk.z, true); forced.add(chunk);
                }
                level.getChunk(chunk.x, chunk.z);
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
                        level.setBlock(new BlockPos(x, anchor.getY() - 1, z), Blocks.TERRACOTTA.defaultBlockState(), 2);
                // The source column (1,1) is only authored soil. It exercises cutting beyond
                // the template top; the first layer above MAX_CUT and a non-authored column must survive.
                var cutColumn = GearChurchLayout.world(new BlockPos(1, 0, 1), anchor, site.rotation());
                if (new ChunkPos(cutColumn).equals(chunk)) {
                    for (int up = 2; up <= GearChurchSite.MAX_CUT + 1; up++)
                        level.setBlock(cutColumn.above(up), Blocks.STONE.defaultBlockState(), 2);
                }
                var outsideColumn = GearChurchLayout.world(new BlockPos(0, 0, 1), anchor, site.rotation());
                if (new ChunkPos(outsideColumn).equals(chunk))
                    level.setBlock(outsideColumn.above(2), Blocks.STONE.defaultBlockState(), 2);
                for (int gap : new int[]{GearChurchSite.MAX_CAVE_SUPPORT, GearChurchSite.MAX_CAVE_SUPPORT + 1}) {
                    var caveBase = GearChurchLayout.world(new BlockPos(1, 0,
                            gap == GearChurchSite.MAX_CAVE_SUPPORT ? 2 : 3), anchor, site.rotation());
                    if (new ChunkPos(caveBase).equals(chunk)) {
                        for (int down = 1; down <= gap; down++)
                            level.setBlock(caveBase.below(down), Blocks.AIR.defaultBlockState(), 2);
                        level.setBlock(caveBase.below(gap + 1), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                var gravityBase = GearChurchLayout.world(new BlockPos(1, 0, 4), anchor, site.rotation());
                if (new ChunkPos(gravityBase).equals(chunk)) {
                    level.setBlock(gravityBase.below(4), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(gravityBase.below(3), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(gravityBase.below(2), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(gravityBase.below(), Blocks.RED_SAND.defaultBlockState(), 2);
                }
                start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        RandomSource.create(281), new BoundingBox(chunk.getMinBlockX(), -64, chunk.getMinBlockZ(),
                                chunk.getMaxBlockX(), 319, chunk.getMaxBlockZ()), chunk);
            }
            if (cursor[0] < chunks.size()) h.runAfterDelay(1, () -> place[0].run());
            else h.runAfterDelay(3, () -> {
                try {
                    assertEntrance(h, level, anchor, site.rotation());
                    var cutColumn = GearChurchLayout.world(new BlockPos(1, 0, 1), anchor, site.rotation());
                    for (int up = 2; up <= GearChurchSite.MAX_CUT; up++)
                        h.assertTrue(level.getBlockState(cutColumn.above(up)).isAir(),
                                "terrain above low authored soil was not cut within budget");
                    h.assertTrue(level.getBlockState(cutColumn.above(GearChurchSite.MAX_CUT + 1)).is(Blocks.STONE),
                            "terrace cut exceeded production budget");
                    h.assertTrue(level.getBlockState(GearChurchLayout.world(new BlockPos(0, 2, 1),
                            anchor, site.rotation())).is(Blocks.STONE), "terrace cut outside authored columns");
                    var allowedCave = GearChurchLayout.world(new BlockPos(1, 0, 2), anchor, site.rotation());
                    for (int down = 1; down <= GearChurchSite.MAX_CAVE_SUPPORT; down++)
                        h.assertTrue(level.getBlockState(allowedCave.below(down)).is(Blocks.TERRACOTTA),
                                "maximum allowed cave support failed to reach the native terminal");
                    h.assertTrue(level.getBlockState(allowedCave.below(GearChurchSite.MAX_CAVE_SUPPORT + 1)).is(Blocks.STONE),
                            "cave support replaced its original ground terminal");
                    var deepCave = GearChurchLayout.world(new BlockPos(1, 0, 3), anchor, site.rotation());
                    for (int down = 1; down <= GearChurchSite.MAX_CAVE_SUPPORT + 1; down++)
                        h.assertTrue(level.getBlockState(deepCave.below(down)).isAir(),
                                "over-budget cave left a partial floating support column");
                    var gravityBase = GearChurchLayout.world(new BlockPos(1, 0, 4), anchor, site.rotation());
                    for (int down = 1; down <= 3; down++)
                        h.assertTrue(level.getBlockState(gravityBase.below(down)).is(Blocks.TERRACOTTA),
                                "falling sand was treated as a stable foundation terminal");
                    h.assertTrue(level.getBlockState(gravityBase.below(4)).is(Blocks.STONE),
                            "gravity support replaced the stable native terminal");
                    // Noncolliding debris must not consume a safe resident slot.
                    var itemPos = GearChurchLayout.world(GearChurchLayout.SPAWNS.get(0), anchor, site.rotation());
                    var debris = new net.minecraft.world.entity.item.ItemEntity(level, itemPos.getX() + 0.5,
                            itemPos.getY() + 0.1, itemPos.getZ() + 0.5, new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.DIRT));
                    level.addFreshEntity(debris);
                    int spawned = GearChurchResidents.populateNear(level, start, Vec3.atCenterOf(anchor));
                    if (spawned != 16) {
                        var record = GearChurchResidents.data(level).get(anchor, site.rotation());
                        System.out.println("GEAR-CHURCH MANUAL BATCH added=" + spawned + " maskCount=" + record.spawnedCount());
                        for (int slot = 0; slot < 16; slot++) {
                            if ((record.mask & (1 << slot)) != 0) continue;
                            var local = GearChurchLayout.SPAWNS.get(slot);
                            var pos = GearChurchLayout.world(local, anchor, site.rotation());
                            var roof = GearChurchLayout.world(new BlockPos(local.getX(),
                                    GearChurchLayout.roofHeight(local.getX(), local.getZ()), local.getZ()), anchor, site.rotation());
                            var box = new net.minecraft.world.phys.AABB(pos.getX() + 0.175, pos.getY(), pos.getZ() + 0.175,
                                    pos.getX() + 0.825, pos.getY() + 1.95, pos.getZ() + 0.825);
                            System.out.println("GEAR-CHURCH MANUAL MISSING slot=" + slot + " pos=" + pos
                                    + " floor=" + level.getBlockState(pos.below()) + " feet=" + level.getBlockState(pos)
                                    + " head=" + level.getBlockState(pos.above()) + " roof=" + level.getBlockState(roof)
                                    + " noBlockCollision=" + level.noCollision(box) + " entities="
                                    + level.getEntities(null, box).stream().map(e -> e.getType() + "@" + e.position()).toList());
                        }
                    }
                    h.assertTrue(spawned == 16, "manual template resident slots not all safe/ready: " + spawned);
                    debris.discard();
                    var saved = GearChurchResidents.data(level).save(new CompoundTag());
                    level.getDataStorage().set(GearChurchResidents.DATA_ID, GearChurchResidents.Data.load(saved));
                    h.assertTrue(GearChurchResidents.populateNear(level, start, Vec3.atCenterOf(anchor)) == 0,
                            "manual save/load duplicated batch");
                    for (int slot = 0; slot < 16; slot++) {
                        var mob = level.getEntity(GearChurchResidents.residentId(level, anchor, slot));
                        h.assertTrue(mob instanceof EntityGearFollower && level.noCollision(mob),
                                "resident absent or colliding: " + slot);
                        mob.discard();
                    }
                    System.out.println("GEAR-CHURCH MANUAL PASS parts=25 residents=16 rotation=CLOCKWISE_90"
                            + " reloadDuplicate=0 terraceCut=" + GearChurchSite.MAX_CUT
                            + " cutOutside=0 caveFill" + GearChurchSite.MAX_CAVE_SUPPORT + "=pass caveFill"
                            + (GearChurchSite.MAX_CAVE_SUPPORT + 1) + "=rejected fallingSand=stableTerminal");
                    h.succeed();
                } finally { forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false)); }
            });
        };
        place[0].run();
    }

    private static void assertEntrance(GameTestHelper h, ServerLevel level, BlockPos anchor, Rotation rotation) {
        var pos = GearChurchLayout.world(GearChurchLayout.ENTRANCE, anchor, rotation);
        h.assertTrue(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP),
                "authored entrance floor was lost");
        h.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),
                "entrance is buried or blocked");
        h.assertTrue(level.noCollision(new net.minecraft.world.phys.AABB(pos.getX() + 0.2, pos.getY(),
                pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8)),
                "player-sized entrance is colliding");
    }
}
