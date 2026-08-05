package com.wzz.lobotocraft.employee.navigation;

import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.world.structure.StructureLoader;
import com.wzz.lobotocraft.world.structure.StructureUpdater;
import com.wzz.lobotocraft.world.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared sparse navigation geometry for the immutable company structure.
 * The base layer is decoded once from the structure resources; live player
 * block changes are kept in a small server-thread overlay.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class FacilityNavigationCache {
    public static final BlockPos COMPANY_MIN = new BlockPos(-25, 1, -6);
    public static final BlockPos COMPANY_MAX = new BlockPos(404, 340, 78);

    private static final BlockPos STRUCTURE_ORIGIN = new BlockPos(5, 2, 0);
    private static final BlockPos REMOVED_INITIAL_REACTOR = new BlockPos(196, 48, 34);
    private static final int MAX_DIRTY_POSITIONS_PER_TICK = 512;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOADING_MESSAGE =
            "message.lobotocraft.navigation_cache.loading";
    private static final String READY_MESSAGE =
            "message.lobotocraft.navigation_cache.ready";
    private static final String FAILED_MESSAGE =
            "message.lobotocraft.navigation_cache.failed";
    private static final Map<ServerLevel, Cache> CACHES = new IdentityHashMap<>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lobotocraft-facility-navigation-cache");
        thread.setDaemon(true);
        return thread;
    });

    private FacilityNavigationCache() {
    }

    public static boolean manages(ServerLevel level) {
        return level.dimension() == ModDimensions.LOBOTO_KEY;
    }

    /** Starts the immutable resource decode eagerly; also used by the GameTest harness. */
    public static void prepare(ServerLevel level) {
        cache(level);
    }

    /** Returns -1 while loading, otherwise the sparse non-air cell count. */
    public static int staticCellCount(ServerLevel level) {
        Cache cache = cache(level);
        cache.publishFinishedBase();
        return cache.baseCells == null ? -1 : cache.baseCells.size();
    }

    /** Returns null only while the one-time static resource decode is still running. */
    @Nullable
    public static NavigationSnapshot snapshot(ServerLevel level, BlockPos minimum,
                                                BlockPos maximum) {
        if (StructureUpdater.isRunning()) return null;
        Cache cache = cache(level);
        cache.publishFinishedBase();
        Map<Long, CellData> base = cache.baseCells;
        if (base == null) return null;

        Map<Long, CellData> liveOverrides = cache.liveOverrides.isEmpty()
                ? Map.of() : Map.copyOf(cache.liveOverrides);
        return NavigationSnapshot.shared(minimum, maximum, position -> {
            if (!contains(position)) return Optional.empty();
            long key = position.asLong();
            CellData cell = liveOverrides.get(key);
            if (cell == null) cell = base.get(key);
            return Optional.of(cell == null ? NavigationSnapshot.airCell() : cell);
        });
    }

    /** Refreshes the few live blocks implicated by a collision mismatch. */
    public static void refreshVolume(ServerLevel level, AABB bounds) {
        if (!manages(level)) return;
        int minX = Math.max(COMPANY_MIN.getX(), (int) Math.floor(bounds.minX));
        int minY = Math.max(COMPANY_MIN.getY(), (int) Math.floor(bounds.minY));
        int minZ = Math.max(COMPANY_MIN.getZ(), (int) Math.floor(bounds.minZ));
        int maxX = Math.min(COMPANY_MAX.getX(), (int) Math.floor(bounds.maxX));
        int maxY = Math.min(COMPANY_MAX.getY(), (int) Math.floor(bounds.maxY));
        int maxZ = Math.min(COMPANY_MAX.getZ(), (int) Math.floor(bounds.maxZ));
        Cache cache = cache(level);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    cache.refreshNow(cursor);
                }
            }
        }
    }

    public static void resetDynamicLayer(ServerLevel level) {
        Cache cache = CACHES.get(level);
        if (cache != null) {
            cache.liveOverrides.clear();
            cache.dirtyPositions.clear();
        }
    }

    private static boolean contains(BlockPos position) {
        return position.getX() >= COMPANY_MIN.getX() && position.getX() <= COMPANY_MAX.getX()
                && position.getY() >= COMPANY_MIN.getY() && position.getY() <= COMPANY_MAX.getY()
                && position.getZ() >= COMPANY_MIN.getZ() && position.getZ() <= COMPANY_MAX.getZ();
    }

    private static Cache cache(ServerLevel level) {
        return CACHES.computeIfAbsent(level, Cache::new);
    }

    private static void markDirty(ServerLevel level, BlockPos position) {
        if (!manages(level) || !contains(position)) return;
        cache(level).dirtyPositions.add(position.immutable());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && manages(level)) cache(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CACHES.remove(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        CACHES.keySet().removeIf(level -> level.getServer() == server);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) notifyIfLoading(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) notifyIfLoading(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().getLevel(ModDimensions.LOBOTO_KEY);
        if (level == null) return;
        Cache cache = cache(level);
        cache.publishFinishedBase();
        cache.refreshDirty();
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) markDirty(level, event.getPos());
    }

    @SubscribeEvent
    public static void onBlocksPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getReplacedBlockSnapshots().forEach(snapshot -> markDirty(level, snapshot.getPos()));
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) markDirty(level, event.getPos());
    }

    @SubscribeEvent
    public static void onNeighborChanged(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        markDirty(level, event.getPos());
        event.getNotifiedSides().forEach(direction -> markDirty(level,
                event.getPos().relative(direction)));
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().forEach(position -> markDirty(level, position));
    }

    private static void notifyIfLoading(ServerPlayer player) {
        if (player instanceof EmployeeServerPlayer || !manages(player.serverLevel())) return;
        Cache cache = cache(player.serverLevel());
        cache.publishFinishedBase();
        if (cache.baseCells == null && !cache.failureReported) {
            player.sendSystemMessage(Component.translatable(LOADING_MESSAGE));
        }
    }

    private static void notifyPlayers(ServerLevel level, String messageKey) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!(player instanceof EmployeeServerPlayer) && player.serverLevel() == level) {
                player.sendSystemMessage(Component.translatable(messageKey));
            }
        }
    }

    private static final class Cache {
        private final ServerLevel level;
        private final CompletableFuture<Map<Long, CellData>> baseFuture;
        private final Map<Long, CellData> liveOverrides = new HashMap<>();
        private final Set<BlockPos> dirtyPositions = new HashSet<>();
        private Map<Long, CellData> baseCells;
        private boolean failureReported;

        private Cache(ServerLevel level) {
            this.level = level;
            HolderLookup<Block> blocks = level.holderLookup(Registries.BLOCK);
            this.baseFuture = CompletableFuture.supplyAsync(() -> loadBaseCells(blocks), LOADER);
            notifyPlayers(level, LOADING_MESSAGE);
        }

        private void publishFinishedBase() {
            if (baseCells != null || failureReported || !baseFuture.isDone()) return;
            try {
                baseCells = baseFuture.join();
                LOGGER.info("Facility navigation cache ready: {} non-air cells", baseCells.size());
                notifyPlayers(level, READY_MESSAGE);
            } catch (RuntimeException exception) {
                if (!failureReported) {
                    failureReported = true;
                    LOGGER.error("Facility navigation cache failed to load", exception);
                    notifyPlayers(level, FAILED_MESSAGE);
                }
            }
        }

        private void refreshDirty() {
            if (baseCells == null || dirtyPositions.isEmpty()) return;
            List<BlockPos> batch = new ArrayList<>(Math.min(
                    MAX_DIRTY_POSITIONS_PER_TICK, dirtyPositions.size()));
            var iterator = dirtyPositions.iterator();
            while (iterator.hasNext() && batch.size() < MAX_DIRTY_POSITIONS_PER_TICK) {
                batch.add(iterator.next());
                iterator.remove();
            }
            batch.forEach(this::refreshNow);
        }

        private void refreshNow(BlockPos position) {
            if (!contains(position) || !level.hasChunkAt(position)) return;
            liveOverrides.put(position.asLong(),
                    NavigationSnapshot.describeLiveCell(level, position));
        }
    }

    private static Map<Long, CellData> loadBaseCells(HolderLookup<Block> blocks) {
        String baseName = Structures.LOBOTO.name();
        CompoundTag metadata = StructureLoader.loadMetadata(baseName + "_main");
        if (metadata == null || metadata.isEmpty()) {
            throw new IllegalStateException("Missing facility structure metadata: " + baseName);
        }
        int xParts = metadata.getInt("totalXChunks");
        int yParts = metadata.getInt("totalYChunks");
        int zParts = metadata.getInt("totalZChunks");
        int partSize = metadata.getInt("chunkSize");
        if (xParts <= 0 || yParts <= 0 || zParts <= 0 || partSize <= 0) {
            throw new IllegalStateException("Invalid facility multipart metadata");
        }

        Map<Long, BlockState> states = new HashMap<>();
        for (int x = 0; x < xParts; x++) {
            for (int y = 0; y < yParts; y++) {
                for (int z = 0; z < zParts; z++) {
                    String partName = String.format(Locale.ROOT, "%s_part%02d%02d%02d",
                            baseName, x, y, z);
                    CompoundTag tag = StructureLoader.loadMetadata(partName);
                    if (tag == null || tag.isEmpty()) continue;
                    StructureTemplate template = new StructureTemplate();
                    template.load(blocks, tag);
                    if (template.palettes.isEmpty()) continue;
                    BlockPos partOrigin = STRUCTURE_ORIGIN.offset(
                            x * partSize, y * partSize, z * partSize);
                    for (StructureTemplate.StructureBlockInfo info
                            : template.palettes.get(0).blocks()) {
                        BlockPos worldPosition = partOrigin.offset(info.pos());
                        if (!contains(worldPosition)) continue;
                        if (info.state().isAir()) states.remove(worldPosition.asLong());
                        else states.put(worldPosition.asLong(), info.state());
                    }
                    template.palettes.clear();
                    template.entityInfoList.clear();
                }
            }
        }

        // StructureUpdater removes this legacy reactor immediately after first generation.
        states.remove(REMOVED_INITIAL_REACTOR.asLong());
        Map<Long, BlockState> immutableStates = Collections.unmodifiableMap(states);
        StaticFacilityBlockGetter getter = new StaticFacilityBlockGetter(immutableStates);
        Map<Long, CellData> cells = new HashMap<>(Math.max(16, states.size() * 4 / 3));
        states.forEach((position, state) -> {
            BlockPos blockPos = BlockPos.of(position);
            cells.put(position, NavigationSnapshot.describeCell(getter, blockPos, state));
        });
        return Collections.unmodifiableMap(cells);
    }

    private static final class StaticFacilityBlockGetter implements BlockGetter {
        private final Map<Long, BlockState> states;

        private StaticFacilityBlockGetter(Map<Long, BlockState> states) {
            this.states = states;
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return states.getOrDefault(position.asLong(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public int getHeight() {
            return COMPANY_MAX.getY() - COMPANY_MIN.getY() + 1;
        }

        @Override
        public int getMinBuildHeight() {
            return COMPANY_MIN.getY();
        }
    }
}
