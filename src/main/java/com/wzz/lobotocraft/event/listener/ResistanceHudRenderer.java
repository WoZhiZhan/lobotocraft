package com.wzz.lobotocraft.event.listener;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ui.DamageTypeVisual;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.init.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ResistanceHudRenderer {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        // 只在物品栏界面渲染
        if (!(event.getScreen() instanceof InventoryScreen inventoryScreen)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // 获取物品栏GUI的位置
        int guiLeft = inventoryScreen.getGuiLeft();
        int guiTop = inventoryScreen.getGuiTop();
        int xSize = inventoryScreen.getXSize();

        // 获取抗性值
        double redRes = player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        double whiteRes = player.getAttributeValue(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        double blackRes = player.getAttributeValue(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        double blueRes = player.getAttributeValue(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());

        // 渲染位置：物品栏GUI上方，居中显示
        // 每个图标16x16，间隔4像素，总宽度约为 4*16 + 3*4 = 76
        int startX = guiLeft + (xSize - 76) / 2; // 居中
        int startY = guiTop - 30; // GUI上方30像素

        // 渲染四个抗性图标和数值
        renderResistance(guiGraphics, DamageTypeVisual.RED, redRes, startX, startY);
        renderResistance(guiGraphics, DamageTypeVisual.WHITE, whiteRes, startX + 20, startY);
        renderResistance(guiGraphics, DamageTypeVisual.BLACK, blackRes, startX + 40, startY);
        renderResistance(guiGraphics, DamageTypeVisual.PALE, blueRes, startX + 60, startY);
    }

    private static void renderResistance(GuiGraphics guiGraphics, DamageTypeVisual visual, double resistance, int x, int y) {
        // 渲染图标
        RenderSystem.enableBlend();
        LobotomyUiRenderer.blitScaled(guiGraphics, visual.icon(), x, y, 16, 16,
                visual.textureWidth(), visual.textureHeight());
        RenderSystem.disableBlend();
        // 渲染数值
        String resText = String.format("%.1f", resistance);
        int color = 0xFFFFFF;
        int textWidth = Minecraft.getInstance().font.width(resText);
        int textX = x + (16 - textWidth) / 2; // 居中对齐
        guiGraphics.drawString(Minecraft.getInstance().font, resText, textX, y + 18, color, true);
    }
}
