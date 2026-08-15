package com.wzz.lobotocraft.event.listener;

import com.lobotocraft.facility.OrdealRewardService;
import com.lobotocraft.facility.data.OrdealTier;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.ordeal.EntityBloodySmall;
import com.wzz.lobotocraft.event.definition.work.WorkCompleteEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OrdealTitlePacket;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.OrdealData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
 *
 * 标题不再用原版 title，统一走 {@link OrdealTitlePacket} -> OrdealTitleOverlay：
 * 半透明黑色横幅 + 左右斜纹警戒条 + 顶部小字 / 中间大标题 / 底部小字，带淡入淡出。
 * 其它考验（绿色/紫罗兰的黎明、深蓝色/绿色的正午）直接调
 * {@link #sendOrdealTitle(MinecraftServer, String, String, String, int)} 即可，
 * 传入自己的主题色。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class CrimsonDawnEvent {
    private static final int UNLOCK_DAY = 3;
    private static final int MIDDAY_UNLOCK_DAY = 10;
    private static final int CHANCE_STEP = 3;
    private static final int MAX_STAGE_TRIGGERS_PER_DAY = 3;
    private static final int LOCATION_NOTICE_INTERVAL_TICKS = 10 * 20;
    private static final int SEARCH_LIMIT = 30_000_000;
    private static long lastLocationNoticeGameTime = Long.MIN_VALUE;

    /* ===== 各考验的主题色（横幅描边、斜纹、大标题都用这个颜色） ===== */
    public static final int BLOOD_DAWN_COLOR = 0xFF3B3B;   // 血色的黎明
    public static final int GREEN_DAWN_COLOR = 0x6BFF8E;   // 绿色的黎明
    public static final int VIOLET_DAWN_COLOR = 0xC77DFF;  // 紫罗兰的黎明
    public static final int AMBER_DAWN_COLOR = 0xFFB347;   // 琥珀色的黎明
    public static final int BLUE_MIDDAY_COLOR = 0x5AB4FF;  // 深蓝色的正午
    public static final int VIOLET_MIDDAY_COLOR = 0xC77DFF; // 紫罗兰的正午
    public static final int GREEN_MIDDAY_COLOR = 0x6BFF8E;  // 绿色的正午
    public static final int CRIMSON_MIDDAY_COLOR = 0xFF3B3B; // 血色的正午

    /* ===== 标题动画时长（tick）：淡入 / 停留 / 淡出 ===== */
    public static final int TITLE_FADE_IN = 10;
    public static final int TITLE_STAY = 80;
    public static final int TITLE_FADE_OUT = 20;

    // Legacy builds rolled an ordeal after every completed work. The facility
    // scheduler now preselects the complete day at its boundary, so this entry
    // point must stay detached from the Forge bus or it can create unscheduled
    // extra ordeals alongside stages II/III/IV/V/VII/VIII/IX.
    public static void onWorkComplete(WorkCompleteEvent event) {
        ServerPlayer player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        int currentDay = getMaxServerDay(player.getServer());
        if (currentDay < UNLOCK_DAY) return;

        OrdealData data = OrdealData.get(level);
        data.syncDay(currentDay);
        if (data.hasActiveOrdeal()) return;
        if (data.getDawnTriggersToday() >= MAX_STAGE_TRIGGERS_PER_DAY) {
            if (currentDay >= MIDDAY_UNLOCK_DAY) {
                handleMiddayChance(level, player.getServer(), data);
            } else {
                int nextDawnType = getOrCreateNextDawnType(level, data);
                broadcastChance(player.getServer(), data.getDawnChance(), nextDawnType, true);
            }
            return;
        }

        int nextDawnType = getOrCreateNextDawnType(level, data);
        int chance = Math.min(100, data.getDawnChance() + CHANCE_STEP);
        data.setDawnChance(chance);
        broadcastChance(player.getServer(), chance, nextDawnType, false);

        if (level.getRandom().nextInt(100) < chance) {
            switch (nextDawnType) {
                case OrdealData.GREEN_DAWN_TYPE -> GreenDawnEvent.triggerGreenDawn(level);
                case OrdealData.VIOLET_DAWN_TYPE -> VioletDawnEvent.triggerVioletDawn(level);
                case OrdealData.AMBER_DAWN_TYPE -> AmberDawnEvent.triggerAmberDawn(level);
                default -> triggerBloodDawn(level);
            }
        }
    }

    private static void handleMiddayChance(ServerLevel level, MinecraftServer server, OrdealData data) {
        int nextMiddayType = getOrCreateNextMiddayType(level, data);
        if (data.getMiddayTriggersToday() >= BlueMiddayEvent.MAX_TRIGGERS_PER_DAY) {
            broadcastMiddayChance(server, data.getDawnChance(), nextMiddayType, true);
            return;
        }

        int chance = Math.min(100, data.getDawnChance() + CHANCE_STEP);
        data.setDawnChance(chance);
        broadcastMiddayChance(server, chance, nextMiddayType, false);

        if (level.getRandom().nextInt(100) < chance) {
            switch (nextMiddayType) {
                case OrdealData.CRIMSON_MIDDAY_TYPE -> CrimsonNoonEvent.triggerCrimsonNoon(level);
                case OrdealData.VIOLET_MIDDAY_TYPE -> VioletNoonEvent.triggerVioletNoon(level);
                case OrdealData.GREEN_MIDDAY_TYPE -> GreenNoonEvent.triggerGreenNoon(level);
                default -> BlueMiddayEvent.triggerBlueMidday(level);
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

    private static int getOrCreateNextDawnType(ServerLevel level, OrdealData data) {
        if (!data.hasNextDawnType()) {
            data.setRandomNextDawnType(level.getRandom());
        }
        return data.getNextDawnType();
    }

    private static int getOrCreateNextMiddayType(ServerLevel level, OrdealData data) {
        if (!data.hasNextMiddayType()) {
            data.setRandomNextMiddayType(level.getRandom());
        }
        return data.getNextMiddayType();
    }

    public static boolean triggerBloodDawn(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (data.isBloodDawnActive()) return false;
        MinecraftServer server = level.getServer();
        List<EntityBloodySmall> spawned = new ArrayList<>();
        for (OrdealSpawnPointService.DepartmentCorridors department
                : OrdealSpawnPointService.openCorridorsByDepartment(level)) {
            EntityBloodySmall departmentSpawn = null;
            for (BlockPos corridor : department.corridors()) {
                departmentSpawn = spawnBloodySmall(level, corridor);
                if (departmentSpawn != null) {
                    break;
                }
            }
            if (departmentSpawn == null) {
                spawned.forEach(EntityBloodySmall::discard);
                return false;
            }
            spawned.add(departmentSpawn);
        }

        if (spawned.isEmpty()) {
            return false;
        }

        data.setDawnChance(0);
        data.incrementDawnTriggersToday();
        data.setRandomNextDawnType(level.getRandom());
        data.startBloodDawn(spawned.size());

        showBloodDawnTitle(server,
                Component.translatable("ordeal.lobotocraft.crimson_dawn.name"),
                Component.translatable("ordeal.lobotocraft.crimson_dawn.title"),
                Component.translatable("ordeal.lobotocraft.crimson_dawn.start_subtitle"));
        playGlobalSound(server, ModSounds.BLOODY_DAWN_START.get());
        EscapeTracker.getInstance().onOrdealStart(level, OrdealTier.DAWN);
        return true;
    }

    public static void onBloodySmallKilled(ServerLevel level) {
        OrdealData data = OrdealData.get(level);
        if (!data.isBloodDawnActive()) return;
        if (data.decrementBloodDawnRemaining() > 0) return;

        data.finishBloodDawn();
        MinecraftServer server = level.getServer();
        OrdealRewardService.complete(server, OrdealTier.DAWN);
        showBloodDawnTitle(server,
                Component.translatable("ordeal.lobotocraft.crimson_dawn.name"),
                Component.translatable("ordeal.lobotocraft.crimson_dawn.title"),
                Component.translatable("ordeal.lobotocraft.crimson_dawn.end_subtitle"));
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
        Component message = Component.translatable("message.lobotocraft.crimson_dawn.location",
                abnormality.getLocalizedAbnormalityName());
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
                        && FacilityAccessService.isPositionOpen(level.getServer(),
                        level.dimension().location(), abnormality.blockPosition()));
    }

    private static EntityBloodySmall spawnBloodySmall(ServerLevel level, BlockPos corridor) {
        EntityBloodySmall clown = ModEntities.bloody_small.get().create(level);
        if (clown == null) return null;

        BlockPos spawnPos = findBloodySmallSpawnPosition(level, clown, corridor, 4);
        if (spawnPos == null) {
            return null;
        }

        clown.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        clown.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        clown.setPersistenceRequired();
        boolean added = level.addFreshEntity(clown);
        if (added) {
            List<AbstractAbnormality> candidates = findCandidateAbnormalities(level);
            if (!candidates.isEmpty()) {
                clown.setTrackedAbnormality(candidates.get(level.getRandom().nextInt(candidates.size())));
            }
        }
        return added ? clown : null;
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

        return null;
    }

    private static boolean canPlaceBloodySmall(ServerLevel level, EntityBloodySmall clown, BlockPos pos) {
        if (pos == null || !FacilityAccessService.isPositionOpen(level.getServer(),
                level.dimension().location(), pos)) {
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

    private static void broadcastChance(MinecraftServer server, int chance, int dawnType, boolean capped) {
        if (server == null) return;
        ChatFormatting labelColor = getDawnLabelColor(dawnType);
        ChatFormatting chanceColor = getDawnChanceColor(dawnType);
        Component message = Component.translatable(getDawnLabelKey(dawnType))
                .withStyle(labelColor)
                .append(Component.translatable("message.lobotocraft.ordeal.chance_suffix", chance)
                        .withStyle(chanceColor));
        if (capped) {
            message = message.copy().append(Component.translatable(
                    "message.lobotocraft.ordeal.dawn_capped").withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        }
    }

    private static void broadcastMiddayChance(MinecraftServer server, int chance, int middayType, boolean capped) {
        if (server == null) return;
        Component message = Component.translatable(getMiddayLabelKey(middayType))
                .withStyle(getMiddayLabelColor(middayType))
                .append(Component.translatable("message.lobotocraft.ordeal.chance_suffix", chance)
                        .withStyle(getMiddayChanceColor(middayType)));
        if (capped) {
            message = message.copy().append(Component.translatable(
                    "message.lobotocraft.ordeal.midday_capped").withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        }
    }

    private static String getDawnLabelKey(int dawnType) {
        return switch (dawnType) {
            case OrdealData.GREEN_DAWN_TYPE -> "ordeal.lobotocraft.green_dawn.name";
            case OrdealData.VIOLET_DAWN_TYPE -> "ordeal.lobotocraft.violet_dawn.name";
            case OrdealData.AMBER_DAWN_TYPE -> "ordeal.lobotocraft.amber_dawn.name";
            default -> "ordeal.lobotocraft.crimson_dawn.name";
        };
    }

    private static String getMiddayLabelKey(int middayType) {
        return switch (middayType) {
            case OrdealData.CRIMSON_MIDDAY_TYPE -> "ordeal.lobotocraft.crimson_midday.name";
            case OrdealData.VIOLET_MIDDAY_TYPE -> "ordeal.lobotocraft.violet_midday.name";
            case OrdealData.GREEN_MIDDAY_TYPE -> "ordeal.lobotocraft.green_midday.name";
            default -> "ordeal.lobotocraft.indigo_midday.name";
        };
    }

    private static ChatFormatting getDawnLabelColor(int dawnType) {
        return switch (dawnType) {
            case OrdealData.GREEN_DAWN_TYPE -> ChatFormatting.GREEN;
            case OrdealData.VIOLET_DAWN_TYPE -> ChatFormatting.DARK_PURPLE;
            case OrdealData.AMBER_DAWN_TYPE -> ChatFormatting.GOLD;
            default -> ChatFormatting.DARK_RED;
        };
    }

    private static ChatFormatting getDawnChanceColor(int dawnType) {
        return switch (dawnType) {
            case OrdealData.GREEN_DAWN_TYPE -> ChatFormatting.GREEN;
            case OrdealData.VIOLET_DAWN_TYPE -> ChatFormatting.LIGHT_PURPLE;
            case OrdealData.AMBER_DAWN_TYPE -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    private static ChatFormatting getMiddayLabelColor(int middayType) {
        return switch (middayType) {
            case OrdealData.CRIMSON_MIDDAY_TYPE -> ChatFormatting.DARK_RED;
            case OrdealData.VIOLET_MIDDAY_TYPE -> ChatFormatting.DARK_PURPLE;
            case OrdealData.GREEN_MIDDAY_TYPE -> ChatFormatting.GREEN;
            default -> ChatFormatting.DARK_BLUE;
        };
    }

    private static ChatFormatting getMiddayChanceColor(int middayType) {
        return switch (middayType) {
            case OrdealData.CRIMSON_MIDDAY_TYPE -> ChatFormatting.RED;
            case OrdealData.VIOLET_MIDDAY_TYPE -> ChatFormatting.LIGHT_PURPLE;
            case OrdealData.GREEN_MIDDAY_TYPE -> ChatFormatting.GREEN;
            default -> ChatFormatting.AQUA;
        };
    }

    private static void showBloodDawnTitle(MinecraftServer server, Component top,
                                           Component middle, Component bottom) {
        sendOrdealTitle(server, top, middle, bottom, BLOOD_DAWN_COLOR);
    }

    /**
     * 发送自定义考验标题（替代原版 title）。
     *
     * @param top        顶部小字（例："血色的黎明"、"紫罗兰的黎明 90%"）
     * @param title      中间大标题（例："开始欢呼吧！"）
     * @param subtitle   底部小字（例：那句台词）
     * @param themeColor 主题色，横幅描边、左右斜纹、大标题都用它
     */
    public static void sendOrdealTitle(MinecraftServer server, String top, String title, String subtitle,
                                       int themeColor) {
        sendOrdealTitle(server,
                Component.literal(top == null ? "" : top),
                Component.literal(title == null ? "" : title),
                Component.literal(subtitle == null ? "" : subtitle),
                themeColor);
    }

    public static void sendOrdealTitle(MinecraftServer server, Component top, Component title,
                                       Component subtitle, int themeColor) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(player,
                    new OrdealTitlePacket(top, title, subtitle, themeColor,
                            TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
        }
    }

    private static void playGlobalSound(MinecraftServer server, net.minecraft.sounds.SoundEvent sound) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }
}
