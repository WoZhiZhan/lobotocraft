package com.wzz.lobotocraft.network.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObtainGiftPacketCompatibilityTest {
    @Test
    void retiredClientGrantRequestIsHarmlessEvenWithInvalidContext() {
        ObtainGiftPacket packet = new ObtainGiftPacket(1, "lobotocraft:unknown");
        assertTrue(packet.sendToServer());
        assertDoesNotThrow(() -> packet.run(null));
    }
}
