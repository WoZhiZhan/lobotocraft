package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {

    @Shadow @Final
    Level level;

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
                                 CallbackInfoReturnable<BlockState> cir) {
        if (level.dimension().equals(ModDimensions.LOBOTO_KEY)) {
            BlockState originalState = level.getBlockState(pos);
            if (state.isAir() && ProtectionHelper.isProtected(level, pos) && !state.equals(originalState)) {
                cir.setReturnValue(originalState);
            }
        }
    }
}
