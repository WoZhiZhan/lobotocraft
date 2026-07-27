package com.wzz.lobotocraft.world;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 收容间区块的临时强制加载。
 * <p>
 * 监控终端要显示实时数据（血量、计数器、出逃状态），前提是那个房间的区块在 tick。
 * 所以打开监控时，会给所有收容间信标所在的区块挂上带 tick 的强制加载票，
 * 之后每次界面刷新（2 秒一次）都会续期；界面关掉后 {@value #TTL_TICKS} tick
 * （60 秒）无人续期就自动释放。
 *
 * <h3>开销提醒</h3>
 * 带 tick 的强制加载 = 那些区块和里面的实体真的在跑。收容间很多的话这是实打实的服务器负载，
 * 所以加了 {@value #MAX_CHUNKS} 个区块的上限，超了就只保留离玩家最近的那些。
 * 觉得卡就把 {@link #MAX_CHUNKS} 调小，或者把 {@link #TTL_TICKS} 调短。
 * <p>
 * 票是按 (modid, owner方块坐标) 注册的，服务器关闭时会全部释放，
 * 不会在存档里留下永久强加载的区块。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class RoomChunkTickets {

    /** 多久没人续期就释放（tick） */
    public static final int TTL_TICKS = 1200;
    /** 同时强制加载的区块数上限 */
    public static final int MAX_CHUNKS = 256;

    private static class Ticket {
        final ResourceKey<Level> dimension;
        final ChunkPos chunk;
        final BlockPos owner;
        int remaining;

        Ticket(ResourceKey<Level> dimension, ChunkPos chunk, BlockPos owner, int remaining) {
            this.dimension = dimension;
            this.chunk = chunk;
            this.owner = owner;
            this.remaining = remaining;
        }
    }

    private static final Map<String, Ticket> TICKETS = new HashMap<>();

    private RoomChunkTickets() {
    }

    private static String key(ResourceKey<Level> dimension, ChunkPos chunk) {
        return dimension.location() + "@" + chunk.toLong();
    }

    /** 已经挂着的票数 */
    public static int activeCount() {
        return TICKETS.size();
    }

    /**
     * 给某个坐标所在的区块挂票 / 续期。
     *
     * @return true 表示这次真的挂上了（或续上了）
     */
    public static boolean keepAlive(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        String key = key(level.dimension(), chunk);

        Ticket existing = TICKETS.get(key);
        if (existing != null) {
            existing.remaining = TTL_TICKS;
            return true;
        }
        if (TICKETS.size() >= MAX_CHUNKS) {
            return false;
        }

        boolean ok = ForgeChunkManager.forceChunk(
                level, ModMain.MODID, pos, chunk.x, chunk.z, true, true);
        if (ok) {
            TICKETS.put(key, new Ticket(level.dimension(), chunk, pos, TTL_TICKS));
        }
        return ok;
    }

    private static void release(Ticket ticket, ServerLevel level) {
        ForgeChunkManager.forceChunk(
                level, ModMain.MODID, ticket.owner, ticket.chunk.x, ticket.chunk.z, false, true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TICKETS.isEmpty()) return;

        Iterator<Map.Entry<String, Ticket>> iterator = TICKETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Ticket ticket = iterator.next().getValue();
            if (--ticket.remaining > 0) continue;

            ServerLevel level = net.minecraftforge.server.ServerLifecycleHooks
                    .getCurrentServer().getLevel(ticket.dimension);
            if (level != null) {
                release(ticket, level);
            }
            iterator.remove();
        }
    }

    /** 服务器关闭时全部释放，避免在存档里留下永久强加载 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Ticket ticket : TICKETS.values()) {
            ServerLevel level = event.getServer().getLevel(ticket.dimension);
            if (level != null) {
                release(ticket, level);
            }
        }
        TICKETS.clear();
    }
}
