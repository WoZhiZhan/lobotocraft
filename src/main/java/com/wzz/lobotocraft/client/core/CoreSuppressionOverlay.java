package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class CoreSuppressionOverlay {
    private CoreSuppressionOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!CoreSuppressionClientState.isActive() || minecraft.player == null) return;
        if (minecraft.screen == null) renderColorFilter(event.getGuiGraphics());
        if (minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.screen != null) return;
        renderObjective(event.getGuiGraphics(), CoreSuppressionClientState.getType());
    }

    @SubscribeEvent
    public static void renderScreenFilter(ScreenEvent.Render.Post event) {
        if (!CoreSuppressionClientState.isActive()
                || !usesColorOnlyFilter()) return;
        fillColorFilter(event.getGuiGraphics(), event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
    }

    private static void renderColorFilter(GuiGraphics graphics) {
        if (!usesColorOnlyFilter()) return;
        fillColorFilter(graphics, graphics.guiWidth(), graphics.guiHeight());
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
            default -> 0x18000000;
        };
        graphics.fill(0, 0, width, height, color);
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
