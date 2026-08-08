package com.wzz.lobotocraft.world;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.DepartmentProgressService;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Keeps every unlocked facility section ticking so escaped abnormalities,
 * ordeals and employee timers cannot freeze when no real player is nearby.
 * Monitor lookups outside that fixed footprint may still request short-lived
 * tickets. Both pools are bounded and all tickets are released on shutdown.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class RoomChunkTickets {

    /** 多久没人续期就释放（tick） */
    public static final int TTL_TICKS = 1200;
    /** 同时强制加载的区块数上限 */
    public static final int MAX_CHUNKS = 256;
    /** Hard guard against a malformed facility layout consuming the global cap. */
    public static final int MAX_FACILITY_CHUNKS = 128;
    private static final int MAX_TEMPORARY_CHUNKS = MAX_CHUNKS - MAX_FACILITY_CHUNKS;
    private static final int FACILITY_REFRESH_TICKS = 20;
    private static final Logger LOGGER = LogUtils.getLogger();

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
    private static final Map<String, Ticket> FACILITY_TICKETS = new HashMap<>();
    private static int facilityRefreshCountdown;
    private static boolean facilityLimitReported;

    private RoomChunkTickets() {
    }

    /**
     * Drops tickets left by an interrupted previous server session. The first
     * server tick rebuilds the exact set allowed by current department progress.
     */
    public static void registerLoadingCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(ModMain.MODID, (level, helper) -> {
            helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
            helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);
        });
    }

    private static String key(ResourceKey<Level> dimension, ChunkPos chunk) {
        return dimension.location() + "@" + chunk.toLong();
    }

    /** 已经挂着的票数 */
    public static int activeCount() {
        return TICKETS.size() + FACILITY_TICKETS.size();
    }

    /**
     * 给某个坐标所在的区块挂票 / 续期。
     *
     * @return true 表示这次真的挂上了（或续上了）
     */
    public static boolean keepAlive(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        String key = key(level.dimension(), chunk);

        if (FACILITY_TICKETS.containsKey(key)) return true;

        Ticket existing = TICKETS.get(key);
        if (existing != null) {
            existing.remaining = TTL_TICKS;
            return true;
        }
        if (TICKETS.size() >= MAX_TEMPORARY_CHUNKS || activeCount() >= MAX_CHUNKS) {
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
        if (event.phase != TickEvent.Phase.END) return;

        if (facilityRefreshCountdown-- <= 0) {
            facilityRefreshCountdown = FACILITY_REFRESH_TICKS;
            refreshFacilityTickets(event.getServer());
        }

        if (TICKETS.isEmpty()) return;

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

    private static void refreshFacilityTickets(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return;

        Set<ChunkPos> desired = FacilityWorldLayout.facilityChunks(
                level.dimension().location(), (departmentId, centralSection) -> {
                    EmployeeDepartment department = EmployeeDepartment.byId(departmentId);
                    return department != EmployeeDepartment.UNASSIGNED
                            && DepartmentProgressService.isSectionOpen(
                                    server, department, centralSection);
                });
        if (desired.size() > MAX_FACILITY_CHUNKS) {
            if (!facilityLimitReported) {
                LOGGER.error("Facility chunk plan contains {} chunks; refusing to exceed safety limit {}",
                        desired.size(), MAX_FACILITY_CHUNKS);
                facilityLimitReported = true;
            }
            return;
        }
        facilityLimitReported = false;

        Iterator<Map.Entry<String, Ticket>> existing = FACILITY_TICKETS.entrySet().iterator();
        while (existing.hasNext()) {
            Ticket ticket = existing.next().getValue();
            if (ticket.dimension.equals(level.dimension()) && !desired.contains(ticket.chunk)) {
                release(ticket, level);
                existing.remove();
            }
        }

        desired.stream()
                .sorted(Comparator.comparingInt((ChunkPos chunk) -> chunk.x)
                        .thenComparingInt(chunk -> chunk.z))
                .forEach(chunk -> addFacilityTicket(level, chunk));
    }

    private static void addFacilityTicket(ServerLevel level, ChunkPos chunk) {
        String key = key(level.dimension(), chunk);
        if (FACILITY_TICKETS.containsKey(key)) return;

        Ticket temporary = TICKETS.remove(key);
        if (temporary != null) {
            FACILITY_TICKETS.put(key, temporary);
            return;
        }
        if (activeCount() >= MAX_CHUNKS) return;

        BlockPos owner = new BlockPos(chunk.getMinBlockX() + 8,
                level.getMinBuildHeight(), chunk.getMinBlockZ() + 8);
        boolean ok = ForgeChunkManager.forceChunk(
                level, ModMain.MODID, owner, chunk.x, chunk.z, true, true);
        if (ok) {
            FACILITY_TICKETS.put(key,
                    new Ticket(level.dimension(), chunk, owner, Integer.MAX_VALUE));
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
        for (Ticket ticket : FACILITY_TICKETS.values()) {
            ServerLevel level = event.getServer().getLevel(ticket.dimension);
            if (level != null) {
                release(ticket, level);
            }
        }
        TICKETS.clear();
        FACILITY_TICKETS.clear();
        facilityRefreshCountdown = 0;
        facilityLimitReported = false;
    }
}
