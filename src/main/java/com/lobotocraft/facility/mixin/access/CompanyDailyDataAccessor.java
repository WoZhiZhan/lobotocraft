package com.lobotocraft.facility.mixin.access;

import com.wzz.lobotocraft.capability.CompanyDailyData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CompanyDailyData.class, remap = false)
public interface CompanyDailyDataAccessor {
    @Accessor("currentDay")
    int facility$getStoredDay();
}
