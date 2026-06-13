package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSetBlock(BlockPos pos, BlockState state, int flags, int recursion,
                            CallbackInfoReturnable<Boolean> cir) {

        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!level.dimension().equals(ModDimensions.LOBOTO_KEY)) return;
        if (!ProtectionHelper.isProtected(serverLevel, pos)) return;
        BlockState currentState = level.getBlockState(pos);
        if (!currentState.isAir() && state.isAir()) {
            cir.setReturnValue(false);
            serverLevel.sendBlockUpdated(pos, currentState, currentState, 3);
        }
    }

    @Inject(
            method = "destroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDestroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft,
                                CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.dimension().equals(ModDimensions.LOBOTO_KEY) && level instanceof ServerLevel serverLevel) {
            if (ProtectionHelper.isProtected(serverLevel, pos)) {
                cir.setReturnValue(false);
                BlockState currentState = level.getBlockState(pos);
                serverLevel.sendBlockUpdated(pos, currentState, currentState, 3);
            }
        }
    }
}