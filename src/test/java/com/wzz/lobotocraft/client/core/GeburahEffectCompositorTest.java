package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahEffectCompositorTest {
    @AfterEach
    void clear() {
        GeburahEffectCompositor.clear();
    }

    @Test
    void higherPriorityEffectWinsWithoutDeletingLowerPriorityState() {
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.COMBO_HIT,
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 2.0F, 10, 1);
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.POSTURE_BREAK,
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 0.2F, 10, 2);

        assertEquals(GeburahVisualEffectType.POSTURE_BREAK,
                GeburahEffectCompositor.dominantType());

        for (int i = 0; i < 10; i++) GeburahEffectCompositor.tick();
        assertEquals(null, GeburahEffectCompositor.dominantType());
    }

    @Test
    void repeatedSameEffectMergesInsteadOfQueueing() {
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.BLOOD_MIST_HIT,
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), 0.3F, 5, 1);
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.BLOOD_MIST_HIT,
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), 0.8F, 8, 2);

        for (int i = 0; i < 7; i++) GeburahEffectCompositor.tick();
        assertEquals(GeburahVisualEffectType.BLOOD_MIST_HIT,
                GeburahEffectCompositor.dominantType());
        GeburahEffectCompositor.tick();
        assertEquals(null, GeburahEffectCompositor.dominantType());
    }

    @Test
    void eachAttackFamilyUsesADistinctVisualProfile() {
        Set<GeburahEffectCompositor.VisualProfile> profiles = Set.of(
                GeburahEffectCompositor.visualProfile(GeburahVisualEffectType.COMBO_HIT),
                GeburahEffectCompositor.visualProfile(
                        GeburahVisualEffectType.WILL_OF_BATTLE_HIT),
                GeburahEffectCompositor.visualProfile(
                        GeburahVisualEffectType.BLOOD_MIST_HIT),
                GeburahEffectCompositor.visualProfile(
                        GeburahVisualEffectType.BODIES_EVERYWHERE_HIT));

        assertEquals(4, profiles.size());
    }

    @Test
    void bloodMistIntensitySelectsAccumulationOpeningAndFinisherTiers() {
        assertEquals(0, GeburahEffectCompositor.bloodMistTier(0.18F));
        assertEquals(1, GeburahEffectCompositor.bloodMistTier(0.42F));
        assertEquals(2, GeburahEffectCompositor.bloodMistTier(1.0F));
    }

    @Test
    void normalMovesUseIndependentShaderProfiles() {
        assertEquals(GeburahVisualEffectType.SLASH_HIT,
                GeburahVisualEffectType.forMove(GeburahMove.SLASH));
        assertEquals(GeburahVisualEffectType.STAB_HIT,
                GeburahVisualEffectType.forMove(GeburahMove.STAB));
        assertEquals(GeburahVisualEffectType.VERTICAL_SLASH_HIT,
                GeburahVisualEffectType.forMove(GeburahMove.VERTICAL_SLASH));
        assertEquals(3, Set.of(
                GeburahEffectCompositor.visualProfile(GeburahVisualEffectType.SLASH_HIT),
                GeburahEffectCompositor.visualProfile(GeburahVisualEffectType.STAB_HIT),
                GeburahEffectCompositor.visualProfile(
                        GeburahVisualEffectType.VERTICAL_SLASH_HIT)).size());
    }

    @Test
    void egoFlagIsExposedToTheAttackShader() {
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.STAB_HIT,
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D),
                0.9F, 0.5F, true, 10, 9);

        assertEquals(1.0F, GeburahEffectCompositor.shaderEgoAttack());
    }

    @Test
    void newestAttackHitSupersedesOlderStrongerAttackEffect() {
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.BLOOD_MIST_HIT,
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                1.2F, 0.6F, true, 20, 1);
        GeburahEffectCompositor.trigger(GeburahVisualEffectType.SLASH_HIT,
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                0.58F, 0.4F, true, 8, 2);

        assertEquals(GeburahVisualEffectType.SLASH_HIT,
                GeburahEffectCompositor.dominantType());
    }

    @Test
    void fullModeFallsBackWhileThePostProcessorIsUnavailable() {
        assertTrue(GeburahEffectCompositor.shouldRenderAttackFallback(
                GeburahEffectCompositor.Mode.FULL, false));
        assertFalse(GeburahEffectCompositor.shouldRenderAttackFallback(
                GeburahEffectCompositor.Mode.FULL, true));
        assertTrue(GeburahEffectCompositor.shouldRenderAttackFallback(
                GeburahEffectCompositor.Mode.REDUCED, true));
    }

    @Test
    void postureBreakPaletteUsesGoldInsteadOfBlue() {
        int crack = GeburahEffectCompositor.POSTURE_BREAK_CRACK_COLOR;
        int shard = GeburahEffectCompositor.POSTURE_BREAK_SHARD_RGB;

        assertTrue((crack >> 16 & 0xFF) > (crack & 0xFF));
        assertTrue((crack >> 8 & 0xFF) > (crack & 0xFF));
        assertTrue((shard >> 16 & 0xFF) > (shard & 0xFF));
        assertTrue((shard >> 8 & 0xFF) > (shard & 0xFF));
    }
}
