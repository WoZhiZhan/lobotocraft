package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenNoon;
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

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.GREEN_MIDDAY_COLOR;
import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.sendOrdealTitle;

/**
 * 绿色的正午考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class GreenNoonEvent {
    public static boolean triggerGreenNoon(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveOrdeal()) {
            return false;
        }

        List<SpawnPoint> spawnPoints = collectSpawnPoints(level);
        if (spawnPoints.isEmpty()) {
            return false;
        }

        List<ServerPlayer> players = getEligiblePlayers(level);
        int count = Math.max(1, players.size());
        int spawned = spawnGreenNoons(level, spawnPoints, count);
        if (spawned <= 0) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementMiddayTriggersToday();
        data.setRandomNextMiddayType(level.getRandom());
        data.startGreenMidday(spawned);

        MinecraftServer server = level.getServer();
        showGreenNoonTitle(server,
                "绿色的正午",
                "理解的过程",
                "他们，终究是被生命束缚着的存在。而我们，将倾泻绝望与怒火！");
        playGlobalSound(server, ModSounds.GREEN_NOON_START.get());
        return true;
    }

    public static void onGreenNoonKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isGreenMiddayActive()) {
            return;
        }
        if (data.decrementGreenMiddayRemaining() > 0) {
            return;
        }

        data.finishGreenMidday();
        MinecraftServer server = level.getServer();
        showGreenNoonTitle(server,
                "绿色的正午",
                "理解的过程",
                "生命，充满了痛苦。");
        playGlobalSound(server, ModSounds.GREEN_NOON_END.get());
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

    private static int spawnGreenNoons(ServerLevel level, List<SpawnPoint> spawnPoints, int count) {
        int spawned = 0;
        int attempts = Math.max(count * Math.max(1, spawnPoints.size()), count);
        for (int i = 0; spawned < count && i < attempts; i++) {
            if (spawnGreenNoonAt(level, chooseSpawnPosition(level, spawnPoints, i))) {
                spawned++;
            }
        }
        return spawned;
    }

    private static BlockPos chooseSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        if (spawnPoints.isEmpty()) {
            return null;
        }
        SpawnPoint point = spawnPoints.get(index % spawnPoints.size());
        return point.reactor
                ? EntityUtil.findReactorSpawnPositionInCompany(level, point.pos, 4)
                : EntityUtil.findSafeGroundPositionInCompany(level, point.pos, 4);
    }

    private static boolean spawnGreenNoonAt(ServerLevel level, BlockPos spawnPos) {
        if (spawnPos == null || !EntityUtil.isInCompany(level, spawnPos)) {
            return false;
        }

        EntityGreenNoon greenNoon = ModEntities.green_noon.get().create(level);
        if (greenNoon == null || !canPlaceGreenNoon(level, greenNoon, spawnPos)) {
            return false;
        }

        greenNoon.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        greenNoon.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        greenNoon.setOrdealSpawn(true);
        greenNoon.setPersistenceRequired();
        return level.addFreshEntity(greenNoon);
    }

    private static boolean canPlaceGreenNoon(ServerLevel level, EntityGreenNoon greenNoon, BlockPos pos) {
        if (pos == null || !EntityUtil.isInCompany(level, pos)) {
            return false;
        }
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1) {
            return false;
        }
        BlockPos below = pos.below();
        if (level.isEmptyBlock(below) || !level.getBlockState(below).isSolid()) {
            return false;
        }
        int requiredClearance = Math.max(2, (int) Math.ceil(greenNoon.getBbHeight()));
        for (int yOffset = 0; yOffset < requiredClearance; yOffset++) {
            if (!level.isEmptyBlock(pos.above(yOffset))) {
                return false;
            }
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        double halfWidth = greenNoon.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + greenNoon.getBbHeight(), z + halfWidth);
        return level.noCollision(greenNoon, boundingBox);
    }

    private static void showGreenNoonTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, GREEN_MIDDAY_COLOR);
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
