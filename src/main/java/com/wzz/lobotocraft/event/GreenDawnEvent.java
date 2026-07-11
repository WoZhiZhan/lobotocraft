package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.wzz.lobotocraft.event.CrimsonDawnEvent.*;

/**
 * 绿色的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class GreenDawnEvent {
    private static final int MAX_NATURAL_GREEN_DAWNS_PER_DIMENSION = 10;
    private static final int SEARCH_LIMIT = 30_000_000;

    public static void triggerGreenDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveDawn()) return;

        List<SpawnPoint> fallbackSpawnPoints = collectSpawnPoints(level);
        if (fallbackSpawnPoints.isEmpty()) {
            return;
        }

        List<ServerPlayer> players = getEligiblePlayers(level);
        int available = MAX_NATURAL_GREEN_DAWNS_PER_DIMENSION - countActiveOrdealGreenDawns(level);
        int count = Math.min(available, Math.max(1, players.size()));
        if (count <= 0) {
            return;
        }

        int spawned = 0;
        if (players.isEmpty()) {
            spawned = spawnGreenDawns(level, fallbackSpawnPoints, count);
        } else {
            for (ServerPlayer player : players) {
                if (spawned >= count) {
                    break;
                }
                if (spawnGreenDawnNearPlayer(level, player, fallbackSpawnPoints)) {
                    spawned++;
                }
            }
            spawned += spawnGreenDawns(level, fallbackSpawnPoints, count - spawned);
        }
        if (spawned <= 0) {
            return;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setRandomNextDawnType(level.getRandom());

        MinecraftServer server = level.getServer();
        data.startGreenDawn(spawned);

        showGreenDawnTitle(server,
                "绿色的黎明",
                "疑问",
                "有一天，我们想到一个问题：我们从何而来？我们被给予了生命，却又被不负责任地抛弃。");
        playGlobalSound(server, ModSounds.GREEN_DAWN_START.get());
    }

    public static void onGreenDawnKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isGreenDawnActive()) return;
        if (data.decrementGreenDawnRemaining() > 0) return;

        data.finishGreenDawn();
        MinecraftServer server = level.getServer();
        showGreenDawnTitle(server,
                "绿色的黎明",
                "疑问",
                "生命，充满了痛苦。");
        playGlobalSound(server, ModSounds.GREEN_DAWN_END.get());
    }

    private static List<SpawnPoint> collectSpawnPoints(ServerLevel level) {
        List<SpawnPoint> spawnPoints = new ArrayList<>();
        EntityUtil.findBlockEntities(level).stream()
                .filter(RegenerationReactorBlockEntity.class::isInstance)
                .filter(blockEntity -> EntityUtil.isInCompany(level, blockEntity.getBlockPos()))
                .map(blockEntity -> new SpawnPoint(blockEntity.getBlockPos(), true))
                .forEach(spawnPoints::add);

        EscapeBlockEntity.getEscapeBlocks(level.dimension()).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                .filter(pos -> EntityUtil.isInCompany(level, pos))
                .map(pos -> new SpawnPoint(pos, false))
                .forEach(spawnPoints::add);

        Collections.shuffle(spawnPoints, new java.util.Random(level.getRandom().nextLong()));
        return spawnPoints;
    }

    private static List<ServerPlayer> getEligiblePlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(player -> !player.isAlive() || player.isSpectator());
        return players;
    }

    private static int countActiveOrdealGreenDawns(ServerLevel level) {
        AABB whole = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        return level.getEntitiesOfClass(EntityGreenDawn.class, whole,
                greenDawn -> greenDawn.isAlive() && greenDawn.isOrdealSpawn()).size();
    }

    private static List<SpawnPoint> collectPlayerSpawnPoints(ServerLevel level, BlockPos playerPos) {
        List<SpawnPoint> spawnPoints = new ArrayList<>();
        BlockPos nearestReactor = findNearestPos(EntityUtil.findBlockEntities(level).stream()
                .filter(RegenerationReactorBlockEntity.class::isInstance)
                .filter(blockEntity -> EntityUtil.isInCompany(level, blockEntity.getBlockPos()))
                .map(blockEntity -> blockEntity.getBlockPos())
                .toList(), playerPos);
        if (nearestReactor != null) {
            spawnPoints.add(new SpawnPoint(nearestReactor, true));
        }

        BlockPos nearestEscapeBlock = findNearestPos(EscapeBlockEntity.getEscapeBlocks(level.dimension()).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                .filter(pos -> EntityUtil.isInCompany(level, pos))
                .toList(), playerPos);
        if (nearestEscapeBlock != null
                && level.getBlockEntity(nearestEscapeBlock) instanceof EscapeBlockEntity
                && EntityUtil.isInCompany(level, nearestEscapeBlock)) {
            spawnPoints.add(new SpawnPoint(nearestEscapeBlock, false));
        }

        Collections.shuffle(spawnPoints, new java.util.Random(level.getRandom().nextLong()));
        return spawnPoints;
    }

    private static boolean spawnGreenDawn(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        return spawnGreenDawnAt(level, chooseSpawnPosition(level, spawnPoints, index));
    }

    private static int spawnGreenDawns(ServerLevel level, List<SpawnPoint> spawnPoints, int count) {
        int spawned = 0;
        int attempts = Math.max(count * Math.max(1, spawnPoints.size()), count);
        for (int i = 0; spawned < count && i < attempts; i++) {
            if (spawnGreenDawn(level, spawnPoints, i)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawnGreenDawnNearPlayer(ServerLevel level, ServerPlayer player,
                                                   List<SpawnPoint> fallbackSpawnPoints) {
        List<SpawnPoint> playerSpawnPoints = collectPlayerSpawnPoints(level, player.blockPosition());
        List<SpawnPoint> spawnPoints = playerSpawnPoints.isEmpty() ? fallbackSpawnPoints : playerSpawnPoints;
        return spawnGreenDawnAt(level, chooseRandomSpawnPosition(level, spawnPoints));
    }

    private static boolean spawnGreenDawnAt(ServerLevel level, BlockPos spawnPos) {
        if (spawnPos == null || !EntityUtil.isInCompany(level, spawnPos)) {
            return false;
        }

        EntityGreenDawn greenDawn = ModEntities.green_dawn.get().create(level);
        if (greenDawn == null || !canPlaceGreenDawn(level, greenDawn, spawnPos)) {
            return false;
        }

        greenDawn.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        greenDawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        greenDawn.setOrdealSpawn(true);
        greenDawn.setPersistenceRequired();
        return level.addFreshEntity(greenDawn);
    }

    private static BlockPos chooseSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        if (!spawnPoints.isEmpty()) {
            SpawnPoint point = spawnPoints.get(index % spawnPoints.size());
            return resolveSpawnPosition(level, point);
        }

        return null;
    }

    private static BlockPos chooseRandomSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints) {
        if (!spawnPoints.isEmpty()) {
            SpawnPoint point = spawnPoints.get(level.getRandom().nextInt(spawnPoints.size()));
            return resolveSpawnPosition(level, point);
        }
        return null;
    }

    private static BlockPos resolveSpawnPosition(ServerLevel level, SpawnPoint point) {
        return point.reactor
                ? EntityUtil.findReactorSpawnPositionInCompany(level, point.pos, 4)
                : EntityUtil.findSafeGroundPositionInCompany(level, point.pos, 4);
    }

    private static boolean canPlaceGreenDawn(ServerLevel level, EntityGreenDawn greenDawn, BlockPos pos) {
        if (pos == null || !EntityUtil.isInCompany(level, pos)) {
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
        double halfWidth = greenDawn.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + greenDawn.getBbHeight(), z + halfWidth);
        return level.noCollision(greenDawn, boundingBox);
    }

    private static BlockPos findNearestPos(List<BlockPos> positions, BlockPos origin) {
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = pos;
            }
        }
        return nearest;
    }

    private static void showGreenDawnTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, GREEN_DAWN_COLOR);
    }

    private static void playGlobalSound(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }

    private record SpawnPoint(BlockPos pos, boolean reactor) {
    }
}
