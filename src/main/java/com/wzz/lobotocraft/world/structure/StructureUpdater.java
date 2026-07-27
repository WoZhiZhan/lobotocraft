package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.world.data.StructureVersionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 公司结构的生成 / 增量更新入口。
 * <p>
 * 玩家进入公司维度时调一次 {@link #onPlayerEnter}，三种情况：
 * <ol>
 *   <li><b>从没生成过</b> -> 完整生成，生成完把玩家传送到出生点（和你原来的逻辑一模一样）</li>
 *   <li><b>生成过但版本落后</b> -> 增量更新：重跑一遍结构放置，但
 *       {@link StructureUpdateContext} 会拦住所有"玩家自己摆的方块"，
 *       异想体一个都不重放，其它实体也不会重复叠加。玩家位置不动，出生点不重设</li>
 *   <li><b>版本一致</b> -> 什么都不做</li>
 * </ol>
 * 改完结构 nbt 记得把 {@link StructureVersions#LOBOTO} 加一，不然不会触发更新。
 */
public final class StructureUpdater {

    /** 生成完成后的出生点，沿用你原来写死的坐标 */
    private static final BlockPos SPAWN = new BlockPos(195, 273, 29);
    /** 结构放置的起点，沿用原来的 (5,2,0) */
    private static final BlockPos PLACE_ORIGIN = new BlockPos(5, 2, 0);

    private static boolean running = false;

    private StructureUpdater() {
    }

    /** 是否有一次生成 / 更新正在进行（防止两个玩家同时进来触发两次） */
    public static boolean isRunning() {
        return running;
    }

    public static void onPlayerEnter(ServerLevel level, Player player) {
        if (level.dimension() != ModDimensions.LOBOTO_KEY) return;
        if (running) return;

        String name = Structures.LOBOTO.name();
        StructureVersionData versionData = StructureVersionData.get(level);

        if (!Structures.LOBOTO.isGenerated(level)) {
            runFullGeneration(level, player, name, versionData);
            return;
        }

        int stored = versionData.getVersion(name);
        if (stored >= StructureVersions.LOBOTO) {
            // 已经是最新版；老存档第一次进来的话顺手把版本号补上
            if (!versionData.hasRecord(name)) {
                versionData.setVersion(name, stored);
            }
            return;
        }

        runIncrementalUpdate(level, player, name, versionData, stored);
    }

    // ------------------------------------------------------------ 完整生成

    private static void runFullGeneration(ServerLevel level, Player player,
                                          String name, StructureVersionData versionData) {
        running = true;
        Structures.LOBOTO.setGenerated(level, 100, 100, 100);
        StructureUpdateContext.beginFull(level);

        boolean started = StructureLoader.beginLoading(level, player, name, PLACE_ORIGIN,
                () -> level.getServer().execute(() -> {
                    StructureUpdateContext.end(level);
                    versionData.setVersion(name, StructureVersions.LOBOTO);
                    running = false;

                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.teleportTo(SPAWN.getX(), SPAWN.getY(), SPAWN.getZ());
                        serverPlayer.resetFallDistance();
                        serverPlayer.setRespawnPosition(level.dimension(), SPAWN, 0.0F, true, false);
                    }
                    level.setDefaultSpawnPos(SPAWN, 0.0F);
                }));

        if (!started) {
            StructureUpdateContext.end(level);
            running = false;
        }
    }

    // ------------------------------------------------------------ 增量更新

    private static void runIncrementalUpdate(ServerLevel level, Player player, String name,
                                             StructureVersionData versionData, int fromVersion) {
        running = true;
        StructureUpdateContext.beginIncremental(level);

        player.displayClientMessage(Component.literal(
                "§b[公司] §7检测到设施结构有更新（v" + fromVersion + " -> v" + StructureVersions.LOBOTO
                        + "），正在增量施工，你放的东西不会被动。").withStyle(ChatFormatting.RESET), false);

        ModLogger.getLogger().info("开始增量更新结构 {}：v{} -> v{}",
                name, fromVersion, StructureVersions.LOBOTO);

        boolean started = StructureLoader.beginLoading(level, player, name, PLACE_ORIGIN,
                () -> level.getServer().execute(() -> {
                    String summary = StructureUpdateContext.summary(level);
                    StructureUpdateContext.applyProtection(level);
                    StructureUpdateContext.end(level);
                    versionData.setVersion(name, StructureVersions.LOBOTO);
                    running = false;

                    player.displayClientMessage(Component.literal(
                            "§b[公司] §a设施更新完成。§7" + summary), false);
                    ModLogger.getLogger().info("增量更新完成：{}", summary);
                    // 注意：这里刻意不传送玩家、不重设出生点、不清理任何实体
                }));

        if (!started) {
            StructureUpdateContext.end(level);
            running = false;
            player.displayClientMessage(Component.literal("§c[公司] 设施更新启动失败，详见日志。"), false);
        }
    }
}
