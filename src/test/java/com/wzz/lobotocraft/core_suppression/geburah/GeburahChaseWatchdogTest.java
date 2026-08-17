package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahChaseWatchdogTest {
    @Test
    void requestsRecoveryOnlyAfterFiveSecondsWithoutProgress() {
        GeburahChaseWatchdog watchdog = new GeburahChaseWatchdog();
        UUID target = UUID.randomUUID();
        assertFalse(watchdog.observe(target, Vec3.ZERO));
        for (int i = 1; i < GeburahChaseWatchdog.STUCK_TICKS; i++) {
            assertFalse(watchdog.observe(target, new Vec3(0.1D, 0.0D, 0.1D)));
        }
        assertTrue(watchdog.observe(target, new Vec3(0.1D, 0.0D, 0.1D)));
    }

    @Test
    void meaningfulMovementOrTargetChangeResetsTheWindow() {
        GeburahChaseWatchdog watchdog = new GeburahChaseWatchdog();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        watchdog.observe(first, Vec3.ZERO);
        for (int i = 0; i < 80; i++) watchdog.observe(first, Vec3.ZERO);
        assertFalse(watchdog.observe(first, new Vec3(1.0D, 0.0D, 0.0D)));
        for (int i = 0; i < 80; i++) watchdog.observe(first, new Vec3(1.0D, 0.0D, 0.0D));
        assertFalse(watchdog.observe(second, new Vec3(1.0D, 0.0D, 0.0D)));
    }
}
