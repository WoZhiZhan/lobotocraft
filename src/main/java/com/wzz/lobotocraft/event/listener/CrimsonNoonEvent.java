package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall;
import com.wzz.lobotocraft.entity.ordeal.EntityCrimsonNoon;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.CRIMSON_MIDDAY_COLOR;
import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.sendOrdealTitle;

/**
 * 血色的正午考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class CrimsonNoonEvent {
    private static final int BLOODY_SMALL_SPAWN_COUNT = 3;

    public static final String CRIMSON_NOON_CLOWN_TAG = "lobotocraft_crimson_noon_clown";

    public static boolean triggerCrimsonNoon(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveOrdeal()) {
            return false;
        }

        List<SpawnPoint> spawnPoints = collectSpawnPoints(level);
        if (spawnPoints.isEmpty()) {
            return false;
        }

        int spawned = spawnCrimsonNoon(level, spawnPoints);
        if (spawned <= 0) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementMiddayTriggersToday();
        data.setRandomNextMiddayType(level.getRandom());
        data.startCrimsonMidday(spawned);

        MinecraftServer server = level.getServer();
        showCrimsonNoonTitle(server,
                "血色的正午",
                "汁水大合唱",
                "我们每时都在游行，每刻都在分享喜悦。");
        playGlobalSound(server, ModSounds.CRIMSON_NOON_START.get());
        return true;
    }

    public static void onCrimsonNoonKilled(ServerLevel level, EntityCrimsonNoon crimsonNoon) {
        int spawnedClowns = spawnBloodySmallAfterDeath(level, crimsonNoon);

        OrdealData data = OrdealData.get(level);
        if (!crimsonNoon.isOrdealSpawn() || !data.isCrimsonMiddayActive()) {
            return;
        }

        data.addCrimsonMiddayRemaining(spawnedClowns);

        if (data.decrementCrimsonMiddayRemaining() > 0) {
            return;
        }

        finishCrimsonNoon(level);
    }

    public static void onCrimsonNoonClownKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isCrimsonMiddayActive()) {
            return;
        }
        if (data.decrementCrimsonMiddayRemaining() > 0) {
            return;
        }

        finishCrimsonNoon(level);
    }

    private static void finishCrimsonNoon(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        data.finishCrimsonMidday();
        MinecraftServer server = level.getServer();
        showCrimsonNoonTitle(server,
                "血色的正午",
                "汁水大合唱",
                "我们绘出了生命的碰撞...肉体的交融...更美丽的外表...");
        playGlobalSound(server, ModSounds.CRIMSON_NOON_END.get());
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

    private static int spawnCrimsonNoon(ServerLevel level, List<SpawnPoint> spawnPoints) {
        for (int i = 0; i < spawnPoints.size(); i++) {
            BlockPos spawnPos = chooseSpawnPosition(level, spawnPoints, i);
            if (spawnCrimsonNoonAt(level, spawnPos)) {
                return 1;
            }
        }
        return 0;
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

    private static boolean spawnCrimsonNoonAt(ServerLevel level, BlockPos spawnPos) {
        if (spawnPos == null || !EntityUtil.isInCompany(level, spawnPos)) {
            return false;
        }

        EntityCrimsonNoon crimsonNoon = ModEntities.crimson_noon.get().create(level);
        if (crimsonNoon == null || !canPlaceCrimsonNoon(level, crimsonNoon, spawnPos)) {
            return false;
        }

        crimsonNoon.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        crimsonNoon.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        crimsonNoon.setOrdealSpawn(true);
        crimsonNoon.setPersistenceRequired();
        return level.addFreshEntity(crimsonNoon);
    }

    private static boolean canPlaceCrimsonNoon(ServerLevel level, EntityCrimsonNoon crimsonNoon, BlockPos pos) {
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
        int requiredClearance = Math.max(2, (int) Math.ceil(crimsonNoon.getBbHeight()));
        for (int yOffset = 0; yOffset < requiredClearance; yOffset++) {
            if (!level.isEmptyBlock(pos.above(yOffset))) {
                return false;
            }
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        double halfWidth = crimsonNoon.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + crimsonNoon.getBbHeight(), z + halfWidth);
        return level.noCollision(crimsonNoon, boundingBox);
    }

    private static int spawnBloodySmallAfterDeath(ServerLevel level, EntityCrimsonNoon crimsonNoon) {
        level.sendParticles(ParticleTypes.EXPLOSION, crimsonNoon.getX(), crimsonNoon.getY() + 0.8D, crimsonNoon.getZ(),
                6, 1.0D, 0.6D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, crimsonNoon.getX(), crimsonNoon.getY() + 0.5D, crimsonNoon.getZ(),
                35, 1.1D, 0.7D, 1.1D, 0.02D);

        int spawned = 0;
        List<AbstractAbnormality> abnormalities = new ArrayList<>(CrimsonDawnEvent.findCandidateAbnormalities(level));
        for (int i = 0; i < BLOODY_SMALL_SPAWN_COUNT; i++) {
            AbstractAbnormality abnormality = chooseCrimsonNoonClownTarget(level, crimsonNoon, abnormalities);
            if (spawnCrimsonNoonClown(level, crimsonNoon.blockPosition(), abnormality)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static AbstractAbnormality chooseCrimsonNoonClownTarget(ServerLevel level, EntityCrimsonNoon crimsonNoon,
                                                                   List<AbstractAbnormality> abnormalities) {
        if (abnormalities.isEmpty()) {
            return null;
        }
        return abnormalities.stream()
                .filter(abnormality -> EntityUtil.isInCompany(level, abnormality.blockPosition()))
                .min(java.util.Comparator.comparingDouble(crimsonNoon::distanceToSqr))
                .orElse(null);
    }

    private static boolean spawnCrimsonNoonClown(ServerLevel level, BlockPos fallbackCenter,
                                                AbstractAbnormality abnormality) {
        EntityBloodySmall clown = ModEntities.bloody_small.get().create(level);
        if (clown == null) {
            return false;
        }

        BlockPos spawnPos = null;
        if (abnormality != null) {
            spawnPos = CrimsonDawnEvent.findBloodySmallSpawnPosition(level, clown, abnormality.blockPosition(), 4);
            if (spawnPos == null) {
                spawnPos = CrimsonDawnEvent.findBloodySmallSpawnPosition(level, clown, abnormality.blockPosition(), 8);
            }
            if (spawnPos == null) {
                spawnPos = CrimsonDawnEvent.findBloodySmallSpawnPosition(level, clown, abnormality.blockPosition(), 12);
            }
        }
        if (spawnPos == null) {
            spawnPos = CrimsonDawnEvent.findBloodySmallSpawnPosition(level, clown, fallbackCenter, 6);
        }
        if (spawnPos == null) {
            return false;
        }

        clown.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        clown.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        clown.setPersistenceRequired();
        clown.setCrimsonNoonSpawn(true);
        clown.getPersistentData().putBoolean(CRIMSON_NOON_CLOWN_TAG, true);
        clown.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        if (abnormality != null) {
            clown.setTrackedAbnormality(abnormality);
        }
        return level.addFreshEntity(clown);
    }

    private static void showCrimsonNoonTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, CRIMSON_MIDDAY_COLOR);
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
