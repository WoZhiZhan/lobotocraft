package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class GeburahChaseWatchdog {
    public static final int STUCK_TICKS = 5 * 20;
    private static final double MIN_PROGRESS_SQR = 0.75D * 0.75D;

    private UUID targetId;
    private Vec3 progressAnchor;
    private int stagnantTicks;

    public boolean observe(UUID observedTargetId, Vec3 currentPosition) {
        if (observedTargetId == null || currentPosition == null) {
            reset();
            return false;
        }
        if (!observedTargetId.equals(targetId) || progressAnchor == null) {
            targetId = observedTargetId;
            progressAnchor = currentPosition;
            stagnantTicks = 0;
            return false;
        }
        if (currentPosition.distanceToSqr(progressAnchor) >= MIN_PROGRESS_SQR) {
            progressAnchor = currentPosition;
            stagnantTicks = 0;
            return false;
        }
        stagnantTicks++;
        if (stagnantTicks < STUCK_TICKS) return false;
        progressAnchor = currentPosition;
        stagnantTicks = 0;
        return true;
    }

    public void reset() {
        targetId = null;
        progressAnchor = null;
        stagnantTicks = 0;
    }
}
