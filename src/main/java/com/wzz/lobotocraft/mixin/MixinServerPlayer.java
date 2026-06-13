package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.item.api.IProhibitDiscarding;
import com.wzz.lobotocraft.work.WorkManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "drop(Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void onDrop(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;
        if (!self.isCreative()) {
            ItemStack stack = self.getInventory().getSelected();
            if (!stack.isEmpty() && stack.getItem() instanceof IProhibitDiscarding) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void teleportTo(double p_20210_, double p_20211_, double p_20212_, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if ((WorkManager.isPlayerWorking(self) || self.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }

    @Inject(method = "moveTo", at = @At("HEAD"), cancellable = true)
    private void moveTo(double p_9171_, double p_9172_, double p_9173_, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if ((WorkManager.isPlayerWorking(self) || self.getPersistentData().getBoolean("NotMove"))) {
            ci.cancel();
        }
    }
}