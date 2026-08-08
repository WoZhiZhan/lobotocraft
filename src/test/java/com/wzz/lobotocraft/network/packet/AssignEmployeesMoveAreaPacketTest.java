package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.FacilityAreaMoveService;
import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssignEmployeesMoveAreaPacketTest {

    @Test
    void packetRoundTripIsStableAndDuplicateEmployeesAreRemoved() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AssignEmployeesMoveAreaPacket original = new AssignEmployeesMoveAreaPacket(
                "dept/control/passage/sefira-malkuth-1", List.of(first, second, first));

        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf reencoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.toBytes(encoded);
            byte[] expected = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), expected);

            AssignEmployeesMoveAreaPacket decoded = new AssignEmployeesMoveAreaPacket();
            decoded.fromBytes(encoded);
            decoded.toBytes(reencoded);
            byte[] actual = new byte[reencoded.readableBytes()];
            reencoded.getBytes(reencoded.readerIndex(), actual);
            assertArrayEquals(expected, actual);
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    @Test
    void decoderRejectsOversizedEmployeeListsBeforeAllocatingThem() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("dept/control/passage/sefira-malkuth-1");
            buffer.writeVarInt(FacilityAreaMoveService.MAX_EMPLOYEES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> new AssignEmployeesMoveAreaPacket().fromBytes(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorRejectsMoreThanTheDispatchLimit() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i <= FacilityAreaMoveService.MAX_EMPLOYEES; i++) ids.add(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> new AssignEmployeesMoveAreaPacket("dept/control/background", ids));
    }

    @Test
    void elevatorPacketIsAppendedWithoutRenumberingMonitorMovement() {
        int movementId = -1;
        for (int id = 0; id < PacketRegistry.size(); id++) {
            if (PacketRegistry.byId(id) == AssignEmployeesMoveAreaPacket.class) movementId = id;
        }
        assertEquals(AssignEmployeesMoveAreaPacket.class, PacketRegistry.byId(movementId));
        assertEquals(ElevatorTravelPacket.class, PacketRegistry.byId(movementId + 1));
    }
}
