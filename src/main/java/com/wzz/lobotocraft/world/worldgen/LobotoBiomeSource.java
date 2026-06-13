package com.wzz.lobotocraft.world.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public class LobotoBiomeSource extends BiomeSource {
    public static final Codec<LobotoBiomeSource> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Biome.CODEC.fieldOf("biome").forGetter(source -> source.biome)
        ).apply(instance, LobotoBiomeSource::new)
    );

    private final Holder<Biome> biome;

    public LobotoBiomeSource(Holder<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.biome);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.biome;
    }
}