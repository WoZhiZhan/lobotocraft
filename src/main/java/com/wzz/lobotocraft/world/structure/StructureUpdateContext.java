package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.capability.IProtectedBlocksCapability;
import com.wzz.lobotocraft.capability.ProtectedBlocksProvider;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 增量更新时的放置过滤器。决定"这一格能不能覆盖""这个实体要不要生成"。
 *
 * <h3>怎么区分结构方块和玩家方块</h3>
 * 不需要额外记账 —— 你原本的 {@code StructureLoader} 在放完每个 part 之后会调
 * {@code ProtectionHelper.markProtectedBatch}，把结构占过的每一格都标记成受保护，
 * 而且有 mixin 拦着不让破坏。所以：
 * <ul>
 *   <li><b>受保护</b> = 当初结构自己放的 -> 可以安全覆盖成新版本</li>
 *   <li><b>不受保护而且不是空气</b> = 玩家自己摆的 -> 跳过，绝不动它</li>
 *   <li><b>空气 / 草之类可替换方块</b> = 新版本要往这里加东西 -> 放</li>
 * </ul>
 * 这个判断对老存档也成立，因为保护标记从第一次生成时就一直在记了。
 *
 * <h3>为什么单独缓存 capability</h3>
 * {@code ProtectionHelper.isProtected} 每次都要 {@code getChunkAt} + 取 capability，
 * 而放置是几百万格的热循环，逐格调会很慢。这里按区块缓存 capability 句柄，
 * 一次结构更新结束后清空。
 */
public final class StructureUpdateContext {

    private static class Session {
        final boolean incremental;
        final Map<Long, IProtectedBlocksCapability> capCache = new HashMap<>();
        /** 增量更新里"新占的空地"，更新结束后要补上保护标记 */
        final Set<BlockPos> newlyPlaced = new HashSet<>();
        int skippedPlayerBlocks = 0;
        int skippedAbnormalities = 0;
        int skippedDuplicateEntities = 0;

        Session(boolean incremental) {
            this.incremental = incremental;
        }
    }

    /** 增量更新时最多记多少个"新占的格子"用于补保护标记，防止内存爆掉 */
    private static final int MAX_TRACKED_NEW_BLOCKS = 200_000;

    /** 同一时刻只会有一个结构在放（你的 StructureLoader 就是这么用的） */
    private static final Map<ServerLevel, Session> SESSIONS = new HashMap<>();

    private StructureUpdateContext() {
    }

    // ------------------------------------------------------------ 会话

    public static void beginFull(ServerLevel level) {
        SESSIONS.put(level, new Session(false));
    }

    public static void beginIncremental(ServerLevel level) {
        SESSIONS.put(level, new Session(true));
    }

    /**
     * 给增量更新中新占的格子补上保护标记。
     * <p>
     * 必须在 {@link #end} 之前调。已经受保护的格子不会重复记，所以这里的量很小。
     * <p>
     * 注意：增量模式下你要把 {@code StructureLoader} 里那句 {@code markProtectedBatch} 跳过，
     * 否则它会把"被跳过的玩家方块"也标记成受保护，等于把玩家自己的东西锁死。
     */
    public static void applyProtection(ServerLevel level) {
        Session session = SESSIONS.get(level);
        if (session == null || !session.incremental || session.newlyPlaced.isEmpty()) return;
        ProtectionHelper.markProtectedBatch(level, session.newlyPlaced);
        session.newlyPlaced.clear();
    }

    public static void end(ServerLevel level) {
        SESSIONS.remove(level);
    }

    public static boolean isIncremental(ServerLevel level) {
        Session session = SESSIONS.get(level);
        return session != null && session.incremental;
    }

    /** 更新结束后给玩家看的统计 */
    public static String summary(ServerLevel level) {
        Session session = SESSIONS.get(level);
        if (session == null) return "";
        return "新增方块 " + session.newlyPlaced.size()
                + " 格，保留玩家方块 " + session.skippedPlayerBlocks
                + " 格，跳过异想体 " + session.skippedAbnormalities
                + " 只，跳过重复实体 " + session.skippedDuplicateEntities + " 个";
    }

    // ------------------------------------------------------------ 方块

    /**
     * 这一格能不能写。完整生成模式永远返回 true，行为和以前完全一致。
     * <p>
     * 由 {@code FastSectionPlacer.placeTemplate} 在写 section 之前调用。
     */
    public static boolean shouldPlaceBlock(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        Session session = SESSIONS.get(level);
        if (session == null || !session.incremental) return true;

        BlockState current = chunk.getBlockState(pos);

        // 1. 玩家自己放的收容间信标 / 楼层标记，任何情况下都不覆盖
        if (current.is(ModBlocks.ROOM_MARKER.get()) || current.is(ModBlocks.FLOOR_MARKER.get())) {
            return false;
        }

        // 2. 空气或可替换方块：新版本要往这里加东西，放。
        //    这些是"新占的地"，原本没有保护标记，记下来等更新结束补上
        if (current.isAir() || current.canBeReplaced()) {
            if (session.newlyPlaced.size() < MAX_TRACKED_NEW_BLOCKS) {
                session.newlyPlaced.add(pos.immutable());
            }
            return true;
        }

        // 3. 受保护 = 当初结构放的，可以更新
        if (isProtected(session, chunk, pos)) {
            return true;
        }

        // 4. 剩下的就是玩家摆的东西，跳过
        session.skippedPlayerBlocks++;
        return false;
    }

    private static boolean isProtected(Session session, LevelChunk chunk, BlockPos pos) {
        ChunkPos chunkPos = chunk.getPos();
        IProtectedBlocksCapability cap = session.capCache.get(chunkPos.toLong());
        if (cap == null) {
            cap = chunk.getCapability(ProtectedBlocksProvider.PROTECTED_BLOCKS).orElse(null);
            if (cap == null) return false;
            session.capCache.put(chunkPos.toLong(), cap);
        }
        return cap.isProtected(pos);
    }

    // ------------------------------------------------------------ 实体

    /**
     * 这个实体要不要生成。完整生成模式永远返回 true。
     * <p>
     * 增量模式下：
     * <ul>
     *   <li><b>异想体一律跳过</b> —— 它们身上有计数器、出逃状态、观察进度这些活数据，
     *       重放一遍等于把玩家的进度洗掉，还会和现有的那只重复</li>
     *   <li>其它实体（画、展示框、职员等）只在<b>附近 1.5 格内没有同类型实体</b>时才生成，
     *       避免每更新一次就叠一层</li>
     * </ul>
     * 由 {@code FastSectionPlacer.addEntities} 在 {@code addFreshEntityWithPassengers} 之前调用。
     */
    public static boolean shouldSpawnEntity(ServerLevel level, Entity entity) {
        Session session = SESSIONS.get(level);
        if (session == null || !session.incremental) return true;

        if (entity instanceof IAbnormality) {
            session.skippedAbnormalities++;
            return false;
        }

        AABB near = new AABB(entity.getX() - 1.5D, entity.getY() - 1.5D, entity.getZ() - 1.5D,
                entity.getX() + 1.5D, entity.getY() + 1.5D, entity.getZ() + 1.5D);
        boolean duplicate = !level.getEntities((Entity) null, near,
                existing -> existing.getType() == entity.getType()).isEmpty();
        if (duplicate) {
            session.skippedDuplicateEntities++;
            return false;
        }
        return true;
    }
}
