package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityAmberDawn;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 琥珀色的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class AmberDawnEvent {
    private static final int AMBER_DAWNS_PER_PLAYER = 4;
    private static final int SEARCH_LIMIT = 30_000_000;

    public static void triggerAmberDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveDawn()) return;

        List<BlockPos> escapeBlocks = collectEscapeBlocks(level);
        if (escapeBlocks.isEmpty()) {
            return;
        }

        int playerCount = Math.max(1, getEligiblePlayers(level).size());
        int count = playerCount * AMBER_DAWNS_PER_PLAYER;
        int spawned = spawnAmberDawns(level, escapeBlocks, count);
        if (spawned <= 0) {
            return;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setRandomNextDawnType(level.getRandom());
        data.startAmberDawn(spawned);

        MinecraftServer server = level.getServer();
        showAmberDawnTitle(server,
                "琥珀色的黎明",
                "新鲜的食物",
                "食物-新鲜。替代品-很好。");
        playGlobalSound(server, ModSounds.AMBER_DAWN_START.get());
    }

    public static void onAmberDawnKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isAmberDawnActive()) return;
        if (data.decrementAmberDawnRemaining() > 0) return;

        data.finishAmberDawn();
        MinecraftServer server = level.getServer();
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

    private static int spawnAmberDawns(ServerLevel level, List<BlockPos> escapeBlocks, int count) {
        BlockPos bestEscapeBlock = null;
        List<BlockPos> bestSpawnPositions = Collections.emptyList();

        for (BlockPos escapeBlock : escapeBlocks) {
            List<BlockPos> spawnPositions = collectAmberSpawnPositions(level, escapeBlock, count);
            if (spawnPositions.size() == count) {
                return spawnAmberDawnsAt(level, escapeBlock, spawnPositions);
            }
            if (spawnPositions.size() > bestSpawnPositions.size()) {
                bestEscapeBlock = escapeBlock;
                bestSpawnPositions = spawnPositions;
            }
        }

        return bestEscapeBlock == null
                ? 0
                : spawnAmberDawnsAt(level, bestEscapeBlock, bestSpawnPositions);
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

    private static int spawnAmberDawnsAt(ServerLevel level, BlockPos escapeBlock, List<BlockPos> spawnPositions) {
        int spawned = 0;
        for (BlockPos spawnPos : spawnPositions) {
            if (spawnAmberDawnAt(level, escapeBlock, spawnPos)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawnAmberDawnAt(ServerLevel level, BlockPos escapeBlock, BlockPos spawnPos) {
        EntityAmberDawn amberDawn = ModEntities.amber_dawn.get().create(level);
        if (amberDawn == null) {
            return false;
        }
        amberDawn.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        amberDawn.setHomePos(escapeBlock);
        amberDawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        amberDawn.setOrdealSpawn(true);
        amberDawn.setPersistenceRequired();
        return level.addFreshEntity(amberDawn);
    }

    private static List<BlockPos> collectEscapeBlocks(ServerLevel level) {
        List<BlockPos> escapeBlocks = new ArrayList<>();
        EscapeBlockEntity.getEscapeBlocks(level.dimension()).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                .filter(pos -> EntityUtil.isInCompany(level, pos))
                .map(BlockPos::immutable)
                .forEach(escapeBlocks::add);
        Collections.shuffle(escapeBlocks, new java.util.Random(level.getRandom().nextLong()));
        return escapeBlocks;
    }

    private static List<ServerPlayer> getEligiblePlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(player -> !player.isAlive() || player.isSpectator());
        return players;
    }

    private static boolean canPlaceAmberDawn(ServerLevel level, EntityAmberDawn amberDawn, BlockPos pos,
                                             Set<BlockPos> reservedPositions) {
        if (pos == null || !EntityUtil.isInCompany(level, pos)) {
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
        if (server == null) return;
        Component topLine = Component.literal(top).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component title = Component.literal(middle).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        Component subtitle = Component.literal(bottom).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetActionBarTextPacket(topLine));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void playGlobalSound(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }
}
