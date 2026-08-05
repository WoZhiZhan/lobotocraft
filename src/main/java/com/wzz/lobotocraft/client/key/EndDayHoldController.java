package com.wzz.lobotocraft.client.key;

/** Pure client-side state machine for the hold-to-end-day gesture. */
public final class EndDayHoldController {
    private final int requiredTicks;
    private int heldTicks;
    private boolean startSent;

    public EndDayHoldController(int requiredTicks) {
        if (requiredTicks <= 0) throw new IllegalArgumentException("requiredTicks must be positive");
        this.requiredTicks = requiredTicks;
    }

    /**
     * Advances the hold and returns true exactly once when the server request
     * should be sent. Releasing the key or becoming ineligible arms a retry.
     */
    public boolean tick(boolean eligible, boolean keyDown) {
        if (!eligible || !keyDown) {
            reset();
            return false;
        }
        if (startSent) return false;
        heldTicks = Math.min(requiredTicks, heldTicks + 1);
        if (heldTicks < requiredTicks) return false;
        startSent = true;
        return true;
    }

    public float progress() {
        return Math.min(1.0F, heldTicks / (float) requiredTicks);
    }

    public void reset() {
        heldTicks = 0;
        startSent = false;
    }
}
