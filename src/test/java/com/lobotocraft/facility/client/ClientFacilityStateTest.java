package com.lobotocraft.facility.client;

import com.lobotocraft.facility.network.FacilityStatePacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientFacilityStateTest {
    private static final UUID TARGET = new UUID(0, 1);

    @BeforeEach
    @AfterEach
    void resetFacilityState() {
        ClientFacilityState.onLogout(null);
    }

    @Test
    void pausedMeltdownAndTrailingVoteStateSurvivePacketRoundTripAndClientTicks() {
        FacilityStatePacket source = new FacilityStatePacket(
                6, 17, 100, 3, 2, 4, 2, 119,
                1, 2, 0x1234L, 0x4321L, 17L, 2,
                true, true, 81, 5, 2, 1, 3, -1, true, 37,
                Map.of(TARGET, 119, new UUID(0, 2), 179));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FacilityStatePacket.encode(source, buffer);
            FacilityStatePacket decoded = FacilityStatePacket.decode(buffer);
            assertEquals(source, decoded);
            assertEquals(0, buffer.readableBytes());
            ClientFacilityState.update(decoded);
        } finally {
            buffer.release();
        }

        for (int tick = 0; tick < 1_200; tick++) ClientFacilityState.tick();

        assertTrue(ClientFacilityState.get().paused());
        assertEquals(2, ClientFacilityState.get().activeMeltdowns());
        assertEquals(119, ClientFacilityState.get().earliestRemainingTicks());
        assertEquals(source.meltdownRemainingTicks(), ClientFacilityState.get().meltdownRemainingTicks());
    }

    @Test
    void newerServerSnapshotsReplacePausedTimeAndClearResolvedMeltdowns() {
        ClientFacilityState.update(snapshot(1, 119, true));
        ClientFacilityState.update(snapshot(1, 79, false));

        assertFalse(ClientFacilityState.get().paused());
        assertEquals(79, ClientFacilityState.get().earliestRemainingTicks());

        ClientFacilityState.update(snapshot(0, 0, false));

        assertEquals(0, ClientFacilityState.get().activeMeltdowns());
        assertEquals(0, ClientFacilityState.get().earliestRemainingTicks());
        assertTrue(ClientFacilityState.get().meltdownRemainingTicks().isEmpty());
    }

    @Test
    void logoutClearsPausedCountdownBeforeAnotherWorldSendsItsFirstSnapshot() {
        ClientFacilityState.update(snapshot(1, 119, true));

        ClientFacilityState.onLogout(null);

        assertFalse(ClientFacilityState.get().paused());
        assertEquals(0, ClientFacilityState.get().activeMeltdowns());
        assertEquals(0, ClientFacilityState.get().earliestRemainingTicks());
        assertTrue(ClientFacilityState.get().meltdownRemainingTicks().isEmpty());
    }

    private static FacilityStatePacket snapshot(int active, int remainingTicks, boolean paused) {
        return new FacilityStatePacket(
                6, 10, 100, 2, 1, 4, active, remainingTicks,
                0, 0, 0L, 0L, 0L, 0,
                false, false, 0, 0, 0, 0, 0, 0, paused, 0,
                active == 0 ? Map.of() : Map.of(TARGET, remainingTicks));
    }
}
