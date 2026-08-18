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
    private static final ResourceLocation[] CHESED_DAMAGE_ENABLED = damageTextures("enabled");
    private static final ResourceLocation[] CHESED_DAMAGE_DISABLED = damageTextures("disabled");
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
        if (minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.screen != null) return;
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
        int centerX = graphics.guiWidth() / 2;
        int centerY = 42;
        renderChesedDamageIcon(graphics, ChesedDamageKind.RED,
                centerX - 16, centerY - 32, mask);
        renderChesedDamageIcon(graphics, ChesedDamageKind.WHITE,
                centerX, centerY - 16, mask);
        renderChesedDamageIcon(graphics, ChesedDamageKind.BLACK,
                centerX - 16, centerY, mask);
        renderChesedDamageIcon(graphics, ChesedDamageKind.PALE,
                centerX - 32, centerY - 16, mask);
        graphics.blit(CHESED_CORE, centerX - 6, centerY - 6,
                0, 0, 12, 11, 37, 35);
    }

    private static void renderChesedDamageIcon(GuiGraphics graphics, ChesedDamageKind kind,
                                               int x, int y, int mask) {
        ResourceLocation[] textures = kind.isSelected(mask)
                ? CHESED_DAMAGE_ENABLED : CHESED_DAMAGE_DISABLED;
        graphics.blit(textures[kind.ordinal()], x, y, 0, 0, 32, 32, 96, 96);
    }

    private static ResourceLocation[] damageTextures(String state) {
        ChesedDamageKind[] kinds = ChesedDamageKind.values();
        ResourceLocation[] textures = new ResourceLocation[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            textures[i] = ResourceUtil.createInstance(
                    CHESED_DAMAGE_TEXTURE_ROOT + kinds[i].id() + "_" + state + ".png");
        }
        return textures;
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
