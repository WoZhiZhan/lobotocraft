package com.lobotocraft.facility.data;

public enum OrdealTier {
    NONE,
    DAWN,
    NOON,
    DUSK,
    MIDNIGHT;

    public static OrdealTier byId(int id) {
        OrdealTier[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
