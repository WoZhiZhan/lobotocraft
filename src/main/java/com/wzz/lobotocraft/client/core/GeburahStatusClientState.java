package com.wzz.lobotocraft.client.core;

public final class GeburahStatusClientState {
    private static int bleedStacks;
    private static int bleedTicks;

    private GeburahStatusClientState() {
    }

    public static void updateBleed(int stacks, int ticks) {
        bleedStacks = Math.max(0, Math.min(5, stacks));
        bleedTicks = bleedStacks == 0 ? 0 : Math.max(0, ticks);
    }

    public static void tick() {
        if (bleedTicks > 0) bleedTicks--;
        if (bleedTicks <= 0) {
            bleedTicks = 0;
            bleedStacks = 0;
        }
    }

    public static void clear() {
        bleedStacks = 0;
        bleedTicks = 0;
    }

    public static int bleedStacks() {
        return bleedStacks;
    }

    public static int bleedTicks() {
        return bleedTicks;
    }
}
