package com.wzz.lobotocraft.client.screen;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.StartCoreSuppressionPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class CoreSuppressionDialogueScreen extends Screen {
    private final CoreSuppressionType type;
    private final int entityId;
    private final LivingEntity npc;

    public CoreSuppressionDialogueScreen(CoreSuppressionType type, int entityId, LivingEntity npc) {
        super(Component.translatable("core_suppression.lobotocraft." + type.getId()));
        this.type = type;
        this.entityId = entityId;
        this.npc = npc;
    }

    @Override
    public void init() {
        int buttonWidth = Math.min(110, Math.max(70, (width - 30) / 2));
        int y = height - 30;
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.lobotocraft.core_suppression_dialogue.ready"), button -> {
                    MessageLoader.getLoader().sendToServer(
                            new StartCoreSuppressionPacket(type.ordinal(), entityId));
                    onClose();
                })
                .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable(
                        "screen.lobotocraft.core_suppression_dialogue.not_ready"),
                button -> onClose())
                .bounds(width / 2 + 6, y, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int color = 0xFF000000 | type.getColor();
        graphics.fillGradient(0, height / 2, width, height, 0xDD111111, 0xEE000000);
        graphics.fill(0, height / 2, width, height / 2 + 2, color);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, width / 2, height / 2 + 4, 76, 0, 0, npc);
        graphics.drawString(font, tr("core_suppression.lobotocraft." + type.getId()),
                20, height / 2 + 8, color, false);
        int textY = height / 2 + 30;
        textY = drawWrappedCentered(graphics,
                tr("screen.lobotocraft.core_suppression_dialogue.prompt"),
                textY, 0xFFFFFF);
        String objectiveKey = type == CoreSuppressionType.CHESED
                ? "screen.lobotocraft.core_suppression_dialogue.objective.chesed"
                : "screen.lobotocraft.core_suppression_dialogue.objective";
        drawWrappedCentered(graphics,
                tr(objectiveKey),
                textY + 4, 0xB8B8B8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawWrappedCentered(GuiGraphics graphics, String text, int y, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(text), width - 36);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, color);
            y += font.lineHeight + 2;
        }
        return y;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
