package com.wzz.lobotocraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.FloorMarkerBlockEntity;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.init.ModBlockEntities;
import com.wzz.lobotocraft.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 两个标记方块都是 INVISIBLE 的，平时完全看不见。
 * <p>
 * 但你摆的时候总得看得见它们在哪，所以：<b>手里拿着监控终端、收容间信标或楼层标记时</b>，
 * 会用线框把它们画出来 —— 收容间信标额外画出当前的扫描范围盒，方便对齐房间。
 * 手上换成别的东西，线框立刻消失。
 * <p>
 * 这个类自带 MOD 总线订阅，注册渲染器不需要你改 ModClientEvent。
 */
public class MarkerBlockRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    /** 信标本体线框颜色 */
    private static final float[] ROOM_COLOR = {0.37F, 0.66F, 0.83F};
    private static final float[] FLOOR_COLOR = {0.72F, 0.45F, 0.90F};
    /** 扫描范围盒的颜色（更淡） */
    private static final float RANGE_ALPHA = 0.28F;

    public MarkerBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static boolean holdingMarkerTool() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return isMarkerTool(player.getMainHandItem().getItem())
                || isMarkerTool(player.getOffhandItem().getItem());
    }

    private static boolean isMarkerTool(Item item) {
        return item == ModItems.ROOM_MARKER.get()
                || item == ModItems.FLOOR_MARKER.get();
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        if (!holdingMarkerTool()) return;

        var lines = buffer.getBuffer(RenderType.lines());

        if (be instanceof RoomMarkerBlockEntity room) {
            LevelRenderer.renderLineBox(pose, lines,
                    0.15D, 0.0D, 0.15D, 0.85D, 0.18D, 0.85D,
                    ROOM_COLOR[0], ROOM_COLOR[1], ROOM_COLOR[2], 1.0F);

            // 扫描范围（相对方块原点）
            double rxz = room.getRadiusXZ();
            double ry = room.getRadiusY();
            LevelRenderer.renderLineBox(pose, lines,
                    0.5D - rxz, 0.5D - ry, 0.5D - rxz,
                    0.5D + rxz, 0.5D + ry, 0.5D + rxz,
                    ROOM_COLOR[0], ROOM_COLOR[1], ROOM_COLOR[2], RANGE_ALPHA);

        } else if (be instanceof FloorMarkerBlockEntity) {
            LevelRenderer.renderLineBox(pose, lines,
                    0.2D, 0.0D, 0.2D, 0.8D, 0.18D, 0.8D,
                    FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2], 1.0F);
            // 楼层标记画一条竖线，远处也容易看到
            LevelRenderer.renderLineBox(pose, lines,
                    0.47D, 0.0D, 0.47D, 0.53D, 3.0D, 0.53D,
                    FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2], 0.5F);
        }
    }

    /** 范围盒可能很大，别让它一出屏幕边缘就被剔掉 */
    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.ROOM_MARKER.get(), MarkerBlockRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.FLOOR_MARKER.get(), MarkerBlockRenderer::new);
        }
    }
}
