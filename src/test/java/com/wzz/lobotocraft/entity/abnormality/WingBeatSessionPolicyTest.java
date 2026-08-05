package com.wzz.lobotocraft.entity.abnormality;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WingBeatSessionPolicyTest {
    @Test
    void activeMatchingSessionMayRecoverAfterEntityLoad() {
        UUID session = UUID.randomUUID();
        assertTrue(WingBeatSessionPolicy.canRebind(session, true, session));
    }

    @Test
    void completedSessionCannotRebindOnTheNextTick() {
        UUID endedSession = UUID.randomUUID();
        assertFalse(WingBeatSessionPolicy.canRebind(endedSession, false, null));
    }

    @Test
    void staleEntityCannotAttachToANewerFeast() {
        assertFalse(WingBeatSessionPolicy.canRebind(
                UUID.randomUUID(), true, UUID.randomUUID()));
    }

    @Test
    void missingEntitySessionIsNeverRecoverable() {
        assertFalse(WingBeatSessionPolicy.canRebind(null, true, UUID.randomUUID()));
    }

    @Test
    void absoluteExpiryDoesNotDependOnAnyFairyEntityBeingLoaded() {
        UUID session = UUID.randomUUID();
        assertTrue(WingBeatSessionPolicy.isActive(true, session, 1_160L, 1_159L));
        assertFalse(WingBeatSessionPolicy.isActive(true, session, 1_160L, 1_160L));
        assertFalse(WingBeatSessionPolicy.isActive(false, session, 1_160L, 1_000L));
        assertFalse(WingBeatSessionPolicy.isActive(true, null, 1_160L, 1_000L));
    }
}
