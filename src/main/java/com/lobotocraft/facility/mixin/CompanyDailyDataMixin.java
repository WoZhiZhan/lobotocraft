package com.lobotocraft.facility.mixin;

import com.lobotocraft.facility.FacilityManager;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.capability.CompanyDailyData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompanyDailyData.class, remap = false)
public abstract class CompanyDailyDataMixin {
    @Shadow
    private transient ServerPlayer owner;

    @Inject(method = "getCurrentDay", at = @At("HEAD"), cancellable = true)
    private void facility$useServerDay(CallbackInfoReturnable<Integer> callback) {
        if (owner != null) callback.setReturnValue(FacilityDayData.get(owner.serverLevel()).getDay());
    }

    @Inject(method = "hasCompletedTodayWork", at = @At("HEAD"), cancellable = true)
    private void facility$useFacilityCompletion(CallbackInfoReturnable<Boolean> callback) {
        if (owner != null) callback.setReturnValue(FacilityManager.canCompleteDay(owner));
    }
}
