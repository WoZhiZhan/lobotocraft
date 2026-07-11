package com.wzz.lobotocraft.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;
    @Redirect(
            method = "render*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"
            )
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void redirectSetupAnim(
            EntityModel instance,
            net.minecraft.world.entity.Entity e,
            float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {

        if (e instanceof LivingEntity le
                && le.getPersistentData().getBoolean("isFreezeKiss")) {
            return;
        }
        instance.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}