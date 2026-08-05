package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.client.ui.LobotomyButton;
import com.wzz.lobotocraft.client.ui.LobotomyUiRenderer;
import com.wzz.lobotocraft.client.ui.LobotomyUiTheme;
import com.wzz.lobotocraft.llm.LlmConfig;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.RequestDepartmentAccessPacket;
import com.wzz.lobotocraft.network.packet.RequestFacilityManagementPacket;
import com.wzz.lobotocraft.network.packet.UpdateLlmConfigPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;

public final class LlmConfigScreen extends Screen {
    private static final int MAX_WIDTH = 470;
    private static final int PANEL_HEIGHT = 264;

    private boolean enabled;
    private final String initialEndpoint;
    private final String initialModel;
    private final int initialTimeout;
    private final boolean hasApiKey;
    private final boolean keyFromEnvironment;
    private boolean clearKey;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private LobotomyButton enabledButton;
    private LobotomyButton clearButton;
    private EditBox endpointBox;
    private EditBox modelBox;
    private EditBox timeoutBox;
    private SecretEditBox apiKeyBox;
    private String validationMessage = "";
    private String endpointDraft;
    private String modelDraft;
    private String timeoutDraft;
    private String apiKeyDraft = "";

    public LlmConfigScreen(boolean enabled, String endpoint, String model, int timeoutSeconds,
                           boolean hasApiKey, boolean keyFromEnvironment) {
        super(Component.literal("LLM 热配置"));
        this.enabled = enabled;
        this.initialEndpoint = endpoint;
        this.initialModel = model;
        this.initialTimeout = timeoutSeconds;
        this.hasApiKey = hasApiKey;
        this.keyFromEnvironment = keyFromEnvironment;
        this.endpointDraft = endpoint;
        this.modelDraft = model;
        this.timeoutDraft = Integer.toString(timeoutSeconds);
    }

    @Override
    public void init() {
        if (endpointBox != null) endpointDraft = endpointBox.getValue();
        if (modelBox != null) modelDraft = modelBox.getValue();
        if (timeoutBox != null) timeoutDraft = timeoutBox.getValue();
        if (apiKeyBox != null) apiKeyDraft = apiKeyBox.getValue();
        panelWidth = Math.min(MAX_WIDTH, Math.max(340, width - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        int fieldX = panelX + 20;
        int fieldWidth = panelWidth - 40;

        enabledButton = addRenderableWidget(LobotomyButton.create(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
            enabledButton.setSelected(enabled);
        }).bounds(fieldX + 92, panelY + 38, 92, 20).build());
        enabledButton.setSelected(enabled);
        addRenderableWidget(LobotomyButton.create(Component.literal("部门权限"), button ->
                        MessageLoader.getLoader().sendToServer(new RequestDepartmentAccessPacket()))
                .bounds(fieldX + 194, panelY + 38, 92, 20).build());

        endpointBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 81,
                fieldWidth - 8, 12, Component.literal("Chat Completions 端点")));
        endpointBox.setBordered(false);
        endpointBox.setMaxLength(512);
        endpointBox.setValue(endpointDraft);

        modelBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 117,
                fieldWidth - 8, 12, Component.literal("模型")));
        modelBox.setBordered(false);
        modelBox.setMaxLength(128);
        modelBox.setValue(modelDraft);

        timeoutBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 153,
                46, 12, Component.literal("超时秒数")));
        timeoutBox.setBordered(false);
        timeoutBox.setMaxLength(2);
        timeoutBox.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,2}"));
        timeoutBox.setValue(timeoutDraft);

        apiKeyBox = addRenderableWidget(new SecretEditBox(font, fieldX + 4, panelY + 189,
                fieldWidth - 94, 12, Component.literal("API key")));
        apiKeyBox.setBordered(false);
        apiKeyBox.setMaxLength(512);
        apiKeyBox.setValue(apiKeyDraft);
        apiKeyBox.setFormatter((value, offset) -> FormattedCharSequence.forward(
                "*".repeat(value.length()), Style.EMPTY));
        apiKeyBox.setHint(Component.literal(hasApiKey ? "密钥保持不变" : "输入本次运行使用的密钥"));
        apiKeyBox.setTooltip(Tooltip.create(Component.literal("密钥不会写入磁盘，也不会从服务器回传")));

        clearButton = addRenderableWidget(LobotomyButton.create(clearLabel(), button -> {
            clearKey = !clearKey;
            apiKeyBox.setEditable(!clearKey);
            if (clearKey) {
                apiKeyDraft = "";
                apiKeyBox.setValue("");
            }
            button.setMessage(clearLabel());
            clearButton.setDanger(clearKey);
        }).bounds(panelX + panelWidth - 98, panelY + 183, 78, 20).build());
        clearButton.active = !keyFromEnvironment && hasApiKey;
        clearButton.setTooltip(Tooltip.create(Component.literal(keyFromEnvironment
                ? "环境变量密钥只能在服务器进程外修改" : "清除本次服务器运行中的密钥")));

        addRenderableWidget(LobotomyButton.create(Component.literal("应用"), button -> save())
                .bounds(panelX + panelWidth / 2 - 104, panelY + 226, 96, 22).build());
        addRenderableWidget(LobotomyButton.create(Component.literal("关闭"), button -> onClose())
                .bounds(panelX + panelWidth / 2 + 8, panelY + 226, 96, 22).build());
        setInitialFocus(endpointBox);
    }

    private void save() {
        int timeout;
        try {
            timeout = Integer.parseInt(timeoutBox.getValue());
        } catch (NumberFormatException ignored) {
            validationMessage = "超时必须是 3-60 秒";
            return;
        }
        if (timeout < 3 || timeout > 60) {
            validationMessage = "超时必须是 3-60 秒";
            return;
        }
        try {
            URI endpoint = URI.create(endpointBox.getValue().trim());
            if (!("https".equalsIgnoreCase(endpoint.getScheme())
                    || "http".equalsIgnoreCase(endpoint.getScheme())) || endpoint.getHost() == null) {
                validationMessage = "端点必须是有效的 HTTP(S) 地址";
                return;
            }
        } catch (IllegalArgumentException ignored) {
            validationMessage = "端点必须是有效的 HTTP(S) 地址";
            return;
        }
        if (enabled && modelBox.getValue().isBlank()) {
            validationMessage = "启用调度前必须填写模型";
            return;
        }
        LlmConfig.KeyAction action = keyFromEnvironment || (!clearKey && apiKeyBox.getValue().isBlank())
                ? LlmConfig.KeyAction.KEEP
                : clearKey ? LlmConfig.KeyAction.CLEAR : LlmConfig.KeyAction.REPLACE;
        MessageLoader.getLoader().sendToServer(new UpdateLlmConfigPacket(enabled,
                endpointBox.getValue(), modelBox.getValue(), timeout,
                action.name(), action == LlmConfig.KeyAction.REPLACE ? apiKeyBox.getValue() : ""));
        validationMessage = "正在应用配置...";
    }

    private Component enabledLabel() {
        return Component.literal(enabled ? "已启用" : "已停用");
    }

    private Component clearLabel() {
        if (keyFromEnvironment) return Component.literal("ENV");
        return Component.literal(clearKey ? "保留密钥" : "清除密钥");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "LLM DISPATCH CONFIGURATION", "热配置 · 聊天前缀 #");

        graphics.drawString(font, "调度服务", panelX + 20, panelY + 44,
                LobotomyUiTheme.TEXT_TITLE, false);
        graphics.drawString(font, "Chat Completions 端点", panelX + 20, panelY + 65,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, "模型", panelX + 20, panelY + 101,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, "超时", panelX + 20, panelY + 137,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, "API key", panelX + 20, panelY + 173,
                LobotomyUiTheme.TEXT_MUTED, false);

        renderFieldFrame(graphics, endpointBox);
        renderFieldFrame(graphics, modelBox);
        renderFieldFrame(graphics, timeoutBox);
        renderFieldFrame(graphics, apiKeyBox);

        boolean newKey = !apiKeyBox.getValue().isBlank();
        String keyStatus = keyFromEnvironment ? "API KEY · ENVIRONMENT"
                : clearKey ? "API KEY · CLEAR"
                : newKey ? "API KEY · NEW"
                : hasApiKey ? "API KEY · SESSION" : "API KEY · NOT SET";
        int keyColor = keyFromEnvironment ? LobotomyUiTheme.GOLD
                : !clearKey && (hasApiKey || newKey) ? LobotomyUiTheme.ACCENT
                : LobotomyUiTheme.ALERT_RED;
        LobotomyUiRenderer.drawTag(graphics, font,
                panelX + panelWidth - 20 - font.width(keyStatus) - 8, panelY + 171,
                keyStatus, keyColor);

        if (!validationMessage.isBlank()) {
            int msgColor = validationMessage.startsWith("正在")
                    ? LobotomyUiTheme.GOLD : LobotomyUiTheme.ALERT_RED;
            LobotomyUiRenderer.drawTag(graphics, font,
                    panelX + (panelWidth - font.width(validationMessage) - 8) / 2, panelY + 208,
                    validationMessage, msgColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 输入框外壳：BG_INSET 底 + 默认灰边、聚焦青白边（EditBox 本体无边框） */
    private void renderFieldFrame(GuiGraphics graphics, EditBox box) {
        int x = box.getX() - 4;
        int y = box.getY() - 6;
        int w = box.getWidth() + 8;
        int h = 20;
        LobotomyUiRenderer.drawInsetBox(graphics, x, y, w, h);
        int border = box.isFocused() ? LobotomyUiTheme.LINE_ACCENT : LobotomyUiTheme.LINE_MID;
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    @Override
    public void onClose() {
        MessageLoader.getLoader().sendToServer(RequestFacilityManagementPacket.returnToFacility());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class SecretEditBox extends EditBox {
        private SecretEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.literal("API key 输入框，已输入 " + getValue().length() + " 个字符");
        }
    }
}
