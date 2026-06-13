package com.wzz.lobotocraft.event;

import com.google.gson.*;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.EscapeAlertPacket;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.structure.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * 玩家出生和维度管理事件处理器
 * 使用JSON文件存储数据，避免NBT死亡丢失问题
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class PlayerSpawnAndDimensionHandler {

    private static final String PLAYER_DATA_FILE = "lobotocraft_player_data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 获取玩家数据文件路径
     */
    private static Path getDataFile(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve(PLAYER_DATA_FILE);
    }

    /**
     * 检查玩家是否已经加入过（使用JSON文件）
     */
    private static boolean hasPlayerJoinedBefore(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Path dataFile = getDataFile(level);
        try {
            if (Files.exists(dataFile)) {
                JsonArray jsonArray = JsonParser.parseReader(Files.newBufferedReader(dataFile)).getAsJsonArray();
                String playerUuid = player.getUUID().toString();
                for (JsonElement element : jsonArray) {
                    if (element.getAsString().equals(playerUuid)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            ModLogger.LOGGER.warn("检查玩家数据失败！", e);
            return false;
        }
    }

    /**
     * 标记玩家已加入（使用JSON文件）
     */
    private static void markPlayerJoined(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Path dataFile = getDataFile(level);
        try {
            JsonArray jsonArray;
            if (Files.exists(dataFile)) {
                jsonArray = JsonParser.parseReader(Files.newBufferedReader(dataFile)).getAsJsonArray();
            } else {
                jsonArray = new JsonArray();
            }

            String playerUuid = player.getUUID().toString();
            // 检查是否已存在，避免重复添加
            for (JsonElement element : jsonArray) {
                if (element.getAsString().equals(playerUuid)) {
                    return;
                }
            }

            jsonArray.add(playerUuid);
            Files.createDirectories(dataFile.getParent());
            Files.writeString(
                    dataFile,
                    GSON.toJson(jsonArray),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            ModLogger.LOGGER.warn("标记玩家数据失败！", e);
        }
    }

    /**
     * 玩家首次加入服务器时的处理
     * 1. 传送到脑叶公司维度
     * 2. 给予传送装置
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAlertToPlayer(player);
        // 使用JSON文件检查是否首次加入
        if (!hasPlayerJoinedBefore(player)) {
            markPlayerJoined(player);

            // 延迟1 tick后执行传送和物品给予，确保玩家完全加载
            Objects.requireNonNull(player.getServer()).tell(new net.minecraft.server.TickTask(
                    player.getServer().getTickCount() + 1,
                    () -> {
                        ServerLevel lobotoLevel = player.getServer().getLevel(ModDimensions.LOBOTO_KEY);
                        if (lobotoLevel != null) {
                            BlockPos spawnPos = new BlockPos(0, 4, 0);
                            EntityUtil.teleportPlayer(player, ModDimensions.LOBOTO_KEY, spawnPos);

                            // 给予传送装置
                            ItemStack conveyer = new ItemStack(ModItems.CONVEYER.get());
                            ItemStack otto = new ItemStack(ModItems.OTTO.get());
                            if (!player.getInventory().add(conveyer)) {
                                player.drop(conveyer, false);
                            }
                            if (!player.getInventory().add(otto)) {
                                player.drop(otto, false);
                            }
                            player.sendSystemMessage(Component.literal("§6欢迎来到脑叶公司！"));
                            Objects.requireNonNull(player.getServer()).tell(new net.minecraft.server.TickTask(
                                    player.getServer().getTickCount() + 40,
                                    () -> {
                                        player.teleportTo(195, 273, 29);
                                        BlockPos bedPos = new BlockPos(195, 273, 29);
                                        player.setRespawnPosition(player.serverLevel().dimension(), bedPos, 0.0F, true, false);
                                    }));
                        }
                    }
            ));
        }
    }

    /**
     * 玩家重生时的处理
     * 在公司维度设置默认重生点，但允许玩家用床或指令覆盖
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAlertToPlayer(player);
        // 如果玩家有设置的重生点，优先使用
        if (player.getRespawnPosition() != null) {
            return;
        }

        // 如果玩家在公司维度死亡且没有设置重生点，重生到公司出生点
        if (player.level().dimension().equals(ModDimensions.LOBOTO_KEY)) {
            player.getServer().tell(new net.minecraft.server.TickTask(
                    player.getServer().getTickCount() + 1,
                    () -> {
                        BlockPos spawnPos = new BlockPos(0, 4, 0);
                        player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    }
            ));
        }
    }

    private static void syncAlertToPlayer(ServerPlayer player) {
        int level = EscapeTracker.getInstance().getCurrentAlertLevel();
        MessageLoader.getLoader().sendToPlayer(player, new EscapeAlertPacket(level));
    }

    /**
     * 方块破坏保护
     * 在脑叶公司维度内，玩家只能破坏自己放置的方块
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            if (!level.dimension().equals(ModDimensions.LOBOTO_KEY)) {
                return;
            }
            BlockPos pos = event.getPos();
            if (ProtectionHelper.isProtected(level, pos)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截爆炸破坏受保护方块
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!event.getLevel().dimension().equals(ModDimensions.LOBOTO_KEY)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos ->
                ProtectionHelper.isProtected(event.getLevel(), pos)
        );
    }

    /**
     * 拦截方块邻居更新导致的破坏（如沙子掉落、火把掉落等）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level) {
            if (!level.dimension().equals(ModDimensions.LOBOTO_KEY)) {
                return;
            }

            BlockPos pos = event.getPos();
            if (ProtectionHelper.isProtected(level, pos)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截流体放置（防止水流/岩浆破坏方块）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof Level level) {
            if (!level.dimension().equals(ModDimensions.LOBOTO_KEY)) {
                return;
            }
            BlockPos pos = event.getPos();
            if (ProtectionHelper.isProtected(level, pos)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦截活塞推动受保护方块
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPistonMove(PistonEvent.Pre event) {
        if (event.getLevel() instanceof Level level) {
            if (!level.dimension().equals(ModDimensions.LOBOTO_KEY) || event.getStructureHelper() == null) {
                return;
            }
            for (BlockPos pos : event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                    ? event.getStructureHelper().getToPush()
                    : event.getStructureHelper().getToDestroy()) {
                if (ProtectionHelper.isProtected(level, pos)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}