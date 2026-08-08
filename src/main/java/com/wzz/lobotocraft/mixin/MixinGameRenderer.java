package com.wzz.lobotocraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.core.CoreSuppressionPostProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Controls whether the owned core post chain runs before or after GUI rendering. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Redirect(
            method = "render(FJZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/PostChain;process(F)V")
    )
    private void lobotocraft$deferCoreEffect(PostChain effect, float partialTick) {
        if (CoreSuppressionPostProcessor.owns(effect)) {
            CoreSuppressionPostProcessor.prepareFrame(effect, partialTick);
            if (CoreSuppressionPostProcessor.shouldRenderAfterGui()
                    || CoreSuppressionPostProcessor.shouldSkipForReadableChat()) return;
        }
        effect.process(partialTick);
    }

    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void lobotocraft$renderCoreEffectAfterGui(float partialTick, long nanoTime,
                                                       boolean renderLevel, CallbackInfo callback) {
        if (!CoreSuppressionPostProcessor.shouldRenderAfterGui()) return;
        Minecraft minecraft = Minecraft.getInstance();
        PostChain effect = minecraft.gameRenderer.currentEffect();
        if (effect == null) return;

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        effect.process(partialTick);
        minecraft.getMainRenderTarget().bindWrite(true);
    }
}
