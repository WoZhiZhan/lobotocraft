package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.mixin.PostChainAccessor;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

/** Owns the full-screen post chain used by active core suppressions. */
public final class CoreSuppressionPostProcessor {
    private static final int RETRY_DELAY_TICKS = 40;

    private static ResourceLocation loadedEffect;
    private static long clientTicks;
    private static long nextRetryTick;

    private CoreSuppressionPostProcessor() {
    }

    public static void tick() {
        clientTicks++;
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation desired = desiredEffect();
        if (desired == null || minecraft.level == null) {
            clear();
            return;
        }
        if (desired.equals(loadedEffect) && minecraft.gameRenderer.currentEffect() != null) return;
        if (clientTicks < nextRetryTick) return;

        minecraft.gameRenderer.shutdownEffect();
        try {
            minecraft.gameRenderer.loadEffect(desired);
        } catch (RuntimeException exception) {
            loadedEffect = null;
            nextRetryTick = clientTicks + RETRY_DELAY_TICKS;
            ModLogger.LOGGER.warn("Unable to load core suppression post effect {}", desired, exception);
            return;
        }
        if (minecraft.gameRenderer.currentEffect() == null) {
            loadedEffect = null;
            nextRetryTick = clientTicks + RETRY_DELAY_TICKS;
            return;
        }
        loadedEffect = desired;
        nextRetryTick = clientTicks;
    }

    public static void clear() {
        if (loadedEffect != null) Minecraft.getInstance().gameRenderer.shutdownEffect();
        loadedEffect = null;
        nextRetryTick = clientTicks;
    }

    public static boolean shouldRenderAfterGui() {
        return loadedEffect != null && CoreSuppressionClientState.isActive()
                && !shouldSkipForReadableChat()
                && Minecraft.getInstance().gameRenderer.currentEffect() != null;
    }

    public static boolean shouldSkipForReadableChat() {
        return loadedEffect != null
                && CoreSuppressionClientState.isActive(CoreSuppressionType.YESOD)
                && Minecraft.getInstance().screen instanceof ChatScreen;
    }

    public static boolean owns(PostChain effect) {
        return loadedEffect != null
                && Minecraft.getInstance().gameRenderer.currentEffect() == effect;
    }

    public static void prepareFrame(PostChain effect, float partialTick) {
        float stage = CoreSuppressionClientState.getVisualStage();
        float transition = CoreSuppressionClientState.getTransitionRate(partialTick);
        float effectTime = CoreSuppressionClientState.getEffectTime(partialTick);
        float farPlane = Minecraft.getInstance().gameRenderer.getDepthFar();
        for (PostPass pass : ((PostChainAccessor) (Object) effect).lobotocraft$getPasses()) {
            EffectInstance shader = pass.getEffect();
            setFloat(shader, "Stage", stage);
            setFloat(shader, "Transition", transition);
            setFloat(shader, "EffectTime", effectTime);
            setFloat(shader, "FarPlane", farPlane);
        }
    }

    private static void setFloat(EffectInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static ResourceLocation desiredEffect() {
        if (!CoreSuppressionClientState.isActive()) return null;
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        if (type == null || !type.isImplemented()) return null;
        if (!CoreSuppressionClientState.usesFullVisualEffects()
                && type == CoreSuppressionType.YESOD) {
            return ResourceUtil.createInstance("shaders/post/core_yesod_safe.json");
        }
        if (!CoreSuppressionClientState.usesFullVisualEffects()) {
            return null;
        }
        if (type == CoreSuppressionType.HOD) {
            int phase = Math.max(0, Math.min(2, CoreSuppressionClientState.getVisualStage()));
            return ResourceUtil.createInstance("shaders/post/core_hod_" + phase + ".json");
        }
        return ResourceUtil.createInstance("shaders/post/core_" + type.getId() + ".json");
    }
}
