package com.wzz.lobotocraft.network;

import net.minecraft.network.FriendlyByteBuf;

/** Shared allocation guards for packet collection fields. */
public final class PacketDecodeLimits {
    private PacketDecodeLimits() {
    }

    public static int readIntCount(FriendlyByteBuf buffer, int maximum, String field) {
        return validateCount(buffer.readInt(), maximum, field);
    }

    public static int readVarIntCount(FriendlyByteBuf buffer, int maximum, String field) {
        return validateCount(buffer.readVarInt(), maximum, field);
    }

    public static int validateCount(int count, int maximum, String field) {
        if (maximum < 0 || count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count
                    + " (maximum " + maximum + ")");
        }
        return count;
    }

    public static void requireExact(int value, int expected, String field) {
        if (value != expected) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value
                    + " (expected " + expected + ")");
        }
    }
}
