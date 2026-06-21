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

        final int[] currentDay = {1};
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> currentDay[0] = data.getCurrentDay());
        if (currentDay[0] < UNLOCK_DAY) return;

        OrdealData data = OrdealData.get(level);
        data.syncDay(currentDay[0]);
        if (data.isBloodDawnActive()) return;
        if (data.getDawnTriggersToday() >= MAX_STAGE_TRIGGERS_PER_DAY) {
            broadcastChance(player.getServer(), data.getDawnChance(), true);
            return;
        }

        int chance = Math.min(100, data.getDawnChance() + CHANCE_STEP);
        data.setDawnChance(chance);
        broadcastChance(player.getServer(), chance, false);

        if (level.getRandom().nextInt(100) < chance) {
            triggerBloodDawn(level);
        }
    }

    private static void triggerBloodDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        data.setDawnChance(0);
        data.incrementDawnTriggersToday();

        MinecraftServer server = level.getServer();
        int count = Math.min(MAX_BLOOD_DAWN_ENTITIES,
                Math.max(1, server.getPlayerList().getPlayers().size()));
        data.startBloodDawn(count);

        showBloodDawnTitle(server,
                "血色的黎明",
                "开始欢呼吧！",
                "让我们在这宛如风中残烛的生命里，纵情放一把大火吧！");
        playGlobalSound(server, ModSounds.BLOODY_DAWN_START.get());

        for (int i = 0; i < count; i++) {
            spawnBloodySmall(level);
        }
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
                        && !abnormality.hasEscape()
                        && abnormality.getQliphothCounter() > 0);
    }

    private static void spawnBloodySmall(ServerLevel level) {
        EntityBloodySmall clown = ModEntities.bloody_small.get().create(level);
        if (clown == null) return;

        AbstractAbnormality target = chooseAbnormality(level);
        BlockPos spawnPos;
        if (target != null) {
            spawnPos = EntityUtil.findSafeGroundPositionInCompany(level, target.blockPosition(), 3);
            clown.setTrackedAbnormality(target);
        } else {
            ServerPlayer fallbackPlayer = level.players().isEmpty() ? null : level.players().get(0);
            BlockPos fallback = fallbackPlayer == null ? level.getSharedSpawnPos() : fallbackPlayer.blockPosition();
            spawnPos = EntityUtil.findSafeGroundPositionInCompany(level, fallback, 3);
        }

        clown.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        clown.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        clown.setPersistenceRequired();
        level.addFreshEntity(clown);
    }

    private static AbstractAbnormality chooseAbnormality(ServerLevel level) {
        List<AbstractAbnormality> candidates = new ArrayList<>(findCandidateAbnormalities(level));
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        return candidates.get(0);
    }

    private static void broadcastChance(MinecraftServer server, int chance, boolean capped) {
        if (server == null) return;
        String suffix = capped ? " §7(今日黎明已达上限)" : "";
        Component message = Component.literal("血色的黎明：")
                .withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(chance + "%" + suffix).withStyle(ChatFormatting.RED));
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
