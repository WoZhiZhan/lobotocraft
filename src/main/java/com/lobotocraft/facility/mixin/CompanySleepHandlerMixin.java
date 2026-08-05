package com.lobotocraft.facility.mixin;

import com.wzz.lobotocraft.event.listener.CompanySleepHandler;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompanySleepHandler.class, remap = false)
public abstract class CompanySleepHandlerMixin {
    @Inject(method = "onPlayerSleep", at = @At("HEAD"), cancellable = true)
    private static void facility$disableOldWorkCountMessage(PlayerSleepInBedEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
