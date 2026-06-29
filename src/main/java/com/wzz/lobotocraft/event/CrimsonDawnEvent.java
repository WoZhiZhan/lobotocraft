package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall;
import com.wzz.lobotocraft.event.work.WorkCompleteEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 血色的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class CrimsonDawnEvent {
    private static final int UNLOCK_DAY = 3;
    private static final int CHANCE_STEP = 3;
    private static final int MAX_STAGE_TRIGGERS_PER_DAY = 3;
    private static final int MAX_BLOOD_DAWN_ENTITIES = 5;
    private static final int LOCATION_NOTICE_INTERVAL_TICKS = 10 * 20;
    private static final int SEARCH_LIMIT = 30_000_000;
    private static long lastLocationNoticeGameTime = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onWorkComplete(WorkCompleteEvent event) {
        ServerPlayer player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        int currentDay = getMaxServerDay(player.getServer());
        if (currentDay < UNLOCK_DAY) return;

        OrdealData data = OrdealData.get(level);
        data.syncDay(currentDay);
        if (data.hasActiveDawn()) return;
        boolean nextGreenDawn = getOrCreateNextDawnType(level, data);
        if (data.getDawnTriggersToday() >= MAX_STAGE_TRIGGERS_PER_DAY) {
            broadcastChance(player.getServer(), data.getDawnChance(), nextGreenDawn, true);
            return;
        }

        int chance = Math.min(100, data.getDawnChance() + CHANCE_STEP);
        data.setDawnChance(chance);
        broadcastChance(player.getServer(), chance, nextGreenDawn, false);

        if (level.getRandom().nextInt(100) < chance) {
            if (nextGreenDawn) {
                GreenDawnEvent.triggerGreenDawn(level);
            } else {
                triggerBloodDawn(level);
            }
        }
    }

    private static int getMaxServerDay(MinecraftServer server) {
        if (server == null) {
            return 1;
        }
        final int[] maxDay = {1};
        for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
            serverPlayer.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                    .ifPresent(data -> maxDay[0] = Math.max(maxDay[0], data.getCurrentDay()));
        }
        return maxDay[0];
    }

    private static boolean getOrCreateNextDawnType(ServerLevel level, OrdealData data) {
        if (!data.hasNextDawnType()) {
            data.setNextDawnType(level.getRandom().nextBoolean());
        }
        return data.isNextGreenDawn();
    }

    private static void triggerBloodDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        MinecraftServer server = level.getServer();
        int count = Math.min(MAX_BLOOD_DAWN_ENTITIES,
                Math.max(1, server.getPlayerList().getPlayers().size()));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawnBloodySmall(level)) {
                spawned++;
            }
        }

        if (spawned <= 0) {
            return;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setNextDawnType(level.getRandom().nextBoolean());
        data.startBloodDawn(spawned);

        showBloodDawnTitle(server,
                "血色的黎明",
                "开始欢呼吧！",
                "让我们在这宛如风中残烛的生命里，纵情放一把大火吧！");
        playGlobalSound(server, ModSounds.BLOODY_DAWN_START.get());
    }

    public static void onBloodySmallKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isBloodDawnActive()) return;
        if (data.decrementBloodDawnRemaining() > 0) return;

        data.finishBloodDawn();
        MinecraftServer server = level.getServer();
        showBloodDawnTitle(server,
                "血色的黎明",
                "开始欢呼吧！",
                "活着，就是为了满足肉欲。");
        playGlobalSound(server, ModSounds.BLOODY_DAWN_END.get());
    }

    public static void notifyAbnormalityLocation(EntityBloodySmall clown, AbstractAbnormality abnormality) {
        if (!(clown.level() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        if (lastLocationNoticeGameTime != Long.MIN_VALUE
                && gameTime - lastLocationNoticeGameTime < LOCATION_NOTICE_INTERVAL_TICKS) {
            return;
        }
        lastLocationNoticeGameTime = gameTime;
        Component message = Component.literal("§4开始欢呼吧！§7出现在 §c"
                + abnormality.getAbnormalityName() + " §7附近。");
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    public static List<AbstractAbnormality> findCandidateAbnormalities(ServerLevel level) {
        AABB whole = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        return level.getEntitiesOfClass(AbstractAbnormality.class, whole,
                abnormality -> abnormality.isAlive()
                        && !abnormality.hasEscape());
    }

    private static boolean spawnBloodySmall(ServerLevel level) {
        EntityBloodySmall clown = ModEntities.bloody_small.get().create(level);
        if (clown == null) return false;

        SpawnTarget spawnTarget = chooseSpawnTarget(level, clown);
        BlockPos spawnPos = spawnTarget.pos();
        if (spawnPos == null) {
            return false;
        }

        clown.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        clown.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        clown.setPersistenceRequired();
        boolean added = level.addFreshEntity(clown);
        if (added && spawnTarget.abnormality() != null) {
            clown.setTrackedAbnormality(spawnTarget.abnormality());
        }
        return added;
    }

    private static SpawnTarget chooseSpawnTarget(ServerLevel level, EntityBloodySmall clown) {
        List<AbstractAbnormality> candidates = new ArrayList<>(findCandidateAbnormalities(level));
        Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        for (AbstractAbnormality candidate : candidates) {
            BlockPos pos = findBloodySmallSpawnPosition(level, clown, candidate.blockPosition(), 4);
            if (pos != null) {
                return new SpawnTarget(pos, candidate);
            }
        }

        List<ServerPlayer> players = new ArrayList<>(level.players());
        Collections.shuffle(players, new java.util.Random(level.getRandom().nextLong()));
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            BlockPos pos = findBloodySmallSpawnPosition(level, clown, player.blockPosition(), 6);
            if (pos != null) {
                return new SpawnTarget(pos, null);
            }
        }

        BlockPos sharedSpawn = findBloodySmallSpawnPosition(level, clown, level.getSharedSpawnPos(), 8);
        return new SpawnTarget(sharedSpawn, null);
    }

    public static BlockPos findBloodySmallSpawnPosition(ServerLevel level, EntityBloodySmall clown,
                                                        BlockPos center, int horizontalRange) {
        BlockPos direct = EntityUtil.findSafeGroundPositionInCompany(level, center, horizontalRange);
        if (canPlaceBloodySmall(level, clown, direct)) {
            return direct;
        }

        int radius = Math.max(4, horizontalRange);
        for (int attempt = 0; attempt < 48; attempt++) {
            int offsetX = level.random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(
                    level, center.offset(offsetX, 0, offsetZ), 0);
            if (canPlaceBloodySmall(level, clown, candidate)) {
                return candidate;
            }
        }

        int fallbackRadius = Math.max(12, radius + 4);
        for (int r = 1; r <= fallbackRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(
                            level, center.offset(dx, 0, dz), 0);
                    if (canPlaceBloodySmall(level, clown, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        BlockPos current = clown.blockPosition();
        return canPlaceBloodySmall(level, clown, current) ? current : null;
    }

    private static boolean canPlaceBloodySmall(ServerLevel level, EntityBloodySmall clown, BlockPos pos) {
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
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            return false;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        double halfWidth = clown.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + clown.getBbHeight(), z + halfWidth);
        return level.noCollision(clown, boundingBox);
    }

    private record SpawnTarget(BlockPos pos, AbstractAbnormality abnormality) {
    }

    private static void broadcastChance(MinecraftServer server, int chance, boolean greenDawn, boolean capped) {
        if (server == null) return;
        ChatFormatting labelColor = greenDawn ? ChatFormatting.GREEN : ChatFormatting.DARK_RED;
        ChatFormatting chanceColor = greenDawn ? ChatFormatting.GREEN : ChatFormatting.RED;
        Component message = Component.literal(greenDawn ? "绿色的黎明：" : "血色的黎明：")
                .withStyle(labelColor)
                .append(Component.literal(chance + "%").withStyle(chanceColor));
        if (capped) {
            message = message.copy().append(Component.literal(" (今日黎明已达上限)").withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        }
    }

    private static void showBloodDawnTitle(MinecraftServer server, String top, String middle, String bottom) {
        if (server == null) return;
        Component topLine = Component.literal(top).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component title = Component.literal(middle).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component subtitle = Component.literal(bottom).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetActionBarTextPacket(topLine));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void playGlobalSound(MinecraftServer server, net.minecraft.sounds.SoundEvent sound) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }
}
