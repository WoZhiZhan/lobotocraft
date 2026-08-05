package com.wzz.lobotocraft.client.audio;

import com.wzz.lobotocraft.client.alert.EscapeAlertManager;
import com.wzz.lobotocraft.client.core.CoreSuppressionClientState;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

public final class NormalWorkMusicManager {
    private static final int MIN_DELAY_TICKS = 5 * 20;
    private static final int MAX_DELAY_TICKS = Math.round(11.19F * 20.0F);
    private static final NormalWorkMusicManager INSTANCE = new NormalWorkMusicManager();

    private NormalMusicInstance currentMusic;
    private int delayTicks = -1;

    private NormalWorkMusicManager() {
    }

    public static NormalWorkMusicManager getInstance() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.player.level().dimension() != ModDimensions.LOBOTO_KEY) {
            clear();
            return;
        }

        // Facility music owns the soundtrack while the player is in the company.
        minecraft.getMusicManager().stopPlaying();
        if (CoreSuppressionClientState.isActive()
                || EscapeAlertManager.getInstance().isBgmActive()
                || GuiMusicManager.getInstance().isPlaying()) {
            suspend();
            return;
        }

        if (currentMusic != null) {
            if (!currentMusic.isStopped()
                    && minecraft.getSoundManager().isActive(currentMusic)) return;
            currentMusic = null;
            delayTicks = randomDelay(minecraft);
        }
        if (delayTicks < 0) {
            delayTicks = randomDelay(minecraft);
            return;
        }
        if (delayTicks-- > 0) return;
        playRandomTrack(minecraft);
    }

    public void suspend() {
        stopCurrentMusic();
        delayTicks = -1;
    }

    public void clear() {
        suspend();
    }

    public boolean isPlaying() {
        return currentMusic != null && !currentMusic.isStopped()
                && Minecraft.getInstance().getSoundManager().isActive(currentMusic);
    }

    private void playRandomTrack(Minecraft minecraft) {
        List<SoundEvent> tracks = List.of(
                ModSounds.NORMAL_WORK_0.get(),
                ModSounds.NORMAL_WORK_1.get(),
                ModSounds.NORMAL_WORK_2.get(),
                ModSounds.NORMAL_WORK_3.get());
        SoundEvent track = tracks.get(minecraft.level.random.nextInt(tracks.size()));

        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        currentMusic = new NormalMusicInstance(track);
        minecraft.getSoundManager().play(currentMusic);
        delayTicks = -1;
    }

    private void stopCurrentMusic() {
        if (currentMusic == null) return;
        currentMusic.forceStop();
        Minecraft.getInstance().getSoundManager().stop(currentMusic);
        currentMusic = null;
    }

    private static int randomDelay(Minecraft minecraft) {
        return minecraft.level.random.nextIntBetweenInclusive(MIN_DELAY_TICKS, MAX_DELAY_TICKS);
    }

    private static final class NormalMusicInstance extends AbstractTickableSoundInstance {
        private NormalMusicInstance(SoundEvent sound) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = false;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.8F;
        }

        @Override
        public void tick() {
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private void forceStop() {
            super.stop();
        }
    }
}
