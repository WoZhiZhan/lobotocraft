package com.wzz.lobotocraft.client.screen.api;

import net.minecraft.sounds.SoundEvent;

/**
 * GUI音乐播放接口
 * 实现此接口的Screen会在打开时播放背景音乐，带淡入淡出效果
 */
public interface IMusicScreen {
    
    /**
     * 获取要播放的音乐
     * @return 音乐的ResourceLocation
     */
    SoundEvent getSoundEvent();

    /**
     * 获取淡入时间（tick）
     * @return 淡入持续的tick数，默认20 (1秒)
     */
    default int getFadeInDuration() {
        return 20;
    }
    
    /**
     * 获取淡出时间（tick）
     * @return 淡出持续的tick数，默认20 (1秒)
     */
    default int getFadeOutDuration() {
        return 20;
    }
    
    /**
     * 获取音乐最大音量
     * @return 0.0-1.0之间的值，默认0.5
     */
    default float getMaxVolume() {
        return 0.5f;
    }
    
    /**
     * 是否循环播放
     * @return true表示循环，默认true
     */
    default boolean shouldLoop() {
        return true;
    }
    
    /**
     * 获取音乐分类标识
     * 相同标识的Screen会共享播放进度
     * @return 分类标识，默认使用类名
     */
    default String getMusicCategory() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 当音乐开始播放时调用
     */
    default void onMusicStart() {}
    
    /**
     * 当音乐停止播放时调用
     */
    default void onMusicStop() {}
}
