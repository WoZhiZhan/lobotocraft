package com.wzz.lobotocraft.client.alert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.wzz.lobotocraft.client.alert.FacilityAlertClientState.Level;

final class FacilityAlertClientStateTest {
    @Test
    void unknownUntilFirstSyncThenMapsAllServerLevels() {
        var state = new FacilityAlertClientState();
        assertEquals(Level.UNKNOWN, state.level());
        for (int wire = -1; wire <= 2; wire++) {
            state.receive(wire);
            assertEquals(new Level[]{Level.NORMAL, Level.I, Level.II, Level.III}[wire + 1], state.level());
        }
    }

    @Test
    void downgradesAndDayResetAreNotStickyAndUnloadBecomesUnknown() {
        var state = new FacilityAlertClientState();
        state.receive(2);
        state.receive(0);
        assertEquals(Level.I, state.level());
        state.receive(-1);
        assertEquals(Level.NORMAL, state.level());
        state.clear();
        assertEquals(Level.UNKNOWN, state.level());
    }

    @Test
    void repeatedSyncRemainsStableAndBoundsMatchMusicReceiver() {
        var state = new FacilityAlertClientState();
        state.receive(1); state.receive(1);
        assertEquals(Level.II, state.level());
        state.receive(Integer.MAX_VALUE);
        assertEquals(Level.III, state.level());
        state.receive(Integer.MIN_VALUE);
        assertEquals(Level.NORMAL, state.level());
    }

    @Test
    void actualReceiverKeepsAuthoritativeLevelWhenCoreMusicSuppressesAlarm() throws Exception {
        var manager = EscapeAlertManager.getInstance();
        manager.clear();
        // The normal core update opens/closes client screens. Seed only its read-only music gate
        // here because this headless test has no Minecraft UI; the actual receiver is not mocked.
        var core = com.wzz.lobotocraft.client.core.CoreSuppressionClientState.class;
        var active = core.getDeclaredField("active");
        var type = core.getDeclaredField("type");
        active.setAccessible(true); type.setAccessible(true);
        Object previousActive = active.get(null), previousType = type.get(null);
        active.set(null, true);
        type.set(null, com.wzz.lobotocraft.core_suppression.CoreSuppressionType.GEBURAH);
        try {
            manager.onAlertReceived(2);
            assertEquals(Level.III, manager.getFacilityAlertLevel());
            assertFalse(manager.isBgmActive());
            manager.suspendForCoreSuppression();
            manager.tick();
            assertEquals(Level.III, manager.getFacilityAlertLevel());
            manager.onAlertReceived(-1);
            assertEquals(Level.NORMAL, manager.getFacilityAlertLevel());
            manager.clear();
            assertEquals(Level.UNKNOWN, manager.getFacilityAlertLevel());
        } finally {
            manager.clear();
            active.set(null, previousActive);
            type.set(null, previousType);
        }
    }
}
