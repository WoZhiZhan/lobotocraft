package com.wzz.lobotocraft.network.packet;

import com.wzz.lobotocraft.employee.EmployeeAppearance;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecruitEmployeePacketTest {
    @Test
    void recruitmentWireFormatCarriesOptionalAppearance() {
        EmployeeAppearance appearance = new EmployeeAppearance(
                EmployeeAppearance.MODERN_YSM_PROVIDER,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "employees/control/clerk", "uniform_a");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new RecruitEmployeePacket("Clerk", 1, 2, 3, 4, "calm", appearance)
                    .toBytes(buffer);

            assertEquals("Clerk", buffer.readUtf(48));
            assertEquals(1, buffer.readVarInt());
            assertEquals(2, buffer.readVarInt());
            assertEquals(3, buffer.readVarInt());
            assertEquals(4, buffer.readVarInt());
            assertEquals("calm", buffer.readUtf());
            assertEquals(appearance.provider(), buffer.readUtf());
            assertEquals(appearance.modelId(), buffer.readUtf());
            assertEquals(appearance.modelPath(), buffer.readUtf());
            assertEquals(appearance.textureId(), buffer.readUtf());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
