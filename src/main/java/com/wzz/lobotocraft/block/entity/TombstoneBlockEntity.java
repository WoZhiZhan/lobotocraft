package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.core.animation.AnimatableManager;

public class TombstoneBlockEntity extends BaseGeoBlockEntity {
    public TombstoneBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TOMBSTONE.get(), pos, blockState);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
    }
}
