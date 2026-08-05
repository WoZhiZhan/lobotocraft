package com.lobotocraft.facility.mixin;

import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.event.listener.CrimsonDawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CrimsonDawnEvent.class, remap = false)
public abstract class CrimsonDawnEventMixin {
    @Inject(method = "onWorkComplete", at = @At("HEAD"), cancellable = true)
    private static void facility$disableRandomOrdeals(WorkCompleteEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
