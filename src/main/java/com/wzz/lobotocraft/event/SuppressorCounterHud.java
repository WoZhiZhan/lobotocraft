package com.wzz.lobotocraft.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 便携式抑制计数 HUD:屏幕左上角显示便携式抑制器图标 + 剩余计数值。
 * 计数由 SuppressorCountSyncPacket 同步到客户端持久数据。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SuppressorCounterHud {

    // 使用现有的便携式抑制器物品贴图
    private static final ResourceLocation ICON =
            ResourceUtil.createInstance("textures/item/portable_suppressor.png");

    // 放在"工作次数"显示(WorkProgressHUD: x=10, y=20)的右边
    private static final int HUD_X = 10 + 60;
    private static final int HUD_Y = 10;       // 与工作次数(y+10=20)同行对齐(图标比文字高,上移4px居中)
    private static final int ICON_SIZE = 16;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.options.renderDebug) return;

        // 读取客户端持久数据中的抑制计数
        CompoundTag persist = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persist.getBoolean("lobotocraft_suppressor_init")) return;
        int count = persist.getInt("lobotocraft_suppressor_count");

        GuiGraphics g = event.getGuiGraphics();

        // 绘制图标
        RenderSystem.enableBlend();
        g.blit(ICON, HUD_X, HUD_Y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.disableBlend();

        // 计数文字(归零时显示红色)
        int color = count <= 0 ? 0xFF5555 : (count <= 20 ? 0xFFD700 : 0xFFFFFF);
        String text = count + " / 120";
        g.drawString(mc.font, text, HUD_X + ICON_SIZE + 4, HUD_Y + 4, color);
    }
}
