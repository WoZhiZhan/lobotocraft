package com.wzz.lobotocraft.client.screen.api;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.sounds.SoundEvent;

/**
 * 异想体音乐适配器
 * 将IAbnormality的音乐配置桥接到IMusicScreen
 * 使用方法：
 * 让异想体管理界面实现IMusicScreen，并使用此适配器委托给异想体
 */
public class AbnormalityMusicAdapter {
    
    private final IAbnormality abnormality;
    
    public AbnormalityMusicAdapter(IAbnormality abnormality) {
        this.abnormality = abnormality;
    }
    
    /**
     * 检查是否应该播放音乐
     */
    public boolean shouldPlayMusic() {
        return abnormality.hasManagementMusic() && abnormality.getManagementMusic() != null;
    }
    
    /**
     * 获取音乐SoundEvent
     */
    public SoundEvent getSoundEvent() {
        return abnormality.getManagementMusic();
    }
    
    /**
     * 获取淡入时间
     */
    public int getFadeInDuration() {
        return abnormality.getMusicFadeInDuration();
    }
    
    /**
     * 获取淡出时间
     */
    public int getFadeOutDuration() {
        return abnormality.getMusicFadeOutDuration();
    }
    
    /**
     * 获取最大音量
     */
    public float getMaxVolume() {
        return abnormality.getMusicMaxVolume();
    }
    
    /**
     * 是否循环播放
     */
    public boolean shouldLoop() {
        return abnormality.isMusicLooping();
    }
    
    /**
     * 获取音乐分类
     */
    public String getMusicCategory() {
        return abnormality.getMusicCategory();
    }
    
    /**
     * 创建IMusicScreen的默认实现
     * 使用方法：在Screen中implements IMusicScreen，然后委托给这个方法
     */
    public static IMusicScreen createMusicScreen(IAbnormality abnormality) {
        return new IMusicScreen() {
            private final AbnormalityMusicAdapter adapter = new AbnormalityMusicAdapter(abnormality);
            
            @Override
            public SoundEvent getSoundEvent() {
                return adapter.shouldPlayMusic() ? adapter.getSoundEvent() : null;
            }
            
            @Override
            public int getFadeInDuration() {
                return adapter.getFadeInDuration();
            }
            
            @Override
            public int getFadeOutDuration() {
                return adapter.getFadeOutDuration();
            }
            
            @Override
            public float getMaxVolume() {
                return adapter.getMaxVolume();
            }
            
            @Override
            public boolean shouldLoop() {
                return adapter.shouldLoop();
            }
            
            @Override
            public String getMusicCategory() {
                return adapter.getMusicCategory();
            }
        };
    }
}