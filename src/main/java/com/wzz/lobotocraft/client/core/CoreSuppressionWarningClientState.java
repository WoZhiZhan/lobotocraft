package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.client.screen.CoreSuppressionWarningScreen;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class CoreSuppressionWarningClientState {
    private static CoreSuppressionType type;
    private static int remainingSeconds;
    private static int confirmed;
    private static int total;
    private static boolean waiting;
    private static long updatedAt;

    private CoreSuppressionWarningClientState() {
    }

    public static void update(int typeOrdinal, int seconds, int confirmedCount,
                              int totalCount, boolean needsChoice) {
        Minecraft minecraft = Minecraft.getInstance();
        CoreSuppressionType nextType = CoreSuppressionType.byOrdinal(typeOrdinal);
        if (nextType == null || seconds <= 0) {
            clear();
            return;
        }
        type = nextType;
        remainingSeconds = Math.max(0, seconds);
        confirmed = Math.max(0, confirmedCount);
        total = Math.max(1, totalCount);
        waiting = !needsChoice;
        updatedAt = Util.getMillis();
        if (needsChoice) {
            if (!(minecraft.screen instanceof CoreSuppressionWarningScreen)) {
                minecraft.setScreen(new CoreSuppressionWarningScreen(nextType));
            }
        } else if (minecraft.screen instanceof CoreSuppressionWarningScreen) {
            minecraft.setScreen(null);
        }
    }

    public static void choiceSubmitted() {
        waiting = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CoreSuppressionWarningScreen) minecraft.setScreen(null);
    }

    public static void clear() {
        type = null;
        remainingSeconds = 0;
        confirmed = 0;
        total = 0;
        waiting = false;
        updatedAt = 0L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CoreSuppressionWarningScreen) minecraft.setScreen(null);
    }

    public static boolean isWaiting() {
        return waiting && type != null && getRemainingSeconds() > 0;
    }

    public static CoreSuppressionType getType() { return type; }
    public static int getConfirmed() { return confirmed; }
    public static int getTotal() { return total; }

    public static int getRemainingSeconds() {
        if (updatedAt == 0L) return 0;
        long elapsed = Math.max(0L, Util.getMillis() - updatedAt);
        return Math.max(0, remainingSeconds - (int) (elapsed / 1000L));
    }
}
