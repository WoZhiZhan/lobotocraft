package com.wzz.lobotocraft.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ScreenDistortionEffect;
import com.wzz.lobotocraft.client.ScreenDistortionState;
import com.wzz.lobotocraft.client.audio.GuiMusicManager;
import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.client.renderer.ExplosionRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityLargeBird;
import com.wzz.lobotocraft.init.ModShaders;
import com.wzz.lobotocraft.network.ClientPacketHandler;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeModClientEvent {
    private static final ResourceLocation LARGE_BIRD_CHARM = ResourceUtil.createInstance("textures/gui/large_bird_charm.png");
    private static final List<LargeBirdCharmEffect> active_charms = new ArrayList<>();

    /**
     * 客户端Tick事件
     * 用于更新音乐管理器的淡入淡出效果
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && !mc.isPaused()) {
                GuiMusicManager.getInstance().tick();
            }
            ClientPacketHandler.tick();
            ++ModShaders.renderTime;
            ScreenDistortionState.tick();
            CoreSuppressionClientState.tick();
        }
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!Minecraft.getInstance().isPaused() && event.phase == TickEvent.Phase.START) {
            ModShaders.renderFrame = event.renderTickTime;
        }
    }

    /**
     * 当玩家登出或切换世界时，清理音乐状态
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            GuiMusicManager.getInstance().clearAll();
            active_charms.clear();
            CoreSuppressionClientState.update(false, -1, "", 0, 0, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        Entity cameraEntity = camera.getEntity();
        int perspective = 0;
        if (cameraEntity instanceof Player player) {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (player == localPlayer) {
                // 0: 第一人称, 1: 第三人称(背后), 2: 第三人称(前面)
                perspective = Minecraft.getInstance().options.getCameraType().ordinal();
            }
        }
        Double animationY = ClientPacketHandler.getCameraAbsoluteY(event.getPartialTick());
        if (animationY != null) {
            Vec3 currentPos = camera.getPosition();
            double offset = 0.63d;
            if (perspective == 1) {
                offset = 1.5d;
            }
            if (perspective == 2) {
                offset = 0.69d;
            }
            camera.setPosition(currentPos.x, animationY + offset, currentPos.z);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof TitleScreen) {
            TimerEntry.shutdownAll();
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player || event.getEntity() instanceof EntityLargeBird) {
            active_charms.clear();
        }
    }

    public static void clearAllBorderEffects() {
        active_charms.clear();
    }

    public static void addBorderEffect(LargeBirdCharmEffect effect) {
        active_charms.add(effect);
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        ExplosionRenderer.onRenderWorldLast(event);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        active_charms.removeIf(LargeBirdCharmEffect::isExpired);
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        for (LargeBirdCharmEffect effect : active_charms) {
            renderBorder(guiGraphics, effect, screenWidth, screenHeight);
        }
        if (ScreenDistortionState.isActive()) {
            ScreenDistortionEffect.renderScreenDistortion(
                    guiGraphics,
                    ScreenDistortionState.getCurrentIntensity()
            );
        }
    }

    private static void renderBorder(GuiGraphics guiGraphics, LargeBirdCharmEffect effect, int screenWidth, int screenHeight) {
        // 获取当前透明度
        int alpha = effect.getAlpha();
        if (alpha <= 0) return;

        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 设置颜色和透明度（RGBA）
        // 白色(1, 1, 1) + alpha
        float alphaFloat = alpha / 255.0f;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alphaFloat);

        // 渲染全屏边框
        // 假设边框材质是256x256，渲染到全屏
        guiGraphics.blit(LARGE_BIRD_CHARM, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);

        // 恢复颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    public static class LargeBirdCharmEffect {
        private final long startTime;
        private final int duration; // 持续时间（毫秒）

        public LargeBirdCharmEffect(int durationMs) {
            this.startTime = System.currentTimeMillis();
            this.duration = durationMs;
        }

        /**
         * 获取当前透明度（0-255）
         * 淡入淡出效果：0 -> 255 -> 0
         */
        public int getAlpha() {
            if (duration == 0) return 0;
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= duration) {
                return 0; // 动画结束
            }
            float progress = (float) elapsed / duration;

            // 使用sin函数实现平滑的淡入淡出
            // sin(progress * PI) 会从 0 -> 1 -> 0
            float alpha = (float) Math.sin(progress * Math.PI);
            return (int) (alpha * 255);
        }

        /**
         * 检查效果是否已结束
         */
        public boolean isExpired() {
            if (duration == 0)
                return true;
            return System.currentTimeMillis() - startTime >= duration;
        }
    }
}
