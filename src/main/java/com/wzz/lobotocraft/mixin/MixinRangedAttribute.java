package com.wzz.lobotocraft.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedAttribute.class)
public class MixinRangedAttribute {
    @Mutable
    @Shadow @Final private double maxValue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(String p_22310_, double p_22311_, double p_22312_, double p_22313_, CallbackInfo ci) {
        this.maxValue = Double.MAX_VALUE;
    }
}
