package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityVioletDawn;
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
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.*;

/**
 * 紫罗兰的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class VioletDawnEvent {
    private static final double REACTOR_ESCAPE_BLOCK_RANGE_SQR = 48.0D * 48.0D;

    public static void triggerVioletDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveDawn()) return;

        MinecraftServer server = level.getServer();
        int count = Math.max(1, server.getPlayerList().getPlayers().size());
        List<ServerPlayer> players = getEligiblePlayers(level);
        List<BlockPos> reactors = collectReactorPositions(level);
        List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
        List<SpawnPoint> fallbackSpawnPoints = collectSpawnPoints(reactors, escapeBlocks, level);

        int spawned = 0;
        if (!players.isEmpty()) {
            Collections.shuffle(players, new java.util.Random(level.getRandom().nextLong()));
            for (int i = 0; i < count; i++) {
                ServerPlayer player = players.get(i % players.size());
                BlockPos spawnPos = chooseSpawnPositionNearPlayer(level, player, reactors, escapeBlocks, fallbackSpawnPoints, i);
                if (spawnVioletDawnAt(level, spawnPos)) {
                    spawned++;
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                if (spawnVioletDawnAt(level, chooseFallbackSpawnPosition(level, fallbackSpawnPoints, i))) {
                    spawned++;
                }
            }
        }

        if (spawned <= 0) {
            return;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setRandomNextDawnType(level.getRandom());
        data.startVioletDawn(spawned);

        showVioletDawnTitle(server,
                "紫罗兰的黎明",
                "理解的果实",
                "有朝一日，我们必将理解那些不能理解的东西。");
        playGlobalSound(server, ModSounds.VIOLET_DAWN_START.get());
    }

    public static void onVioletDawnRemoved(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isVioletDawnActive()) return;
        if (data.decrementVioletDawnRemaining() > 0) return;

        data.finishVioletDawn();
        MinecraftServer server = level.getServer();
        showVioletDawnTitle(server,
                "紫罗兰的黎明",
                "理解的果实",
                "为了理解，我们只能这么做。");
        playGlobalSound(server, ModSounds.VIOLET_DAWN_END.get());
    }

    private static List<ServerPlayer> getEligiblePlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(player -> !player.isAlive() || player.isSpectator());
        return players;
    }

    private static List<BlockPos> collectReactorPositions(ServerLevel level) {
        return EntityUtil.findBlockEntities(level).stream()
                .filter(RegenerationReactorBlockEntity.class::isInstance)
                .map(blockEntity -> blockEntity.getBlockPos())
                .toList();
    }

    private static List<SpawnPoint> collectSpawnPoints(List<BlockPos> reactors, List<BlockPos> escapeBlocks,
                                                       ServerLevel level) {
        List<SpawnPoint> spawnPoints = new ArrayList<>();
        reactors.stream()
                .map(pos -> new SpawnPoint(pos, true))
                .forEach(spawnPoints::add);
        escapeBlocks.stream()
                .map(pos -> new SpawnPoint(pos, false))
                .forEach(spawnPoints::add);
        Collections.shuffle(spawnPoints, new java.util.Random(level.getRandom().nextLong()));
        return spawnPoints;
    }

    private static BlockPos chooseSpawnPositionNearPlayer(ServerLevel level, ServerPlayer player,
                                                          List<BlockPos> reactors,
                                                          List<BlockPos> escapeBlocks,
                                                          List<SpawnPoint> fallbackSpawnPoints,
                                                          int index) {
        BlockPos playerPos = player.blockPosition();
        BlockPos nearestReactor = findNearestPos(reactors, playerPos);
        List<SpawnPoint> playerSpawnPoints = new ArrayList<>();

        if (nearestReactor != null) {
            BlockPos reactorEscapeBlock = chooseRandomEscapeBlockNear(level, escapeBlocks, nearestReactor);
            if (reactorEscapeBlock != null) {
                playerSpawnPoints.add(new SpawnPoint(reactorEscapeBlock, false));
            }
            playerSpawnPoints.add(new SpawnPoint(nearestReactor, true));
        }

        BlockPos nearestEscapeBlock = findNearestPos(escapeBlocks, playerPos);
        if (nearestEscapeBlock != null) {
            playerSpawnPoints.add(new SpawnPoint(nearestEscapeBlock, false));
        }

        Collections.shuffle(playerSpawnPoints, new java.util.Random(level.getRandom().nextLong()));
        if (!playerSpawnPoints.isEmpty()) {
            return resolveSpawnPosition(level, playerSpawnPoints.get(0));
        }
        return chooseFallbackSpawnPosition(level, fallbackSpawnPoints, index, playerPos);
    }

    private static BlockPos chooseFallbackSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        ServerPlayer fallbackPlayer = level.players().isEmpty() ? null : level.players().get(0);
        BlockPos fallback = fallbackPlayer == null ? level.getSharedSpawnPos() : fallbackPlayer.blockPosition();
        return chooseFallbackSpawnPosition(level, spawnPoints, index, fallback);
    }

    private static BlockPos chooseFallbackSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints, int index,
                                                        BlockPos fallback) {
        if (!spawnPoints.isEmpty()) {
            return resolveSpawnPosition(level, spawnPoints.get(index % spawnPoints.size()));
        }
        return EntityUtil.findSafeGroundPositionInCompany(level, fallback, 4);
    }

    private static BlockPos resolveSpawnPosition(ServerLevel level, SpawnPoint point) {
        return point.reactor
                ? EntityUtil.findReactorSpawnPositionInCompany(level, point.pos, 4)
                : EntityUtil.findSafeGroundPositionInCompany(level, point.pos, 4);
    }

    private static boolean spawnVioletDawnAt(ServerLevel level, BlockPos spawnPos) {
        if (spawnPos == null) {
            return false;
        }
        EntityVioletDawn violetDawn = ModEntities.violet_dawn.get().create(level);
        if (violetDawn == null) {
            return false;
        }

        violetDawn.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        violetDawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        violetDawn.setOrdealSpawn(true);
        violetDawn.setPersistenceRequired();
        return level.addFreshEntity(violetDawn);
    }

    private static BlockPos chooseRandomEscapeBlockNear(ServerLevel level, List<BlockPos> escapeBlocks,
                                                        BlockPos origin) {
        List<BlockPos> nearby = escapeBlocks.stream()
                .filter(pos -> pos.distSqr(origin) <= REACTOR_ESCAPE_BLOCK_RANGE_SQR)
                .toList();
        if (nearby.isEmpty()) {
            return null;
        }
        return nearby.get(level.getRandom().nextInt(nearby.size()));
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

    private static void showVioletDawnTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, VIOLET_DAWN_COLOR);
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
