package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.entity.abnormality.EntityDarkSkadi;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "深蓝色正午——大群的意志" 考验事件。
 * 玩家收容浊心斯卡蒂后,游戏时间每 24 分钟有 25% 概率触发:
 * 屏幕显示标题,公司维度内随机选 5 个出逃方块,每个方块生成 6 种海嗣中随机 3 种。
 * 精英怪概率 30%,普通怪概率 65%。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class BlueMiddayEvent {

    private static final int CHECK_INTERVAL = 24 * 60 * 20; // 24分钟(tick)
    private static final float TRIGGER_CHANCE = 0.25f;
    private static int tickCounter = 0;

    // 当前是否有考验正在进行(供伊莎玛拉机制2判断:出逃时结束考验、吸收海嗣)
    private static boolean trialActive = false;

    public static boolean isTrialActive() {
        return trialActive;
    }

    /** 由伊莎玛拉出逃调用:结束考验 */
    public static void endTrial() {
        trialActive = false;
    }

    /** 是否为海嗣实体类型 */
    public static boolean isSeaborn(net.minecraft.world.entity.Entity e) {
        return e instanceof com.wzz.lobotocraft.entity.seaborn.EntityBasinSeaborn;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // 仅在存在浊心斯卡蒂(已收容)的维度触发
        for (ServerLevel level : server.getAllLevels()) {
            boolean hasSkadi = !level.getEntitiesOfClass(EntityDarkSkadi.class,
                    new net.minecraft.world.phys.AABB(
                            -30000000, level.getMinBuildHeight(), -30000000,
                            30000000, level.getMaxBuildHeight(), 30000000),
                    e -> e.isAlive()).isEmpty();
            if (!hasSkadi) continue;
            if (level.getRandom().nextFloat() < TRIGGER_CHANCE) {
                triggerTrial(level);
            }
        }
    }

    private static void triggerTrial(ServerLevel level) {
        trialActive = true;
        // 屏幕标题
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("深蓝色正午").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("大群的意志").withStyle(ChatFormatting.BLUE)));
        }

        // 随机选取5个出逃方块
        List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
        if (escapeBlocks.isEmpty()) return;
        Collections.shuffle(escapeBlocks, new java.util.Random(level.getRandom().nextLong()));
        int count = Math.min(5, escapeBlocks.size());

        for (int i = 0; i < count; i++) {
            BlockPos pos = escapeBlocks.get(i);
            // 每个方块生成6种中随机3种
            List<EntityType<? extends Mob>> pool = new ArrayList<>(POOL);
            Collections.shuffle(pool, new java.util.Random(level.getRandom().nextLong()));
            for (int j = 0; j < 3; j++) {
                EntityType<? extends Mob> type = pickByRarity(level, pool.get(j));
                spawnSeaborn(level, type, pos);
            }
        }
    }

    // 6种海嗣(3普通 + 3精英)
    private static final List<EntityType<? extends Mob>> NORMAL = new ArrayList<>();
    private static final List<EntityType<? extends Mob>> ELITE = new ArrayList<>();
    private static final List<EntityType<? extends Mob>> POOL = new ArrayList<>();

    private static void ensurePools() {
        if (!POOL.isEmpty()) return;
        NORMAL.add(ModEntities.shell_sea_runner.get());
        NORMAL.add(ModEntities.deepsea_slider.get());
        NORMAL.add(ModEntities.ridgesea_spitter.get());
        ELITE.add(ModEntities.primalsea_piercer.get());
        ELITE.add(ModEntities.nucleic_maleficent.get());
        ELITE.add(ModEntities.basinsea_reaper.get());
        POOL.addAll(NORMAL);
        POOL.addAll(ELITE);
    }

    /**
     * 按稀有度决定实际生成类型:30%精英、65%普通(其余5%不替换,沿用抽到的类型)。
     */
    private static EntityType<? extends Mob> pickByRarity(ServerLevel level, EntityType<? extends Mob> fallback) {
        ensurePools();
        float roll = level.getRandom().nextFloat();
        if (roll < 0.30f) {
            return ELITE.get(level.getRandom().nextInt(ELITE.size()));
        } else if (roll < 0.95f) {
            return NORMAL.get(level.getRandom().nextInt(NORMAL.size()));
        }
        return fallback;
    }

    private static void spawnSeaborn(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
        Mob mob = type.create(level);
        if (mob == null) return;
        mob.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(mob);
    }
}
