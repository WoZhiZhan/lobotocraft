package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.ElevatorBlock;
import com.wzz.lobotocraft.block.EscapeBlock;
import com.wzz.lobotocraft.block.PunishingBirdBlock;
import com.wzz.lobotocraft.block.RegenerationReactorBlock;
import com.wzz.lobotocraft.block.TombstoneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModMain.MODID);

    public static final RegistryObject<Block> REGENERATION_REACTOR = BLOCKS.register(
        "regeneration_reactor",
        () -> new RegenerationReactorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel(state -> 8)
        )
    );

    public static final RegistryObject<Block> ESCAPE = BLOCKS.register(
            "escape",
            () -> new EscapeBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.0f, 6.0f)
                            .isRedstoneConductor((state, getter, pos) -> true)
                            .sound(SoundType.STONE)
                            .noOcclusion()
                            .lightLevel(state -> 8)
            )
    );

    public static final RegistryObject<Block> ELEVATOR = BLOCKS.register(
            "elevator",
            () -> new ElevatorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .lightLevel(state -> 8)
            )
    );

    public static final RegistryObject<Block> PUNISHING_BIRD = BLOCKS.register(
            "punishing_bird",
            () -> new PunishingBirdBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GRASS)
                            .strength(1.0f, 6.0f)
                            .isRedstoneConductor((state, getter, pos) -> true)
                            .sound(SoundType.GRASS)
                            .noOcclusion()
                            .lightLevel(state -> 2)
            )
    );

    public static final RegistryObject<Block> TOMBSTONE = BLOCKS.register(
            "tombstone",
            () -> new TombstoneBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(-1.0F, 3600000.0F)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            )
    );
}
