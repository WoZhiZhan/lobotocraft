package com.wzz.lobotocraft.client.fear;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.ScreenDistortionEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FearFeedbackRenderer {
    private FearFeedbackRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        FearClientState.Entry entry = FearClientState.textFor(entity.getId());
        if (entry == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        PoseStack poseStack = event.getPoseStack();
        Component text = Component.literal(label(entry.level()));
        float alpha = Math.min(1.0F, entry.remainingTicks() / 6.0F);
        int color = (Math.round(alpha * 255.0F) << 24) | color(entry.level());

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.72D
                + (TEXT_DURATION_OFFSET(entry.remainingTicks())), 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, 0.0F, color, false, poseStack.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        float alpha = FearClientState.visualAlpha();
        if (alpha <= 0.001F) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ScreenDistortionEffect.renderScreenDistortion(graphics, FearClientState.distortionIntensity());
        if (FearClientState.visualLevel() == 5) {
            int redAlpha = Math.round(alpha * 24.0F);
            graphics.fill(0, 0, width, height, (redAlpha << 24) | 0x160000);
        } else {
            int edge = Math.max(18, Math.min(width, height) / 7);
            int color = (Math.round(alpha * 255.0F) << 24) | 0x9B0808;
            graphics.fillGradient(0, 0, width, edge, color, 0x00000000);
            graphics.fillGradient(0, height - edge, width, height, 0x00000000, color);
            graphics.fillGradient(0, 0, edge, height, color, 0x00000000);
            graphics.fillGradient(width - edge, 0, width, height, 0x00000000, color);
        }
        RenderSystem.disableBlend();
    }

    private static double TEXT_DURATION_OFFSET(int remainingTicks) {
        return (24 - remainingTicks) * 0.0125D;
    }

    private static String label(int level) {
        return switch (level) {
            case 2 -> "[!紧张!]";
            case 3 -> "[!!恐惧!!]";
            case 4 -> "[!!!绝望!!!]";
            default -> "[!!!!不知所措!!!!]";
        };
    }

    private static int color(int level) {
        return switch (level) {
            case 2 -> 0xFFF04A;
            case 3 -> 0xFF9829;
            case 4 -> 0xEF3939;
            default -> 0xC40C0C;
        };
    }
}
