package com.wzz.lobotocraft.client.alert;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 无衰减、循环播放的2D警报BGM音效实例
 */
public class AlertBGMSoundInstance extends AbstractTickableSoundInstance {

    private boolean stopped = false;

    public AlertBGMSoundInstance(SoundEvent sound) {
        super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.attenuation = Attenuation.NONE;
        this.relative = true; // 跟随玩家，不受位置影响
        this.volume = 0.8f;
        this.pitch = 1.0f;
        this.x = 0; this.y = 0; this.z = 0;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    public void forceStop() {
        stopped = true;
    }
}