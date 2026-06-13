package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.core.animation.AnimatableManager;

public class PunishingBirdBlockEntity extends BaseGeoBlockEntity {
    public PunishingBirdBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PUNISHING_BIRD.get(), pos, blockState);
    }
    
    /**
     * 服务端Tick逻辑
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PunishingBirdBlockEntity blockEntity) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }
}