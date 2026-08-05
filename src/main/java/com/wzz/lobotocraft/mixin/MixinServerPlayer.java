package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.api.IProhibitDiscarding;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.EgoProjectionRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "drop(Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void onDrop(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;
        ItemStack stack = self.getInventory().getSelected();
        if (!EgoProjectionRules.mayDiscard(EgoLoadoutService.isProjection(stack), self.isCreative())
                || !self.isCreative() && !stack.isEmpty()
                && stack.getItem() instanceof IProhibitDiscarding) {
            cir.setReturnValue(false);
        }
    }

}
