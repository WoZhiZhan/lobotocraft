package com.wzz.lobotocraft.world.nbt;

import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraft.nbt.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * JAR资源专用流水线NBT加载器
 * 特点：预加载资源流，边读边用，无缓存
 */
public class OptimizedNBTReader {

    // ==================== 配置常量 ====================
    private static final int BUFFER_SIZE = 16384; // 16KB缓冲区
    private static final int IO_THREADS = 8; // IO线程数（JAR读取可以多线程）
    private static final int PIPELINE_DEPTH = 30; // 流水线深度（提前加载30个）

    // ==================== IO线程池 ====================
    private static final ExecutorService IO_EXECUTOR = new ThreadPoolExecutor(
            IO_THREADS,
            IO_THREADS,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r, "NBT-JAR-Loader");
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY); // 最高优先级
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // ==================== 对象池 ====================
    private static final ThreadLocal<Queue<CompoundTag>> tagPool =
            ThreadLocal.withInitial(ConcurrentLinkedQueue::new);
    private static final ThreadLocal<Queue<ListTag>> listPool =
            ThreadLocal.withInitial(ConcurrentLinkedQueue::new);

    // ==================== 基础读取方法 ====================

    public static CompoundTag readNBTFast(InputStream inputStream) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(inputStream), BUFFER_SIZE))) {
            return (CompoundTag) readNBTTag(dis);
        } catch (IOException e) {
            ModLogger.getLogger().warn("快速读取失败，使用标准方法");
            return NbtIo.readCompressed(inputStream);
        }
    }

    // ==================== JAR资源流水线加载器 ====================

    /**
     * 创建JAR资源流水线加载器
     */
    public static JarPipelineLoader createJarPipelineLoader(
            String basePath,
            String baseName,
            int xChunks,
            int yChunks,
            int zChunks,
            Class<?> resourceClass
    ) {
        return new JarPipelineLoader(basePath, baseName, xChunks, yChunks, zChunks, resourceClass);
    }

    /**
     * JAR资源流水线加载器
     * 工作原理：
     * 1. 预先生成所有资源路径
     * 2. 后台线程池并发加载接下来的N个NBT
     * 3. 主线程从队列获取已加载的NBT
     * 4. 用完即释放，保持低内存占用
     */
    public static class JarPipelineLoader implements AutoCloseable {
        private final List<String> allResourcePaths;
        private final Class<?> resourceClass;
        private final BlockingQueue<LoadedNBT> readyQueue;

        private final AtomicInteger nextLoadIndex = new AtomicInteger(0);
        private final AtomicInteger loadedCount = new AtomicInteger(0);
        private final AtomicInteger failedCount = new AtomicInteger(0);

        private volatile boolean running = true;
        private final List<CompletableFuture<Void>> loaderTasks = new CopyOnWriteArrayList<>();

        public JarPipelineLoader(
                String basePath,
                String baseName,
                int xChunks,
                int yChunks,
                int zChunks,
                Class<?> resourceClass
        ) {
            this.resourceClass = resourceClass;
            this.readyQueue = new LinkedBlockingQueue<>(PIPELINE_DEPTH);

            // 预先生成所有资源路径（按Y-X-Z顺序）
            this.allResourcePaths = generateResourcePaths(basePath, baseName, xChunks, yChunks, zChunks);
            // 启动多个加载任务
            startLoaderTasks();
        }

        private List<String> generateResourcePaths(
                String basePath,
                String baseName,
                int xChunks,
                int yChunks,
                int zChunks
        ) {
            List<String> paths = new ArrayList<>();

            // 按Y-X-Z顺序生成路径（从下往上，减少悬空方块更新）
            for (int y = 0; y < yChunks; y++) {
                for (int x = 0; x < xChunks; x++) {
                    for (int z = 0; z < zChunks; z++) {
                        String fileName = baseName + String.format("_part%02d%02d%02d.nbt", x, y, z);
                        String resourcePath = basePath + "/" + fileName;
                        paths.add(resourcePath);
                    }
                }
            }

            return paths;
        }

        private void startLoaderTasks() {
            // 启动多个并发加载任务
            for (int i = 0; i < IO_THREADS; i++) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(this::loaderLoop, IO_EXECUTOR);
                loaderTasks.add(task);
            }
        }

        private void loaderLoop() {
            while (running) {
                try {
                    // 获取下一个要加载的索引
                    int index = nextLoadIndex.getAndIncrement();

                    if (index >= allResourcePaths.size()) {
                        // 没有更多文件了
                        break;
                    }

                    String resourcePath = allResourcePaths.get(index);

                    // 加载NBT
                    LoadedNBT loaded = loadFromJarResource(resourcePath, index);

                    // 放入队列（会阻塞直到队列有空间）
                    if (running) {
                        readyQueue.put(loaded);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    ModLogger.getLogger().error("加载器循环异常", e);
                }
            }
        }

        private LoadedNBT loadFromJarResource(String resourcePath, int index) {
            try {
                InputStream inputStream = resourceClass.getResourceAsStream(resourcePath);

                if (inputStream == null) {
                    failedCount.incrementAndGet();
                    return new LoadedNBT(
                            resourcePath,
                            index,
                            null,
                            new FileNotFoundException("资源不存在: " + resourcePath)
                    );
                }

                // 读取并解析NBT
                CompoundTag tag;
                try (inputStream) {
                    tag = readNBTFast(inputStream);
                }

                loadedCount.incrementAndGet();
                return new LoadedNBT(resourcePath, index, tag, null);

            } catch (Exception e) {
                failedCount.incrementAndGet();
                ModLogger.getLogger().error("加载JAR资源失败: {}", resourcePath, e);
                return new LoadedNBT(resourcePath, index, null, e);
            }
        }

        /**
         * 获取下一个已加载的NBT（阻塞）
         * @param timeoutMs 超时时间
         * @return 已加载的NBT，超时返回null
         */
        public LoadedNBT getNext(long timeoutMs) {
            try {
                return readyQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        /**
         * 获取下一个已加载的NBT（不阻塞）
         */
        public LoadedNBT getNextNonBlocking() {
            return readyQueue.poll();
        }

        /**
         * 批量获取（最多count个）
         */
        public List<LoadedNBT> getNextBatch(int count, long timeoutMs) {
            List<LoadedNBT> batch = new ArrayList<>();
            long deadline = System.currentTimeMillis() + timeoutMs;

            for (int i = 0; i < count; i++) {
                long remainingTime = deadline - System.currentTimeMillis();
                if (remainingTime <= 0) break;

                LoadedNBT nbt = getNext(remainingTime);
                if (nbt == null) break;

                batch.add(nbt);
            }

            return batch;
        }

        /**
         * 检查是否还有更多
         */
        public boolean hasMore() {
            return loadedCount.get() + failedCount.get() < allResourcePaths.size() || !readyQueue.isEmpty();
        }

        /**
         * 获取总数
         */
        public int getTotalCount() {
            return allResourcePaths.size();
        }

        /**
         * 获取统计信息
         */
        public LoaderStats getStats() {
            return new LoaderStats(
                    loadedCount.get(),
                    failedCount.get(),
                    allResourcePaths.size(),
                    readyQueue.size()
            );
        }

        @Override
        public void close() {
            running = false;

            // 取消所有加载任务
            for (CompletableFuture<Void> task : loaderTasks) {
                task.cancel(true);
            }

            readyQueue.clear();
        }
    }

    /**
     * 已加载的NBT数据
     */
    public static class LoadedNBT {
        public final String resourcePath;
        public final int index; // 在总列表中的索引
        public final CompoundTag tag;
        public final Exception error;

        // 坐标信息（从路径解析）
        public final ChunkCoord coord;

        public LoadedNBT(String resourcePath, int index, CompoundTag tag, Exception error) {
            this.resourcePath = resourcePath;
            this.index = index;
            this.tag = tag;
            this.error = error;
            this.coord = parseCoordFromPath(resourcePath);
        }

        private ChunkCoord parseCoordFromPath(String path) {
            try {
                // 从 ".../baseName_part010203.nbt" 提取坐标
                int partIndex = path.lastIndexOf("_part");
                if (partIndex == -1) return null;

                String coordStr = path.substring(partIndex + 5, path.length() - 4);
                int x = Integer.parseInt(coordStr.substring(0, 2));
                int y = Integer.parseInt(coordStr.substring(2, 4));
                int z = Integer.parseInt(coordStr.substring(4, 6));

                return new ChunkCoord(x, y, z);
            } catch (Exception e) {
                return null;
            }
        }

        public boolean isSuccess() {
            return tag != null && error == null;
        }

        public String getFileName() {
            return resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        }
    }

    /**
     * 分块坐标
     */
    public static class ChunkCoord {
        public final int x, y, z;

        public ChunkCoord(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return String.format("[%d,%d,%d]", x, y, z);
        }
    }

    /**
     * 加载器统计信息
     */
    public static class LoaderStats {
        public final int loaded;
        public final int failed;
        public final int total;
        public final int queueSize;

        public LoaderStats(int loaded, int failed, int total, int queueSize) {
            this.loaded = loaded;
            this.failed = failed;
            this.total = total;
            this.queueSize = queueSize;
        }

        public int getProcessed() {
            return loaded + failed;
        }

        public int getProgress() {
            return total == 0 ? 100 : (getProcessed() * 100 / total);
        }

        @Override
        public String toString() {
            return String.format(
                    "进度: %d%% (%d/%d), 成功: %d, 失败: %d, 队列: %d",
                    getProgress(), getProcessed(), total, loaded, failed, queueSize
            );
        }
    }

    // ==================== NBT解析（核心）====================

    private static Tag readNBTTag(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        if (type == 0) return EndTag.INSTANCE;
        dis.readUTF();
        return readNBTPayload(dis, type);
    }

    private static Tag readNBTPayload(DataInputStream dis, byte type) throws IOException {
        return switch (type) {
            case 1 -> ByteTag.valueOf(dis.readByte());
            case 2 -> ShortTag.valueOf(dis.readShort());
            case 3 -> IntTag.valueOf(dis.readInt());
            case 4 -> LongTag.valueOf(dis.readLong());
            case 5 -> FloatTag.valueOf(dis.readFloat());
            case 6 -> DoubleTag.valueOf(dis.readDouble());
            case 7 -> new ByteArrayTag(readByteArrayFast(dis));
            case 8 -> StringTag.valueOf(dis.readUTF());
            case 9 -> readListTag(dis);
            case 10 -> readCompoundTag(dis);
            case 11 -> new IntArrayTag(readIntArray(dis));
            case 12 -> new LongArrayTag(readLongArray(dis));
            default -> throw new IOException("未知NBT标签类型: " + type);
        };
    }

    private static CompoundTag readCompoundTag(DataInputStream dis) throws IOException {
        CompoundTag compound = tagPool.get().poll();
        if (compound == null) {
            compound = new CompoundTag();
        } else {
            compound.tags.clear();
        }

        while (true) {
            byte type = dis.readByte();
            if (type == 0) break;
            String name = dis.readUTF();
            Tag value = readNBTPayload(dis, type);
            compound.put(name, value);
        }
        return compound;
    }

    private static ListTag readListTag(DataInputStream dis) throws IOException {
        ListTag list = listPool.get().poll();
        if (list == null) {
            list = new ListTag();
        } else {
            list.clear();
        }

        byte elementType = dis.readByte();
        int length = dis.readInt();

        for (int i = 0; i < length; i++) {
            list.add(readNBTPayload(dis, elementType));
        }
        return list;
    }

    private static byte[] readByteArrayFast(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        byte[] array = new byte[length];
        dis.readFully(array);
        return array;
    }

    private static int[] readIntArray(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = dis.readInt();
        }
        return array;
    }

    private static long[] readLongArray(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        long[] array = new long[length];
        for (int i = 0; i < length; i++) {
            array[i] = dis.readLong();
        }
        return array;
    }

    /**
     * 关闭所有资源
     */
    public static void shutdown() {
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}