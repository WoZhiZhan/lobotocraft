package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.network.PacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class UniqueCommandPacketsTest {
    @Test
    void clientCannotPublishTargetListsAndServerCannotSubmitPlayerSelections() {
        int targets = PacketRegistry.idOf(UniqueCommandTargetsPacket.class);
        int commit = PacketRegistry.idOf(CommitUniqueCommandPacket.class);
        assertTrue(PacketRegistry.accepts(targets, true));
        assertFalse(PacketRegistry.accepts(targets, false));
        assertTrue(PacketRegistry.accepts(commit, false));
        assertFalse(PacketRegistry.accepts(commit, true));
    }

    @Test
    void oversizedTargetListIsRejectedBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(UUID.randomUUID());
            buf.writeVarInt(129);
            assertThrows(IllegalArgumentException.class, () -> new UniqueCommandTargetsPacket().fromBytes(buf));
        } finally { buf.release(); }
    }
}
