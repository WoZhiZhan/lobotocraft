package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.alert.EscapeAlertManager;
import com.wzz.lobotocraft.client.audio.GuiMusicManager;
import com.wzz.lobotocraft.client.audio.NormalWorkMusicManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Plays the original upper-layer suppression tracks from the authoritative core state. */
@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class CoreSuppressionAudio {
    private static CoreMusicInstance currentMusic;
    private static ResourceLocation currentTrack;

    private CoreSuppressionAudio() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (CoreSuppressionClientState.isActive()
                && CoreSuppressionClientState.getType() == CoreSuppressionType.GEBURAH) {
            prepareExclusiveMusic(minecraft);
            tickGeburahMusic(minecraft, CoreSuppressionClientState.getVisualStage());
            return;
        }
        ResourceLocation desired = desiredTrack();
        if (desired == null) {
            stop(minecraft);
            return;
        }
        prepareExclusiveMusic(minecraft);
        if (desired.equals(currentTrack) && currentMusic != null && !currentMusic.isStopped()
                && minecraft.getSoundManager().isActive(currentMusic)) return;
        stop(minecraft);
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        currentTrack = desired;
        float volume = "escape_alert_bgm_1".equals(desired.getPath()) ? 0.8F * 0.31622776F : 0.8F;
        currentMusic = new CoreMusicInstance(SoundEvent.createVariableRangeEvent(desired), volume, true);
        minecraft.getSoundManager().play(currentMusic);
    }

    private static void tickGeburahMusic(Minecraft minecraft, int visualStage) {
        ResourceLocation ego = ResourceUtil.createInstance("geburah_ego_release");
        ResourceLocation first = ResourceUtil.createInstance("geburah_bgm_distorted_night");
        ResourceLocation second = ResourceUtil.createInstance("geburah_bgm_insignia_decay");
        GeburahMusicPolicy.Track current = first.equals(currentTrack)
                ? GeburahMusicPolicy.Track.FIRST
                : second.equals(currentTrack) ? GeburahMusicPolicy.Track.SECOND
                : ego.equals(currentTrack) ? GeburahMusicPolicy.Track.EGO : null;
        GeburahMusicPolicy.Decision decision = GeburahMusicPolicy.decide(
                visualStage, current, isCurrentActive(minecraft));
        if (!decision.shouldStart()) return;
        ResourceLocation next = switch (decision.track()) {
            case FIRST -> first;
            case SECOND -> second;
            case EGO -> ego;
        };
        play(minecraft, next, 0.8F, decision.looping());
    }

    private static boolean isCurrentActive(Minecraft minecraft) {
        return currentMusic != null && !currentMusic.isStopped()
                && minecraft.getSoundManager().isActive(currentMusic);
    }

    private static void play(Minecraft minecraft, ResourceLocation track, float volume,
                             boolean looping) {
        stop(minecraft);
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        currentTrack = track;
        currentMusic = new CoreMusicInstance(SoundEvent.createVariableRangeEvent(track), volume, looping);
        minecraft.getSoundManager().play(currentMusic);
    }

    private static void prepareExclusiveMusic(Minecraft minecraft) {
        EscapeAlertManager.getInstance().suspendForCoreSuppression();
        NormalWorkMusicManager.getInstance().suspend();
        GuiMusicManager.getInstance().suspendForHigherPriorityBgm();
        minecraft.getMusicManager().stopPlaying();
    }

    private static ResourceLocation desiredTrack() {
        if (!CoreSuppressionClientState.isActive()) return null;
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        if (type == null) return null;
        int visualStage = CoreSuppressionClientState.getVisualStage();
        if (visualStage <= 0) return ResourceUtil.createInstance("escape_alert_bgm_1");
        String department = switch (type) {
            case MALKUTH -> "malkuth";
            case YESOD -> "yesod";
            case HOD -> "hod";
            case NETZACH -> "netzach";
            default -> null;
        };
        if (department == null) return null;
        int phase = visualStage >= 2 ? 2 : 1;
        return ResourceUtil.createInstance("core." + department + "_" + phase);
    }

    private static void stop(Minecraft minecraft) {
        if (currentMusic != null) {
            currentMusic.forceStop();
            minecraft.getSoundManager().stop(currentMusic);
        }
        currentMusic = null;
        currentTrack = null;
    }

    private static final class CoreMusicInstance extends AbstractTickableSoundInstance {
        private CoreMusicInstance(SoundEvent sound, float volume, boolean looping) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = looping;
            relative = true;
            attenuation = Attenuation.NONE;
            this.volume = volume;
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
