package com.wzz.lobotocraft.client.fear;

import com.wzz.lobotocraft.client.config.ClientVisualConfig;
import com.wzz.lobotocraft.client.core.GeburahCameraShakeState;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Client presentation for one server-authoritative horror encounter. */
public final class FearClientState {
    private static final int TEXT_DURATION_TICKS = 24;
    private static final int DISORIENTATION_DURATION_TICKS = 40;
    private static final int PANIC_AUDIO_DURATION_TICKS = 160;
    private static final SoundSource[] DUCKED_SOURCES = {
            SoundSource.AMBIENT, SoundSource.HOSTILE, SoundSource.NEUTRAL,
            SoundSource.BLOCKS, SoundSource.WEATHER, SoundSource.RECORDS,
            SoundSource.MUSIC, SoundSource.PLAYERS
    };
    private static final Map<Integer, Entry> ACTIVE_TEXT = new HashMap<>();
    private static final Map<SoundSource, Float> ORIGINAL_VOLUMES = new EnumMap<>(SoundSource.class);

    private static int duckTicks;
    private static int visualTicks;
    private static int visualTotalTicks;
    private static int visualLevel;
    private static int disorientationTicks;
    private static int disorientationTotalTicks;
    private static int disorientationTargetId = -1;
    private static float duckMultiplier = 1.0F;
    private static int fearSeed;
    private static boolean exclusiveAudio;
    private static FearTinnitusSound tinnitus;

    private FearClientState() {
    }

