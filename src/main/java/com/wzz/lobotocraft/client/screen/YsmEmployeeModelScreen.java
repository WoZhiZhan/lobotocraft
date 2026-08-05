package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.compat.YsmClientCompat;
import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Lightweight model catalog picker that does not link against YSM classes. */
public final class YsmEmployeeModelScreen extends Screen {
    private final Screen parent;
    private final String provider;
    private final Consumer<EmployeeAppearance> selectionHandler;
    private List<YsmClientCompat.ModelOption> allModels;
    private YsmClientCompat.ModelOption selectedModel;
    private int selectedTexture;
    private int page;
    private int maxPage;
    private int rowsPerPage;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private String searchDraft = "";
    private EditBox searchBox;
    private Button previousPage;
    private Button nextPage;
    private int catalogRefreshTicks;

    public YsmEmployeeModelScreen(Screen parent, String provider, EmployeeAppearance current,
                                  Consumer<EmployeeAppearance> selectionHandler) {
        super(Component.literal("YSM员工外观"));
        this.parent = parent;
        this.provider = provider;
        this.selectionHandler = selectionHandler;
        this.allModels = YsmClientCompat.models(provider);
        if (current != null && current.usesYsm()) {
            this.selectedModel = allModels.stream()
                    .filter(model -> model.provider().equals(current.provider())
                            && (model.modelId().equals(current.modelId())
                            || model.path().equals(current.modelPath())))
                    .findFirst().orElse(null);
            if (selectedModel != null) {
                selectedTexture = selectedModel.textureIndex(current.textureId());
            }
        }
    }

    @Override
    public void init() {
        if (searchBox != null) searchDraft = searchBox.getValue();
        boolean searchFocused = searchBox != null && searchBox.isFocused();
        clearWidgets();
        panelWidth = Math.min(460, width - 16);
        panelHeight = Math.min(330, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, (height - panelHeight) / 2);
        rowsPerPage = Math.max(3, (panelHeight - 162) / 24);

        searchBox = addRenderableWidget(new EditBox(font, panelX + 25, panelY + 53,
                panelWidth - 50, 18, Component.literal("搜索YSM模型")));
        searchBox.setMaxLength(128);
        searchBox.setValue(searchDraft);
        searchBox.setHint(Component.literal("搜索模型"));
        searchBox.setFocused(searchFocused);
        if (searchFocused) setFocused(searchBox);

        LobotomyButton defaultButton = LobotomyButton.create(Component.literal("原版员工外观"), button -> {
            selectedModel = null;
            selectedTexture = 0;
            init();
        }).bounds(panelX + 24, panelY + 78, panelWidth - 48, 20)
                .selected(selectedModel == null).build();
        addRenderableWidget(defaultButton);

        List<YsmClientCompat.ModelOption> filtered = filteredModels();
        maxPage = Math.max(0, (filtered.size() - 1) / rowsPerPage);
        page = Math.min(page, maxPage);
        int first = page * rowsPerPage;
        for (int row = 0; row < rowsPerPage && first + row < filtered.size(); row++) {
            YsmClientCompat.ModelOption model = filtered.get(first + row);
            int y = panelY + 103 + row * 24;
            LobotomyButton button = LobotomyButton.create(
                    Component.literal(ellipsize(model.displayName(), panelWidth - 70)), ignored -> {
                        selectedModel = model;
                        selectedTexture = 0;
                        init();
                    }).bounds(panelX + 24, y, panelWidth - 48, 20)
                    .selected(model.equals(selectedModel))
                    .tooltip(Tooltip.create(Component.literal(model.path())))
                    .build();
            addRenderableWidget(button);
        }

        int textureY = panelY + panelHeight - 55;
        Button previousTexture = addRenderableWidget(LobotomyButton.create(Component.literal("<"), ignored -> {
            if (selectedModel == null) return;
            selectedTexture = Math.floorMod(selectedTexture - 1, selectedModel.textures().size());
        }).bounds(panelX + 24, textureY, 24, 20).build());
        Button nextTexture = addRenderableWidget(LobotomyButton.create(Component.literal(">"), ignored -> {
            if (selectedModel == null) return;
            selectedTexture = Math.floorMod(selectedTexture + 1, selectedModel.textures().size());
        }).bounds(panelX + panelWidth - 48, textureY, 24, 20).build());
        previousTexture.active = selectedModel != null && selectedModel.textures().size() > 1;
        nextTexture.active = previousTexture.active;

        previousPage = addRenderableWidget(LobotomyButton.create(Component.literal("<"), ignored -> {
            page = Math.max(0, page - 1);
            init();
        }).bounds(panelX + 24, panelY + panelHeight - 29, 24, 20)
                .tooltip(Tooltip.create(Component.literal("上一页"))).build());
        nextPage = addRenderableWidget(LobotomyButton.create(Component.literal(">"), ignored -> {
            page = Math.min(maxPage, page + 1);
            init();
        }).bounds(panelX + 52, panelY + panelHeight - 29, 24, 20)
                .tooltip(Tooltip.create(Component.literal("下一页"))).build());
        previousPage.active = page > 0;
        nextPage.active = page < maxPage;

        addRenderableWidget(LobotomyButton.create(Component.literal("确定"), ignored -> confirm())
                .bounds(panelX + panelWidth - 152, panelY + panelHeight - 29, 64, 20).build());
        addRenderableWidget(LobotomyButton.create(Component.literal("取消"), ignored -> onClose())
                .bounds(panelX + panelWidth - 84, panelY + panelHeight - 29, 60, 20).build());
    }

