package com.wzz.lobotocraft.world.data;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomEmployeeSnapshotTest {
    @Test
    void networkRoundTripPreservesWorkPeBoxes() {
        RoomEmployeeSnapshot source = new RoomEmployeeSnapshot(
                UUID.randomUUID(), "Malkuth", "control",
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"), new BlockPos(12, 64, 28),
                73.0F, 100.0F, 42.0F, 80.0F,
                55, 60, 65, 70, 4,
                "忏悔", "item.lobotocraft.repentance_weapon",
                "忏悔", "item.lobotocraft.repentance_armor", "WORK", true,
                false, "正在工作", "screen.lobotocraft.monitor.employee.unavailable.working",
                0.72F, 14,
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(160, 270, 30),
                com.wzz.lobotocraft.work.WorkType.INSIGHT, 7, 3, 15);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        source.write(buffer);
        RoomEmployeeSnapshot decoded = RoomEmployeeSnapshot.read(buffer);

        assertEquals(source, decoded);
        assertTrue(decoded.isWorking());
        assertEquals("screen.lobotocraft.monitor.employee.unavailable.working",
                decoded.unavailableReasonKey());
        assertEquals("item.lobotocraft.repentance_weapon", decoded.weaponNameKey());
        assertEquals("item.lobotocraft.repentance_armor", decoded.armorNameKey());
        assertEquals(com.wzz.lobotocraft.work.WorkType.INSIGHT, decoded.currentWorkType());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void networkRoundTripAllowsNoActiveWorkType() {
        RoomEmployeeSnapshot source = new RoomEmployeeSnapshot(
                UUID.randomUUID(), "player", "control",
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"), BlockPos.ZERO,
                20.0F, 20.0F, 20.0F, 20.0F,
                1, 1, 1, 1, 1, "none", "", "none", "", "PLAYER", false,
                false, "", "", -1.0F, 0, null, null, null, 0, 0, 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        source.write(buffer);
        RoomEmployeeSnapshot decoded = RoomEmployeeSnapshot.read(buffer);

        assertEquals(source, decoded);
        assertNull(decoded.currentWorkType());
        assertEquals(0, buffer.readableBytes());
    }
}
