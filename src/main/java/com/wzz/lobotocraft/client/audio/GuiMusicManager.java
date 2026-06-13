package com.wzz.lobotocraft.client.audio;

import com.wzz.lobotocraft.client.screen.api.IMusicScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class GuiMusicManager {

    private static GuiMusicManager instance;

    // 当前播放的音乐实例
    private GuiMusicInstance currentMusic;

    // 当前绑定的Screen
    private IMusicScreen currentScreen;

    // 淡入淡出状态
    private FadeState fadeState = FadeState.NONE;
    private int fadeTimer = 0;
    private int fadeDuration = 0;

    // 记录每个分类的播放位置（以秒为单位）
    private final Map<String, Float> playPositions = new HashMap<>();

    // 等待设置位置的计时器
    private int waitForPositionSet = 0;

    // 当前音乐的起始位置（用于计算实际播放时间）
    private float currentMusicStartPosition = 0;

    // 记录音乐实际开始播放的时间（tick）
    private long musicStartTick = 0;

    private GuiMusicManager() {}

    public static GuiMusicManager getInstance() {
        if (instance == null) {
            instance = new GuiMusicManager();
        }
        return instance;
    }

    /**
     * 当Screen打开时调用
     */
    public void onScreenOpen(IMusicScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        this.currentScreen = screen;
        String category = screen.getMusicCategory();

        // 如果是相同分类的音乐且正在播放
        if (currentMusic != null && currentMusic.getCategory().equals(category) && !currentMusic.isStopped()) {
            if (fadeState == FadeState.FADE_OUT) {
                // 正在淡出，改为淡入
                startFadeIn(screen.getFadeInDuration());
            }
            return;
        }

        // 停止当前音乐
        stopCurrentMusic();

        // 创建新的音乐实例
        SoundEvent musicLocation = screen.getSoundEvent();
        if (musicLocation == null) return;

        // 获取该分类上次播放的位置（秒）
        float savedPosition = playPositions.getOrDefault(category, 0.0f);
        currentMusicStartPosition = savedPosition;

        currentMusic = new GuiMusicInstance(
                musicLocation,
                screen.getMaxVolume(),
                screen.shouldLoop(),
                category,
                savedPosition
        );

        mc.getSoundManager().play(currentMusic);

        // 记录音乐开始播放的时间
        musicStartTick = getCurrentTick();

        // 如果需要设置起始位置
        if (savedPosition > 0) {
            waitForPositionSet = 20;
        }

        // 开始淡入
        startFadeIn(screen.getFadeInDuration());

        screen.onMusicStart();
    }

    /**
     * 当Screen关闭时调用
     */
    public void onScreenClose(IMusicScreen screen) {
        if (currentScreen != screen) return;

        // 保存当前播放位置
        if (currentMusic != null && !currentMusic.isStopped()) {
            String category = currentMusic.getCategory();

            // 计算实际播放时间
            long currentTick = getCurrentTick();
            long playedTicks = currentTick - musicStartTick;
            float playedSeconds = playedTicks / 20.0f;

            // 实际位置 = 起始位置 + 已播放时间
            float actualPosition = currentMusicStartPosition + playedSeconds;

            // 只有播放了超过1秒才保存位置（避免保存错误的小值）
            if (playedSeconds > 1.0f) {
                playPositions.put(category, actualPosition);
            }
            // 开始淡出
            startFadeOut(screen.getFadeOutDuration());
        }

        currentScreen = null;
    }

    /**
     * 每tick更新
     */
    public void tick() {
        if (currentMusic == null) return;

        // 如果正在等待设置位置
        if (waitForPositionSet > 0) {
            waitForPositionSet--;

            if (waitForPositionSet == 0 && currentMusic.needsStartPositionSet()) {
                // 尝试设置播放位置
                float startTime = currentMusic.getStartTimeSeconds();
                boolean success = AudioPositionUtil.setPlaybackPosition(currentMusic, startTime);

                if (success) {
                    currentMusic.markStartPositionSet();
                } else {
                    // 设置失败，更新起始位置为0
                    currentMusicStartPosition = 0;
                }
            }
        }

        // 更新淡入淡出
        updateFade();

        // updateFade()可能会将currentMusic设为null，需要再次检查
        if (currentMusic == null) return;

        // 检查音乐是否结束
        if (currentMusic.isStopped() && fadeState == FadeState.NONE) {
            if (currentScreen != null) {
                currentScreen.onMusicStop();
            }
            currentMusic = null;
        }
    }

    /**
     * 获取当前游戏tick
     */
    private long getCurrentTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.getGameTime();
        }
        return 0;
    }

    /**
     * 开始淡入
     */
    private void startFadeIn(int duration) {
        fadeState = FadeState.FADE_IN;
        fadeTimer = 0;
        fadeDuration = duration;
        if (currentMusic != null) {
            currentMusic.setVolume(0.0f);
        }
    }

    /**
     * 开始淡出
     */
    private void startFadeOut(int duration) {
        fadeState = FadeState.FADE_OUT;
        fadeTimer = 0;
        fadeDuration = duration;
    }

    /**
     * 更新淡入淡出效果
     */
    private void updateFade() {
        if (fadeState == FadeState.NONE || currentMusic == null) return;

        fadeTimer++;

        float progress = Math.min(1.0f, (float) fadeTimer / fadeDuration);

        if (fadeState == FadeState.FADE_IN) {
            // 淡入：音量从0到最大
            float targetVolume = currentScreen != null ? currentScreen.getMaxVolume() : 1.0f;
            currentMusic.setVolume(targetVolume * progress);

            if (fadeTimer >= fadeDuration) {
                fadeState = FadeState.NONE;
            }
        } else if (fadeState == FadeState.FADE_OUT) {
            // 淡出：音量从当前到0
            float startVolume = currentMusic.getVolume();
            currentMusic.setVolume(startVolume * (1.0f - progress));

            if (fadeTimer >= fadeDuration) {
                stopCurrentMusic();
                fadeState = FadeState.NONE;
                return;
            }
        }
    }

    /**
     * 停止当前音乐
     */
    private void stopCurrentMusic() {
        if (currentMusic == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(currentMusic);
        }

        if (currentScreen != null) {
            currentScreen.onMusicStop();
        }

        currentMusic = null;
        waitForPositionSet = 0;
        currentMusicStartPosition = 0;
        musicStartTick = 0;
    }

    /**
     * 重置某个分类的播放位置
     */
    public void resetPosition(String category) {
        playPositions.remove(category);
    }

    /**
     * 清空所有记录
     */
    public void clearAll() {
        stopCurrentMusic();
        playPositions.clear();
        fadeState = FadeState.NONE;
        currentScreen = null;
    }

    /**
     * 获取当前音量
     */
    public float getCurrentVolume() {
        return currentMusic != null ? currentMusic.getVolume() : 0.0f;
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return currentMusic != null && !currentMusic.isStopped();
    }

    /**
     * 获取当前播放位置（估算值）
     */
    public float getCurrentPosition() {
        if (currentMusic == null) return 0;

        long currentTick = getCurrentTick();
        long playedTicks = currentTick - musicStartTick;
        float playedSeconds = playedTicks / 20.0f;

        return currentMusicStartPosition + playedSeconds;
    }

    // 淡入淡出状态枚举
    private enum FadeState {
        NONE,
        FADE_IN,
        FADE_OUT
    }
}