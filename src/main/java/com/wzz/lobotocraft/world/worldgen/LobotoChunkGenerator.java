package com.wzz.lobotocraft.world.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LobotoChunkGenerator extends ChunkGenerator {
    public static final Codec<LobotoChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
            ).apply(instance, LobotoChunkGenerator::new)
    );


    public LobotoChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            generateFlatTerrain(chunk);
            return chunk;
        }, executor);
    }

    private void generateFlatTerrain(ChunkAccess chunk) {
        int minY = getMinY(); // 0
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                // Y = 0 → 基岩
                chunk.setBlockState(
                        new BlockPos(x, minY, z),
                        Blocks.BEDROCK.defaultBlockState(),
                        false
                );

                // Y = 1~2 → 泥土
                chunk.setBlockState(
                        new BlockPos(x, minY + 1, z),
                        Blocks.DIRT.defaultBlockState(),
                        false
                );
                chunk.setBlockState(
                        new BlockPos(x, minY + 2, z),
                        Blocks.DIRT.defaultBlockState(),
                        false
                );

                // Y = 3 → 草方块
                chunk.setBlockState(
                        new BlockPos(x, minY + 3, z),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        false
                );
            }
        }
    }

    @Override
    public int getSeaLevel() {
        return 4;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int i, int i1, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return 4;
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(0, new BlockState[level.getHeight()]);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
    }
}