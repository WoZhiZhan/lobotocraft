package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GearChurchSiteTest {
    private GearChurchSite.Terrain expandedTerraceTerrain(int relief) {
        return new GearChurchSite.Terrain() {
            public int surface(int x, int z) {
                return z >= GearChurchLayout.DEPTH - GearChurchLayout.CENTER.getZ()
                        ? 81 + Math.min(relief, 16) : 80 + (x < 0 ? relief : 0);
            }
            public int oceanFloor(int x, int z) { return surface(x, z); }
            public boolean badlands(int x, int y, int z) { return true; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
    }
    @Test void expandedTerraceAllowsThirtySixReliefButRejectsThirtySeven() {
        var site = GearChurchSite.find(expandedTerraceTerrain(36), 0, 0, Rotation.NONE).orElseThrow();
        int platform = site.anchor().getY() + 1;
        assertEquals(20, site.maxSurface() - platform);
        assertEquals(16, platform - site.minSurface());
        assertTrue(GearChurchSite.find(expandedTerraceTerrain(37), 0, 0, Rotation.NONE).isEmpty());
    }
    private GearChurchSite.Terrain terrain(int relief, boolean water, boolean badlands) {
        return new GearChurchSite.Terrain() {
            public int surface(int x, int z) {
                return z >= GearChurchLayout.DEPTH - GearChurchLayout.CENTER.getZ()
                        ? 81 + Math.min(relief, 12) : 80 + (x < 0 ? relief : 0);
            }
            public int oceanFloor(int x, int z) { return surface(x, z) - (water ? 1 : 0); }
            public boolean badlands(int x, int y, int z) { return badlands; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
    }
    @Test void fitsOnlyDryLowReliefBadlands() {
        assertTrue(GearChurchSite.find(terrain(8, false, true), 0, 0, Rotation.NONE).isPresent());
        assertTrue(GearChurchSite.find(terrain(12, false, true), 0, 0, Rotation.NONE).isPresent());
        assertTrue(GearChurchSite.find(terrain(13, false, true), 0, 0, Rotation.NONE).isPresent());
        assertEquals(20, GearChurchSite.MAX_CUT);
        assertEquals(16, GearChurchSite.MAX_SUPPORT);
        assertEquals(25, GearChurchSite.MAX_CAVE_SUPPORT);
        assertEquals(36, GearChurchSite.MAX_RELIEF,
                "cave columns must not silently expand the surface relief budget");
        assertTrue(GearChurchSite.find(terrain(0, true, true), 0, 0, Rotation.NONE).isEmpty());
        assertTrue(GearChurchSite.find(terrain(0, false, false), 0, 0, Rotation.NONE).isEmpty());
    }
    @Test void priorTerracesRemainWithinExpandedBudgetWithoutSinkingFlatGround() {
        var terrace = GearChurchSite.find(terrain(24, false, true), 0, 0, Rotation.NONE).orElseThrow();
        int platformSurface = terrace.anchor().getY() + 1;
        assertTrue(terrace.maxSurface() - platformSurface <= GearChurchSite.MAX_CUT);
        assertTrue(platformSurface - terrace.minSurface() <= GearChurchSite.MAX_SUPPORT);
        var flat = GearChurchSite.find(terrain(0, false, true), 0, 0, Rotation.NONE).orElseThrow();
        assertEquals(79, flat.anchor().getY(), "flat terrain must not be cut just because cutting is allowed");
    }
    @Test void usableTerraceStillRejectsAnExteriorEntranceCliff() {
        var safe = terrain(0, false, true);
        int[] heightQueries = {0};
        var cliff = new GearChurchSite.Terrain() {
            public int surface(int x, int z) {
                heightQueries[0]++;
                return z >= GearChurchLayout.DEPTH - GearChurchLayout.CENTER.getZ() ? 100 : safe.surface(x, z);
            }
            public int oceanFloor(int x, int z) { return surface(x, z); }
            public boolean badlands(int x, int y, int z) { return true; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
        assertTrue(GearChurchSite.find(cliff, 0, 0, Rotation.NONE).isEmpty(),
                "an open indoor door must not accept an impassable exterior cliff");
        assertTrue(heightQueries[0] < 256,
                "an already impossible entrance must not pay for full per-column noise scans");
    }
    @Test void footprintRotationAndIndoorAnchorsAgreeAcrossAllRotations() {
        var anchor = new BlockPos(-180, 75, 302);
        assertEquals(25, GearChurchLayout.PARTS.size());
        assertEquals(16, GearChurchLayout.SPAWNS.size());
        for (var rotation : Rotation.values()) for (var pos : GearChurchLayout.SPAWNS) {
            assertEquals(pos, GearChurchLayout.local(GearChurchLayout.world(pos, anchor, rotation), anchor, rotation));
            assertTrue(GearChurchLayout.indoors(pos));
        }
    }
    @Test void trialSpacingKeepsBuildingsApartWithoutTheOldTwoThousandGuarantee() {
        int minimumCenters = (GearChurchSite.SEPARATION + 1) * 16;
        int maxDiameter = (int) Math.ceil(Math.hypot(GearChurchLayout.WIDTH, GearChurchLayout.DEPTH));
        int maximumCombinedLocalShift = (int) Math.ceil(2 * Math.hypot(32, 32));
        assertEquals(96, GearChurchSite.SPACING);
        assertEquals(48, GearChurchSite.SEPARATION);
        assertTrue(GearChurchSite.SPACING > GearChurchSite.SEPARATION);
        assertTrue(minimumCenters - maxDiameter - maximumCombinedLocalShift > 0);
        assertTrue(minimumCenters - maxDiameter - maximumCombinedLocalShift < 2000);
    }
    @Test void packagedPlacementMatchesTrialWithoutOtherStructureExclusion() throws Exception {
        try (var stream = GearChurchSiteTest.class.getResourceAsStream(
                "/data/lobotocraft/worldgen/structure_set/gear_church.json")) {
            assertNotNull(stream);
            var placement = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("placement");
            assertEquals(GearChurchSite.SPACING, placement.get("spacing").getAsInt());
            assertEquals(GearChurchSite.SEPARATION, placement.get("separation").getAsInt());
            assertEquals(GearChurchSite.SALT, placement.get("salt").getAsInt());
            assertFalse(placement.has("exclusion_zone"),
                    "user removed other-structure exclusion; do not retain a village candidate veto");
        }
    }
    @Test void naturalRestrictionDoesNotDeleteOrBlockPetsSummonsOrResidents() {
        assertTrue(GearChurchResidents.blocksNatural(MobSpawnType.NATURAL, false, true));
        assertTrue(GearChurchResidents.blocksNatural(MobSpawnType.CHUNK_GENERATION, false, true));
        assertFalse(GearChurchResidents.blocksNatural(MobSpawnType.SPAWNER, false, true));
        assertFalse(GearChurchResidents.blocksNatural(MobSpawnType.COMMAND, false, true));
        assertFalse(GearChurchResidents.blocksNatural(MobSpawnType.NATURAL, true, true));
        assertFalse(GearChurchResidents.blocksNatural(MobSpawnType.NATURAL, false, false));
    }
    @Test void unsampledFoundationGullyCannotProduceAFloatingSupportPillar() {
        var flat = terrain(0, false, true);
        var gully = new GearChurchSite.Terrain() {
            public int surface(int x, int z) {
                return x == 17 - GearChurchLayout.CENTER.getX() && z == 17 - GearChurchLayout.CENTER.getZ()
                        ? 60 : flat.surface(x, z);
            }
            public int oceanFloor(int x, int z) { return surface(x, z); }
            public boolean badlands(int x, int y, int z) { return true; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
        assertTrue(GearChurchLayout.foundation(17, 17));
        assertTrue(GearChurchSite.find(gully, 0, 0, Rotation.NONE).isEmpty());
    }
    @Test void savedFootprintRemainsAvailableWithoutLoadingTheSourceChunk() {
        var data = new GearChurchResidents.Data();
        BlockPos anchor = new BlockPos(8, 80, 8);
        data.get(anchor, Rotation.NONE);
        var indoor = GearChurchLayout.world(GearChurchLayout.SPAWNS.get(0), anchor, Rotation.NONE);
        assertNotEquals(new net.minecraft.world.level.ChunkPos(anchor), new net.minecraft.world.level.ChunkPos(indoor));
        assertTrue(data.indoors(indoor));
        var reloaded = GearChurchResidents.Data.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertTrue(reloaded.indoors(indoor));
        assertFalse(reloaded.indoors(indoor.above(100)));
        assertFalse(GearChurchResidents.withinResidentRange(anchor, Vec3.atCenterOf(anchor).add(140, 0, 0)));
        assertTrue(GearChurchResidents.withinResidentRange(anchor, Vec3.atCenterOf(anchor).add(20, 0, 0)));
    }
    @Test void overlappingLocalProbesDoNotRecomputeNoiseColumns() {
        var calls = new java.util.HashMap<Long, Integer>();
        var input = new GearChurchSite.Terrain() {
            public int surface(int x, int z) {
                calls.merge(net.minecraft.world.level.ChunkPos.asLong(x, z), 1, Integer::sum);
                return 80;
            }
            public int oceanFloor(int x, int z) { return 80; }
            public boolean badlands(int x, int y, int z) { return false; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
        assertTrue(GearChurchSite.findNear(input, 0, 0, Rotation.NONE).isEmpty());
        assertTrue(calls.size() <= 25,
                "a center that cannot be badlands at any permitted height must not scan whole footprints");
        assertTrue(calls.values().stream().allMatch(count -> count == 1));
    }
    @Test void centerBiomeScreenKeepsLegalHighSurfaceBiomesInsteadOfUsingFixedY80() {
        var input = terrain(20, false, true);
        var highCenter = new GearChurchSite.Terrain() {
            public int surface(int x, int z) { return input.surface(x, z); }
            public int oceanFloor(int x, int z) { return input.oceanFloor(x, z); }
            public boolean badlands(int x, int y, int z) {
                return x != 0 || z != 0 || y == 99;
            }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
        assertTrue(GearChurchSite.find(highCenter, 0, 0, Rotation.NONE).isPresent(),
                "center y=99 is legal even though y=80 is not badlands");
    }
}
