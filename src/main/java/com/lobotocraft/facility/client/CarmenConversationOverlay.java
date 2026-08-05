package com.lobotocraft.facility.client;

import com.lobotocraft.facility.FacilityPatch;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.init.ModDimensions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FacilityPatch.MOD_ID, value = Dist.CLIENT)
public final class CarmenConversationOverlay {
    private static final ResourceLocation PANEL = texture("conversation_panel.png");
    private static final Deque<Message> MESSAGES = new ArrayDeque<>();

    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_START = 180;
    private static final int LIFETIME_TICKS = 200;
    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 50;
    private static final int PANEL_GAP = 3;
    private static final int PANEL_START_Y = 146;
    /** 文字左缘偏移：避开面板左侧 Carmen 头像区。 */
    private static final int TEXT_X = 55;
    private static final int WRAP_WIDTH = PANEL_WIDTH - 60;
    private static final int MAX_BODY_LINES = 3;
    private static final String SPEAKER = "Carmen";
    private static final String ELLIPSIS = "…";

    private CarmenConversationOverlay() {
    }

    public static void add(String message) {
        MESSAGES.addLast(new Message(message));
        while (MESSAGES.size() > 7) MESSAGES.removeFirst();
    }

    /** 退出世界时清空，否则上个存档未播完的 Carmen 播报会继续在新世界里显示。 */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        MESSAGES.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        Iterator<Message> iterator = MESSAGES.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            message.age++;
            if (message.age >= LIFETIME_TICKS) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_LIST.type()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || MESSAGES.isEmpty()
            || minecraft.player.level().dimension() != ModDimensions.LOBOTO_KEY) return;

        boolean hasPanelTexture = LobotomyUiRenderer.textureExists(PANEL);
        int maxVisible = Math.max(1, (event.getGuiGraphics().guiHeight() - PANEL_START_Y - 2)
            / (PANEL_HEIGHT + PANEL_GAP));
        int skip = Math.max(0, MESSAGES.size() - maxVisible);
        int row = 0;
        int index = 0;
        for (Message message : MESSAGES) {
            if (index++ < skip) continue;
            renderMessage(event.getGuiGraphics(), minecraft.font, message,
                8, PANEL_START_Y + row * (PANEL_HEIGHT + PANEL_GAP), hasPanelTexture);
            row++;
        }
    }

    private static void renderMessage(GuiGraphics graphics, Font font, Message message,
                                      int x, int y, boolean hasPanelTexture) {
        float slide = message.age < FADE_IN_TICKS
            ? slideProgress(message.age / (float) FADE_IN_TICKS)
            : 1.0F;
        x += Math.round((slide - 1.0F) * (PANEL_WIDTH + 8));
        float alpha = message.age < FADE_IN_TICKS
            ? slide
            : message.age >= FADE_OUT_START
                ? (LIFETIME_TICKS - message.age) / (float) (LIFETIME_TICKS - FADE_OUT_START)
                : 1.0F;
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        if (hasPanelTexture) {
            LobotomyUiRenderer.blitScaled(graphics, PANEL, x, y, PANEL_WIDTH, PANEL_HEIGHT, 420, 100);
        } else {
            // 贴图缺失降级：主题面板 + 左侧头像位（DESIGN_PLAN §2C）
            LobotomyUiRenderer.drawPanel(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);
            LobotomyUiRenderer.drawInsetBox(graphics, x + 2, y + 2, 50, PANEL_HEIGHT - 4);
        }
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (message.lines == null) message.lines = wrapBody(font, message.text);

        graphics.drawString(font, SPEAKER, x + TEXT_X, y + 6,
            LobotomyUiRenderer.withAlpha(LobotomyUiTheme.ACCENT, alpha), true);
        int bodyColor = LobotomyUiRenderer.withAlpha(LobotomyUiTheme.TEXT_TITLE, alpha);
        int lineY = y + 18;
        for (String line : message.lines) {
            graphics.drawString(font, line, x + TEXT_X, lineY, bodyColor, true);
            lineY += 10;
        }
        RenderSystem.disableBlend();
    }

    /** 折行一次并缓存到 Message；超出行数时最后一行以"…"截断。 */
    private static String[] wrapBody(Font font, String text) {
        List<FormattedText> parts = font.getSplitter().splitLines(text, WRAP_WIDTH, Style.EMPTY);
        int count = Math.min(parts.size(), MAX_BODY_LINES);
        String[] lines = new String[count];
        for (int i = 0; i < count; i++) lines[i] = parts.get(i).getString();
        if (parts.size() > MAX_BODY_LINES && count > 0) {
            String last = lines[count - 1];
            lines[count - 1] = font.plainSubstrByWidth(last, WRAP_WIDTH - font.width(ELLIPSIS)) + ELLIPSIS;
        }
        return lines;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(FacilityPatch.ASSET_NAMESPACE,
            "textures/gui/conversation/" + name);
    }

    // Original ConFIrst clip: -225 at 0s, 140 at 0.25s, 225 at 0.5s.
    private static float slideProgress(float time) {
        float t = Mth.clamp(time, 0.0F, 1.0F);
        if (t <= 0.5F) {
            return hermite(t * 2.0F, 0.0F, 0.811111F, 0.0F, 0.377778F);
        }
        return hermite((t - 0.5F) * 2.0F, 0.811111F, 1.0F, 0.377778F, 0.0F);
    }

    private static float hermite(float t, float start, float end,
                                 float startTangent, float endTangent) {
        float t2 = t * t;
        float t3 = t2 * t;
        return (2.0F * t3 - 3.0F * t2 + 1.0F) * start
            + (t3 - 2.0F * t2 + t) * startTangent
            + (-2.0F * t3 + 3.0F * t2) * end
            + (t3 - t2) * endTangent;
    }

    private static final class Message {
        private final String text;
        /** 懒折行缓存：首帧计算一次，之后每帧直接绘制。 */
        private String[] lines;
        private int age;

        private Message(String text) {
            this.text = text;
        }
    }
}
