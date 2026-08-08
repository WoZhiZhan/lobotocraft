package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ShaderQuality;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class ResolutionShaderHandler {

    private static final int MIN_RELOAD_DELAY_TICKS = 20;
    private static final int MAX_RELOAD_DELAY_TICKS = 600;

    private static ShaderQuality current = ShaderQuality.NATIVE;
    private static int remainingTicks;
    private static long clientTicks;
    private static long nextReloadTick;
    private static int consecutiveReloadFailures;

    private ResolutionShaderHandler() {
    }

    /** Applies a shader quality until another quality is selected. */
    public static void apply(ShaderQuality quality) {
        apply(quality, 0);
    }

    /**
     * Applies a shader quality and optionally restores native rendering after the given duration.
     * Re-applying the already active quality only refreshes its duration; it does not synchronously
     * tear down and reload the same post chain again.
     */
    public static void apply(ShaderQuality quality, int durationTicks) {
        ShaderQuality requested = quality == null ? ShaderQuality.NATIVE : quality;
        boolean qualityChanged = requested != current;
        current = requested;
        remainingTicks = Math.max(0, durationTicks);

        Minecraft minecraft = Minecraft.getInstance();
        if (requested.effect == null) {
            if (qualityChanged || minecraft.gameRenderer.currentEffect() != null) {
                minecraft.gameRenderer.shutdownEffect();
            }
            resetReloadBackoff();
            return;
        }

        if (!qualityChanged && minecraft.gameRenderer.currentEffect() != null) {
            resetReloadBackoff();
            return;
        }

        if (qualityChanged) {
            minecraft.gameRenderer.shutdownEffect();
            resetReloadBackoff();
        }
        tryReload(minecraft, qualityChanged);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTicks++;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                apply(ShaderQuality.NATIVE);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.literal("画质已恢复"), true);
                }
                return;
            }
        }

        if (current.effect != null && minecraft.gameRenderer.currentEffect() == null) {
            tryReload(minecraft, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (current == ShaderQuality.NATIVE) return;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.shutdownEffect();
        resetReloadBackoff();
        tryReload(minecraft, true);
    }

    private static void tryReload(Minecraft minecraft, boolean force) {
        if (current.effect == null || (!force && clientTicks < nextReloadTick)) return;

        long sectionStarted = ClientPerformanceMonitor.beginSection("shader_reload");
        try {
            minecraft.gameRenderer.loadEffect(current.effect);
        } catch (RuntimeException exception) {
            registerReloadFailure();
            if (consecutiveReloadFailures == 1) {
                ModLogger.LOGGER.warn("Unable to load resolution shader {}; retrying with backoff",
                        current.effect, exception);
            }
            return;
        } finally {
            ClientPerformanceMonitor.endSection("shader_reload", sectionStarted);
        }

        if (minecraft.gameRenderer.currentEffect() != null) {
            resetReloadBackoff();
        } else {
            registerReloadFailure();
        }
    }

    private static void registerReloadFailure() {
        consecutiveReloadFailures = Math.min(consecutiveReloadFailures + 1, 30);
        nextReloadTick = clientTicks + reloadDelayTicks(consecutiveReloadFailures);
    }

    private static void resetReloadBackoff() {
        consecutiveReloadFailures = 0;
        nextReloadTick = clientTicks;
    }

    static int reloadDelayTicks(int consecutiveFailures) {
        int exponent = Math.max(0, Math.min(consecutiveFailures - 1, 5));
        return Math.min(MAX_RELOAD_DELAY_TICKS, MIN_RELOAD_DELAY_TICKS << exponent);
    }
}
