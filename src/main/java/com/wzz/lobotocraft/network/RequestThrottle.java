package com.wzz.lobotocraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端请求节流 —— 用于"客户端主动索要完整数据快照"这类重活。
 *
 * <p>快照构建要遍历全部玩家、员工与 E.G.O 仓库并打包成 NBT，正常开界面时每次一份没有问题，
 * 但改装客户端可以每秒发数百次请求把服务端拖死。这里按 (玩家, 通道) 记录上次放行时刻，
 * 冷却内的重复请求直接丢弃。
 *
 * <p>全部调用都发生在服务端主线程（包 handler 经 {@code enqueueWork} 回主线程），
 * 因此使用普通 {@link HashMap} 即可。玩家登出时清理，避免离线玩家条目堆积。
 */
@Mod.EventBusSubscriber(modid = com.wzz.lobotocraft.ModMain.MODID)
public final class RequestThrottle {

    /** 快照类请求的默认冷却（毫秒）。正常开/刷新界面远达不到这个频率。 */
    public static final long SNAPSHOT_COOLDOWN_MS = 500L;
    public static final long NAVIGATION_COOLDOWN_MS = 100L;

    private static final Map<UUID, Map<String, Long>> LAST_ALLOWED = new HashMap<>();

    private RequestThrottle() {
    }

    /**
     * 判断本次请求是否放行，并在放行时记录时刻。
     *
     * @param player     请求者
     * @param channel    通道标识，不同界面互不影响
     * @param cooldownMs 冷却毫秒数
     * @return 放行返回 {@code true}；处于冷却中返回 {@code false}（调用方应静默丢弃该请求）
     */
    public static boolean allow(ServerPlayer player, String channel, long cooldownMs) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        Map<String, Long> channels = LAST_ALLOWED.computeIfAbsent(player.getUUID(),
                ignored -> new HashMap<>());
        Long last = channels.get(channel);
        if (last != null && now - last < cooldownMs) return false;
        channels.put(channel, now);
        return true;
    }

    /** 便捷重载，使用 {@link #SNAPSHOT_COOLDOWN_MS}。 */
    public static boolean allowSnapshot(ServerPlayer player, String channel) {
        return allow(player, channel, SNAPSHOT_COOLDOWN_MS);
    }

    /** Navigation uses a separate, short channel so ESC cannot be eaten by a preceding refresh. */
    public static boolean allowNavigation(ServerPlayer player, String channel) {
        return allow(player, channel, NAVIGATION_COOLDOWN_MS);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LAST_ALLOWED.remove(player.getUUID());
    }
}
