package com.wzz.lobotocraft.natives;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.wzz.lobotocraft.natives.jna.User32;
import com.wzz.lobotocraft.natives.jna.WinUser;
import com.wzz.lobotocraft.util.RandomGenerator;

import java.util.Random;
import java.util.concurrent.*;

public class DisplayMessage {
    static final Random random = new Random();

    static final int DURATION_SECONDS = 3;
    static final int INTERVAL_MS = 50;
    static final int MAX_POPUPS = 500;

    static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(8);

    static final ExecutorService popupPool =
            Executors.newFixedThreadPool(100);

    static final ExecutorService moverPool =
            Executors.newFixedThreadPool(50);

    static int popupCount = 0;

    public static void start() {
        long startTime = System.currentTimeMillis();
        scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - startTime > DURATION_SECONDS * 1000L
                    || popupCount >= MAX_POPUPS) {
                shutdown();
                return;
            }
            int id = popupCount++;
            String title = RandomGenerator.generateRandomStringShort();
            String text = RandomGenerator.generateRandomString();
            int iconType = getRandomIcon();
            popupPool.submit(() -> showPopup(title, text, iconType));
            moverPool.submit(() -> keepMoving(title, 3000));
            moverPool.submit(() -> randomResize(title));
            if (random.nextBoolean()) {
                moverPool.submit(() -> flashWindow(title));
            }

        }, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    static int getRandomIcon() {
        int[] icons = {
                WinUser.MB_ICONERROR,
                WinUser.MB_ICONWARNING,
                WinUser.MB_ICONINFORMATION,
                WinUser.MB_ICONQUESTION
        };
        return icons[random.nextInt(icons.length)];
    }

    static void showPopup(String title, String text, int icon) {
        User32.INSTANCE.MessageBoxW(
                null,
                new WString(text),
                new WString(title),
                WinUser.MB_OK | icon | WinUser.MB_TOPMOST | WinUser.MB_SYSTEMMODAL
        );
    }

    static void keepMoving(String title, int durationMs) {
        long endTime = System.currentTimeMillis() + durationMs;
        try {
            WinDef.HWND hwnd = findWindow(title);
            if (hwnd == null) return;
            while (System.currentTimeMillis() < endTime) {
                int x = random.nextInt(1920);
                int y = random.nextInt(1080);

                User32.INSTANCE.SetWindowPos(
                        hwnd,
                        new WinDef.HWND(new com.sun.jna.Pointer(-1)), // HWND_TOPMOST
                        x, y, 0, 0,
                        WinUser.SWP_NOSIZE | WinUser.SWP_SHOWWINDOW
                );
                Thread.sleep(100 + random.nextInt(200)); // éšæœºç§»åŠ¨é€Ÿåº¦
            }
        } catch (InterruptedException ignored) {}
    }

    static void randomResize(String title) {
        try {
            Thread.sleep(random.nextInt(500));
            WinDef.HWND hwnd = findWindow(title);
            if (hwnd == null) return;

            int width = 200 + random.nextInt(400);
            int height = 100 + random.nextInt(300);

            User32.INSTANCE.SetWindowPos(
                    hwnd, null, 0, 0,
                    width, height,
                    WinUser.SWP_NOMOVE | WinUser.SWP_NOZORDER
            );
        } catch (InterruptedException ignored) {}
    }

    static void flashWindow(String title) {
        try {
            WinDef.HWND hwnd = findWindow(title);
            if (hwnd == null) return;

            for (int i = 0; i < 5; i++) {
                User32.INSTANCE.FlashWindow(hwnd, true);
                Thread.sleep(200);
            }
        } catch (InterruptedException ignored) {}
    }

    static WinDef.HWND findWindow(String title) {
        for (int i = 0; i < 20; i++) {
            WinDef.HWND hwnd = User32.INSTANCE.FindWindowW(null, new WString(title));
            if (hwnd != null) return hwnd;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
        return null;
    }

    static void shutdown() {
        scheduler.shutdownNow();
        popupPool.shutdown();
        moverPool.shutdown();
    }
}