    public static void trigger(int entityId, int fearLevel, int threatEntityId) {
        int safeLevel = Math.max(2, Math.min(5, fearLevel));
        if (ClientVisualConfig.FEAR_VISUALS_ENABLED.get()) {
            Entry existing = ACTIVE_TEXT.get(entityId);
            if (existing == null || safeLevel >= existing.level()) {
                ACTIVE_TEXT.put(entityId, new Entry(safeLevel, TEXT_DURATION_TICKS));
            } else {
                ACTIVE_TEXT.put(entityId, new Entry(existing.level(), TEXT_DURATION_TICKS));
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getId() != entityId) return;
        triggerLocal(minecraft.player, safeLevel, threatEntityId);
    }

    public static void tick() {
        ACTIVE_TEXT.replaceAll((id, entry) -> new Entry(entry.level(), entry.remainingTicks() - 1));
        ACTIVE_TEXT.entrySet().removeIf(entry -> entry.getValue().remainingTicks() <= 0);
        if (disorientationTicks > 0) {
            tickDisorientationAudio(disorientationTotalTicks - disorientationTicks);
        }
        if (visualTicks > 0) {
            int elapsed = visualTotalTicks - visualTicks;
            tickLocalPulses(elapsed);
            visualTicks--;
        }
        if (disorientationTicks > 0) disorientationTicks--;
        if (disorientationTicks <= 0) disorientationTargetId = -1;
        if (duckTicks > 0) {
            maintainSoundDuck();
            if (--duckTicks == 0) restoreSoundVolumes();
        }
        if (tinnitus != null && tinnitus.isStopped()) tinnitus = null;
        if (duckTicks <= 0) exclusiveAudio = false;
    }

    public static void clear() {
        ACTIVE_TEXT.clear();
        visualTicks = 0;
        visualTotalTicks = 0;
        visualLevel = 0;
        disorientationTicks = 0;
        disorientationTotalTicks = 0;
        disorientationTargetId = -1;
        duckTicks = 0;
        duckMultiplier = 1.0F;
        exclusiveAudio = false;
        if (tinnitus != null) {
            Minecraft.getInstance().getSoundManager().stop(tinnitus);
            tinnitus.forceStop();
            tinnitus = null;
        }
        restoreSoundVolumes();
    }

    public static Entry textFor(int entityId) {
        return ACTIVE_TEXT.get(entityId);
    }

    public static float visualAlpha() {
        if (visualTicks <= 0 || visualTotalTicks <= 0) return 0.0F;
        float progress = 1.0F - visualTicks / (float) visualTotalTicks;
        if (visualLevel == 5) {
            float disorientationProgress = 1.0F - disorientationTicks
                    / (float) Math.max(1, disorientationTotalTicks);
            float fade = 1.0F - smoothstep(0.72F, 1.0F, disorientationProgress);
            return Math.max(0.0F, fade * (0.82F + 0.18F * (float) Math.sin(progress * Math.PI * 5.0F)));
        }
        float initialShock = (float) Math.pow(Math.max(0.0F, 1.0F - progress * 4.5F), 2.0D);
        float pressure = (float) Math.sin(progress * Math.PI);
        float pulse = 0.78F + 0.22F * (float) Math.sin(progress * Math.PI * 7.0F);
        return Math.min(1.0F, initialShock * (visualLevel == 5 ? 0.98F : 0.72F)
                + pressure * pulse * (visualLevel == 5 ? 0.78F : 0.52F));
    }

    public static int visualLevel() {
        return visualLevel;
    }

    public static boolean isDisorientationActive() {
        return disorientationTicks > 0 && disorientationTargetId >= 0;
    }

    public static boolean isDisorientationActive(net.minecraft.world.entity.player.Player player) {
        return player == Minecraft.getInstance().player && isDisorientationActive();
    }

    public static int disorientationTargetId() {
        return disorientationTargetId;
    }

    public static float disorientationProgress() {
        if (disorientationTotalTicks <= 0) return 1.0F;
        return 1.0F - disorientationTicks / (float) disorientationTotalTicks;
    }

    public static float distortionIntensity() {
        float alpha = visualAlpha();
        return visualLevel == 5 ? alpha * 0.82F : alpha * 0.42F;
    }

    public static boolean blocksSound(net.minecraft.client.resources.sounds.SoundInstance sound) {
        return exclusiveAudio && sound != tinnitus;
    }

    public static double fovMultiplier() {
        if (visualTicks <= 0) return 1.0D;
        if (visualLevel == 5 && isDisorientationActive()) {
            float progress = disorientationProgress();
            if (progress < 0.10F) {
                return 1.0D + 0.20D * progress / 0.10D;
            }
            float contraction = 0.22F * Math.min(1.0F, (progress - 0.10F) / 0.22F);
            contraction *= 1.0F - smoothstep(0.72F, 1.0F, progress);
            return 1.0D - contraction;
        }
        float progress = 1.0F - visualTicks / (float) visualTotalTicks;
        float shock = Math.max(0.0F, 1.0F - progress * 3.6F);
        float pressure = (float) Math.sin(progress * Math.PI);
        float contraction = shock * (visualLevel == 5 ? 0.24F : 0.13F)
                + pressure * (visualLevel == 5 ? 0.11F : 0.055F);
        return 1.0D - contraction;
    }

    private static void triggerLocal(LocalPlayer player, int level, int threatEntityId) {
        if (ClientVisualConfig.FEAR_AUDIO_ENABLED.get()) {
            if (level >= 4) {
                int audioDuration = level == 5 ? PANIC_AUDIO_DURATION_TICKS : 44;
                if (level == 5) {
                    exclusiveAudio = true;
                    Minecraft.getInstance().getSoundManager().stop();
                    tinnitus = null;
                } else {
                    player.playSound(SoundEvents.WARDEN_HEARTBEAT, 1.05F, 0.62F);
                }
                startTinnitus(level, audioDuration);
                applySoundDuck(audioDuration, level == 5 ? 0.0F : 0.20F);
            } else if (level == 3) {
                player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.48F, 0.84F);
            } else {
                player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.25F, 1.18F);
            }
        }
        if (level >= 4 && ClientVisualConfig.FEAR_VISUALS_ENABLED.get()) {
            int duration = level == 5 ? DISORIENTATION_DURATION_TICKS : 44;
            if (level >= visualLevel || visualTicks <= 0) {
                visualLevel = level;
                visualTotalTicks = duration;
                visualTicks = duration;
            }
        }
        if (level == 5) {
            disorientationTotalTicks = DISORIENTATION_DURATION_TICKS;
            disorientationTicks = DISORIENTATION_DURATION_TICKS;
            disorientationTargetId = threatEntityId;
        }
        if (level >= 4 && ClientVisualConfig.FEAR_CAMERA_SHAKE_ENABLED.get()) {
            fearSeed = player.getId() * 31 + level;
            GeburahCameraShakeState.trigger(level == 5 ? 20 : 13,
                    level == 5 ? 2.0F : 1.1F, fearSeed);
        }
    }

    private static void startTinnitus(int level, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tinnitus == null || tinnitus.isStopped()) {
            tinnitus = new FearTinnitusSound(ModSounds.FEAR_TINNITUS.get(), level, durationTicks);
            minecraft.getSoundManager().play(tinnitus);
        } else {
            tinnitus.upgrade(level, durationTicks);
        }
    }

    private static void tickDisorientationAudio(int elapsed) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || visualLevel != 5) return;
        if (ClientVisualConfig.FEAR_CAMERA_SHAKE_ENABLED.get() && elapsed % 6 == 0 && elapsed < 18) {
            float strength = 1.5F - disorientationProgress() * 0.92F;
            GeburahCameraShakeState.trigger(5, strength * 0.8F, fearSeed + elapsed * 17);
        }
    }

    private static void tickLocalPulses(int elapsed) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || visualLevel < 4 || isDisorientationActive()) return;
        if (elapsed == 10 || visualLevel == 5 && elapsed == 25) {
            float strength = visualLevel == 5 ? 1.25F : 0.72F;
            player.playSound(SoundEvents.WARDEN_HEARTBEAT, strength,
                    visualLevel == 5 ? 0.52F : 0.70F);
            if (ClientVisualConfig.FEAR_CAMERA_SHAKE_ENABLED.get()) {
                GeburahCameraShakeState.trigger(visualLevel == 5 ? 13 : 8,
                        visualLevel == 5 ? 1.55F : 0.72F, fearSeed + elapsed * 17);
            }
        }
    }

    private static void applySoundDuck(int durationTicks, float multiplier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ORIGINAL_VOLUMES.isEmpty()) {
            for (SoundSource source : DUCKED_SOURCES) {
                ORIGINAL_VOLUMES.put(source, minecraft.options.getSoundSourceVolume(source));
            }
        }
        duckMultiplier = Math.min(duckMultiplier, multiplier);
        maintainSoundDuck();
        duckTicks = Math.max(duckTicks, durationTicks);
    }

    private static void maintainSoundDuck() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        float release = duckTicks <= 10 ? duckTicks / 10.0F : 1.0F;
        float multiplier = 1.0F - (1.0F - duckMultiplier) * release;
        for (Map.Entry<SoundSource, Float> entry : ORIGINAL_VOLUMES.entrySet()) {
            soundManager.updateSourceVolume(entry.getKey(), entry.getValue() * multiplier);
        }
    }

    private static void restoreSoundVolumes() {
        if (ORIGINAL_VOLUMES.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        for (Map.Entry<SoundSource, Float> entry : ORIGINAL_VOLUMES.entrySet()) {
            soundManager.updateSourceVolume(entry.getKey(), entry.getValue());
        }
        ORIGINAL_VOLUMES.clear();
        duckMultiplier = 1.0F;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    public record Entry(int level, int remainingTicks) {
    }
}
