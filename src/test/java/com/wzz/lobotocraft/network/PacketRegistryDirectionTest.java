package com.wzz.lobotocraft.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wzz.lobotocraft.network.packet.OpenWorkScreenPacket;
import com.wzz.lobotocraft.network.packet.OpenPlayerProfilePacket;
import com.wzz.lobotocraft.network.packet.RequestPlayerProfilePacket;
import com.wzz.lobotocraft.network.packet.StartWorkPacket;
import org.junit.jupiter.api.Test;

class PacketRegistryDirectionTest {
    @Test
    void serverboundPacketsAreRejectedBeforeClientDecoding() {
        int id = PacketRegistry.idOf(StartWorkPacket.class);
        assertTrue(PacketRegistry.accepts(id, false));
        assertFalse(PacketRegistry.accepts(id, true));
    }

    @Test
    void clientboundPacketsAreRejectedBeforeServerDecoding() {
        int id = PacketRegistry.idOf(OpenWorkScreenPacket.class);
        assertTrue(PacketRegistry.accepts(id, true));
        assertFalse(PacketRegistry.accepts(id, false));
    }

    @Test
    void playerProfilePacketsKeepStrictDirections() {
        int request = PacketRegistry.idOf(RequestPlayerProfilePacket.class);
        int snapshot = PacketRegistry.idOf(OpenPlayerProfilePacket.class);

        assertTrue(PacketRegistry.accepts(request, false));
        assertFalse(PacketRegistry.accepts(request, true));
        assertTrue(PacketRegistry.accepts(snapshot, true));
        assertFalse(PacketRegistry.accepts(snapshot, false));
    }
}
