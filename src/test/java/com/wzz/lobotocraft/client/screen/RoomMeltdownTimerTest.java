package com.wzz.lobotocraft.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lobotocraft.facility.client.ClientFacilityState;
import com.lobotocraft.facility.network.FacilityStatePacket;
import com.wzz.lobotocraft.world.data.RoomEntry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomMeltdownTimerTest {
    private static final UUID TARGET = new UUID(0, 1);

    @BeforeEach
    @AfterEach
    void resetFacilityState() {
        ClientFacilityState.onLogout(null);
    }

    @Test
    void facilityPacketsAdvanceTheTimerWithoutWaitingForAnotherRoomScan() {
        RoomEntry room = room(800);
        update(800, false);
        assertEquals(800, ClientFacilityState.roomMeltdownTicks(room));

        update(780, false);
        assertEquals(780, ClientFacilityState.roomMeltdownTicks(room));
        update(760, false);
        assertEquals(760, ClientFacilityState.roomMeltdownTicks(room));
        assertEquals(800, room.meltdownRemainingTicks);
    }

    @Test
    void clientTicksDoNotPredictServerProgressOrExpireTheBorder() {
        RoomEntry room = room(10);
        update(10, false);
        for (int tick = 0; tick < 1_200; tick++) ClientFacilityState.tick();

        assertEquals(10, ClientFacilityState.roomMeltdownTicks(room));
        update(0, false);
        assertEquals(0, ClientFacilityState.roomMeltdownTicks(room));
    }

    @ParameterizedTest
    @ValueSource(longs = {50L, 1_000L, 2_000L, 60_000L})
    void pausedMonitorKeepsTheSameRemainingTicksAsTheAuthoritativeHud(long elapsedMillis) {
        RoomEntry room = room(119);
        update(119, true);

        for (int tick = 0; tick < elapsedMillis / 50L; tick++) {
            ClientFacilityState.tick();
        }

        assertEquals(119, ClientFacilityState.get().earliestRemainingTicks());
        assertEquals(ClientFacilityState.get().earliestRemainingTicks(),
                ClientFacilityState.roomMeltdownTicks(room),
                "A paused facility must not lose monitor time merely because the UI stays open");
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "19,0", "20,1", "21,1", "119,5", "120,6"})
    void hudCardAndTooltipUseTheSameFlooredSeconds(int ticks, int seconds) {
        RoomEntry room = room(ticks + 60);
        update(ticks, false);

        assertEquals(seconds, ClientFacilityState.meltdownSeconds(
                ClientFacilityState.get().earliestRemainingTicks()));
        assertEquals(seconds, ClientFacilityState.meltdownSeconds(
                ClientFacilityState.roomMeltdownTicks(room)));
    }

    @Test
    void activeAndResolvedPacketsOverrideStaleRoomFlags() {
        RoomEntry room = room(800);
        room.meltdownActive = false;
        update(119, false);
        assertEquals(119, ClientFacilityState.roomMeltdownTicks(room));

        room.meltdownActive = true;
        ClientFacilityState.update(new FacilityStatePacket(
                6, 10, 100, 2, 1, 4, 0, 0,
                0, 0, 0L, 0L, 0L, 0,
                false, false, 0, 0, 0, 0, 0, 0, false, 0, Map.of()));
        assertEquals(-1, ClientFacilityState.roomMeltdownTicks(room));
    }

    @Test
    void missingOrDifferentBindingCannotMatchByAbnormalityCode() {
        RoomEntry room = room(800);
        room.abnormalityCode = "O-03-89";
        update(119, false);
        room.boundAbnormality = null;
        assertEquals(-1, ClientFacilityState.roomMeltdownTicks(room));
        room.boundAbnormality = new UUID(0, 2);
        assertEquals(-1, ClientFacilityState.roomMeltdownTicks(room));
        room.boundAbnormality = TARGET;
        room.status = RoomEntry.STATUS_UNEXPANDED;
        assertEquals(-1, ClientFacilityState.roomMeltdownTicks(room));
    }

    private static RoomEntry room(int snapshotTicks) {
        RoomEntry room = new RoomEntry();
        room.boundAbnormality = TARGET;
        room.status = RoomEntry.STATUS_OCCUPIED;
        room.meltdownActive = true;
        room.meltdownRemainingTicks = snapshotTicks;
        return room;
    }

    @ParameterizedTest
    @ValueSource(ints = {RoomEntry.STATUS_EMPTY, RoomEntry.STATUS_LOST, RoomEntry.STATUS_UNEXPANDED})
    void unavailableOccupantCannotReviveAnOldMeltdown(int status) {
        RoomEntry room = room(800);
        room.status = status;
        room.meltdownActive = false;
        update(119, false);
        assertEquals(-1, ClientFacilityState.roomMeltdownTicks(room));
    }

    private static void update(int ticks, boolean paused) {
        ClientFacilityState.update(new FacilityStatePacket(
                6, 10, 100, 2, 1, 4, 1, ticks,
                0, 0, 0L, 0L, 0L, 0,
                false, false, 0, 0, 0, 0, 0, 0, paused, 0, Map.of(TARGET, ticks)));
    }
}
