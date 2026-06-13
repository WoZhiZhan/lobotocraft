package com.wzz.lobotocraft.world.structure;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.world.nbt.OptimizedNBTReader;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StructureLoader {
    private static final int PROGRESS_UPDATE_INTERVAL = 30;
    private static final double MEMORY_WARNING_THRESHOLD = 0.910;
    private static final int MEMORY_CHECK_INTERVAL = 80;

    private static final ExecutorService SHARED_EXECUTOR = createSharedExecutor();

    private static ExecutorService createSharedExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int maxThreads = Math.max(12, processors * 3);

        return new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(5000),
                r -> {
                    Thread t = new Thread(r, "StructureLoader-Worker");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static boolean beginLoading(ServerLevel level, Player player, String baseName, BlockPos targetPos) {
        return beginLoading(level, player, baseName, targetPos, Rotation.NONE, null, ()->{});
    }

    public static boolean beginLoading(ServerLevel level, Player player, String baseName, BlockPos targetPos, Runnable callBack) {
        return beginLoading(level, player, baseName, targetPos, Rotation.NONE, null, callBack);
    }

    public static boolean beginLoading(ServerLevel level, Player player, String baseName, BlockPos targetPos, Rotation rotation, BlockPos rotationPivot, Runnable callBack) {
        try {
            CompoundTag metadata = loadMetadata(baseName + "_main");
            int xChunks, yChunks, zChunks, chunkSize;

            if (!metadata.isEmpty() &&
                    metadata.contains("totalXChunks") &&
                    metadata.contains("totalYChunks") &&
                    metadata.contains("totalZChunks") &&
                    metadata.contains("chunkSize") &&
                    "multi_part_structure".equals(metadata.getString("type"))) {
                xChunks = metadata.getInt("totalXChunks");
                yChunks = metadata.getInt("totalYChunks");
                zChunks = metadata.getInt("totalZChunks");
                chunkSize = metadata.getInt("chunkSize");
            } else {
                chunkSize = 0;
                zChunks = 0;
                xChunks = 0;
                yChunks = 0;
            }

            LoadTask task = new LoadTask(level, player, baseName, targetPos, rotation, rotationPivot, xChunks, yChunks, zChunks, chunkSize, callBack);
            SHARED_EXECUTOR.execute(task);
            return true;
        } catch (Exception e) {
            if (player != null)
                player.displayClientMessage(Component.literal("§c加载失败: " + e.getMessage()), false);
            ModLogger.getLogger().error("结构加载失败", e);
            return false;
        }
    }

    public static CompoundTag loadMetadata(String name) {
        ResourceLocation metaId = ResourceUtil.createInstance(name);
        String jarPath = "/data/" + metaId.getNamespace() + "/structures/" + metaId.getPath() + ".nbt";

        try (InputStream input = StructureLoader.class.getResourceAsStream(jarPath)) {
            if (input != null) {
                return OptimizedNBTReader.readNBTFast(new BufferedInputStream(input, 8192));
            }
        } catch (IOException e) {
            ModLogger.getLogger().error("从JAR加载失败: {}", jarPath, e);
        }
        return null;
    }

    public static void saveMetadata(StructureTemplateManager manager, String name, CompoundTag metadata) {
        try {
            String cleanName = name.endsWith(".nbt") ? name.substring(0, name.length() - 4) : name;
            ResourceLocation metaId = ResourceUtil.createInstance(cleanName);
            Path path = manager.getPathToGeneratedStructure(metaId, ".nbt");

            Files.createDirectories(path.getParent());
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path), 8192)) {
                NbtIo.writeCompressed(metadata, output);
            }
        } catch (IOException e) {
            ModLogger.getLogger().error("保存元数据失败", e);
        }
    }

    public static class LoadTask implements Runnable {
        private final ServerLevel level;
        private final Player player;
        private final String baseName;
        private final BlockPos targetPos;
        private final Rotation rotation;
        private final BlockPos rotationPivot;
        private final Runnable callBack;
        private final int xChunks, yChunks, zChunks, chunkSize;
        private volatile boolean cancelled = false;
        private final long startTime;

        // 计数：pipeline 入队数 / 主线程已放置数
        private final AtomicInteger enqueuedCount = new AtomicInteger(0);
        private final AtomicInteger placedCount = new AtomicInteger(0);

        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failCount = new AtomicInteger(0);

        // 放置队列（跨线程）
        private final LinkedBlockingQueue<PlaceTask> placeQueue =
                new LinkedBlockingQueue<>(500);

        // pipeline 状态标记：主线程只依赖它，不依赖 nbtLoader
        private volatile boolean pipelineFinished = false;

        private OptimizedNBTReader.JarPipelineLoader nbtLoader;

        private record PlaceTask(StructureTemplate template, BlockPos placePos) {
        }

        private static final RandomSource STATIC_RANDOM = RandomSource.create(0);

        public LoadTask(ServerLevel level, Player player, String baseName, BlockPos targetPos,
                        Rotation rotation, BlockPos rotationPivot,
                        int xChunks, int yChunks, int zChunks, int chunkSize, Runnable callBack) {
            this.level = level;
            this.player = player;
            this.baseName = baseName;
            this.targetPos = targetPos;
            this.rotation = rotation;
            this.rotationPivot = rotationPivot;
            this.xChunks = xChunks;
            this.yChunks = yChunks;
            this.zChunks = zChunks;
            this.chunkSize = chunkSize;
            this.startTime = System.currentTimeMillis();
            this.callBack = callBack;
        }

        @Override
        public void run() {
            final int totalChunks = xChunks * yChunks * zChunks;
            final StructurePlaceSettings settings = createPlaceSettings();
            final AABB structureBounds = calculateStructureBounds();

            try {

                    nbtLoader = OptimizedNBTReader.createJarPipelineLoader(
                            "/data/"+ ModMain.MODID +"/structures",
                            baseName,
                            xChunks, yChunks, zChunks,
                            this.getClass()
                    );
                    startMainThreadPlacer(settings, structureBounds);
                    loadWithJarPipeline(settings, structureBounds, totalChunks);

            } catch (Exception e) {
                ModLogger.getLogger().error("结构加载过程出错", e);
                if (player != null) {
                    player.displayClientMessage(Component.literal("§c结构加载出错: " + e.getMessage()), false);
                }
            } finally {
                // pipeline 结束标记：必须在 finally，确保异常/取消也能让主线程收尾
                pipelineFinished = true;

                if (nbtLoader != null) {
                    try {
                        nbtLoader.close();
                    } catch (Exception ignore) {
                    }
                }
            }

            // 只在真正放完后 finalize（包含补光/掉落物/清理）
            finalizeLoadingWhenDone(totalChunks);
        }

        private void finalizeLoadingWhenDone(int totalChunks) {
            level.getServer().execute(() -> {
                if (cancelled) {
                    if (player != null) player.displayClientMessage(Component.literal("§6结构加载已取消"), false);
                    // 取消也做一次清理
                    if (this.callBack != null)
                        this.callBack.run();
                    cleanup();
                    return;
                }
                if (!pipelineFinished || !placeQueue.isEmpty() || placedCount.get() < totalChunks) {
                    level.getServer().execute(() -> finalizeLoadingWhenDone(totalChunks));
                    return;
                }

                finalizeLoading(totalChunks); // 只会走一次
                cleanup();                    // 放完后再清理（非常关键）
            });
        }

        private void loadWithJarPipeline(StructurePlaceSettings settings, AABB structureBounds, int totalChunks) {
            int enqueued = 0;
            long lastProgressTime = System.currentTimeMillis();

            while (!cancelled && nbtLoader.hasMore()) {
                OptimizedNBTReader.LoadedNBT loadedNBT = nbtLoader.getNext(20000);

                if (loadedNBT == null) {
                    ModLogger.getLogger().warn("获取NBT超时，流水线状态: {}", nbtLoader.getStats());
                    continue;
                }

                if (!loadedNBT.isSuccess()) {
                    ModLogger.getLogger().error(
                            "NBT加载失败 [{}]: {}",
                            loadedNBT.getFileName(),
                            loadedNBT.error != null ? loadedNBT.error.getMessage() : "未知错误"
                    );
                    failCount.incrementAndGet();
                    continue;
                }

                if (loadedNBT.coord == null) {
                    ModLogger.getLogger().error("无法解析坐标: {}", loadedNBT.getFileName());
                    failCount.incrementAndGet();
                    continue;
                }

                try {
                    StructureTemplate template = new StructureTemplate();
                    template.setAuthor("WZZ");
                    template.load(level.holderLookup(Registries.BLOCK), loadedNBT.tag);

                    BlockPos originalOffset = new BlockPos(
                            loadedNBT.coord.x * chunkSize,
                            loadedNBT.coord.y * chunkSize,
                            loadedNBT.coord.z * chunkSize
                    );
                    BlockPos rotatedOffset = rotateOffset(originalOffset, rotation);
                    BlockPos placePos = targetPos.offset(rotatedOffset);

                    // 入队：不清 template，不算 success
                    placeQueue.add(new PlaceTask(template, placePos));
                    enqueuedCount.incrementAndGet();
                    enqueued++;

                    long now = System.currentTimeMillis();
                    if (enqueued % PROGRESS_UPDATE_INTERVAL == 0 || now - lastProgressTime > 15000) {
                        updateProgressWithPipeline(enqueued, totalChunks, nbtLoader.getStats());
                        lastProgressTime = now;
                    }

                    if (enqueued % MEMORY_CHECK_INTERVAL == 0) {
                        checkMemoryUsage();
                    }
                } catch (Exception e) {
                    ModLogger.getLogger().error("入队分块失败: {}", loadedNBT.getFileName(), e);
                    failCount.incrementAndGet();
                }
            }
        }

        private void startMainThreadPlacer(StructurePlaceSettings settings, AABB structureBounds) {
            // 收集需要刷新的区块
            Set<ChunkPos> affectedChunks = ConcurrentHashMap.newKeySet();

            level.getServer().execute(new Runnable() {
                @Override
                public void run() {
                    if (cancelled) return;

                    final int MAX_PER_RUN = 20;
                    int processed = 0;

                    while (processed < MAX_PER_RUN) {
                        PlaceTask task = placeQueue.poll();
                        if (task == null) break;

                        try {
                            // 记录受影响的区块
                            ChunkPos chunkPos = new ChunkPos(task.placePos);
                            affectedChunks.add(chunkPos);

                            placeChunkSafely(task.template, task.placePos, settings, structureBounds);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            ModLogger.getLogger().error("主线程放置失败", e);
                        } finally {
                            task.template.palettes.clear();
                            task.template.entityInfoList.clear();
                        }

                        placedCount.incrementAndGet();
                        processed++;
                    }

                    // 批量刷新区块（降低频率）
                    if (placedCount.get() % 100 == 0) {
                        refreshAffectedChunks(affectedChunks);
                        affectedChunks.clear();
                    }

                    if (!placeQueue.isEmpty() || !pipelineFinished) {
                        level.getServer().execute(this);
                    } else {
                        // 最后刷新所有剩余区块
                        refreshAffectedChunks(affectedChunks);
                    }
                }
            });
        }

        private void refreshAffectedChunks(Set<ChunkPos> chunks) {
            for (ChunkPos chunkPos : chunks) {
                LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                // 批量标记区块需要更新
                chunk.setUnsaved(true);
            }
        }

        private void updateProgressWithPipeline(int enqueued, int total, OptimizedNBTReader.LoaderStats stats) {
            int progress = total == 0 ? 100 : (enqueued * 100 / total);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal(String.format(
                                "§b加载进度: %d%% (入队 %d/%d) | 流水线: %d%% | 已放置: %d | 成功: %d, 失败: %d",
                                progress, enqueued, total,
                                stats.getProgress(),
                                placedCount.get(),
                                successCount.get(), failCount.get()
                        )),
                        true
                );
            }
        }

        private void placeChunkSafely(StructureTemplate template, BlockPos placePos,
                                      StructurePlaceSettings settings, AABB structureBounds) {
            try {
                Set<BlockPos> placedPositions = new HashSet<>();
                if (!template.palettes.isEmpty()) {
                    for (StructureTemplate.StructureBlockInfo blockInfo : template.palettes.get(0).blocks()) {
                        BlockPos worldPos = StructureTemplate.calculateRelativePosition(settings, blockInfo.pos)
                                .offset(placePos);
                        placedPositions.add(worldPos);
                    }
                }
                template.placeInWorld(level, placePos, placePos, settings, STATIC_RANDOM, 2 | 16 | 128);
                if (!placedPositions.isEmpty()) {
                    ProtectionHelper.markProtectedBatch(level, placedPositions);
                }
            } catch (ClassCastException e) {
                if (player != null) {
                    player.displayClientMessage(Component.literal("§c检测到损坏的方块实体，正在修复..."), false);
                }
                level.removeBlockEntity(placePos);
                template.placeInWorld(level, placePos, placePos, settings, STATIC_RANDOM, 2);
            } catch (Exception e) {
                ModLogger.getLogger().error("放置结构失败", e);
                throw e;
            }
        }

        private StructurePlaceSettings createPlaceSettings() {
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(rotation)
                    .setIgnoreEntities(false)
                    .setKeepLiquids(false);

            if (rotationPivot != null) {
                settings.setRotationPivot(rotationPivot);
            }
            return settings;
        }

        private AABB calculateStructureBounds() {
            BlockPos structureEnd = targetPos.offset(
                    xChunks * chunkSize,
                    yChunks * chunkSize,
                    zChunks * chunkSize
            );
            return new AABB(targetPos, structureEnd);
        }

        private void checkMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            double usedRatio = (double) usedMemory / maxMemory;

            if (usedRatio > MEMORY_WARNING_THRESHOLD) {
                if (player != null) {
                    player.displayClientMessage(
                            Component.literal(String.format("§e内存使用率 %.1f%%，释放缓存中...", usedRatio * 100)),
                            false
                    );
                }
                clearCaches();
            }
        }

        private void clearCaches() {
            System.gc();
            MemoryReserve.release();
        }

        private void finalizeLoading(int totalChunks) {
            if (!cancelled && player != null) {
                long elapsed = System.currentTimeMillis() - startTime;

                player.displayClientMessage(
                        Component.literal(String.format(
                                "§a结构加载完成! 总计: %d, 成功: %d, 失败: %d, 用时: %d 秒",
                                totalChunks, successCount.get(), failCount.get(), elapsed / 1000
                        )),
                        false
                );
                if (this.callBack != null)
                    this.callBack.run();
            } else if (cancelled && player != null) {
                player.displayClientMessage(Component.literal("§6结构加载已取消"), false);
            }
        }

        private void cleanup() {
            clearCaches();
        }

        private BlockPos rotateOffset(BlockPos offset, Rotation rotation) {
            return switch (rotation) {
                case NONE -> offset;
                case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
                case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            };
        }
    }
}