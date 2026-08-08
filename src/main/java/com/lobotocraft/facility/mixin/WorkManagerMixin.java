package com.lobotocraft.facility.mixin;

import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorkManager.class, remap = false)
public abstract class WorkManagerMixin {
    @Inject(
        method = "startWork(Lnet/minecraft/server/level/ServerPlayer;Lcom/wzz/lobotocraft/entity/base/AbstractAbnormality;Lcom/wzz/lobotocraft/work/WorkType;Lcom/wzz/lobotocraft/work/WorkType;ZZ)Z",
        at = @At("RETURN")
    )
    private static void facility$afterWorkStarted(ServerPlayer player, AbstractAbnormality abnormality,
                                                  WorkType requestedWorkType, WorkType resolvedWorkType,
                                                  boolean continuousMode,
                                                  boolean openClientScreen,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) FacilityManager.onWorkStarted(player, abnormality);
    }
}
