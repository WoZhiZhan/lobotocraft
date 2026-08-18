package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
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
    private static PostChain loadedChain;
    private static ResourceLocation failedEffect;
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
        PostChain currentChain = minecraft.gameRenderer.currentEffect();
        if (!shouldAttemptLoad(desired.equals(loadedEffect), currentChain == loadedChain,
                desired.equals(failedEffect), clientTicks, nextRetryTick)) return;

        minecraft.gameRenderer.shutdownEffect();
        loadedChain = null;
        try {
            minecraft.gameRenderer.loadEffect(desired);
        } catch (RuntimeException exception) {
            loadedEffect = null;
            failedEffect = desired;
            nextRetryTick = clientTicks + RETRY_DELAY_TICKS;
            ModLogger.LOGGER.warn("Unable to load core suppression post effect {}", desired, exception);
            return;
        }
        PostChain loaded = minecraft.gameRenderer.currentEffect();
        if (loaded == null) {
            loadedEffect = null;
            loadedChain = null;
            failedEffect = desired;
            nextRetryTick = clientTicks + RETRY_DELAY_TICKS;
            return;
        }
        loadedEffect = desired;
        loadedChain = loaded;
        failedEffect = null;
        nextRetryTick = clientTicks;
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (loadedChain != null && minecraft.gameRenderer.currentEffect() == loadedChain) {
            minecraft.gameRenderer.shutdownEffect();
        }
        loadedEffect = null;
        loadedChain = null;
        failedEffect = null;
        nextRetryTick = clientTicks;
    }

    public static boolean shouldRenderAfterGui() {
        return loadedChain != null && CoreSuppressionClientState.isActive()
                && shouldRenderAfterGui(CoreSuppressionClientState.getType())
                && !shouldSkipForReadableChat()
                && Minecraft.getInstance().gameRenderer.currentEffect() == loadedChain;
    }

    static boolean shouldRenderAfterGui(CoreSuppressionType type) {
        return type != CoreSuppressionType.CHESED;
    }

    public static boolean shouldSkipForReadableChat() {
        return loadedChain != null
                && CoreSuppressionClientState.isActive(CoreSuppressionType.YESOD)
                && Minecraft.getInstance().screen instanceof ChatScreen;
    }

    public static boolean isReady() {
        return loadedChain != null
                && Minecraft.getInstance().gameRenderer.currentEffect() == loadedChain;
    }

    public static boolean owns(PostChain effect) {
        return effect != null && effect == loadedChain
                && Minecraft.getInstance().gameRenderer.currentEffect() == loadedChain;
    }

    static boolean shouldAttemptLoad(boolean desiredMatchesLoaded, boolean ownsCurrentChain,
                                     boolean desiredMatchesFailed, long currentTick,
                                     long retryTick) {
        if (desiredMatchesLoaded && ownsCurrentChain) return false;
        return !desiredMatchesFailed || currentTick >= retryTick;
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
            GeburahVisualEffectType effectType = GeburahEffectCompositor.dominantType();
            setFloat(shader, "EffectKind", effectType == null ? 0.0F : effectType.shaderKind());
            setFloat(shader, "EffectProgress", GeburahEffectCompositor.shaderProgress(partialTick));
            setFloat(shader, "EffectIntensity", GeburahEffectCompositor.shaderIntensity());
            setFloat(shader, "HitSeverity", GeburahEffectCompositor.shaderHitSeverity());
            setFloat(shader, "EgoAttack", GeburahEffectCompositor.shaderEgoAttack());
            setFloat(shader, "GeburahProximity", GeburahEffectCompositor.shaderProximity());
            setVec2(shader, "EffectOrigin", GeburahEffectCompositor.shaderOrigin());
            setVec2(shader, "EffectDirection", GeburahEffectCompositor.shaderDirection());
        }
    }

    private static void setFloat(EffectInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static void setVec2(EffectInstance shader, String name, float[] values) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null && values != null && values.length >= 2) {
            uniform.set(values[0], values[1]);
        }
    }

    private static ResourceLocation desiredEffect() {
        if (!CoreSuppressionClientState.isActive()) {
            return GeburahEffectCompositor.usesShader()
                    ? ResourceUtil.createInstance("shaders/post/core_geburah.json") : null;
        }
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        if (type == null || !type.isImplemented()) return null;
        if (type == CoreSuppressionType.GEBURAH) {
            return CoreSuppressionClientState.usesFullVisualEffects()
                    && GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.FULL
                    ? ResourceUtil.createInstance("shaders/post/core_geburah.json") : null;
        }
        if (!CoreSuppressionClientState.usesFullVisualEffects()
                && type == CoreSuppressionType.YESOD) {
            return ResourceUtil.createInstance("shaders/post/core_yesod_safe.json");
        }
        if (!CoreSuppressionClientState.usesFullVisualEffects()) {
            return null;
        }
        if (type == CoreSuppressionType.CHESED) {
            return ResourceUtil.createInstance("shaders/post/core_chesed.json");
        }
        if (type == CoreSuppressionType.HOD) {
            int phase = Math.max(0, Math.min(2, CoreSuppressionClientState.getVisualStage()));
            return ResourceUtil.createInstance("shaders/post/core_hod_" + phase + ".json");
        }
        return ResourceUtil.createInstance("shaders/post/core_" + type.getId() + ".json");
    }
}
