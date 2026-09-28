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
        // New packets append after the legacy tail without renumbering this block.
        int legacyEnd = PacketRegistry.idOf(ToggleFacilityPausePacket.class) + 1;
        assertEquals(noticeId + 1, acknowledgementId);
        assertTrue(PacketRegistry.accepts(acknowledgementId, false));
        assertFalse(PacketRegistry.accepts(acknowledgementId, true));
        int meterId = PacketRegistry.idOf(MustOpenPathMeterSyncPacket.class);
        assertEquals(acknowledgementId + 1, meterId);
        assertEquals(legacyEnd - 8, meterId);
        assertFalse(PacketRegistry.accepts(meterId, false));
        assertTrue(PacketRegistry.accepts(meterId, true));
        int prototypeId = PacketRegistry.idOf(QueenLaserPrototypePacket.class);
        assertEquals(meterId + 1, prototypeId);
        assertEquals(legacyEnd - 7, prototypeId);
        assertFalse(PacketRegistry.accepts(prototypeId, false));
        assertTrue(PacketRegistry.accepts(prototypeId, true));
        int starPrototypeId = PacketRegistry.idOf(QueenStarPrototypePacket.class);
        assertEquals(prototypeId + 1, starPrototypeId);
        assertEquals(legacyEnd - 6, starPrototypeId);
        assertFalse(PacketRegistry.accepts(starPrototypeId, false));
        assertTrue(PacketRegistry.accepts(starPrototypeId, true));
        int geburahShakeId = PacketRegistry.idOf(GeburahCameraShakePacket.class);
        assertEquals(starPrototypeId + 1, geburahShakeId);
        assertEquals(legacyEnd - 5, geburahShakeId);
        assertFalse(PacketRegistry.accepts(geburahShakeId, false));
        assertTrue(PacketRegistry.accepts(geburahShakeId, true));
        int geburahStatusId = PacketRegistry.idOf(GeburahStatusSyncPacket.class);
        assertEquals(geburahShakeId + 1, geburahStatusId);
        assertEquals(legacyEnd - 4, geburahStatusId);
        assertFalse(PacketRegistry.accepts(geburahStatusId, false));
        assertTrue(PacketRegistry.accepts(geburahStatusId, true));
        int geburahVisualId = PacketRegistry.idOf(GeburahVisualEffectPacket.class);
        assertEquals(geburahStatusId + 1, geburahVisualId);
        assertEquals(legacyEnd - 3, geburahVisualId);
        assertFalse(PacketRegistry.accepts(geburahVisualId, false));
        assertTrue(PacketRegistry.accepts(geburahVisualId, true));
        int fearFeedbackId = PacketRegistry.idOf(FearFeedbackPacket.class);
        assertEquals(geburahVisualId + 1, fearFeedbackId);
        assertEquals(legacyEnd - 2, fearFeedbackId);
        assertFalse(PacketRegistry.accepts(fearFeedbackId, false));
        assertTrue(PacketRegistry.accepts(fearFeedbackId, true));
        int pauseId = PacketRegistry.idOf(ToggleFacilityPausePacket.class);
        assertEquals(fearFeedbackId + 1, pauseId);
        assertEquals(legacyEnd - 1, pauseId);
        assertTrue(PacketRegistry.accepts(pauseId, false));
        assertFalse(PacketRegistry.accepts(pauseId, true));
        int requestId = PacketRegistry.idOf(RequestUniqueRoomWorkPacket.class);
        assertEquals(pauseId + 1, requestId);
        assertTrue(PacketRegistry.accepts(requestId, false));
        assertFalse(PacketRegistry.accepts(requestId, true));
        for (Class<? extends com.wzz.lobotocraft.network.IMessage> packet
                : java.util.List.of(UniqueWorkStatePacket.class, FacilityKnowledgeSyncPacket.class)) {
            int packetId = PacketRegistry.idOf(packet);
            assertTrue(packetId > requestId);
            assertTrue(PacketRegistry.accepts(packetId, true));
            assertFalse(PacketRegistry.accepts(packetId, false));
        }
    }
}
