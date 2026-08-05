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
        super(Component.literal(type.getDisplayName()));
        this.type = type;
        this.entityId = entityId;
        this.npc = npc;
    }

    @Override
    public void init() {
        int buttonWidth = Math.min(110, Math.max(70, (width - 30) / 2));
        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("准备好了"), button -> {
                    MessageLoader.getLoader().sendToServer(
                            new StartCoreSuppressionPacket(type.ordinal(), entityId));
                    onClose();
                })
                .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("不，还没有"), button -> onClose())
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
        graphics.drawString(font, type.getDisplayName(), 20, height / 2 + 8, color, false);
        int textY = height / 2 + 30;
        textY = drawWrappedCentered(graphics,
                "看来你在这个世界待的时间已经足够久了，准备好接受考验了吗？",
                textY, 0xFFFFFF);
        drawWrappedCentered(graphics,
                "完成当天能源配额并到达逆卡巴拉熔毁等级 VI，即可结束挑战。",
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
