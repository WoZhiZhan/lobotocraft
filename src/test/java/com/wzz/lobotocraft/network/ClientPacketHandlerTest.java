package com.wzz.lobotocraft.network;

import com.wzz.lobotocraft.network.packet.ElevatorTeleportPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPacketHandlerTest {
    @Test
    void worldUnloadCancelsCameraAnimation() {
        ClientPacketHandler.handleElevatorTeleport(
                new ElevatorTeleportPacket(new Vec3(0, 40, 0), new Vec3(0, 80, 0), 120));
        assertTrue(ClientPacketHandler.isAnimating());
        ClientPacketHandler.clear();
        assertFalse(ClientPacketHandler.isAnimating());
    }
}
