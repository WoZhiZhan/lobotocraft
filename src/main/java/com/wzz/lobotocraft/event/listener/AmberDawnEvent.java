package com.wzz.lobotocraft.event.listener;

import com.lobotocraft.facility.OrdealRewardService;
import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.ordeal.EntityAmberDawn;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.RoomChunkTickets;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.AMBER_DAWN_COLOR;
import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.sendOrdealTitle;

/**
 * 琥珀色的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class AmberDawnEvent {
    private static final int SEARCH_LIMIT = 30_000_000;
    private static final int RECONCILE_DELAY_TICKS = 100;
    private static final int ENTITY_LOAD_GRACE_TICKS = 40;
    private static final int RECONCILE_RETRY_TICKS = 20;
    private static final Map<MinecraftServer, Integer> RECONCILE_DELAYS = new WeakHashMap<>();
    private static final Set<MinecraftServer> RECONCILIATION_PRIMED =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static boolean triggerAmberDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.isAmberDawnActive()) return false;

        List<OrdealSpawnPointService.DepartmentCorridors> departments =
                OrdealSpawnPointService.openCorridorsByDepartment(level);
        if (departments.isEmpty()) {
            return false;
        }

        List<EntityAmberDawn> spawned = new ArrayList<>();
        for (OrdealSpawnPointService.DepartmentCorridors department : departments) {
            List<EntityAmberDawn> batch = spawnAmberDawns(level, department.corridors(),
                    OrdealSpawnPolicy.AMBER_DAWNS_PER_DEPARTMENT);
            if (batch.size() != OrdealSpawnPolicy.AMBER_DAWNS_PER_DEPARTMENT) {
                batch.forEach(Entity::discard);
                spawned.forEach(Entity::discard);
                return false;
            }
            spawned.addAll(batch);
        }
        if (spawned.isEmpty()) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setRandomNextDawnType(level.getRandom());
        data.startAmberDawn(spawned.size());
        for (EntityAmberDawn amberDawn : spawned) {
            trackAmberDawn(data, amberDawn);
        }

        MinecraftServer server = level.getServer();
        showAmberDawnTitle(server,
                "琥珀色的黎明",
                "新鲜的食物",
                "食物-新鲜。替代品-很好。");
        playGlobalSound(server, ModSounds.AMBER_DAWN_START.get());
        EscapeTracker.getInstance().onOrdealStart(level, OrdealTier.DAWN);
        return true;
    }

    public static void onAmberDawnKilled(ServerLevel level, UUID entityId) {
        OrdealData data = OrdealData.get(level);
        if (!data.isAmberDawnActive()) return;
        if (data.removeTrackedAmberDawn(entityId) > 0) return;

        completeAmberDawn(level.getServer(), data);
    }

    public static void updateAmberDawnTracking(EntityAmberDawn amberDawn) {
        if (!(amberDawn.level() instanceof ServerLevel level) || !amberDawn.isActiveOrdealSpawn()) return;
        OrdealData data = OrdealData.get(level);
        if (!data.isAmberDawnActive()) return;
        trackAmberDawn(data, amberDawn);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Integer delay = RECONCILE_DELAYS.get(server);
        if (delay == null) {
            RECONCILE_DELAYS.put(server, RECONCILE_DELAY_TICKS);
            return;
        }
        if (delay < 0) return;
        if (delay > 0) {
            RECONCILE_DELAYS.put(server, delay - 1);
            return;
        }
        OrdealData data = OrdealData.get(server.overworld());
        if (!data.isAmberDawnActive()) {
            RECONCILE_DELAYS.put(server, -1);
            return;
        }
        if (data.isAmberDawnActive() && !data.getTrackedAmberDawns().isEmpty()
                && RECONCILIATION_PRIMED.add(server)) {
            primeTrackedChunks(server, data.getTrackedAmberDawns());
            RECONCILE_DELAYS.put(server, ENTITY_LOAD_GRACE_TICKS);
            return;
        }
        boolean reconciled = reconcileAfterRestart(server);
        RECONCILE_DELAYS.put(server, reconciled ? -1 : RECONCILE_RETRY_TICKS);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RECONCILE_DELAYS.remove(event.getServer());
        RECONCILIATION_PRIMED.remove(event.getServer());
    }

    private static void primeTrackedChunks(MinecraftServer server,
                                           List<OrdealData.AmberDawnRecord> tracked) {
        for (OrdealData.AmberDawnRecord record : tracked) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(record.dimension());
            if (dimensionId == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level != null) {
                RoomChunkTickets.keepAlive(level, chunkProbe(level, record));
            }
        }
    }

    private static boolean reconcileAfterRestart(MinecraftServer server) {
        OrdealData data = OrdealData.get(server.overworld());
        if (!data.isAmberDawnActive()) return true;

        List<OrdealData.AmberDawnRecord> tracked = data.getTrackedAmberDawns();
        if (tracked.isEmpty()) {
            List<OrdealData.AmberDawnRecord> migrated = findLoadedLegacyAmberDawns(server);
            if (migrated.isEmpty()) {
                data.cancelAmberDawn();
                ModLogger.LOGGER.warn("已清理缺少实体索引的旧版琥珀色黎明状态，未计入考验完成");
                return true;
            }
            data.replaceTrackedAmberDawns(migrated);
            tracked = migrated;
            ModLogger.LOGGER.info("已从 {} 个已加载实体迁移旧版琥珀色黎明状态", migrated.size());
        }

        List<OrdealData.AmberDawnRecord> alive = new ArrayList<>();
        boolean waitingForEntityLoad = false;
        for (OrdealData.AmberDawnRecord record : tracked) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(record.dimension());
            if (dimensionId == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel level = server.getLevel(dimension);
            if (level == null) continue;

            BlockPos probe = chunkProbe(level, record);
            RoomChunkTickets.keepAlive(level, probe);
            if (!level.hasChunkAt(probe) || !level.isPositionEntityTicking(probe)) {
                alive.add(record);
                waitingForEntityLoad = true;
                continue;
            }
            Entity entity = level.getEntity(record.uuid());
            if (entity instanceof EntityAmberDawn amberDawn
                    && amberDawn.isAlive() && amberDawn.isActiveOrdealSpawn()) {
                alive.add(toRecord(amberDawn));
            }
        }

        data.replaceTrackedAmberDawns(alive);
        if (waitingForEntityLoad) {
            return false;
        }
        if (alive.isEmpty()) {
            completeAmberDawn(server, data);
        } else if (alive.size() != tracked.size()) {
            ModLogger.LOGGER.info("琥珀色黎明重启核对：保留 {} 个实体，移除 {} 个失效索引",
                    alive.size(), tracked.size() - alive.size());
        }
        return true;
    }

    private static BlockPos chunkProbe(ServerLevel level, OrdealData.AmberDawnRecord record) {
        return new BlockPos(record.chunkX() * 16 + 8, level.getMinBuildHeight() + 1,
                record.chunkZ() * 16 + 8);
    }

    private static List<OrdealData.AmberDawnRecord> findLoadedLegacyAmberDawns(MinecraftServer server) {
        List<OrdealData.AmberDawnRecord> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityAmberDawn amberDawn
                        && amberDawn.isAlive() && amberDawn.isActiveOrdealSpawn()) {
                    result.add(toRecord(amberDawn));
                }
            }
        }
        return result;
    }

    private static void trackAmberDawn(OrdealData data, EntityAmberDawn amberDawn) {
        OrdealData.AmberDawnRecord record = toRecord(amberDawn);
        data.trackAmberDawn(record.uuid(), record.dimension(), record.chunkX(), record.chunkZ());
    }

    private static OrdealData.AmberDawnRecord toRecord(EntityAmberDawn amberDawn) {
        return new OrdealData.AmberDawnRecord(
                amberDawn.getUUID(),
                amberDawn.level().dimension().location().toString(),
                amberDawn.chunkPosition().x,
                amberDawn.chunkPosition().z);
    }

    private static void completeAmberDawn(MinecraftServer server, OrdealData data) {
        data.finishAmberDawn();
        OrdealRewardService.complete(server, OrdealTier.DAWN);
        showAmberDawnTitle(server,
                "琥珀色的黎明",
                "新鲜的食物",
                "我们-生存。我们-吃。我们-腐烂。成为-食物...");
        playGlobalSound(server, ModSounds.AMBER_DAWN_END.get());
    }

    public static BlockPos chooseOtherEscapeSpawnPosition(ServerLevel level, EntityAmberDawn amberDawn,
                                                          @org.jetbrains.annotations.Nullable BlockPos currentHome) {
        List<BlockPos> escapeBlocks = collectEscapeBlocks(level);
        if (escapeBlocks.isEmpty()) {
            return null;
        }
        if (currentHome != null && escapeBlocks.size() > 1) {
            escapeBlocks.removeIf(pos -> pos.distSqr(currentHome) <= 4.0D);
        }
        Collections.shuffle(escapeBlocks, new java.util.Random(level.getRandom().nextLong()));
        for (BlockPos escapeBlock : escapeBlocks) {
            BlockPos pos = findAmberSpawnPosition(level, amberDawn, escapeBlock, 1);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    public static BlockPos findAmberSpawnPosition(ServerLevel level, EntityAmberDawn amberDawn,
                                                  BlockPos center, int horizontalRange) {
        return findAmberSpawnPosition(level, amberDawn, center, horizontalRange, Collections.emptySet());
    }

    private static BlockPos findAmberSpawnPosition(ServerLevel level, EntityAmberDawn amberDawn,
                                                   BlockPos center, int horizontalRange,
                                                   Set<BlockPos> reservedPositions) {
        BlockPos direct = EntityUtil.findSafeGroundPositionInCompany(level, center, horizontalRange);
        if (canPlaceAmberDawn(level, amberDawn, direct, reservedPositions)) {
            return direct;
        }

        int radius = Math.max(1, horizontalRange);
        for (int attempt = 0; attempt < 24; attempt++) {
            int offsetX = level.random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(
                    level, center.offset(offsetX, 0, offsetZ), 0);
            if (canPlaceAmberDawn(level, amberDawn, candidate, reservedPositions)) {
                return candidate;
            }
        }
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(
                        level, center.offset(offsetX, 0, offsetZ), 0);
                if (canPlaceAmberDawn(level, amberDawn, candidate, reservedPositions)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static List<EntityAmberDawn> spawnAmberDawns(ServerLevel level, List<BlockPos> escapeBlocks, int count) {
        for (BlockPos escapeBlock : escapeBlocks) {
            List<BlockPos> spawnPositions = collectAmberSpawnPositions(level, escapeBlock, count);
            if (spawnPositions.size() == count) {
                List<EntityAmberDawn> spawned = spawnAmberDawnsAt(level, escapeBlock, spawnPositions);
                if (spawned.size() == count) return spawned;
                spawned.forEach(Entity::discard);
            }
        }
        return Collections.emptyList();
    }

    private static List<BlockPos> collectAmberSpawnPositions(ServerLevel level, BlockPos escapeBlock, int count) {
        EntityAmberDawn amberDawn = ModEntities.amber_dawn.get().create(level);
        if (amberDawn == null) {
            return Collections.emptyList();
        }

        Set<BlockPos> reservedPositions = new HashSet<>();
        List<BlockPos> spawnPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findAmberSpawnPosition(level, amberDawn, escapeBlock, 3, reservedPositions);
            if (spawnPos == null) {
                break;
            }
            BlockPos immutableSpawnPos = spawnPos.immutable();
            reservedPositions.add(immutableSpawnPos);
            spawnPositions.add(immutableSpawnPos);
        }
        return spawnPositions;
    }

    private static List<EntityAmberDawn> spawnAmberDawnsAt(ServerLevel level, BlockPos escapeBlock,
                                                            List<BlockPos> spawnPositions) {
        List<EntityAmberDawn> spawned = new ArrayList<>();
        for (BlockPos spawnPos : spawnPositions) {
            EntityAmberDawn amberDawn = spawnAmberDawnAt(level, escapeBlock, spawnPos);
            if (amberDawn != null) {
                spawned.add(amberDawn);
            }
        }
        return spawned;
    }

    private static EntityAmberDawn spawnAmberDawnAt(ServerLevel level, BlockPos escapeBlock, BlockPos spawnPos) {
        EntityAmberDawn amberDawn = ModEntities.amber_dawn.get().create(level);
        if (amberDawn == null) {
            return null;
        }
        amberDawn.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        amberDawn.setHomePos(escapeBlock);
        amberDawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        amberDawn.setOrdealSpawn(true);
        amberDawn.setPersistenceRequired();
        return level.addFreshEntity(amberDawn) ? amberDawn : null;
    }

    private static List<BlockPos> collectEscapeBlocks(ServerLevel level) {
        List<BlockPos> escapeBlocks = new ArrayList<>(
                OrdealSpawnPointService.collectOpenCorridors(level));
        Collections.shuffle(escapeBlocks, new java.util.Random(level.getRandom().nextLong()));
        return escapeBlocks;
    }

    private static boolean canPlaceAmberDawn(ServerLevel level, EntityAmberDawn amberDawn, BlockPos pos,
                                             Set<BlockPos> reservedPositions) {
        if (pos == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                level.dimension().location(), pos)) {
            return false;
        }
        if (reservedPositions.contains(pos)) {
            return false;
        }
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            return false;
        }
        BlockPos below = pos.below();
        if (level.isEmptyBlock(below) || !level.getBlockState(below).isSolid()) {
            return false;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        double halfWidth = amberDawn.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + amberDawn.getBbHeight(), z + halfWidth);
        return level.noCollision(amberDawn, boundingBox);
    }

    private static void showAmberDawnTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, AMBER_DAWN_COLOR);
    }

    private static void playGlobalSound(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }
}
