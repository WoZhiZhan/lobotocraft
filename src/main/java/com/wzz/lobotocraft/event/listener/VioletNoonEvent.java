package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityVioletNoon;
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

import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.VIOLET_MIDDAY_COLOR;
import static com.wzz.lobotocraft.event.listener.CrimsonDawnEvent.sendOrdealTitle;

/**
 * 紫罗兰的正午考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class VioletNoonEvent {
    private static final int MAX_NATURAL_VIOLET_NOONS_PER_DIMENSION = 5;
    private static final int SPAWN_HEIGHT_ABOVE_REACTOR = 9;
    private static final int SEARCH_LIMIT = 30_000_000;

    public static boolean triggerVioletNoon(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveOrdeal()) {
            return false;
        }

        List<BlockPos> reactors = collectReactorPositions(level);
        if (reactors.isEmpty()) {
            return false;
        }

        MinecraftServer server = level.getServer();
        int available = MAX_NATURAL_VIOLET_NOONS_PER_DIMENSION - countActiveOrdealVioletNoons(level);
        int count = Math.min(available, Math.max(1, server.getPlayerList().getPlayers().size()));
        if (count <= 0) {
            return false;
        }

        int spawned = spawnVioletNoons(level, reactors, count);
        if (spawned <= 0) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementMiddayTriggersToday();
        data.setRandomNextMiddayType(level.getRandom());
        data.startVioletMidday(spawned);

        showVioletNoonTitle(server,
                "紫罗兰的正午",
                "请给我们爱！！！",
                "我们听见了弱者的挣扎与悲鸣，向它们乞求爱与慈悲吧。");
        playGlobalSound(server, ModSounds.VIOLET_NOON_START.get());
        return true;
    }

    public static void onVioletNoonKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isVioletMiddayActive()) {
            return;
        }
        if (data.decrementVioletMiddayRemaining() > 0) {
            return;
        }

        data.finishVioletMidday();
        MinecraftServer server = level.getServer();
        showVioletNoonTitle(server,
                "紫罗兰的正午",
                "请给我们爱！！！",
                "我们不能理解它们，它们更不会理解我们。");
        playGlobalSound(server, ModSounds.VIOLET_NOON_END.get());
    }

    private static int spawnVioletNoons(ServerLevel level, List<BlockPos> reactors, int count) {
        int spawned = 0;
        for (BlockPos reactor : reactors) {
            if (spawned >= count) {
                break;
            }
            if (spawnVioletNoonAt(level, reactor)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawnVioletNoonAt(ServerLevel level, BlockPos reactorPos) {
        if (reactorPos == null || !EntityUtil.isInCompany(level, reactorPos)) {
            return false;
        }

        EntityVioletNoon violetNoon = ModEntities.violet_noon.get().create(level);
        if (violetNoon == null) {
            return false;
        }

        BlockPos spawnPos = reactorPos.above(SPAWN_HEIGHT_ABOVE_REACTOR);
        if (!canPlaceVioletNoon(level, violetNoon, spawnPos)) {
            return false;
        }

        violetNoon.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        violetNoon.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        violetNoon.setOrdealSpawn(true);
        violetNoon.setPersistenceRequired();
        return level.addFreshEntity(violetNoon);
    }

    private static boolean canPlaceVioletNoon(ServerLevel level, EntityVioletNoon violetNoon, BlockPos pos) {
        if (pos == null || !EntityUtil.isInCompany(level, pos)) {
            return false;
        }
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 6) {
            return false;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        double halfWidth = violetNoon.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + violetNoon.getBbHeight(), z + halfWidth);
        return level.noCollision(violetNoon, boundingBox);
    }

    private static List<BlockPos> collectReactorPositions(ServerLevel level) {
        List<BlockPos> reactors = new ArrayList<>();
        EntityUtil.findBlockEntities(level).stream()
                .filter(RegenerationReactorBlockEntity.class::isInstance)
                .map(blockEntity -> blockEntity.getBlockPos().immutable())
                .filter(pos -> EntityUtil.isInCompany(level, pos))
                .forEach(reactors::add);
        Collections.shuffle(reactors, new java.util.Random(level.getRandom().nextLong()));
        return reactors;
    }

    private static int countActiveOrdealVioletNoons(ServerLevel level) {
        AABB whole = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        return level.getEntitiesOfClass(EntityVioletNoon.class, whole,
                violetNoon -> violetNoon.isAlive() && violetNoon.isOrdealSpawn()).size();
    }

    private static void showVioletNoonTitle(MinecraftServer server, String top, String middle, String bottom) {
        sendOrdealTitle(server, top, middle, bottom, VIOLET_MIDDAY_COLOR);
    }

    private static void playGlobalSound(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }

    private VioletNoonEvent() {
    }
}
