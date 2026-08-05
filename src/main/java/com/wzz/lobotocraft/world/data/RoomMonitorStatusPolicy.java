package com.wzz.lobotocraft.world.data;

/** Keeps monitor room state aligned with the server's authoritative escape flag. */
public final class RoomMonitorStatusPolicy {
    private RoomMonitorStatusPolicy() {
    }

    public static int trackedStatus(boolean escaped, boolean insideScanBounds) {
        if (escaped) return RoomEntry.STATUS_ESCAPED;
        return insideScanBounds ? RoomEntry.STATUS_OCCUPIED : RoomEntry.STATUS_LOST;
    }

    public static int missingBoundStatus() {
        return RoomEntry.STATUS_LOST;
    }
}
