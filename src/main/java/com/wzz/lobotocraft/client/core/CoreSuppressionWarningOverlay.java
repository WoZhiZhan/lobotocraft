package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public final class CoreSuppressionWarningOverlay {
    private CoreSuppressionWarningOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || !CoreSuppressionWarningClientState.isWaiting()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        GuiGraphics graphics = event.getGuiGraphics();
        String text = "核心抑制等待确认  " + CoreSuppressionWarningClientState.getConfirmed()
                + "/" + CoreSuppressionWarningClientState.getTotal()
                + "  ·  " + CoreSuppressionWarningClientState.getRemainingSeconds() + " 秒";
        int width = minecraft.font.width(text) + 20;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 12;
        graphics.fill(x, y, x + width, y + 22, 0xD0000000);
        graphics.fill(x, y + 21, x + width, y + 22, 0xFFFF4545);
        graphics.drawCenteredString(minecraft.font, text, graphics.guiWidth() / 2, y + 7, 0xFFFFD2D2);
    }
}
