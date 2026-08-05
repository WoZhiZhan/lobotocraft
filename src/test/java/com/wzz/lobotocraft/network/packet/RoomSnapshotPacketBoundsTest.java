package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import com.wzz.lobotocraft.work.WorkType;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomSnapshotPacketBoundsTest {
    @Test
    void suppressionCandidatesAreTruncatedWithoutCorruptingTrailingMessage() {
        List<RoomEmployeeSnapshot> employees = snapshots(513);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new RoomSuppressionCandidatesPacket(BlockPos.ZERO, employees, "tail").toBytes(buffer);

        assertEquals(BlockPos.ZERO, buffer.readBlockPos());
        assertEquals(512, buffer.readVarInt());
        for (int index = 0; index < 512; index++) RoomEmployeeSnapshot.read(buffer);
        assertEquals("tail", buffer.readUtf(128));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void oversizedSuppressionCandidatePayloadIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(513);

        assertThrows(IllegalArgumentException.class,
                () -> new RoomSuppressionCandidatesPacket().fromBytes(buffer));
    }

    @Test
    void oversizedMonitorRoomPayloadIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1025);

        assertThrows(IllegalArgumentException.class,
                () -> new OpenRoomMapPacket().fromBytes(buffer));
    }

    @Test
    void oversizedMonitorFloorPayloadIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(0);
        buffer.writeVarInt(129);

        assertThrows(IllegalArgumentException.class,
                () -> new OpenRoomMapPacket().fromBytes(buffer));
    }

    @Test
    void oversizedMonitorAreaPayloadIsRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(2049);

        assertThrows(IllegalArgumentException.class,
                () -> new OpenRoomMapPacket().fromBytes(buffer));
    }

    @Test
    void workCandidatesAreTruncatedWithoutCorruptingTrailingMessage() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new RoomWorkCandidatesPacket(BlockPos.ZERO, WorkType.INSTINCT,
                snapshots(513), "tail").toBytes(buffer);

        assertEquals(BlockPos.ZERO, buffer.readBlockPos());
        assertEquals(WorkType.INSTINCT, buffer.readEnum(WorkType.class));
        assertEquals(512, buffer.readVarInt());
        for (int index = 0; index < 512; index++) RoomEmployeeSnapshot.read(buffer);
        assertEquals("tail", buffer.readUtf(128));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void monitorSnapshotTruncatesCollectionsAndKeepsPacketAligned() {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");
        Set<String> danger = new LinkedHashSet<>();
        for (int index = 0; index < 513; index++) danger.add("area/" + index);
        OpenRoomMapPacket source = new OpenRoomMapPacket(
                List.of(), List.of(), List.of(), snapshots(513), danger,
                12345L, true, true, dimension);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        source.toBytes(buffer);
        OpenRoomMapPacket decoded = new OpenRoomMapPacket();
        decoded.fromBytes(buffer);

        assertEquals(512, decoded.getEmployees().size());
        assertEquals(512, decoded.getDangerousAreaIds().size());
        assertEquals(12345L, decoded.getServerGameTime());
        assertTrue(decoded.isGlobalTt2Enabled());
        assertEquals(dimension, decoded.getDimension());
        assertEquals(0, buffer.readableBytes());
    }

    private static List<RoomEmployeeSnapshot> snapshots(int count) {
        List<RoomEmployeeSnapshot> result = new ArrayList<>(count);
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto");
        for (int index = 0; index < count; index++) {
            result.add(new RoomEmployeeSnapshot(
                    new UUID(0L, index + 1L), "employee-" + index, "control",
                    dimension, BlockPos.ZERO,
                    20.0F, 20.0F, 20.0F, 20.0F,
                    1, 1, 1, 1, 1, "无", "无", "IDLE", true,
                    true, "", -1.0F, 0, null, null, null, 0, 0, 0));
        }
        return result;
    }
}
