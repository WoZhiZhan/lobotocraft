package com.wzz.lobotocraft.block.entity;

import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.world.data.ClerkTombstoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.core.animation.AnimatableManager;

public class TombstoneBlockEntity extends BaseGeoBlockEntity {
    public TombstoneBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TOMBSTONE.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ClerkTombstoneData.get(serverLevel).add(worldPosition);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
    }
}
