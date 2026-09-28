package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanerEventTest {

    @Test
    void naturalSpawningUsesStrictNighttimeClockWindow() {
        assertFalse(CleanerEvent.isNaturalSpawnTime(0L));
        assertFalse(CleanerEvent.isNaturalSpawnTime(12_999L));
        assertTrue(CleanerEvent.isNaturalSpawnTime(13_000L));
        assertTrue(CleanerEvent.isNaturalSpawnTime(22_999L));
        assertFalse(CleanerEvent.isNaturalSpawnTime(23_000L));
        assertFalse(CleanerEvent.isNaturalSpawnTime(23_999L));
    }

    @Test
    void naturalSpawnWindowRepeatsAcrossMinecraftDays() {
        assertTrue(CleanerEvent.isNaturalSpawnTime(37_000L));
        assertFalse(CleanerEvent.isNaturalSpawnTime(48_000L));
        assertTrue(CleanerEvent.isNaturalSpawnTime(-11_000L));
    }

    @Test
    void cleanerHealthIsNerfedToThirty() {
        assertEquals(30.0D, EntityCleaner.MAX_HEALTH);
    }
}
