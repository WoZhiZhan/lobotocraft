package com.wzz.lobotocraft.client.fear;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Relative looping tinnitus layer that remains audible while world sounds are ducked. */
final class FearTinnitusSound extends AbstractTickableSoundInstance {
    private int level;
    private int totalTicks;
    private int remainingTicks;

    FearTinnitusSound(SoundEvent sound, int level, int durationTicks) {
        super(sound, SoundSource.VOICE, SoundInstance.createUnseededRandom());
        looping = true;
        relative = true;
        attenuation = Attenuation.NONE;
        upgrade(level, durationTicks);
    }

    void upgrade(int requestedLevel, int durationTicks) {
        level = Math.max(level, requestedLevel);
        totalTicks = Math.max(totalTicks, durationTicks);
        remainingTicks = Math.max(remainingTicks, durationTicks);
    }

    @Override
    public void tick() {
        if (remainingTicks-- <= 0) {
            stop();
            return;
        }
        int elapsed = totalTicks - remainingTicks;
        float attack = Math.min(1.0F, elapsed / 4.0F);
        float release = Math.min(1.0F, remainingTicks / 12.0F);
        float envelope = attack * release;
        volume = envelope * (level == 5 ? 1.75F : 0.64F);
        pitch = 1.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    void forceStop() {
        stop();
    }
}
