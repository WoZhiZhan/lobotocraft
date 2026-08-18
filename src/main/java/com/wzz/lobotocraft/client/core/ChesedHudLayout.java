package com.wzz.lobotocraft.client.core;

/** Maps the original 1920x1080 ChesedBossUI RectTransforms to Minecraft GUI coordinates. */
final class ChesedHudLayout {
    private ChesedHudLayout() {
    }

    static Layout calculate(int screenWidth, int screenHeight) {
        float scale = Math.min(screenWidth / 1920.0F, screenHeight / 1080.0F);
        float centerX = screenWidth * 0.5F;
        float centerY = screenHeight * 0.5F - 440.0F * scale;
        int iconSize = Math.max(1, Math.round(96.0F * scale));
        return new Layout(
                rect(centerX - 47.2F * scale, centerY - 96.2F * scale, iconSize, iconSize),
                rect(centerX - 1.3F * scale, centerY - 48.3F * scale, iconSize, iconSize),
                rect(centerX - 48.9F * scale, centerY + 1.9F * scale, iconSize, iconSize),
                rect(centerX - 98.4F * scale, centerY - 46.8F * scale, iconSize, iconSize),
                rect(centerX - 18.5F * scale, centerY - 17.5F * scale,
                        Math.max(1, Math.round(37.0F * scale)),
                        Math.max(1, Math.round(35.0F * scale))));
    }

    private static Rect rect(float x, float y, int width, int height) {
        return new Rect(Math.round(x), Math.round(y), width, height);
    }

    record Layout(Rect red, Rect white, Rect black, Rect pale, Rect core) {
    }

    record Rect(int x, int y, int width, int height) {
    }
}
