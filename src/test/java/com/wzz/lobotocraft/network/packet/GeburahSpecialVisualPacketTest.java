package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals.*;
import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class GeburahSpecialVisualPacketTest {
    @Test
    void allSkillsAndAuthoritativeCuesRoundTripAsClientboundOnly() {
        for (Skill skill : Skill.values()) {
            int elapsed = skill.major() == null ? 19 : skill.major().cues().get(0).tick();
            var s = new Snapshot(UUID.randomUUID(), 9, 17, skill, Event.CUE, elapsed, 0, true,
                    Vec3.ZERO, new Vec3(1, 2, 3), new Vec3(1, 0, 0));
            var source = new GeburahSpecialVisualPacket(new ResourceLocation("lobotocraft", "facility"), s);
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            var roundTrip = new FriendlyByteBuf(Unpooled.buffer());
            try {
                source.toBytes(buf);
                byte[] expected = new byte[buf.readableBytes()]; buf.getBytes(0, expected);
                var decoded = new GeburahSpecialVisualPacket(); decoded.fromBytes(buf);
                assertEquals(0, buf.readableBytes()); decoded.toBytes(roundTrip);
                byte[] actual = new byte[roundTrip.readableBytes()]; roundTrip.readBytes(actual);
                assertArrayEquals(expected, actual);
                int id = PacketRegistry.idOf(GeburahSpecialVisualPacket.class);
                assertTrue(PacketRegistry.accepts(id, true));
                assertFalse(PacketRegistry.accepts(id, false));
            } finally { buf.release(); roundTrip.release(); }
        }
    }
    @Test
    void rejectsInvalidCueClockAndNonfiniteOrNonhorizontalVectors() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(id, 1, 1, Skill.BODIES,
                Event.CUE, 45, 0, true, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(id, 1, 1, Skill.WILL,
                Event.START, 0, -1, false, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(id, 1, 1, Skill.WILL,
                Event.START, 0, -1, false, new Vec3(Double.NaN, 0, 0), Vec3.ZERO, new Vec3(0, 0, 1)));
    }
}