    private void confirm() {
        selectionHandler.accept(selectedModel == null
                ? EmployeeAppearance.DEFAULT : selectedModel.appearance(selectedTexture));
    }

    private List<YsmClientCompat.ModelOption> filteredModels() {
        String search = searchDraft.strip().toLowerCase(Locale.ROOT);
        if (search.isEmpty()) return allModels;
        return allModels.stream().filter(model ->
                model.path().toLowerCase(Locale.ROOT).contains(search)
                        || model.displayName().toLowerCase(Locale.ROOT).contains(search)).toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "EMPLOYEE APPEARANCE", "YSM 模型选择");

        int textureY = panelY + panelHeight - 55;
        LobotomyUiRenderer.drawInsetBox(graphics, panelX + 52, textureY,
                panelWidth - 104, 20);
        String texture = selectedModel == null ? "原版皮肤"
                : selectedModel.textures().get(selectedTexture);
        graphics.drawCenteredString(font, ellipsize(texture, panelWidth - 118),
                panelX + panelWidth / 2, textureY + 6, LobotomyUiTheme.TEXT_BODY);
        String pageText = (page + 1) + " / " + (maxPage + 1);
        graphics.drawString(font, pageText, panelX + 82, panelY + panelHeight - 23,
                LobotomyUiTheme.TEXT_MUTED, false);
        if (allModels.isEmpty()) {
            graphics.drawCenteredString(font, "YSM 模型目录尚未就绪",
                    panelX + panelWidth / 2, panelY + 116, LobotomyUiTheme.TEXT_MUTED);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        searchBox.tick();
        if (allModels.isEmpty() && ++catalogRefreshTicks % 20 == 0) {
            List<YsmClientCompat.ModelOption> refreshed = YsmClientCompat.models(provider);
            if (!refreshed.isEmpty()) {
                allModels = refreshed;
                init();
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        String previous = searchBox.getValue();
        if (!searchBox.charTyped(codePoint, modifiers)) return super.charTyped(codePoint, modifiers);
        if (!Objects.equals(previous, searchBox.getValue())) refreshSearch();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        String previous = searchBox.getValue();
        if (!searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!Objects.equals(previous, searchBox.getValue())) refreshSearch();
        return true;
    }

    private void refreshSearch() {
        searchDraft = searchBox.getValue();
        page = 0;
        init();
    }

    private String ellipsize(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
