package com.wzz.lobotocraft.world.data;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RoomEntryTest {

    @Test
    void unexpandedStatusOverridesStaleOccupantTitleAndSurvivesSave() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(160, 270, 30));
        room.status = RoomEntry.STATUS_UNEXPANDED;
        room.abnormalityName = "stale occupant";

        assertEquals("未扩建", room.resolveTitle());

        RoomEntry restored = RoomEntry.load(room.save());
        assertEquals(RoomEntry.STATUS_UNEXPANDED, restored.status);
        assertEquals("未扩建", restored.resolveTitle());
    }

    @Test
    void meltdownMarkerSurvivesPersistentSnapshot() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(0, 64, 0));
        room.meltdownActive = true;
        room.meltdownRemainingTicks = 119;

        RoomEntry restored = RoomEntry.load(room.save());
        assertTrue(restored.meltdownActive);
        assertEquals(119, restored.meltdownRemainingTicks);
    }

    @Test
    void repeatedWorkPenaltySurvivesSnapshots() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(2, 70, 3));
        room.repeatedWorkPenaltyPercent = 24;

        assertEquals(24, RoomEntry.load(room.save()).repeatedWorkPenaltyPercent);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        room.write(buffer);
        assertEquals(24, RoomEntry.read(buffer).repeatedWorkPenaltyPercent);
        buffer.release();
    }

    @Test
    void meltdownMarkerSurvivesNetworkSnapshot() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(4, 72, -9));
        room.meltdownActive = true;
        room.meltdownRemainingTicks = 1200;

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        room.write(buffer);
        RoomEntry restored = RoomEntry.read(buffer);

        assertTrue(restored.meltdownActive);
        assertEquals(1200, restored.meltdownRemainingTicks);
    }

    @Test
    void toolClassificationSurvivesPersistentAndNetworkSnapshots() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(7, 80, 12));
        room.toolType = true;

        assertTrue(RoomEntry.load(room.save()).toolType);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        room.write(buffer);
        assertTrue(RoomEntry.read(buffer).toolType);
    }

    @Test
    void abnormalityNameKeySurvivesPersistentAndNetworkSnapshots() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(9, 81, 14));
        room.abnormalityName = "一罪与百善";
        room.abnormalityNameKey = "entity.lobotocraft.onebad";

        assertEquals(room.abnormalityNameKey,
                RoomEntry.load(room.save()).abnormalityNameKey);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        room.write(buffer);
        assertEquals(room.abnormalityNameKey, RoomEntry.read(buffer).abnormalityNameKey);
        buffer.release();
    }

    @Test
    void legacySnapshotWithoutNameKeyFallsBackToStoredName() {
        RoomEntry room = new RoomEntry(
                ResourceLocation.fromNamespaceAndPath("lobotocraft", "loboto"),
                new BlockPos(10, 82, 15));
        room.abnormalityName = "旧存档异想体";
        net.minecraft.nbt.CompoundTag legacy = room.save();
        legacy.remove("AbNameKey");

        RoomEntry restored = RoomEntry.load(legacy);

        assertEquals("", restored.abnormalityNameKey);
        assertInstanceOf(LiteralContents.class, restored.resolveTitleComponent().getContents());
        assertEquals("旧存档异想体", restored.resolveTitleComponent().getString());
    }
}
