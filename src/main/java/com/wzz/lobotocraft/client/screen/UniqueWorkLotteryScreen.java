package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.work.UniqueWorkRequest.Stage;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Presentation only: closing or slowing this screen cannot change the server draw. */
public final class UniqueWorkLotteryScreen extends Screen {
    private final UUID source;
    private final Screen parent;
    private final List<String> names;
    private String selected;
    private Stage stage;
    private int animationTicks;

    private UniqueWorkLotteryScreen(UUID source, String name, Stage stage, List<String> names, Screen parent) {
        super(Component.translatable("work.lobotocraft.sacrifice"));
        this.source = source;
        this.selected = name;
        this.stage = stage;
        this.names = List.copyOf(names);
        this.parent = parent;
    }

    public static void receive(UUID source, String name, Stage stage, List<String> names) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof UniqueWorkLotteryScreen screen && screen.source.equals(source)) {
            screen.selected = name;
            screen.stage = stage;
        } else if (stage == Stage.DRAWING) {
            client.setScreen(new UniqueWorkLotteryScreen(source, name, stage, names, client.screen));
        } else if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(stageKey(stage)), false);
        }
    }

    private static String stageKey(Stage stage) {
        return "screen.lobotocraft.sacrifice." + stage.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override public void tick() { animationTicks++; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int w = Math.min(340, width - 16);
        int left = (width - w) / 2;
        int top = Math.max(8, (height - 164) / 2);
        LobotomyUiRenderer.drawPanel(graphics, left, top, w, 164);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, left, top, w,
                "CENSORED", title.getString());
        LobotomyUiRenderer.drawInsetBox(graphics, left + 12, top + 49, w - 24, 60);
        boolean spinning = stage == Stage.DRAWING && !names.isEmpty();
        for (int row = -1; row <= 1; row++) {
            String name = spinning
                    ? names.get(Math.floorMod(animationTicks / 3 + row, names.size()))
                    : row == 0 ? selected : "";
            graphics.drawCenteredString(font, font.plainSubstrByWidth(name, w - 42),
                    width / 2, top + 73 + row * 17,
                    row == 0 ? LobotomyUiTheme.GOLD : LobotomyUiTheme.TEXT_MUTED);
        }
        graphics.drawCenteredString(font, Component.translatable(stageKey(stage)),
                width / 2, top + 119, LobotomyUiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, Component.translatable("screen.lobotocraft.sacrifice.close"),
                width / 2, top + 144, LobotomyUiTheme.TEXT_MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
