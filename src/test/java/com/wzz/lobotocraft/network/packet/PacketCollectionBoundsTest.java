package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.world.data.AbnormalityCandidate;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PacketCollectionBoundsTest {
    @Test
    void toolWarningsAreBoundedOnBothSides() {
        String[] warnings = new String[65];
        Arrays.fill(warnings, "warning");
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new OpenToolUseScreenPacket(
                    1, "name", "entity.lobotocraft.onebad", "type", "title", warnings)
                    .toBytes(encoded);
            encoded.readInt();
            encoded.readUtf();
            encoded.readUtf();
            encoded.readUtf();
            encoded.readUtf();
            assertEquals(64, encoded.readInt());
            for (int i = 0; i < 64; i++) encoded.readUtf();
            assertEquals(0, encoded.readableBytes());
        } finally {
            encoded.release();
        }

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oversized.writeInt(1);
            oversized.writeUtf("name");
            oversized.writeUtf("entity.lobotocraft.onebad");
            oversized.writeUtf("type");
            oversized.writeUtf("title");
            oversized.writeInt(65);
            assertThrows(IllegalArgumentException.class,
                    () -> new OpenToolUseScreenPacket(0, "", "", "", "", new String[0])
                            .fromBytes(oversized));
        } finally {
            oversized.release();
        }
    }

    @Test
    void fullscreenTexturesAreTruncatedWithoutMisaligningDuration() {
        List<ResourceLocation> textures = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            textures.add(ResourceLocation.fromNamespaceAndPath("lobotocraft", "texture_" + i));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FullScreenRenderMessage.builder().textures(textures, 123).build().toBytes(buffer);
            assertEquals(1024 | 2048, buffer.readInt());
            assertEquals(64, buffer.readInt());
            for (int i = 0; i < 64; i++) buffer.readResourceLocation();
            assertEquals(123, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void nearbyCandidatesAreTruncatedAtProtocolLimit() {
        List<AbnormalityCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 513; i++) candidates.add(candidate(i));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new NearbyAbnormalitiesPacket(BlockPos.ZERO, candidates).toBytes(buffer);
            assertEquals(BlockPos.ZERO, buffer.readBlockPos());
            assertEquals(512, buffer.readVarInt());
            for (int i = 0; i < 512; i++) AbnormalityCandidate.read(buffer);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void workScreenAlwaysWritesTheFixedShape() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new OpenWorkScreenPacket(1, new float[][]{{0.25F}}, RiskLevel.HE,
                    2, 3, new boolean[]{true, false, true, false, true}).toBytes(buffer);
            assertEquals(1, buffer.readInt());
            assertEquals(4, buffer.readInt());
            assertEquals(5, buffer.readInt());
            assertEquals(0.25F, buffer.readFloat());
            for (int i = 1; i < 20; i++) buffer.readFloat();
            assertEquals(RiskLevel.HE, buffer.readEnum(RiskLevel.class));
            assertEquals(2, buffer.readInt());
            assertEquals(3, buffer.readInt());
            assertEquals(4, buffer.readInt());
            for (int i = 0; i < 4; i++) buffer.readBoolean();
            assertEquals(0, buffer.readVarInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static AbnormalityCandidate candidate(int index) {
        AbnormalityCandidate candidate = new AbnormalityCandidate();
        candidate.uuid = new UUID(0L, index + 1L);
        candidate.code = "code";
        candidate.name = "name";
        candidate.nameKey = "entity.lobotocraft.onebad";
        candidate.riskName = "HE";
        return candidate;
    }
}
