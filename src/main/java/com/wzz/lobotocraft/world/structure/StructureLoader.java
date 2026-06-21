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
        private FastSectionPlacer fastPlacer;
        private final AtomicInteger savedTickCounter = new AtomicInteger(0);
        // 计数：pipeline 入队数 / 主线程已放置数
        private final AtomicInteger enqueuedCount = new AtomicInteger(0);
        private final AtomicInteger placedCount = new AtomicInteger(0);

        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failCount = new AtomicInteger(0);

        // 放置队列（跨线程）
        private final LinkedBlockingQueue<PlaceTask> placeQueue =
                new LinkedBlockingQueue<>(computeQueueCapacity());

        /** 单个 Template 的保守内存估算上限（字节）。64³ 满块约 20MB。 */
        private static final long EST_BYTES_PER_TEMPLATE = 20L * 1024 * 1024;
        /** 队列可占用的堆内存比例 */
        private static final double QUEUE_HEAP_FRACTION = 0.15;
        /** 队列容量上下限 */
        private static final int QUEUE_MIN = 16;
        private static final int QUEUE_MAX = 256;

        /**
         * 根据可用堆内存动态计算 placeQueue 容量。
         * 队列里每个元素是一个待放置的 StructureTemplate，
         * 容量过大会 OOM，过小会让 IO 流水线频繁阻塞。
         */
        public static int computeQueueCapacity() {
            long maxHeap = Runtime.getRuntime().maxMemory();
            long budget = (long) (maxHeap * QUEUE_HEAP_FRACTION);
            int cap = (int) (budget / EST_BYTES_PER_TEMPLATE);
            cap = Math.max(QUEUE_MIN, Math.min(QUEUE_MAX, cap));
            return cap;
        }

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
                    if (player != null) player.displayClientMessage(
                            Component.literal("§6结构加载已取消"), false);
                    if (this.callBack != null) this.callBack.run();
                    cleanup();
                    return;
                }

                // 真正的完成判据：pipeline 结束 + 队列空 + 放完所有已入队的
                boolean done = pipelineFinished
                        && placeQueue.isEmpty()
                        && placedCount.get() >= enqueuedCount.get();

                if (!done) {
                    level.getServer().execute(() -> finalizeLoadingWhenDone(totalChunks));
                    return;
                }

                finalizeLoading(totalChunks);
                cleanup();
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
                    try {
                        placeQueue.put(new PlaceTask(template, placePos));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break; // 被中断（多半是取消），退出加载循环
                    }
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
            level.getServer().execute(new Runnable() {
                @Override
                public void run() {
                    if (cancelled) return;

                    // 首次：创建流水线 placer + 预计算区块覆盖（不再 force-load）
                    if (fastPlacer == null) {
                        fastPlacer = new FastSectionPlacer(
                                level, settings, STATIC_RANDOM, targetPos, chunkSize);
                        fastPlacer.precomputeCoverage(xChunks, yChunks, zChunks);
                    }

                    final int MAX_PER_RUN = 8; // 队列小了，每 tick 消费 8 个即可让 IO 持续供给
                    int processed = 0;

                    while (processed < MAX_PER_RUN) {
                        PlaceTask task = placeQueue.poll();
                        if (task == null) break;

                        try {
                            // 保护方块（沿用原逻辑）
                            if (!task.template().palettes.isEmpty()) {
                                Set<BlockPos> placedPositions = new HashSet<>();
                                for (StructureTemplate.StructureBlockInfo blockInfo
                                        : task.template().palettes.get(0).blocks()) {
                                    BlockPos worldPos = StructureTemplate
                                            .calculateRelativePosition(settings, blockInfo.pos())
                                            .offset(task.placePos());
                                    placedPositions.add(worldPos);
                                }
                                fastPlacer.placeTemplate(task.template(), task.placePos());
                                if (!placedPositions.isEmpty()) {
                                    ProtectionHelper.markProtectedBatch(level, placedPositions);
                                }
                            } else {
                                fastPlacer.placeTemplate(task.template(), task.placePos());
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            ModLogger.getLogger().error("主线程直写放置失败", e);
                        } finally {
                            // 放完立刻清空 Template，释放内存（关键！）
                            task.template().palettes.clear();
                            task.template().entityInfoList.clear();
                        }

                        placedCount.incrementAndGet();
                        processed++;
                    }

                    // 周期性 save：每 ~100 tick 存一次，把已收尾区块落盘
                    if (savedTickCounter.incrementAndGet() % 100 == 0) {
                        try { level.getChunkSource().save(false); } catch (Exception ignore) {}
                    }

                    if (!placeQueue.isEmpty() || !pipelineFinished) {
                        level.getServer().execute(this);
                    } else {
                        // 全部放完：处理延迟实体/BE + 兜底收尾 + 最终存盘
                        try {
                            fastPlacer.finalizeAll();
                            level.getChunkSource().save(false);
                        } catch (Exception e) {
                            ModLogger.getLogger().error("最终收尾失败", e);
                        }
                    }
                }
            });
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