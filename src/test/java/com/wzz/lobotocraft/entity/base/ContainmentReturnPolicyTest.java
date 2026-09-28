package com.wzz.lobotocraft.entity.base;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentReturnPolicyTest {
    private static final ResourceLocation HOME = ResourceLocation.tryParse("lobotocraft:loboto");
    private static final ResourceLocation ESCAPE = ResourceLocation.tryParse("minecraft:the_nether");
    private static final ResourceLocation CURRENT = ResourceLocation.tryParse("minecraft:overworld");

    @Test
    void authoritativeHomeDimensionWinsAcrossDimensions() {
        assertEquals(HOME, ContainmentReturnPolicy.selectTargetDimension(HOME, ESCAPE, CURRENT));
        assertEquals(ESCAPE, ContainmentReturnPolicy.selectTargetDimension(null, ESCAPE, CURRENT));
        assertEquals(CURRENT, ContainmentReturnPolicy.selectTargetDimension(null, null, CURRENT));
    }

    @Test
    void onlyUnregisteredAbnormalitiesReturnToTheirEscapeOrigin() {
        assertTrue(ContainmentReturnPolicy.returnsToEscapeOrigin(false));
        assertFalse(ContainmentReturnPolicy.returnsToEscapeOrigin(true));
    }

    @Test
    void legacyPendingSaveBecomesEscapedHealthyAndRetryable() {
        ContainmentReturnPolicy.LoadedState loaded = ContainmentReturnPolicy.normalizeLoadedState(
                false, true, true, 1.0F, 3000.0F);

        assertTrue(loaded.escaped());
        assertTrue(loaded.pending());
        assertTrue(loaded.stopEventPending());
        assertTrue(loaded.suppression());
        assertEquals(3000.0F, loaded.health());
    }

    @Test
    void loadedNormalizationIsIdempotentAndClampsCheatedHealth() {
        ContainmentReturnPolicy.LoadedState first = ContainmentReturnPolicy.normalizeLoadedState(
                true, true, false, Float.POSITIVE_INFINITY, 2000.0F);
        ContainmentReturnPolicy.LoadedState second = ContainmentReturnPolicy.normalizeLoadedState(
                first.escaped(), first.pending(), first.suppression(), first.health(), 2000.0F);

        assertEquals(first, second);
        assertEquals(2000.0F, first.health());
        assertEquals(1.0F, ContainmentReturnPolicy.healthAfterDeferredSuppression(Float.NaN));
    }

    @Test
    void retryScheduleSupportsRepeatedAttemptsWithoutDuplicateTicks() {
        assertFalse(ContainmentReturnPolicy.shouldRetry(false, 20, 20));
        assertFalse(ContainmentReturnPolicy.shouldRetry(true, 19, 20));
        assertTrue(ContainmentReturnPolicy.shouldRetry(true, 20, 20));
        assertTrue(ContainmentReturnPolicy.shouldRetry(true, 40, 20));
        assertFalse(ContainmentReturnPolicy.shouldRetry(true, 40, 0));
    }

    @Test
    void ordinaryLoadedEntityDoesNotAcquireTransactionState() {
        ContainmentReturnPolicy.LoadedState loaded = ContainmentReturnPolicy.normalizeLoadedState(
                false, false, true, 17.0F, 20.0F);

        assertFalse(loaded.escaped());
        assertFalse(loaded.pending());
        assertFalse(loaded.stopEventPending());
        assertFalse(loaded.suppression());
        assertEquals(17.0F, loaded.health());
    }
}
