package com.wzz.lobotocraft.core_suppression.chesed;

public enum ChesedDamageKind {
    RED(1, "red"),
    WHITE(1 << 1, "white"),
    BLACK(1 << 2, "black"),
    PALE(1 << 3, "pale");

    private final int bit;
    private final String id;

    ChesedDamageKind(int bit, String id) {
        this.bit = bit;
        this.id = id;
    }

    public int bit() {
        return bit;
    }

    public String id() {
        return id;
    }

    public boolean isSelected(int mask) {
        return (mask & bit) != 0;
    }
}
