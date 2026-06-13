package com.wzz.lobotocraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 全屏渲染器类 - 支持淡入淡出动画、持续时间控制和多图轮播
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FullScreenRenderer {

    private static FullScreenRenderer instance;

    public enum RenderState {
        HIDDEN,
        FADE_IN,
        SHOWING,
        TEXTURE_SWITCH,
        FADE_OUT
    }

    // 配置参数
    private int fadeInDuration = 1000;
    private int fadeOutDuration = 1000;
    private int showDuration = 3000;

    // 状态管理
    private RenderState currentState = RenderState.HIDDEN;
    private long startTime = 0;
    private long stateStartTime = 0;

    // 渲染内容
    private ResourceLocation texture;
    private ResourceLocation nextTexture;
    private int textureSwitchDuration = 500;
    private int backgroundColor = 0x80000000;
    private String text = "";
    private int textColor = 0xFFFFFF;

    // 新增：多图轮播队列
    private Queue<ResourceLocation> textureQueue = new LinkedList<>();
    private long perImageDuration = 3000;     // 每张图显示时长
    private long imageShowStartTime = 0;      // 当前这张图开始显示的时间
    private List<ResourceLocation> originalTextureList = new ArrayList<>(); // 保存原始列表用于循环/引用

    // 回调接口
    public interface RenderCallback {
        void onRenderComplete();
        void onRenderStart();
    }

    private RenderCallback callback;

    private FullScreenRenderer() {}

    public static FullScreenRenderer getInstance() {
        if (instance == null) {
            instance = new FullScreenRenderer();
        }
        return instance;
    }

    // ==================== 原有方法 ====================

    public void startRender(int duration) {
        startRender(duration, null);
    }

    public void startRender(int duration, RenderCallback callback) {
        this.showDuration = duration;
        this.callback = callback;
        this.currentState = RenderState.FADE_IN;
        this.startTime = System.currentTimeMillis();
        this.stateStartTime = this.startTime;
        this.imageShowStartTime = this.startTime;

        if (callback != null) {
            callback.onRenderStart();
        }
    }

    public void forceStop() {
        if (currentState == RenderState.FADE_IN || currentState == RenderState.SHOWING || currentState == RenderState.TEXTURE_SWITCH) {
            this.currentState = RenderState.FADE_OUT;
            this.stateStartTime = System.currentTimeMillis();
        }
    }

    public void stopImmediately() {
        this.currentState = RenderState.HIDDEN;
        this.textureQueue.clear();
        this.nextTexture = null;
        if (callback != null) {
            callback.onRenderComplete();
        }
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    public void setBackgroundTexture(ResourceLocation texture) {
        setBackgroundTexture(texture, 500);
    }

    public void setBackgroundTexture(ResourceLocation texture, int switchDuration) {
        if (currentState == RenderState.SHOWING && this.texture != null && !this.texture.equals(texture)) {
            this.nextTexture = texture;
            this.textureSwitchDuration = switchDuration;
            this.currentState = RenderState.TEXTURE_SWITCH;
            this.stateStartTime = System.currentTimeMillis();
        } else {
            this.texture = texture;
        }
    }

    public void setBackgroundTextureImmediately(ResourceLocation texture) {
        this.texture = texture;
        this.nextTexture = null;
    }

    public void switchTexture(ResourceLocation newTexture, int switchDuration) {
        if (currentState == RenderState.SHOWING) {
            this.nextTexture = newTexture;
            this.textureSwitchDuration = switchDuration;
            this.currentState = RenderState.TEXTURE_SWITCH;
            this.stateStartTime = System.currentTimeMillis();
        } else {
            this.texture = newTexture;
        }
    }

    public void switchTexture(ResourceLocation newTexture) {
        switchTexture(newTexture, 500);
    }

    public void setText(String text, int color) {
        this.text = text;
        this.textColor = color;
    }

    public void setFadeDuration(int fadeIn, int fadeOut) {
        this.fadeInDuration = fadeIn;
        this.fadeOutDuration = fadeOut;
    }

    public RenderState getCurrentState() {
        return currentState;
    }

    public boolean isRendering() {
        return currentState != RenderState.HIDDEN;
    }

    // ==================== 新增：多图轮播方法 ====================

    /**
     * 设置纹理队列（多图轮播）
     * @param textures 纹理列表
     * @param perImageDuration 每张图显示时间（毫秒）
     * @param switchDuration 切换动画时长（毫秒）
     */
    public void setTextureQueue(List<ResourceLocation> textures, long perImageDuration, int switchDuration) {
        this.originalTextureList = new ArrayList<>(textures);
        this.textureQueue.clear();
        this.textureQueue.addAll(textures);
        this.perImageDuration = perImageDuration;
        this.textureSwitchDuration = switchDuration;

        if (!textures.isEmpty()) {
            // 取出第一张作为当前纹理
            ResourceLocation first = this.textureQueue.poll();
            if (this.texture == null || this.currentState == RenderState.HIDDEN) {
                this.texture = first;
            } else {
                // 如果正在渲染，切换到第一张
                switchTexture(first, switchDuration);
            }
            this.imageShowStartTime = System.currentTimeMillis();
        }
    }

    /**
     * 设置纹理队列（使用默认切换动画时长）
     */
    public void setTextureQueue(List<ResourceLocation> textures, long perImageDuration) {
        setTextureQueue(textures, perImageDuration, 500);
    }

    /**
     * 获取纹理队列中剩余的图片数量
     */
    public int getRemainingImagesCount() {
        return textureQueue.size();
    }

    // ==================== 状态更新（修改后） ====================

    private void updateState() {
        if (currentState == RenderState.HIDDEN) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long stateElapsed = currentTime - stateStartTime;

        switch (currentState) {
            case FADE_IN:
                if (stateElapsed >= fadeInDuration) {
                    currentState = RenderState.SHOWING;
                    stateStartTime = currentTime;
                    imageShowStartTime = currentTime; // 重置图片显示计时
                }
                break;

            case SHOWING:
                // 检查是否需要自动切换到下一张图（轮播逻辑）
                long imageElapsed = currentTime - imageShowStartTime;
                if (!textureQueue.isEmpty() && imageElapsed >= perImageDuration) {
                    // 切换到队列中的下一张
                    ResourceLocation next = textureQueue.poll();
                    this.nextTexture = next;
                    this.currentState = RenderState.TEXTURE_SWITCH;
                    this.stateStartTime = currentTime;
                } else if (showDuration > 0 && stateElapsed >= showDuration) {
                    // 总显示时间到了，开始淡出
                    currentState = RenderState.FADE_OUT;
                    stateStartTime = currentTime;
                }
                break;

            case TEXTURE_SWITCH:
                if (stateElapsed >= textureSwitchDuration) {
                    // 切换完成
                    texture = nextTexture;
                    nextTexture = null;
                    currentState = RenderState.SHOWING;
                    stateStartTime = currentTime;
                    imageShowStartTime = currentTime; // 重置图片显示计时

                    // 如果队列空了且 showDuration 为 0 或负数，立即淡出
                    // 否则继续显示直到 showDuration 用完
                }
                break;

            case FADE_OUT:
                if (stateElapsed >= fadeOutDuration) {
                    currentState = RenderState.HIDDEN;
                    textureQueue.clear();
                    nextTexture = null;
                    if (callback != null) {
                        callback.onRenderComplete();
                    }
                }
                break;
        }
    }

    // ==================== 透明度计算（不变） ====================

    private float calculateAlpha() {
        if (currentState == RenderState.HIDDEN) {
            return 0.0f;
        }

        long currentTime = System.currentTimeMillis();
        long stateElapsed = currentTime - stateStartTime;

        switch (currentState) {
            case FADE_IN:
                return Mth.clamp((float) stateElapsed / fadeInDuration, 0.0f, 1.0f);
            case SHOWING:
                return 1.0f;
            case TEXTURE_SWITCH:
                return 1.0f;
            case FADE_OUT:
                return 1.0f - Mth.clamp((float) stateElapsed / fadeOutDuration, 0.0f, 1.0f);
            default:
                return 0.0f;
        }
    }

    private float calculateSwitchProgress() {
        if (currentState != RenderState.TEXTURE_SWITCH) {
            return 0.0f;
        }

        long currentTime = System.currentTimeMillis();
        long stateElapsed = currentTime - stateStartTime;
        return Mth.clamp((float) stateElapsed / textureSwitchDuration, 0.0f, 1.0f);
    }

    // ==================== 渲染方法（不变） ====================

    private void render(GuiGraphics guiGraphics) {
        if (currentState == RenderState.HIDDEN) {
            return;
        }

        updateState();

        float alpha = calculateAlpha();
        if (alpha <= 0.0f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (texture != null) {
            if (currentState == RenderState.TEXTURE_SWITCH && nextTexture != null) {
                float switchProgress = calculateSwitchProgress();

                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * (1.0f - switchProgress));
                guiGraphics.blit(texture, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);

                RenderSystem.setShaderTexture(0, nextTexture);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * switchProgress);
                guiGraphics.blit(nextTexture, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
            } else {
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                guiGraphics.blit(texture, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight);
            }
        } else {
            int alphaColor = ((int) (alpha * ((backgroundColor >> 24) & 0xFF)) << 24) | (backgroundColor & 0xFFFFFF);
            guiGraphics.fill(0, 0, screenWidth, screenHeight, alphaColor);
        }

        if (!text.isEmpty()) {
            int textAlpha = (int) (alpha * 255);
            int finalTextColor = (textAlpha << 24) | (textColor & 0xFFFFFF);

            int textWidth = mc.font.width(text);
            int textX = (screenWidth - textWidth) / 2;
            int textY = (screenHeight - mc.font.lineHeight) / 2;

            guiGraphics.drawString(mc.font, text, textX, textY, finalTextColor);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) {
            getInstance().render(event.getGuiGraphics());
        }
    }

    // ==================== Builder 类 ====================

    public static class Builder {
        private int duration = 3000;
        private int fadeIn = 1000;
        private int fadeOut = 1000;
        private ResourceLocation texture;
        private int backgroundColor = 0x80000000;
        private String text = "";
        private int textColor = 0xFFFFFF;
        private RenderCallback callback;
        private List<ResourceLocation> textureList;
        private int perImageDuration = 3000;
        private int switchDuration = 500;

        public Builder duration(int duration) {
            this.duration = duration;
            return this;
        }

        public Builder fadeIn(int fadeIn) {
            this.fadeIn = fadeIn;
            return this;
        }

        public Builder fadeOut(int fadeOut) {
            this.fadeOut = fadeOut;
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder textures(List<ResourceLocation> textures, int perImageDuration, int switchDuration) {
            this.textureList = textures;
            this.perImageDuration = perImageDuration;
            this.switchDuration = switchDuration;
            return this;
        }

        public Builder textures(List<ResourceLocation> textures, int perImageDuration) {
            return textures(textures, perImageDuration, 500);
        }

        public Builder textures(List<ResourceLocation> textures) {
            return textures(textures, 3000, 500);
        }

        public Builder backgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }

        public Builder text(String text, int color) {
            this.text = text;
            this.textColor = color;
            return this;
        }

        public Builder callback(RenderCallback callback) {
            this.callback = callback;
            return this;
        }

        public void start() {
            FullScreenRenderer renderer = FullScreenRenderer.getInstance();
            renderer.setFadeDuration(fadeIn, fadeOut);
            renderer.setBackgroundColor(backgroundColor);
            renderer.setText(text, textColor);

            if (textureList != null && !textureList.isEmpty()) {
                renderer.setTextureQueue(textureList, perImageDuration, switchDuration);
            } else if (texture != null) {
                renderer.setBackgroundTexture(texture);
            }

            renderer.startRender(duration, callback);
        }
    }
}