package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.PunishingBirdBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.block.entity.TombstoneBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModMain.MODID);

    public static final RegistryObject<BlockEntityType<RegenerationReactorBlockEntity>> REGENERATION_REACTOR =
        BLOCK_ENTITIES.register(
            "regeneration_reactor",
            () -> BlockEntityType.Builder.of(
                RegenerationReactorBlockEntity::new,
                ModBlocks.REGENERATION_REACTOR.get()
            ).build(null)
        );

    public static final RegistryObject<BlockEntityType<EscapeBlockEntity>> ESCAPE =
            BLOCK_ENTITIES.register(
                    "escape",
                    () -> BlockEntityType.Builder.of(
                            EscapeBlockEntity::new,
                            ModBlocks.ESCAPE.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<ElevatorBlockEntity>> ELEVATOR =
            BLOCK_ENTITIES.register(
                    "elevator",
                    () -> BlockEntityType.Builder.of(
                            ElevatorBlockEntity::new,
                            ModBlocks.ELEVATOR.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<PunishingBirdBlockEntity>> PUNISHING_BIRD =
            BLOCK_ENTITIES.register(
                    "punishing_bird",
                    () -> BlockEntityType.Builder.of(
                            PunishingBirdBlockEntity::new,
                            ModBlocks.PUNISHING_BIRD.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<TombstoneBlockEntity>> TOMBSTONE =
            BLOCK_ENTITIES.register(
                    "tombstone",
                    () -> BlockEntityType.Builder.of(
                            TombstoneBlockEntity::new,
                            ModBlocks.TOMBSTONE.get()
                    ).build(null)
            );
}
