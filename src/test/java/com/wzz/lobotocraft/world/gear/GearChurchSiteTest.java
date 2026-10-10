package com.wzz.lobotocraft.world.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GearChurchSiteTest {
    private GearChurchSite.Terrain terrain(int relief, boolean water, boolean badlands) {
        return new GearChurchSite.Terrain() {
            public int surface(int x, int z) { return 80 + (x < 0 ? relief : 0); }
            public int oceanFloor(int x, int z) { return surface(x, z) - (water ? 1 : 0); }
            public boolean badlands(int x, int y, int z) { return badlands; }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
    }
    @Test void fitsOnlyDryLowReliefBadlands() {
        assertTrue(GearChurchSite.find(terrain(8, false, true), 0, 0, Rotation.NONE).isPresent());
        assertTrue(GearChurchSite.find(terrain(9, false, true), 0, 0, Rotation.NONE).isEmpty());
        assertTrue(GearChurchSite.find(terrain(0, true, true), 0, 0, Rotation.NONE).isEmpty());
        assertTrue(GearChurchSite.find(terrain(0, false, false), 0, 0, Rotation.NONE).isEmpty());
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
    @Test void spacingGuaranteesTwoThousandEvenBetweenNeighboringGridCells() {
        int minimumCenters = (GearChurchSite.SEPARATION + 1) * 16;
        int maxDiameter = (int) Math.ceil(Math.hypot(GearChurchLayout.WIDTH, GearChurchLayout.DEPTH));
        int maximumCombinedLocalShift = (int) Math.ceil(2 * Math.hypot(32, 32));
        assertTrue(minimumCenters - maxDiameter - maximumCombinedLocalShift > 2000);
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
}
