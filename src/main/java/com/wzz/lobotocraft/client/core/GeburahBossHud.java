package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GeburahBossHud {
    private static final String HEALTH_KEY = "bossbar.lobotocraft.geburah.health";
    private static final String POSTURE_KEY = "bossbar.lobotocraft.geburah.posture";
    private static final String EGO_KEY = "bossbar.lobotocraft.geburah.ego";
    private static final int RESERVED_HEIGHT = 48;

    private static float health;
    private static float posture;
    private static float ego;
    private static boolean healthSeen;
    private static boolean postureSeen;
    private static boolean egoSeen;
    private static boolean reservedThisFrame;

    private GeburahBossHud() {
    }

    @SubscribeEvent
    public static void captureBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!CoreSuppressionClientState.isActive(CoreSuppressionType.GEBURAH)) return;
        String key = translationKey(event.getBossEvent().getName());
        boolean geburahBar = true;
        switch (key) {
            case HEALTH_KEY -> {
                health = clamp(event.getBossEvent().getProgress());
                healthSeen = true;
            }
            case POSTURE_KEY -> {
                posture = clamp(event.getBossEvent().getProgress());
                postureSeen = true;
            }
            case EGO_KEY -> {
                ego = clamp(event.getBossEvent().getProgress());
                egoSeen = true;
            }
            default -> geburahBar = false;
        }
        if (!geburahBar) return;

        event.setCanceled(true);
        event.setIncrement(reservedThisFrame ? 0 : RESERVED_HEIGHT);
        reservedThisFrame = true;
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) return;
        if (!CoreSuppressionClientState.isActive(CoreSuppressionType.GEBURAH)) {
            resetFrame();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.hideGui && (healthSeen || postureSeen || egoSeen)) {
            renderHud(event.getGuiGraphics(), minecraft.font, health, posture, ego);
        }
        resetFrame();
    }

    static void renderHud(GuiGraphics graphics, Font font,
                          float healthProgress, float postureProgress, float egoProgress) {
        int screenWidth = graphics.guiWidth();
        int barWidth = Mth.clamp(Math.round(screenWidth * 0.44F), 190, 336);
        int centerX = screenWidth / 2;
        int left = centerX - barWidth / 2;
        int top = 16;

        drawAngularRail(graphics, left, top, barWidth, 10,
                clamp(healthProgress), 0xFF1A0709, 0xFFE01925, 0xFFFF4650);
        drawAngularRail(graphics, left + 10, top + 13, barWidth - 20, 6,
                clamp(postureProgress), 0xFF171306, 0xFFF2D51B, 0xFFFFF06A);
        drawHelmetBadge(graphics, font, centerX, top + 7, clamp(egoProgress));
    }

    private static void drawAngularRail(GuiGraphics graphics, int x, int y,
                                        int width, int height, float progress,
                                        int trackColor, int fillColor, int highlightColor) {
        int outer = 0xFF222326;
        int edge = 0xFF9B9DA0;
        graphics.fill(x - 8, y + 2, x + width + 8, y + height - 2, outer);
        graphics.fill(x - 4, y, x + width + 4, y + height, edge);
        graphics.fill(x, y + 1, x + width, y + height - 1, trackColor);

        int fillWidth = Math.round(width * progress);
        if (fillWidth > 0) {
            graphics.fill(x, y + 1, x + fillWidth, y + height - 1, fillColor);
            graphics.fill(x, y + 1, x + fillWidth, y + 2, highlightColor);
        }

        graphics.fill(x - 13, y, x - 4, y + 2, edge);
        graphics.fill(x - 9, y + 2, x - 4, y + 5, outer);
        graphics.fill(x - 5, y + height - 2, x, y + height + 2, edge);
        graphics.fill(x + width + 4, y, x + width + 13, y + 2, edge);
        graphics.fill(x + width + 4, y + 2, x + width + 9, y + 5, outer);
        graphics.fill(x + width, y + height - 2,
                x + width + 5, y + height + 2, edge);
    }

    private static void drawHelmetBadge(GuiGraphics graphics, Font font,
                                        int centerX, int centerY, float egoProgress) {
        boolean active = egoProgress > 0.001F;
        int outer = active ? 0xFF9D1822 : 0xFF77797B;
        int mid = active ? 0xFF4A090E : 0xFF3D3F41;
        int inner = active ? 0xFF120305 : 0xFF171819;
        int highlight = active ? 0xFFFF4B54 : 0xFFBFC1C2;

        int x = centerX;
        int y = centerY;
        graphics.fill(x - 17, y - 12, x + 17, y + 12, 0xD8000000);
        graphics.fill(x - 13, y - 16, x + 13, y + 16, outer);
        graphics.fill(x - 16, y - 10, x + 16, y + 10, outer);
        graphics.fill(x - 11, y - 13, x + 11, y + 13, mid);
        graphics.fill(x - 13, y - 7, x + 13, y + 9, mid);
        graphics.fill(x - 8, y - 9, x + 8, y + 10, inner);
        graphics.fill(x - 11, y - 5, x + 11, y + 7, inner);

        graphics.fill(x - 22, y - 13, x - 11, y - 10, outer);
        graphics.fill(x - 20, y - 10, x - 14, y - 7, mid);
        graphics.fill(x + 11, y - 13, x + 22, y - 10, outer);
        graphics.fill(x + 14, y - 10, x + 20, y - 7, mid);
        graphics.fill(x - 10, y - 14, x + 10, y - 12, highlight);
        graphics.fill(x - 7, y + 10, x + 7, y + 13, outer);

        String percent = Math.round(egoProgress * 100.0F) + "%";
        int textX = x - font.width(percent) / 2;
        int textY = y - 4;
        graphics.drawString(font, percent, textX + 1, textY + 1, 0xFF000000, false);
        graphics.drawString(font, percent, textX, textY,
                active ? 0xFFFFE6A3 : 0xFFE0E0E0, false);
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey() : "";
    }

    private static float clamp(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static void resetFrame() {
        healthSeen = false;
        postureSeen = false;
        egoSeen = false;
        reservedThisFrame = false;
    }
}
