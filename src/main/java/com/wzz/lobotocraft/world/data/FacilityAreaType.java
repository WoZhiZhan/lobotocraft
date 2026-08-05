package com.wzz.lobotocraft.world.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/** Types of spaces rendered by the facility overview. */
public enum FacilityAreaType {
    CONTAINMENT,
    CORRIDOR,
    REST_ROOM,
    ELEVATOR,
    OTHER,
    ELEVATOR_SHAFT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FacilityAreaType fromSerializedName(String value) {
        if (value == null || value.isBlank()) return OTHER;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OTHER;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(ordinal());
    }

    public static FacilityAreaType read(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        FacilityAreaType[] values = values();
        return ordinal < values.length ? values[ordinal] : OTHER;
    }
}
