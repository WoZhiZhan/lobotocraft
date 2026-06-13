package com.wzz.lobotocraft.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuiMusicInstance extends AbstractTickableSoundInstance {

    private final String category;
    private final float maxVolume;

    // 起始播放位置（秒）
    private final float startTimeSeconds;

    // 当前音量（用于淡入淡出）
    private float currentVolume;

    // 是否已经设置过起始位置
    private boolean hasSetStartPosition = false;

    public GuiMusicInstance(SoundEvent sound, float maxVolume, boolean loop, String category, float startTimeSeconds) {
        super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());

        this.category = category;
        this.maxVolume = maxVolume;
        this.startTimeSeconds = startTimeSeconds;

        // 初始音量设为0（准备淡入）
        this.currentVolume = 0.0f;
        this.volume = 0.0f;

        // 设置为循环
        this.looping = loop;

        // 设置音量相关
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (isStopped()) {
            return;
        }

        // 更新实际音量
        this.volume = currentVolume;
    }

    /**
     * 设置音量（用于淡入淡出）
     */
    public void setVolume(float volume) {
        this.currentVolume = Math.max(0.0f, Math.min(maxVolume, volume));
        this.volume = this.currentVolume;
    }

    /**
     * 获取当前音量
     */
    public float getVolume() {
        return currentVolume;
    }

    /**
     * 获取最大音量
     */
    public float getMaxVolume() {
        return maxVolume;
    }

    /**
     * 获取分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 获取起始播放位置（秒）
     */
    public float getStartTimeSeconds() {
        return startTimeSeconds;
    }

    /**
     * 标记已经设置过起始位置
     */
    public void markStartPositionSet() {
        this.hasSetStartPosition = true;
    }

    /**
     * 是否需要设置起始位置
     */
    public boolean needsStartPositionSet() {
        return startTimeSeconds > 0 && !hasSetStartPosition;
    }

    /**
     * 停止播放
     */
    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !isStopped();
    }
}