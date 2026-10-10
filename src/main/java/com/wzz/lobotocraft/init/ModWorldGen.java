package com.wzz.lobotocraft.init;

import com.mojang.serialization.Codec;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.world.worldgen.LobotoBiomeSource;
import com.wzz.lobotocraft.world.worldgen.LobotoChunkGenerator;
import com.wzz.lobotocraft.world.gear.*;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModWorldGen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ModMain.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ModMain.MODID);
    public static final RegistryObject<StructureType<GearChurchStructure>> GEAR_CHURCH =
            STRUCTURE_TYPES.register("gear_church", () -> () -> GearChurchStructure.CODEC);
    public static final RegistryObject<StructurePieceType> GEAR_CHURCH_PIECE =
            PIECE_TYPES.register("gear_church", () -> GearChurchPiece::new);
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, ModMain.MODID);
    
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = 
        DeferredRegister.create(Registries.BIOME_SOURCE, ModMain.MODID);

    public static final RegistryObject<Codec<LobotoChunkGenerator>> LOBOTO_CHUNK_GENERATOR =
        CHUNK_GENERATORS.register("loboto", () -> LobotoChunkGenerator.CODEC);
    
    public static final RegistryObject<Codec<LobotoBiomeSource>> LOBOTO_BIOME_SOURCE =
        BIOME_SOURCES.register("loboto", () -> LobotoBiomeSource.CODEC);

    public static void register(IEventBus eventBus) {
        CHUNK_GENERATORS.register(eventBus);
        BIOME_SOURCES.register(eventBus);
        STRUCTURE_TYPES.register(eventBus);
        PIECE_TYPES.register(eventBus);
    }
}
