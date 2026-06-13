package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.entity.abnormality.EntityEndBird;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggEye;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ConcurrentModificationException;

@Mixin(DimensionType.class)
public class MixinDimensionType {

    @Unique
    private static float lobotocraft$currentLight = -1f;

    @Unique
    private static float lobotocraft$targetLight = -1f;

    @Unique
    private static int lobotocraft$tickCounter = 0;

    @Unique
    private static final float LERP_SPEED = 0.0001f;

    @Final
    @Shadow
    private float ambientLight;

    @Inject(method = "ambientLight", at = @At("HEAD"), cancellable = true)
    private void ambientLight(CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null
                || mc.level.dimension() != ModDimensions.LOBOTO_KEY
                || mc.player == null) {
            lobotocraft$currentLight = -1f;
            lobotocraft$targetLight = -1f;
            return;
        }
        float originalLight = this.ambientLight;
        if (lobotocraft$currentLight < 0f) {
            lobotocraft$currentLight = originalLight;
            lobotocraft$targetLight = originalLight;
        }
        lobotocraft$tickCounter++;
        if (lobotocraft$tickCounter >= 20) {
            lobotocraft$tickCounter = 0;
            try {
                boolean found = false;
                for (Entity entity : EntityUtil.findAllEntities(mc.player, 300)) {
                    if (entity instanceof EntityEndBird
                            || (entity instanceof EntityLargeBird largeBird && largeBird.hasEscape())
                            || entity instanceof EntityEndBirdEggEye) {
                        found = true;
                        break;
                    }
                }
                lobotocraft$targetLight = found ? 0f : originalLight;
            } catch (ConcurrentModificationException ignored) {
            }
        }
        lobotocraft$currentLight += (lobotocraft$targetLight - lobotocraft$currentLight) * LERP_SPEED;
        if (Math.abs(lobotocraft$currentLight - lobotocraft$targetLight) < 0.001f) {
            lobotocraft$currentLight = lobotocraft$targetLight;
        }
        cir.setReturnValue(lobotocraft$currentLight);
    }
}