package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaborn;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 深蓝色的正午——大群的意志。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class BlueMiddayEvent {
    public static final int MAX_TRIGGERS_PER_DAY = 2;
    public static final String BLUE_MIDDAY_SPAWN_TAG = "lobotocraft_blue_midday_spawn";
    private static final String BLUE_MIDDAY_COUNTED_TAG = "lobotocraft_blue_midday_counted";

    private static boolean trialActive = false;

    public static boolean isTrialActive() {
        if (trialActive) {
            return true;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (OrdealData.get(level).isBlueMiddayActive()) {
                trialActive = true;
                return true;
            }
        }
        return false;
    }

    /**
     * 由伊莎玛拉出逃调用：结束正午考验，后续吸收海嗣仍通过 isSeaborn 判定。
     */
    public static void endTrial() {
        trialActive = false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        OrdealData.get(server.overworld()).finishBlueMidday();
    }

    public static boolean isSeaborn(Entity entity) {
        return entity instanceof EntityBasinSeaborn;
    }

    public static boolean isBlueMiddaySpawn(Entity entity) {
        return entity.getPersistentData().getBoolean(BLUE_MIDDAY_SPAWN_TAG);
    }

    public static boolean triggerBlueMidday(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveOrdeal()) {
            return false;
        }

        List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
        escapeBlocks.removeIf(pos -> !(level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                || !EntityUtil.isInCompany(level, pos));
        if (escapeBlocks.isEmpty()) {
            return false;
        }

        MinecraftServer server = level.getServer();
        int groupCount = Math.max(1, server.getPlayerList().getPlayers().size());
        List<ServerPlayer> players = getEligiblePlayers(level);
        Collections.shuffle(players, new java.util.Random(level.getRandom().nextLong()));
        Collections.shuffle(escapeBlocks, new java.util.Random(level.getRandom().nextLong()));

        int spawned = 0;
        for (int i = 0; i < groupCount; i++) {
            ServerPlayer player = players.isEmpty() ? null : players.get(i % players.size());
            BlockPos anchor = chooseEscapeBlockNearPlayer(level, escapeBlocks, player, i);
            for (EntityType<? extends Mob> type : chooseSeabornTypes(level)) {
                if (spawnSeaborn(level, type, anchor)) {
                    spawned++;
                }
            }
        }

        if (spawned <= 0) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementMiddayTriggersToday();
        data.setRandomNextMiddayType(level.getRandom());
        data.startBlueMidday(spawned);
        trialActive = true;

        showBlueMiddayTitle(server,
                "深蓝色的正午",
                "大群的意志",
                "自由的进化和退化，是前文明留给它们的礼物。");
        applyStartRegeneration(server);
        return true;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof EntityBasinSeaborn seaborn)) {
            return;
        }
        if (!isBlueMiddaySpawn(seaborn)
                || seaborn.getPersistentData().getBoolean(BLUE_MIDDAY_COUNTED_TAG)) {
            return;
        }

        seaborn.getPersistentData().putBoolean(BLUE_MIDDAY_COUNTED_TAG, true);
        onBlueMiddaySeabornKilled(level);
    }

    private static void onBlueMiddaySeabornKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isBlueMiddayActive()) {
            return;
        }
        if (data.decrementBlueMiddayRemaining() > 0) {
            return;
        }

        data.finishBlueMidday();
        trialActive = false;
        MinecraftServer server = level.getServer();
        awardCompletionWorkCount(server);
        showBlueMiddayTitle(server,
                "深蓝色的正午",
                "大群的意志",
                "它们无法理解情感与音乐，是前文明留给它们的缺陷。");
    }

    private static List<ServerPlayer> getEligiblePlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(player -> !player.isAlive() || player.isSpectator());
        return players;
    }

    private static BlockPos chooseEscapeBlockNearPlayer(ServerLevel level, List<BlockPos> escapeBlocks,
                                                        Player player, int index) {
        if (player == null) {
            return escapeBlocks.get(index % escapeBlocks.size());
        }

        BlockPos nearest = findNearestPos(escapeBlocks, player.blockPosition());
        return nearest == null ? escapeBlocks.get(index % escapeBlocks.size()) : nearest;
    }

    private static List<EntityType<? extends Mob>> chooseSeabornTypes(ServerLevel level) {
        List<EntityType<? extends Mob>> normal = new ArrayList<>();
        normal.add(ModEntities.shell_sea_runner.get());
        normal.add(ModEntities.ridgesea_spitter.get());
        normal.add(ModEntities.deepsea_slider.get());
        Collections.shuffle(normal, new java.util.Random(level.getRandom().nextLong()));

        List<EntityType<? extends Mob>> elite = new ArrayList<>();
        elite.add(ModEntities.basinsea_reaper.get());
        elite.add(ModEntities.primalsea_piercer.get());
        elite.add(ModEntities.nucleic_maleficent.get());

        List<EntityType<? extends Mob>> selected = new ArrayList<>();
        selected.add(normal.get(0));
        selected.add(normal.get(1));
        selected.add(elite.get(level.getRandom().nextInt(elite.size())));
        Collections.shuffle(selected, new java.util.Random(level.getRandom().nextLong()));
        return selected;
    }

    private static boolean spawnSeaborn(ServerLevel level, EntityType<? extends Mob> type, BlockPos anchor) {
        if (anchor == null) {
            return false;
        }

        Mob mob = type.create(level);
        if (mob == null) {
            return false;
        }

        BlockPos spawnPos = findSpawnPosition(level, mob, anchor);
        if (spawnPos == null) {
            return false;
        }

        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(BLUE_MIDDAY_SPAWN_TAG, true);
        mob.getPersistentData().putBoolean(BLUE_MIDDAY_COUNTED_TAG, false);
        mob.setGlowingTag(true);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));
        if (mob instanceof EntityBasinSeaborn seaborn) {
            seaborn.activateBlueMiddayAggression();
        }
        return level.addFreshEntity(mob);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, Mob mob, BlockPos anchor) {
        BlockPos direct = EntityUtil.findSafeGroundPositionInCompany(level, anchor, 4);
        if (canPlaceMob(level, mob, direct)) {
            return direct;
        }

        for (int attempt = 0; attempt < 48; attempt++) {
            int offsetX = level.getRandom().nextInt(9) - 4;
            int offsetZ = level.getRandom().nextInt(9) - 4;
            BlockPos candidate = EntityUtil.findSafeGroundPositionInCompany(
                    level, anchor.offset(offsetX, 0, offsetZ), 0);
            if (canPlaceMob(level, mob, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean canPlaceMob(ServerLevel level, Mob mob, BlockPos pos) {
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
        double halfWidth = mob.getBbWidth() / 2.0D;
        AABB boundingBox = new AABB(
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + mob.getBbHeight(), z + halfWidth);
        return level.noCollision(mob, boundingBox);
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

    private static void applyStartRegeneration(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 1, false, true, true));
        }
    }

    private static void awardCompletionWorkCount(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                data.setTodayWorkCount(data.getTodayWorkCount() + 2);
                data.setHasSleep(false);
                MessageLoader.getLoader().sendToPlayer(player,
                        new CompanyDailySyncPacket(
                                data.getCurrentDay(),
                                data.getTodayWorkCount(),
                                data.isArmorLocked(),
                                data.isHasSleep()
                        ));
            });
        }
    }

    private static void showBlueMiddayTitle(MinecraftServer server, String top, String middle, String bottom) {
        if (server == null) {
            return;
        }
        Component topLine = Component.literal(top).withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD);
        Component title = Component.literal(middle).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD);
        Component subtitle = Component.literal(bottom).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetActionBarTextPacket(topLine));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private BlueMiddayEvent() {
    }
}
