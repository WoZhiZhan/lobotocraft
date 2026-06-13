package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "startDestroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onStartDestroyBlock(BlockPos pos, Direction direction,
                                     CallbackInfoReturnable<Boolean> cir) {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        if (!level.dimension().equals(ModDimensions.LOBOTO_KEY)) return;
        if (ProtectionHelper.isProtected(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
