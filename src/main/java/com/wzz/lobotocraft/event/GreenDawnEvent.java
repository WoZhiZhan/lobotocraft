package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.ordeal.EntityGreenDawn;
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
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 绿色的黎明考验。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class GreenDawnEvent {
    public static void triggerGreenDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.hasActiveDawn()) return;

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();

        MinecraftServer server = level.getServer();
        int count = Math.max(1, server.getPlayerList().getPlayers().size()) * 3;
        data.startGreenDawn(count);

        showGreenDawnTitle(server,
                "绿色的黎明",
                "疑问",
                "有一天，我们想到一个问题：我们从何而来？我们被给予了生命，却又被不负责任地抛弃。");
        playGlobalSound(server, ModSounds.GREEN_DAWN_START.get());

        List<SpawnPoint> spawnPoints = collectSpawnPoints(level);
        for (int i = 0; i < count; i++) {
            spawnGreenDawn(level, spawnPoints, i);
        }
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
                .map(blockEntity -> new SpawnPoint(blockEntity.getBlockPos(), true))
                .forEach(spawnPoints::add);

        EscapeBlockEntity.getEscapeBlocks(level.dimension()).stream()
                .map(pos -> new SpawnPoint(pos, false))
                .forEach(spawnPoints::add);

        Collections.shuffle(spawnPoints, new java.util.Random(level.getRandom().nextLong()));
        return spawnPoints;
    }

    private static void spawnGreenDawn(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        EntityGreenDawn greenDawn = ModEntities.green_dawn.get().create(level);
        if (greenDawn == null) {
            OrdealData.get(level).decrementGreenDawnRemaining();
            return;
        }

        BlockPos spawnPos = chooseSpawnPosition(level, spawnPoints, index);
        greenDawn.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        greenDawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        greenDawn.setPersistenceRequired();
        level.addFreshEntity(greenDawn);
    }

    private static BlockPos chooseSpawnPosition(ServerLevel level, List<SpawnPoint> spawnPoints, int index) {
        if (!spawnPoints.isEmpty()) {
            SpawnPoint point = spawnPoints.get(index % spawnPoints.size());
            return point.reactor
                    ? EntityUtil.findReactorSpawnPositionInCompany(level, point.pos, 4)
                    : EntityUtil.findSafeGroundPositionInCompany(level, point.pos, 4);
        }

        ServerPlayer fallbackPlayer = level.players().isEmpty() ? null : level.players().get(0);
        BlockPos fallback = fallbackPlayer == null ? level.getSharedSpawnPos() : fallbackPlayer.blockPosition();
        return EntityUtil.findSafeGroundPositionInCompany(level, fallback, 4);
    }

    private static void showGreenDawnTitle(MinecraftServer server, String top, String middle, String bottom) {
        if (server == null) return;
        Component topLine = Component.literal(top).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        Component title = Component.literal(middle).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
        Component subtitle = Component.literal(bottom).withStyle(ChatFormatting.GREEN);
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

    private record SpawnPoint(BlockPos pos, boolean reactor) {
    }
}
