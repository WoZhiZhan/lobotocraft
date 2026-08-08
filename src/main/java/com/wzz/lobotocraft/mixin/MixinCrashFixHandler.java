package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.client.performance.ClientCrashReportSaver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional integration for the recovery mod used by the distributed modpack. */
@Pseudo
@Mixin(targets = "com.wzz.crashfix.CrashFixHandler", remap = false)
public abstract class MixinCrashFixHandler {
    @Inject(method = "onCrash", at = @At("HEAD"), remap = false, require = 0)
    private static void lobotocraft$saveCompleteReport(Minecraft minecraft, Throwable throwable,
                                                       CallbackInfo callback) {
        ClientCrashReportSaver.save(minecraft, throwable);
    }

    @Inject(method = "brief", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void lobotocraft$includeCauseChain(Throwable throwable,
                                                       CallbackInfoReturnable<String> callback) {
        callback.setReturnValue(ClientCrashReportSaver.describe(throwable));
    }
}
