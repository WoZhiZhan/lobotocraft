package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CommitUniqueCommandPacket;
import com.wzz.lobotocraft.network.packet.UniqueCommandTargetsPacket.Target;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UniqueCommandTargetScreen extends Screen {
    private final UUID source;
    private final List<Target> targets;
    private final Screen parent;
    private int page;
    private int left, top, panelWidth, rows;

    public UniqueCommandTargetScreen(UUID source, List<Target> targets, Screen parent) {
        super(Component.translatable("work.lobotocraft.commission"));
        this.source = source;
        this.targets = List.copyOf(targets);
        this.parent = parent;
    }

    @Override public void init() {
        panelWidth = Math.min(340, width - 16);
        rows = Math.max(1, Math.min(6, (height - 104) / 24));
        page = Math.min(page, Math.max(0, (targets.size() - 1) / rows));
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - (rows * 24 + 80)) / 2);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        for (int row = 0; row < rows && page * rows + row < targets.size(); row++) {
            Target target = targets.get(page * rows + row);
            addRenderableWidget(LobotomyButton.create(Component.literal(target.name()), button -> {
                MessageLoader.getLoader().sendToServer(new CommitUniqueCommandPacket(source, target.id()));
                onClose();
            }).bounds(left + 12, top + 46 + row * 24, panelWidth - 24, 20).build());
        }
        addRenderableWidget(LobotomyButton.create(Component.literal("<"), button -> {
            page = Math.max(0, page - 1); rebuild();
        }).bounds(left + 12, top + 50 + rows * 24, 32, 20).build()).active = page > 0;
        addRenderableWidget(LobotomyButton.create(Component.literal(">"), button -> {
            page++; rebuild();
        }).bounds(left + panelWidth - 44, top + 50 + rows * 24, 32, 20).build())
                .active = (page + 1) * rows < targets.size();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, left, top, panelWidth, rows * 24 + 80);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, left, top, panelWidth,
                "COMMISSION", Component.translatable("screen.lobotocraft.commission.cost").getString());
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
