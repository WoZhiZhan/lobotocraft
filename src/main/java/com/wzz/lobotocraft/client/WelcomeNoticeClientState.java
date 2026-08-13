package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.client.screen.WelcomeNoticeScreen;
import net.minecraft.client.Minecraft;

/** Defers the first-save notice until the local player has entered the world. */
public final class WelcomeNoticeClientState {
    private static boolean pending;

    private WelcomeNoticeClientState() {
    }

    public static void requestOpen() {
        pending = true;
    }

    public static void tick() {
        if (!pending) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (minecraft.screen instanceof WelcomeNoticeScreen) {
            pending = false;
            return;
        }
        if (minecraft.screen == null) {
            pending = false;
            minecraft.setScreen(new WelcomeNoticeScreen());
        }
    }

    public static void clear() {
        pending = false;
    }
}
