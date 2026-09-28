package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.core_suppression.chesed.ChesedDamageKind;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class CoreSuppressionOverlay {
    private static final String CHESED_DAMAGE_TEXTURE_ROOT =
            "textures/gui/core_suppression/chesed/damage/";
    private static final ResourceLocation CHESED_CORE = ResourceUtil.createInstance(
            CHESED_DAMAGE_TEXTURE_ROOT + "core.png");

    private CoreSuppressionOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (minecraft.screen == null) {
            if (CoreSuppressionClientState.isActive()) renderAtmosphere(event.getGuiGraphics());
            GeburahEffectCompositor.render(event.getGuiGraphics(), event.getPartialTick());
        }
        if (!CoreSuppressionClientState.isActive()) return;
        if (minecraft.options.hideGui || minecraft.screen != null) return;
        if (CoreSuppressionClientState.getType() != CoreSuppressionType.GEBURAH) {
            renderObjective(event.getGuiGraphics(), CoreSuppressionClientState.getType());
            if (CoreSuppressionClientState.getType() == CoreSuppressionType.CHESED) {
                renderChesedDamageTypes(event.getGuiGraphics());
            }
        } else {
            renderGeburahStatuses(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void renderScreenFilter(ScreenEvent.Render.Post event) {
        if (CoreSuppressionClientState.isActive()) renderAtmosphere(event.getGuiGraphics());
        GeburahEffectCompositor.render(event.getGuiGraphics(), event.getPartialTick());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !minecraft.options.hideGui
                && CoreSuppressionClientState.isActive(CoreSuppressionType.CHESED)) {
            renderChesedDamageTypes(event.getGuiGraphics());
        }
    }

    private static void renderAtmosphere(GuiGraphics graphics) {
        if (CoreSuppressionClientState.isActive(CoreSuppressionType.GEBURAH)) {
            renderGeburahAtmosphere(graphics);
        } else if (usesColorOnlyFilter()) {
            fillColorFilter(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
    }

    private static boolean usesColorOnlyFilter() {
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        return !CoreSuppressionClientState.usesFullVisualEffects()
                || type == CoreSuppressionType.MALKUTH
                || type == CoreSuppressionType.YESOD;
    }

    private static void fillColorFilter(GuiGraphics graphics, int width, int height) {
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        if (type == null) return;
        int color = switch (type) {
            case MALKUTH -> 0x24B64A2A;
            case YESOD -> 0x204F2870;
            case HOD -> 0x24B58A52;
            case NETZACH -> 0x243B7A28;
            case CHESED -> 0x243A628E;
            default -> 0x18000000;
        };
        graphics.fill(0, 0, width, height, color);
    }

    private static void renderGeburahAtmosphere(GuiGraphics graphics) {
        if (GeburahEffectCompositor.mode() == GeburahEffectCompositor.Mode.OFF) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int stage = Math.max(0, CoreSuppressionClientState.getVisualStage());
        float pulse = 0.5F + 0.5F * (float) Math.sin(
                CoreSuppressionClientState.getEffectTime(0.0F) * 1.7F);
        int baseAlpha = (CoreSuppressionClientState.usesFullVisualEffects() ? 46 : 30)
                + stage * 8 + Math.round(pulse * 8.0F);

        int layers = 12;
        int cornerWidth = Math.max(72, width / 3);
        int cornerHeight = Math.max(54, height / 3);
        for (int i = 0; i < layers; i++) {
            int inset = i * 3;
            int alpha = Math.max(2, baseAlpha * (layers - i) / layers);
            int color = argb(alpha, 150 + stage * 18, 8, 10);
            int thickness = Math.max(1, 5 - i / 3);
            int horizontal = Math.max(18, cornerWidth - inset * 2);
            int vertical = Math.max(18, cornerHeight - inset * 2);

            graphics.fill(inset, inset, inset + horizontal, inset + thickness, color);
            graphics.fill(width - inset - horizontal, inset, width - inset, inset + thickness, color);
            graphics.fill(inset, height - inset - thickness, inset + horizontal, height - inset, color);
            graphics.fill(width - inset - horizontal, height - inset - thickness,
                    width - inset, height - inset, color);

            graphics.fill(inset, inset, inset + thickness, inset + vertical, color);
            graphics.fill(width - inset - thickness, inset, width - inset, inset + vertical, color);
            graphics.fill(inset, height - inset - vertical, inset + thickness, height - inset, color);
            graphics.fill(width - inset - thickness, height - inset - vertical,
                    width - inset, height - inset, color);
        }

        int panelAlpha = Math.min(150, baseAlpha + 36);
        int panel = argb(panelAlpha, 70 + stage * 16, 3, 5);
        int line = argb(Math.min(210, panelAlpha + 48), 220, 24, 28);
        drawCornerPanels(graphics, width, height, panel, line);
        GeburahEffectCompositor.renderPersistent(graphics);
    }

    private static void drawCornerPanels(GuiGraphics graphics, int width, int height,
                                         int panelColor, int lineColor) {
        int longBar = Math.max(46, Math.min(120, width / 7));
        int shortBar = Math.max(26, longBar / 2);

        graphics.fill(12, 12, 12 + longBar, 18, panelColor);
        graphics.fill(12, 21, 12 + shortBar, 24, lineColor);
        graphics.fill(18, 28, 22, 28 + shortBar, panelColor);

        graphics.fill(width - 12 - longBar, 12, width - 12, 18, panelColor);
        graphics.fill(width - 12 - shortBar, 21, width - 12, 24, lineColor);
        graphics.fill(width - 22, 28, width - 18, 28 + shortBar, panelColor);

        graphics.fill(12, height - 18, 12 + longBar, height - 12, panelColor);
        graphics.fill(12, height - 24, 12 + shortBar, height - 21, lineColor);
        graphics.fill(18, height - 28 - shortBar, 22, height - 28, panelColor);

        graphics.fill(width - 12 - longBar, height - 18, width - 12, height - 12, panelColor);
        graphics.fill(width - 12 - shortBar, height - 24, width - 12, height - 21, lineColor);
        graphics.fill(width - 22, height - 28 - shortBar, width - 18, height - 28, panelColor);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (Math.max(0, Math.min(255, red)) << 16)
                | (Math.max(0, Math.min(255, green)) << 8)
                | Math.max(0, Math.min(255, blue));
    }

    private static void renderGeburahStatuses(GuiGraphics graphics) {
        int stacks = GeburahStatusClientState.bleedStacks();
        if (stacks <= 0) return;

        Minecraft minecraft = Minecraft.getInstance();
        float seconds = GeburahStatusClientState.bleedTicks() / 20.0F;
        Component label = Component.translatable(
                "overlay.lobotocraft.geburah.bleed", stacks, String.format("%.1f", seconds));
        int width = Math.max(82, minecraft.font.width(label) + 20);
        int x = graphics.guiWidth() - width - 8;
        int y = graphics.guiHeight() - 48;
        graphics.fill(x, y, x + width, y + 22, 0xC40A0708);
        graphics.fill(x, y, x + 4, y + 22, 0xFFE32A31);
        graphics.fill(x + 8, y + 5, x + 13, y + 17, 0xFFC20F1B);
        graphics.drawString(minecraft.font, label, x + 17, y + 7, 0xFFFFD8D8, false);
    }

    private static void renderObjective(GuiGraphics graphics, CoreSuppressionType type) {
        Minecraft minecraft = Minecraft.getInstance();
        int y = graphics.guiHeight() - 50;
        Component title = Component.translatable("overlay.lobotocraft.core_suppression.title",
                Component.translatable(type.getTranslationKey()));
        Component energy = Component.translatable("overlay.lobotocraft.core_suppression.energy",
                CoreSuppressionClientState.getEnergy(), CoreSuppressionClientState.getEnergyQuota());
        Component meltdown = Component.translatable("overlay.lobotocraft.core_suppression.meltdown",
                roman(CoreSuppressionClientState.getMeltdownLevel()),
                roman(CoreSuppressionClientState.getRequiredMeltdownLevel()));
        int width = Math.max(minecraft.font.width(title),
                Math.max(minecraft.font.width(energy), minecraft.font.width(meltdown))) + 12;
        graphics.fill(7, y - 5, 7 + width, y + 31, 0xB0000000);
        graphics.fill(7, y - 5, 9, y + 31, 0xFF000000 | type.getColor());
        graphics.drawString(minecraft.font, title, 13, y, 0xFF000000 | type.getColor(), false);
        graphics.drawString(minecraft.font, energy, 13, y + 10, 0xFFFFFF, false);
        graphics.drawString(minecraft.font, meltdown, 13, y + 20, 0xD0D0D0, false);
        if (CoreSuppressionClientState.requirementsMet()) {
            graphics.drawString(minecraft.font,
                    Component.translatable("overlay.lobotocraft.core_suppression.complete"),
                    13, y - 15, 0x7CFF8C, true);
        }
    }

    private static void renderChesedDamageTypes(GuiGraphics graphics) {
        int mask = CoreSuppressionClientState.getMechanicState();
        ChesedHudLayout.Layout layout = ChesedHudLayout.calculate(
                graphics.guiWidth(), graphics.guiHeight());
        drawChesedDamageIcon(graphics, ChesedDamageKind.RED, layout.red(), mask,
                directionFor(ChesedDamageKind.RED));
        drawChesedDamageIcon(graphics, ChesedDamageKind.WHITE, layout.white(), mask,
                directionFor(ChesedDamageKind.WHITE));
        drawChesedDamageIcon(graphics, ChesedDamageKind.BLACK, layout.black(), mask,
                directionFor(ChesedDamageKind.BLACK));
        drawChesedDamageIcon(graphics, ChesedDamageKind.PALE, layout.pale(), mask,
                directionFor(ChesedDamageKind.PALE));
        ChesedHudLayout.Rect core = layout.core();
        graphics.blit(CHESED_CORE, core.x(), core.y(), core.width(), core.height(),
                0, 0, 37, 35, 37, 35);
    }

    enum TriangleDirection {
        UP, DOWN, LEFT, RIGHT
    }

    static TriangleDirection directionFor(ChesedDamageKind kind) {
        return switch (kind) {
            case RED -> TriangleDirection.DOWN;
            case WHITE -> TriangleDirection.LEFT;
            case BLACK -> TriangleDirection.UP;
            case PALE -> TriangleDirection.RIGHT;
        };
    }

    private static void drawChesedDamageIcon(GuiGraphics graphics, ChesedDamageKind kind,
                                             ChesedHudLayout.Rect rect, int mask,
                                             TriangleDirection direction) {
        boolean selected = kind.isSelected(mask);
        int outer = selected ? 0xFFF48A3C : 0xFFC06A38;
        int inner = selected ? selectedDamageColor(kind) : 0xFF20232A;
        int padding = Math.max(1, Math.min(rect.width(), rect.height()) / 14);
        drawTriangle(graphics, rect.x() + padding, rect.y() + padding,
                Math.max(1, rect.width() - padding * 2),
                Math.max(1, rect.height() - padding * 2), direction, outer);
        int innerPadding = Math.max(2, padding + 2);
        drawTriangle(graphics, rect.x() + innerPadding, rect.y() + innerPadding,
                Math.max(1, rect.width() - innerPadding * 2),
                Math.max(1, rect.height() - innerPadding * 2), direction, inner);

        String letter = switch (kind) {
            case RED -> "R";
            case WHITE -> "W";
            case BLACK -> "B";
            case PALE -> "P";
        };
        Minecraft minecraft = Minecraft.getInstance();
        int textColor = selected ? 0xFFFFFFFF : 0xFFD1D4DA;
        int textX = rect.x() + (rect.width() - minecraft.font.width(letter)) / 2;
        int textY = rect.y() + (rect.height() - 8) / 2;
        graphics.drawString(minecraft.font, letter, textX, textY, textColor, true);
    }

    private static int selectedDamageColor(ChesedDamageKind kind) {
        return switch (kind) {
            case RED -> 0xFFB91F31;
            case WHITE -> 0xFFE8E3C8;
            case BLACK -> 0xFF76508F;
            case PALE -> 0xFF36C8C5;
        };
    }

    private static void drawTriangle(GuiGraphics graphics, int x, int y, int width, int height,
                                     TriangleDirection direction, int color) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        switch (direction) {
            case DOWN -> {
                for (int row = 0; row < height; row++) {
                    int halfWidth = triangleHalfWidth(direction, row, height, width);
                    graphics.fill(centerX - halfWidth, y + row,
                            centerX + halfWidth + 1, y + row + 1, color);
                }
            }
            case UP -> {
                for (int row = 0; row < height; row++) {
                    int halfWidth = triangleHalfWidth(direction, row, height, width);
                    graphics.fill(centerX - halfWidth, y + row,
                            centerX + halfWidth + 1, y + row + 1, color);
                }
            }
            case RIGHT -> {
                for (int column = 0; column < width; column++) {
                    int halfHeight = triangleHalfHeight(direction, column, width, height);
                    graphics.fill(x + column, centerY - halfHeight,
                            x + column + 1, centerY + halfHeight + 1, color);
                }
            }
            case LEFT -> {
                for (int column = 0; column < width; column++) {
                    int halfHeight = triangleHalfHeight(direction, column, width, height);
                    graphics.fill(x + column, centerY - halfHeight,
                            x + column + 1, centerY + halfHeight + 1, color);
                }
            }
        }
    }

    static int triangleHalfWidth(TriangleDirection direction, int position,
                                 int span, int crossSpan) {
        if (span <= 0 || crossSpan <= 0) return 1;
        return switch (direction) {
            case DOWN -> Math.max(1, Math.round((span - position) * crossSpan
                    / (2.0F * span)));
            case UP -> Math.max(1, Math.round((position + 1) * crossSpan
                    / (2.0F * span)));
            case LEFT, RIGHT -> 1;
        };
    }

    static int triangleHalfHeight(TriangleDirection direction, int position,
                                  int span, int crossSpan) {
        if (span <= 0 || crossSpan <= 0) return 1;
        return switch (direction) {
            case RIGHT -> Math.max(1, Math.round((span - position) * crossSpan
                    / (2.0F * span)));
            case LEFT -> Math.max(1, Math.round((position + 1) * crossSpan
                    / (2.0F * span)));
            case UP, DOWN -> 1;
        };
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> "-";
        };
    }
}
