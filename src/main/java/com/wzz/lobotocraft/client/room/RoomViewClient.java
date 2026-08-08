package com.wzz.lobotocraft.client.room;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.performance.ClientPerformanceMonitor;
import com.wzz.lobotocraft.client.screen.RoomMapScreen;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenRoomMapPacket;
import com.wzz.lobotocraft.network.packet.RequestRoomDataPacket;
import com.wzz.lobotocraft.world.data.FloorEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.RoomEntry;
import com.wzz.lobotocraft.world.data.RoomEmployeeSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.List;

/**
 * 客户端：监控视角的过渡动画 + 状态机。
 * <p>
 * 独立的 FORGE 总线订阅者，不需要改动 ForgeModClientEvent。
 * <pre>
 * IDLE --收到 OpenRoomMapPacket--> ENTERING(相机升空 + 转成俯视 + 黑幕淡入, 22t)
 *      --动画结束--> VIEWING(打开 RoomMapScreen, 每 40t 请求刷新一次数据)
 *      --ESC / 点击传送--> EXITING(反向, 14t) --> IDLE
 * </pre>
 * VIEWING 期间如果界面被换成别的（比如右键房间打开了异想体图鉴），
 * 相机会保持在天上不动；等那个界面关掉，监控界面自己会回来 —— 只有明确
 * 调用 {@link #requestExit()} 才会真的落回地面。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RoomViewClient {

    public enum Phase {IDLE, ENTERING, VIEWING, EXITING}

    /** 进入动画时长（tick） */
    public static final int ENTER_TICKS = 22;
    /** 退出动画时长（tick） */
    public static final int EXIT_TICKS = 14;
    /** 监控界面使用固定的观察点，不依赖玩家打开终端时的位置。 */
    private static final Vec3 MONITOR_CAMERA_POSITION = new Vec3(194.0D, 231.0D, 154.0D);
    /** Minecraft yaw 180/-180 faces toward negative Z. */
    private static final float MONITOR_CAMERA_YAW = 180.0F;
    /** Keep the monitor camera level instead of looking down at the facility. */
    private static final float MONITOR_CAMERA_PITCH = 0.0F;
    /** 数据刷新间隔 */
    private static final int REFRESH_INTERVAL = 40;
    /** Stop a delayed server response from allowing refresh requests to pile up indefinitely. */
    private static final int REFRESH_REQUEST_TIMEOUT = 200;

    private static Phase phase = Phase.IDLE;
    private static int ticks = 0;
    private static int prevTicks = 0;
    private static int refreshTimer = 0;
    private static int refreshPendingTicks = 0;
    private static boolean refreshPending = false;

    private static List<RoomEntry> rooms = Collections.emptyList();
    private static List<FloorEntry> floors = Collections.emptyList();
    private static List<FacilityAreaEntry> areas = Collections.emptyList();
    private static List<RoomEmployeeSnapshot> employees = Collections.emptyList();
    private static java.util.Set<String> dangerousAreaIds = Collections.emptySet();
    private static long serverGameTime = 0L;
    private static boolean globalTt2Enabled;
    private static ResourceLocation dimension = null;

    private static float startYaw = 0F;
    private static float startPitch = 0F;

    private RoomViewClient() {
    }

    // -------------------------------------------------------------- 对外接口

    public static Phase getPhase() {
        return phase;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static List<RoomEntry> getRooms() {
        return rooms;
    }

    public static List<FloorEntry> getFloors() {
        return floors;
    }

    public static List<FacilityAreaEntry> getAreas() {
        return areas;
    }

    /** 收到服务端的房间数据 */
    public static void onRoomData(OpenRoomMapPacket packet) {
        long sectionStarted = ClientPerformanceMonitor.beginSection("room_packet_apply");
        try {
            applyRoomData(packet);
        } finally {
            ClientPerformanceMonitor.endSection("room_packet_apply", sectionStarted);
        }
    }

    private static void applyRoomData(OpenRoomMapPacket packet) {
        if (packet.isRefresh()) {
            refreshPending = false;
            refreshPendingTicks = 0;
        }
        rooms = packet.getRooms();
        floors = packet.getFloors();
        areas = packet.getAreas();
        employees = packet.getEmployees();
        dangerousAreaIds = packet.getDangerousAreaIds();
        serverGameTime = packet.getServerGameTime();
        globalTt2Enabled = packet.isGlobalTt2Enabled();
        dimension = packet.getDimension();

        if (packet.isRefresh()) {
            if (Minecraft.getInstance().screen instanceof RoomMapScreen screen) {
                screen.onDataRefreshed(rooms, floors, areas, employees, dangerousAreaIds,
                        serverGameTime, globalTt2Enabled);
            }
            return;
        }

        if (phase != Phase.IDLE) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (rooms.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§c[监控终端] §7没有检测到任何收容间信标。"), true);
            return;
        }

        startYaw = player.getYRot();
        startPitch = player.getXRot();
        phase = Phase.ENTERING;
        ticks = 0;
        prevTicks = 0;
        refreshTimer = 0;
        refreshPendingTicks = 0;
        refreshPending = false;

        playOpenSound();
    }

    /** 开启监控的提示音。想换成别的就改这一处 */
    private static void playOpenSound() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.7F, 0.35F));
        mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5F, 0.35F));
    }

    /** 界面请求退出（ESC / 点了传送） */
    public static void requestExit() {
        if (phase == Phase.VIEWING || phase == Phase.ENTERING) {
            phase = Phase.EXITING;
            ticks = 0;
            prevTicks = 0;
            RoomMapViewState.save();
        }
    }

    /** 强行复位（切世界、死亡等） */
    public static void reset() {
        phase = Phase.IDLE;
        ticks = prevTicks = refreshTimer = refreshPendingTicks = 0;
        refreshPending = false;
        rooms = Collections.emptyList();
        floors = Collections.emptyList();
        areas = Collections.emptyList();
        employees = Collections.emptyList();
        dangerousAreaIds = Collections.emptySet();
        serverGameTime = 0L;
        globalTt2Enabled = false;
        dimension = null;
    }

    // -------------------------------------------------------------- 动画进度

    /** 0 = 玩家视角，1 = 固定监控观察点。 */
    public static float progress(float partialTick) {
        float raw;
        switch (phase) {
            case ENTERING -> raw = Mth.clamp(Mth.lerp(partialTick, prevTicks, ticks) / ENTER_TICKS, 0F, 1F);
            case VIEWING -> raw = 1F;
            case EXITING -> raw = 1F - Mth.clamp(Mth.lerp(partialTick, prevTicks, ticks) / EXIT_TICKS, 0F, 1F);
            default -> raw = 0F;
        }
        return easeInOutCubic(raw);
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
    }

    // -------------------------------------------------------------- 事件

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (phase == Phase.IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        prevTicks = ticks;

        switch (phase) {
            case ENTERING -> {
                lockInput(mc.player);
                ticks++;
                if (ticks >= ENTER_TICKS) {
                    phase = Phase.VIEWING;
                    ticks = ENTER_TICKS;
                    prevTicks = ENTER_TICKS;
                    openMapScreen();
                }
            }
            case VIEWING -> {
                // 界面关掉了但没有走 requestExit（比如刚从异想体图鉴退回来）-> 把监控界面接回去
                if (mc.screen == null) {
                    openMapScreen();
                    return;
                }
                if (refreshPending && ++refreshPendingTicks >= REFRESH_REQUEST_TIMEOUT) {
                    refreshPending = false;
                    refreshPendingTicks = 0;
                }
                if (!refreshPending && ++refreshTimer >= REFRESH_INTERVAL) {
                    refreshTimer = 0;
                    refreshPending = true;
                    refreshPendingTicks = 0;
                    MessageLoader.getLoader().sendToServer(new RequestRoomDataPacket());
                }
            }
            case EXITING -> {
                lockInput(mc.player);
                ticks++;
                if (ticks >= EXIT_TICKS) {
                    reset();
                }
            }
            default -> {
            }
        }
    }

    private static void openMapScreen() {
        Minecraft.getInstance().setScreen(new RoomMapScreen(
                rooms, floors, areas, employees, dangerousAreaIds, serverGameTime,
                globalTt2Enabled, dimension));
    }

    private static void lockInput(LocalPlayer player) {
        player.input.leftImpulse = 0;
        player.input.forwardImpulse = 0;
        player.input.jumping = false;
        player.input.up = false;
        player.input.down = false;
        player.input.left = false;
        player.input.right = false;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (phase == Phase.IDLE) return;

        float p = progress((float) event.getPartialTick());
        if (p <= 0.0001F) return;

        // Move to the fixed observation point while facing horizontally toward negative Z.
        event.setPitch(Mth.lerp(p, startPitch, MONITOR_CAMERA_PITCH));
        float yawDelta = Mth.wrapDegrees(MONITOR_CAMERA_YAW - startYaw);
        event.setYaw(startYaw + yawDelta * p);

        Vec3 current = event.getCamera().getPosition();
        Vec3 monitorPosition = current.lerp(MONITOR_CAMERA_POSITION, p);
        event.getCamera().setPosition(monitorPosition.x, monitorPosition.y, monitorPosition.z);
    }

    /** 过渡期间不渲染手里的物品 */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (phase != Phase.IDLE) {
            event.setCanceled(true);
        }
    }

    /** 黑幕淡入淡出。画在 HUD 层，界面本体会盖在它上面 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (phase == Phase.IDLE) return;

        float p = progress(event.getPartialTick());
        // 前 35% 不遮挡，让玩家看清相机是怎么升上去的
        float fade = Mth.clamp((p - 0.35F) / 0.65F, 0F, 1F);
        if (fade <= 0F) return;

        int alpha = (int) (fade * 0xE0);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            RoomMapViewState.save();
            reset();
        }
    }
}
