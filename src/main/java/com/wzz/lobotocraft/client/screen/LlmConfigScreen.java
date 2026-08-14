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
    private boolean validationPending;
    private String endpointDraft;
    private String modelDraft;
    private String timeoutDraft;
    private String apiKeyDraft = "";

    public LlmConfigScreen(boolean enabled, String endpoint, String model, int timeoutSeconds,
                           boolean hasApiKey, boolean keyFromEnvironment) {
        super(Component.translatable("screen.lobotocraft.llm_config.title"));
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
        addRenderableWidget(LobotomyButton.create(Component.translatable(
                        "screen.lobotocraft.llm_config.department_access"), button ->
                        MessageLoader.getLoader().sendToServer(new RequestDepartmentAccessPacket()))
                .bounds(fieldX + 194, panelY + 38, 92, 20).build());

        endpointBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 81,
                fieldWidth - 8, 12, Component.translatable(
                "screen.lobotocraft.llm_config.endpoint")));
        endpointBox.setBordered(false);
        endpointBox.setMaxLength(512);
        endpointBox.setValue(endpointDraft);

        modelBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 117,
                fieldWidth - 8, 12, Component.translatable("screen.lobotocraft.llm_config.model")));
        modelBox.setBordered(false);
        modelBox.setMaxLength(128);
        modelBox.setValue(modelDraft);

        timeoutBox = addRenderableWidget(new EditBox(font, fieldX + 4, panelY + 153,
                46, 12, Component.translatable("screen.lobotocraft.llm_config.timeout_seconds")));
        timeoutBox.setBordered(false);
        timeoutBox.setMaxLength(2);
        timeoutBox.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,2}"));
        timeoutBox.setValue(timeoutDraft);

        apiKeyBox = addRenderableWidget(new SecretEditBox(font, fieldX + 4, panelY + 189,
                fieldWidth - 94, 12, Component.translatable("screen.lobotocraft.llm_config.api_key")));
        apiKeyBox.setBordered(false);
        apiKeyBox.setMaxLength(512);
        apiKeyBox.setValue(apiKeyDraft);
        apiKeyBox.setFormatter((value, offset) -> FormattedCharSequence.forward(
                "*".repeat(value.length()), Style.EMPTY));
        apiKeyBox.setHint(Component.translatable(hasApiKey
                ? "screen.lobotocraft.llm_config.key_unchanged"
                : "screen.lobotocraft.llm_config.key_input"));
        apiKeyBox.setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.llm_config.key_security")));

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
        clearButton.setTooltip(Tooltip.create(Component.translatable(keyFromEnvironment
                ? "screen.lobotocraft.llm_config.key_environment_tooltip"
                : "screen.lobotocraft.llm_config.key_clear_tooltip")));

        addRenderableWidget(LobotomyButton.create(Component.translatable(
                        "screen.lobotocraft.llm_config.apply"), button -> save())
                .bounds(panelX + panelWidth / 2 - 104, panelY + 226, 96, 22).build());
        addRenderableWidget(LobotomyButton.create(Component.translatable(
                        "screen.lobotocraft.common.close"), button -> onClose())
                .bounds(panelX + panelWidth / 2 + 8, panelY + 226, 96, 22).build());
        setInitialFocus(endpointBox);
    }

    private void save() {
        int timeout;
        try {
            timeout = Integer.parseInt(timeoutBox.getValue());
        } catch (NumberFormatException ignored) {
            setValidation("screen.lobotocraft.llm_config.validation.timeout", false);
            return;
        }
        if (timeout < 3 || timeout > 60) {
            setValidation("screen.lobotocraft.llm_config.validation.timeout", false);
            return;
        }
        try {
            URI endpoint = URI.create(endpointBox.getValue().trim());
            if (!("https".equalsIgnoreCase(endpoint.getScheme())
                    || "http".equalsIgnoreCase(endpoint.getScheme())) || endpoint.getHost() == null) {
                setValidation("screen.lobotocraft.llm_config.validation.endpoint", false);
                return;
            }
        } catch (IllegalArgumentException ignored) {
            setValidation("screen.lobotocraft.llm_config.validation.endpoint", false);
            return;
        }
        if (enabled && modelBox.getValue().isBlank()) {
            setValidation("screen.lobotocraft.llm_config.validation.model", false);
            return;
        }
        LlmConfig.KeyAction action = keyFromEnvironment || (!clearKey && apiKeyBox.getValue().isBlank())
                ? LlmConfig.KeyAction.KEEP
                : clearKey ? LlmConfig.KeyAction.CLEAR : LlmConfig.KeyAction.REPLACE;
        MessageLoader.getLoader().sendToServer(new UpdateLlmConfigPacket(enabled,
                endpointBox.getValue(), modelBox.getValue(), timeout,
                action.name(), action == LlmConfig.KeyAction.REPLACE ? apiKeyBox.getValue() : ""));
        setValidation("screen.lobotocraft.llm_config.validation.applying", true);
    }

    private Component enabledLabel() {
        return Component.translatable(enabled
                ? "screen.lobotocraft.llm_config.enabled"
                : "screen.lobotocraft.llm_config.disabled");
    }

    private Component clearLabel() {
        if (keyFromEnvironment) return Component.literal("ENV");
        return Component.translatable(clearKey
                ? "screen.lobotocraft.llm_config.keep_key"
                : "screen.lobotocraft.llm_config.clear_key");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LobotomyUiRenderer.drawPanel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
        LobotomyUiRenderer.drawPanelHeader(graphics, font, panelX, panelY, panelWidth,
                "LLM DISPATCH CONFIGURATION", tr("screen.lobotocraft.llm_config.heading"));

        graphics.drawString(font, tr("screen.lobotocraft.llm_config.dispatch_service"),
                panelX + 20, panelY + 44,
                LobotomyUiTheme.TEXT_TITLE, false);
        graphics.drawString(font, tr("screen.lobotocraft.llm_config.endpoint"),
                panelX + 20, panelY + 65,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, tr("screen.lobotocraft.llm_config.model"),
                panelX + 20, panelY + 101,
                LobotomyUiTheme.TEXT_MUTED, false);
        graphics.drawString(font, tr("screen.lobotocraft.llm_config.timeout"),
                panelX + 20, panelY + 137,
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
            int msgColor = validationPending ? LobotomyUiTheme.GOLD : LobotomyUiTheme.ALERT_RED;
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

    private void setValidation(String key, boolean pending) {
        validationMessage = tr(key);
        validationPending = pending;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static final class SecretEditBox extends EditBox {
        private SecretEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("screen.lobotocraft.llm_config.api_key_narration",
                    getValue().length());
        }
    }
}
