package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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
        if (!CoreSuppressionClientState.isActive() || minecraft.player == null
                || minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        CoreSuppressionType type = CoreSuppressionClientState.getType();
        renderObjective(graphics, type);
    }

    private static void renderObjective(GuiGraphics graphics, CoreSuppressionType type) {
        Minecraft minecraft = Minecraft.getInstance();
        int y = graphics.guiHeight() - 50;
        String title = type.getDisplayName() + " 核心抑制";
        String ordeal = "黎明 " + Math.min(3, CoreSuppressionClientState.getDawnCompleted())
                + "/3  正午 " + Math.min(2, CoreSuppressionClientState.getMiddayCompleted()) + "/2";
        String work = "接取者 " + CoreSuppressionClientState.getOwnerName() + "：工作 "
                + CoreSuppressionClientState.getWorkCompleted() + "/"
                + CoreSuppressionClientState.getWorkRequired();
        int width = Math.max(minecraft.font.width(title), Math.max(minecraft.font.width(ordeal), minecraft.font.width(work))) + 12;
        graphics.fill(7, y - 5, 7 + width, y + 31, 0xB0000000);
        graphics.fill(7, y - 5, 9, y + 31, 0xFF000000 | type.getColor());
        graphics.drawString(minecraft.font, title, 13, y, 0xFF000000 | type.getColor(), false);
        graphics.drawString(minecraft.font, ordeal, 13, y + 10, 0xFFFFFF, false);
        graphics.drawString(minecraft.font, work, 13, y + 20, 0xD0D0D0, false);
        if (CoreSuppressionClientState.requirementsMet()) {
            graphics.drawString(minecraft.font, "目标完成：接取者可以休息了。", 13, y - 15, 0x7CFF8C, true);
        }
    }
}
