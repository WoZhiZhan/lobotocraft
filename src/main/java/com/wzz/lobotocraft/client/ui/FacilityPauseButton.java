package com.wzz.lobotocraft.client.ui;

import com.lobotocraft.facility.client.ClientFacilityState;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ToggleFacilityPausePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Shared facility pause control for management and monitor screens. */
public final class FacilityPauseButton extends LobotomyButton {
    public FacilityPauseButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), button -> requestToggle());
        setTooltip(Tooltip.create(Component.translatable(
                "screen.lobotocraft.facility.pause.tooltip")));
        updateState();
    }

    public static boolean canToggle() {
        var player = Minecraft.getInstance().player;
        return player != null && player.level().dimension() == ModDimensions.LOBOTO_KEY
                && ClientFacilityState.get().pauseCooldownTicks() <= 0;
    }

    public static void requestToggle() {
        if (canToggle()) {
            MessageLoader.getLoader().sendToServer(new ToggleFacilityPausePacket());
        }
    }

    private void updateState() {
        var state = ClientFacilityState.get();
        Component label = Component.translatable(state.paused()
                ? "screen.lobotocraft.facility.pause.on"
                : "screen.lobotocraft.facility.pause.off");
        if (state.pauseCooldownTicks() > 0) {
            label = Component.translatable("screen.lobotocraft.facility.pause.cooldown",
                    label, (state.pauseCooldownTicks() + 19) / 20);
        }
        setMessage(label);
        setSelected(state.paused());
        active = canToggle();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateState();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
