package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.EmployeeAppearance;
import com.wzz.lobotocraft.debug.prototype.queenlaser.QueenLaserPrototypePacket;
import com.wzz.lobotocraft.debug.prototype.queenstar.QueenStarPrototypePacket;
import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SetEmployeeAppearancePacketTest {
    @Test
    void wireFormatCarriesEmployeeAndAppearance() {
        UUID employeeId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        EmployeeAppearance appearance = new EmployeeAppearance(
                EmployeeAppearance.LEGACY_YSM_PROVIDER,
                "employees/control/clerk", "employees/control/clerk", "uniform_a");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SetEmployeeAppearancePacket(employeeId, appearance).toBytes(buffer);

            assertEquals(employeeId, buffer.readUUID());
            assertEquals(appearance.provider(), buffer.readUtf());
            assertEquals(appearance.modelId(), buffer.readUtf());
            assertEquals(appearance.modelPath(), buffer.readUtf());
            assertEquals(appearance.textureId(), buffer.readUtf());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void registryKeepsAppearancePacketServerboundBeforeWelcomeNotice() {
        int id = PacketRegistry.idOf(SetEmployeeAppearancePacket.class);
        int noticeId = PacketRegistry.idOf(OpenWelcomeNoticePacket.class);

        assertEquals(noticeId - 1, id);
        assertTrue(PacketRegistry.accepts(id, false));
        assertFalse(PacketRegistry.accepts(id, true));
        assertFalse(PacketRegistry.accepts(noticeId, false));
        assertTrue(PacketRegistry.accepts(noticeId, true));
        int acknowledgementId = PacketRegistry.idOf(AcknowledgeWelcomeNoticePacket.class);
        assertEquals(noticeId + 1, acknowledgementId);
        assertTrue(PacketRegistry.accepts(acknowledgementId, false));
        assertFalse(PacketRegistry.accepts(acknowledgementId, true));
        int meterId = PacketRegistry.idOf(MustOpenPathMeterSyncPacket.class);
        assertEquals(acknowledgementId + 1, meterId);
        assertEquals(PacketRegistry.size() - 6, meterId);
        assertFalse(PacketRegistry.accepts(meterId, false));
        assertTrue(PacketRegistry.accepts(meterId, true));
        int prototypeId = PacketRegistry.idOf(QueenLaserPrototypePacket.class);
        assertEquals(meterId + 1, prototypeId);
        assertEquals(PacketRegistry.size() - 5, prototypeId);
        assertFalse(PacketRegistry.accepts(prototypeId, false));
        assertTrue(PacketRegistry.accepts(prototypeId, true));
        int starPrototypeId = PacketRegistry.idOf(QueenStarPrototypePacket.class);
        assertEquals(prototypeId + 1, starPrototypeId);
        assertEquals(PacketRegistry.size() - 4, starPrototypeId);
        assertFalse(PacketRegistry.accepts(starPrototypeId, false));
        assertTrue(PacketRegistry.accepts(starPrototypeId, true));
        int geburahShakeId = PacketRegistry.idOf(GeburahCameraShakePacket.class);
        assertEquals(starPrototypeId + 1, geburahShakeId);
        assertEquals(PacketRegistry.size() - 3, geburahShakeId);
        assertFalse(PacketRegistry.accepts(geburahShakeId, false));
        assertTrue(PacketRegistry.accepts(geburahShakeId, true));
        int geburahStatusId = PacketRegistry.idOf(GeburahStatusSyncPacket.class);
        assertEquals(geburahShakeId + 1, geburahStatusId);
        assertEquals(PacketRegistry.size() - 2, geburahStatusId);
        assertFalse(PacketRegistry.accepts(geburahStatusId, false));
        assertTrue(PacketRegistry.accepts(geburahStatusId, true));
        int geburahVisualId = PacketRegistry.idOf(GeburahVisualEffectPacket.class);
        assertEquals(geburahStatusId + 1, geburahVisualId);
        assertEquals(PacketRegistry.size() - 1, geburahVisualId);
        assertFalse(PacketRegistry.accepts(geburahVisualId, false));
        assertTrue(PacketRegistry.accepts(geburahVisualId, true));
    }
}
