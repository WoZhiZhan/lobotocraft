package com.wzz.lobotocraft.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShockwaveEffectManagerTest {
    @BeforeEach
    @AfterEach
    void clearEffects() {
        ShockwaveEffectManager.clear();
    }

    @Test
    void rejectsNonFiniteAndNonPositiveEffects() {
        ShockwaveEffectManager.addShockwave(Double.NaN, 0.0D, 0.0D, 1.0F, 0);
        ShockwaveEffectManager.addShockwave(0.0D, Double.POSITIVE_INFINITY, 0.0D, 1.0F, 0);
        ShockwaveEffectManager.addShockwave(0.0D, 0.0D, 0.0D, Float.NaN, 0);
        ShockwaveEffectManager.addShockwave(0.0D, 0.0D, 0.0D, 0.0F, 0);

        assertEquals(0, ShockwaveEffectManager.getActiveShockwaveCount());
    }

    @Test
    void clampsRadiusAndEvictsOldestEffectsAtCapacity() {
        for (int index = 0; index <= ShockwaveEffectManager.MAX_ACTIVE_SHOCKWAVES; index++) {
            ShockwaveEffectManager.addShockwave(index, 0.0D, 0.0D,
                    ShockwaveEffectManager.MAX_RADIUS * 2.0F, index);
        }

        assertEquals(ShockwaveEffectManager.MAX_ACTIVE_SHOCKWAVES,
                ShockwaveEffectManager.getActiveShockwaveCount());
        assertEquals(1.0D, ShockwaveEffectManager.getShockwaves().get(0).x);
        assertEquals(ShockwaveEffectManager.MAX_RADIUS,
                ShockwaveEffectManager.getShockwaves().get(0).maxRadius);
    }

    @Test
    void tickUpdatesAnimationAndEventuallyRemovesEffect() {
        ShockwaveEffectManager.addShockwave(0.0D, 0.0D, 0.0D, 30.0F, 0xFFFFFF);
        ShockwaveEffectManager.tick();

        ShockwaveEffectManager.Shockwave wave = ShockwaveEffectManager.getShockwaves().get(0);
        assertEquals(0.5F, wave.getRenderRadius(0.5F), 0.0001F);
        assertTrue(wave.getRenderAlpha(0.5F) > 0.0F);

        for (int tick = 0; tick < 60; tick++) {
            ShockwaveEffectManager.tick();
        }
        assertEquals(0, ShockwaveEffectManager.getActiveShockwaveCount());
    }
}
