package com.lobotocraft.facility.mixin.client;

import com.wzz.lobotocraft.event.WorkProgressHUD;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorkProgressHUD.class, remap = false)
public abstract class WorkProgressHudMixin {
    @Inject(method = "onRenderGameOverlay", at = @At("HEAD"), cancellable = true)
    private static void facility$disableOldHud(RenderGuiOverlayEvent.Post event, CallbackInfo callback) {
        callback.cancel();
    }
}
