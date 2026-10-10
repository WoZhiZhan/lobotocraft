package com.wzz.lobotocraft.world.gear;

import com.mojang.serialization.Codec;
import com.wzz.lobotocraft.init.ModWorldGen;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import java.util.*;

public final class GearChurchStructure extends Structure {
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            new ResourceLocation("lobotocraft", "has_structure/gear_church"));
    public static final Codec<GearChurchStructure> CODEC = simpleCodec(GearChurchStructure::new);
    public GearChurchStructure(StructureSettings settings) { super(settings); }
    public static GearChurchSite.Terrain terrain(GenerationContext context) {
        return new GearChurchSite.Terrain() {
            public int surface(int x, int z) { return context.chunkGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()); }
            public int oceanFloor(int x, int z) { return context.chunkGenerator().getBaseHeight(x, z,
                    Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()); }
            public boolean badlands(int x, int y, int z) {
                return context.biomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, context.randomState().sampler()).is(BIOMES);
            }
            public int minY() { return context.heightAccessor().getMinBuildHeight(); }
            public int maxY() { return context.heightAccessor().getMaxBuildHeight(); }
        };
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        Rotation rotation = Rotation.getRandom(context.random());
        var site = GearChurchSite.findNear(terrain(context), x, z, rotation);
        if (site.isEmpty()) return Optional.empty();
        return Optional.of(new GenerationStub(site.get().anchor(), builder -> {
            for (var part : GearChurchLayout.PARTS)
                builder.addPiece(new GearChurchPiece(context.structureTemplateManager(), part, site.get()));
        }));
    }
    @Override public StructureType<?> type() { return ModWorldGen.GEAR_CHURCH.get(); }
}
