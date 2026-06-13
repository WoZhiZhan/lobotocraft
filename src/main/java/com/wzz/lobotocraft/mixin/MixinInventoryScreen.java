package com.wzz.lobotocraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.lobotocraft.client.screen.EmployeeStatsScreen;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * InventoryScreen的Mixin
 * 在玩家模型左上角添加员工属性按钮
 */
@Mixin(InventoryScreen.class)
public class MixinInventoryScreen {

    @Unique
    private static final ResourceLocation BUTTON_TEXTURE =
            ResourceUtil.createInstance("textures/gui/employee_stats.png");

    @Unique
    private static final int BUTTON_SIZE = 8;
    @Unique
    private int lobotocraft$buttonX = 0;
    @Unique
    private int lobotocraft$buttonY = 0;

    /**
     * 在渲染时添加按钮
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen)(Object)this;

        // 计算按钮位置（在玩家模型左上角）
        int screenWidth = screen.width;
        int screenHeight = screen.height;

        // InventoryScreen的左上角偏移
        int leftPos = (screenWidth - 176) / 2;
        int topPos = (screenHeight - 166) / 2;

        int playerModelBoxLeft = leftPos + 12;    // 玩家模型框的左上角X
        int playerModelBoxTop = topPos + 5;      // 玩家模型框的左上角Y

        // 计算玩家模型框的右上角坐标，然后稍微向内偏移
        lobotocraft$buttonX = playerModelBoxLeft + 66 - BUTTON_SIZE - 2; // 66是模型框宽度
        lobotocraft$buttonY = playerModelBoxTop + 2; // 靠近顶部

        // 判断鼠标是否悬停
        boolean isHovered = mouseX >= lobotocraft$buttonX && mouseX < lobotocraft$buttonX + BUTTON_SIZE &&
                mouseY >= lobotocraft$buttonY && mouseY < lobotocraft$buttonY + BUTTON_SIZE;

        // 渲染贴图
        RenderSystem.setShaderTexture(0, BUTTON_TEXTURE);
        RenderSystem.enableBlend();

        if (isHovered) {
            // 悬停时发光效果
            RenderSystem.setShaderColor(1.0F, 1.0F, 0.6F, 1.0F);
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        graphics.blit(BUTTON_TEXTURE, lobotocraft$buttonX, lobotocraft$buttonY, 0, 0, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        // 如果悬停，显示提示文字
        if (isHovered) {
            graphics.renderTooltip(screen.getMinecraft().font,
                    net.minecraft.network.chat.Component.literal("§6员工属性"),
                    mouseX, mouseY);
        }
    }

    /**
     * 处理鼠标点击
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) {  // 左键
            // 检查是否点击了按钮
            if (mouseX >= lobotocraft$buttonX && mouseX < lobotocraft$buttonX + BUTTON_SIZE &&
                    mouseY >= lobotocraft$buttonY && mouseY < lobotocraft$buttonY + BUTTON_SIZE) {

                InventoryScreen screen = (InventoryScreen)(Object)this;

                // 打开员工属性界面
                if (screen.getMinecraft().player != null) {
                    screen.getMinecraft().getSoundManager().play(new
                            SimpleSoundInstance(SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.VOICE, 1, 1, RandomSource.create(), screen.getMinecraft().player.blockPosition()));
                }
                screen.getMinecraft().setScreen(new EmployeeStatsScreen(screen));

                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}