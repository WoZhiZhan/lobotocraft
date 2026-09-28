package com.lobotocraft.facility.client;

import com.lobotocraft.facility.network.FacilityStatePacket;
import com.wzz.lobotocraft.world.data.RoomEntry;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.wzz.lobotocraft.ModMain.MODID, value = Dist.CLIENT)
public final class ClientFacilityState {
    private static FacilityStatePacket state = emptyState();
    private static int warningTicks;
    private static long lastNotificationSerial;

    private ClientFacilityState() {
    }

    public static void update(FacilityStatePacket newState) {
        if (newState.notificationSerial() != lastNotificationSerial && lastNotificationSerial != 0) {
            warningTicks = switch (newState.notificationType()) {
                case 1, 2, 3 -> 100;
                default -> 0;
            };
        }
        lastNotificationSerial = newState.notificationSerial();
        state = newState;
    }

    public static FacilityStatePacket get() {
        return state;
    }

    /** A missing target is inactive; never revive a meltdown from a stale room scan. */
    public static int roomMeltdownTicks(RoomEntry room) {
        if (room.boundAbnormality == null || (room.status != RoomEntry.STATUS_OCCUPIED
                && room.status != RoomEntry.STATUS_ESCAPED)) return -1;
        return state.meltdownRemainingTicks().getOrDefault(room.boundAbnormality, -1);
    }

    public static int meltdownSeconds(int remainingTicks) {
        return Math.max(0, remainingTicks) / 20;
    }

    public static int getWarningTicks() {
        return warningTicks;
    }

    public static void tick() {
        if (warningTicks > 0) warningTicks--;
    }

    /**
     * 退出世界时重置。否则 {@code lastNotificationSerial} 会带着上个存档的编号进入新存档，
     * 与新存档的编号不一致即被判为「来了新通知」，导致进服瞬间闪一次并不存在的熔毁警报；
     * {@code state} 也会在新世界首包到达前沿用旧世界的天数/能源。
     */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = emptyState();
        warningTicks = 0;
        lastNotificationSerial = 0;
    }

    private static FacilityStatePacket emptyState() {
        return new FacilityStatePacket(1, 0, 15, 1, 0, 4, 0, 0, 0, 0, 0L, 0L, 0, 0,
                false, false, 0, 0, 0, 0, 0, 0, false, 0, Map.of());
    }
}
