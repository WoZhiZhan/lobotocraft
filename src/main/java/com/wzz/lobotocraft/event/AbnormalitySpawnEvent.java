package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.*;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 异想体获取/生成途径。
 * 仅在主世界生效;生成的异想体均持久化常驻;同类在局部范围内最多一只。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class AbnormalitySpawnEvent {

    // ============================================================
    //  右键触发:棘刺公交 / 波迪 / 被遗弃的杀人魔
    // ============================================================

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;
        if (!AbnormalitySpawnHelper.isOverworld(level)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Entity target = event.getTarget();
        ServerLevel serverLevel = (ServerLevel) level;

        // 6. 被遗弃的杀人魔:对卫道士使用任意异想体能源(消耗一个)
        if (target instanceof Vindicator vindicator) {
            if (AbnormalitySpawnHelper.hasAnyPEBox(player)) {
                BlockPos pos = vindicator.blockPosition();
                if (AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                        AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityAbandonedMurderer.class)) {
                    player.displayClientMessage(Component.literal("§c附近已经存在「被遗弃的杀人魔」"), true);
                    event.setCanceled(true);
                    return;
                }
                if (AbnormalitySpawnHelper.consumeAnyPEBox(player)) {
                    vindicator.discard();
                    AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                            ModEntities.abandoned_murderer.get(), pos);
                    player.displayClientMessage(Component.literal("§5「被遗弃的杀人魔」出现了……"), false);
                    event.setCanceled(true);
                }
            }
            return;
        }

        // 2. 波迪:对狼使用任意异想体能源(消耗一个)
        if (target instanceof Wolf wolf) {
            if (AbnormalitySpawnHelper.hasAnyPEBox(player)) {
                BlockPos pos = wolf.blockPosition();
                if (AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                        AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityPpodae.class)) {
                    player.displayClientMessage(Component.literal("§c附近已经存在「波迪」"), true);
                    event.setCanceled(true);
                    return;
                }
                if (AbnormalitySpawnHelper.consumeAnyPEBox(player)) {
                    wolf.discard();
                    AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                            ModEntities.ppodae.get(), pos);
                    player.displayClientMessage(Component.literal("§5「波迪」兴奋地跑了出来……"), false);
                    event.setCanceled(true);
                }
            }
            return;
        }

        // 1. 棘刺公交:对站在仙人掌上(或仙人掌4x4范围内)的村民使用任意异想体能源(消耗一个)
        if (target instanceof Villager villager) {
            if (AbnormalitySpawnHelper.hasAnyPEBox(player) && isNearCactus(level, villager.blockPosition())) {
                BlockPos pos = villager.blockPosition();
                if (AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                        AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityThornBus.class)) {
                    player.displayClientMessage(Component.literal("§c附近已经存在「棘刺公交」"), true);
                    event.setCanceled(true);
                    return;
                }
                if (AbnormalitySpawnHelper.consumeAnyPEBox(player)) {
                    villager.discard();
                    AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                            ModEntities.thorn_bus.get(), pos);
                    player.displayClientMessage(Component.literal("§5「棘刺公交」出现了……"), false);
                    event.setCanceled(true);
                }
            }
        }
    }

    /** 检查坐标是否在仙人掌上,或周围 4x4 范围内有仙人掌 */
    private static boolean isNearCactus(Level level, BlockPos center) {
        // 脚下/所在方块
        if (level.getBlockState(center.below()).is(Blocks.CACTUS)
                || level.getBlockState(center).is(Blocks.CACTUS)) {
            return true;
        }
        // 周围 4x4 水平范围(±2),上下各 1 格
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getBlockState(center.offset(dx, dy, dz)).is(Blocks.CACTUS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ============================================================
    //  击杀触发:冰雪女皇 / 血肉偶像 / 焦化少女
    // ============================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Level level = dead.level();
        if (level.isClientSide) return;
        if (!AbnormalitySpawnHelper.isOverworld(level)) return;
        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos pos = dead.blockPosition();
        DamageSource source = event.getSource();
        Entity killer = source != null ? source.getEntity() : null;

        // 3. 冰雪女皇:在冰原/冰刺地形,击杀一只带有近战武器的敌对生物
        if (dead instanceof Monster monster && isArmedMeleeMob(monster)
                && AbnormalitySpawnHelper.isFrozenBiome(level, pos)) {
            if (!AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                    AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntitySnowQueen.class)) {
                AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                        ModEntities.snowqueen.get(), pos);
                announce(killer, "§b「冰雪女皇」在严寒中苏醒了……");
            }
            return;
        }

        // 11. 焦化少女:使一只燃烧的村民被爆炸伤害击杀后生成
        if (dead instanceof Villager && dead.isOnFire() && source != null && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            if (!AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                    AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityFourthMatchFlame.class)) {
                AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                        ModEntities.fourth_match_flame.get(), pos);
                announce(killer, "§6「焦化少女」从灰烬中诞生……");
            }
            return;
        }

        // 10. 血肉偶像:由玩家在村庄教堂附近击杀一只村民后
        if (dead instanceof Villager && killer instanceof Player && isNearChurch(level, pos)) {
            if (!AbnormalitySpawnHelper.existsNearby(serverLevel, pos,
                    AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityMeatIdol.class)) {
                AbnormalitySpawnHelper.spawnPersistent(serverLevel,
                        ModEntities.meat_idol.get(), pos);
                announce(killer, "§4「血肉偶像」在祭坛旁显现……");
            }
        }
    }

    /** 是否为手持近战武器的敌对生物(包括自带武器的卫道士等) */
    private static boolean isArmedMeleeMob(Monster monster) {
        // 自带武器的非法村民(卫道士等)
        if (monster instanceof AbstractIllager) {
            return true;
        }
        // 主手持有近战武器(剑/斧)
        ItemStack main = monster.getMainHandItem();
        return main.getItem() instanceof net.minecraft.world.item.SwordItem
                || main.getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    /** 村庄教堂附近(以酿造台作为教堂特征方块,周围范围内存在即可) */
    private static boolean isNearChurch(Level level, BlockPos center) {
        int r = 8;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    if (level.getBlockState(center.offset(dx, dy, dz)).is(Blocks.BREWING_STAND)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void announce(Entity killer, String msg) {
        if (killer instanceof Player player) {
            player.displayClientMessage(Component.literal(msg), false);
        }
    }

    // ============================================================
    //  Tick 触发:银河之子(观星) / 快乐泰迪 & 我们可以改变一切(废弃矿井) / 三鸟(黑森林)
    // ============================================================

    // 观星所需的持续 tick 数(5秒)
    private static final int STARGAZE_REQUIRED_TICKS = 100;
    private static final String STARGAZE_TAG = "lobotocraft_stargaze_ticks";

    // 矿井 / 黑森林检测节流:每隔多少 tick 检测一次
    private static final int STRUCTURE_CHECK_INTERVAL = 100;
    // 局部刷新去重半径
    private static final double STRUCTURE_DEDUP_RADIUS = 96.0;
    // 每次检测的随机生成概率
    private static final float MINESHAFT_SPAWN_CHANCE = 0.15f;
    private static final float DARK_FOREST_SPAWN_CHANCE = 0.12f;

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;
        if (!AbnormalitySpawnHelper.isOverworld(level)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ---- 7. 银河之子:使用望远镜观察夜空 5 秒 ----
        handleStargaze(player, serverLevel);

        // ---- 结构相关检测(节流) ----
        if (player.tickCount % STRUCTURE_CHECK_INTERVAL == 0) {
            BlockPos pos = player.blockPosition();

            // 8. 快乐泰迪 / 9. 我们可以改变一切:废弃矿井结构附近刷新(各最多一只)
            if (isInMineshaft(serverLevel, pos)) {
                if (serverLevel.random.nextFloat() < MINESHAFT_SPAWN_CHANCE) {
                    trySpawnMineshaftAbnormality(serverLevel, pos);
                }
            }

            // 12/13/14. 大鸟 / 惩戒鸟 / 审判鸟:黑森林地形刷新(各最多一只)
            if (AbnormalitySpawnHelper.isDarkForestBiome(level, pos)) {
                if (serverLevel.random.nextFloat() < DARK_FOREST_SPAWN_CHANCE) {
                    trySpawnDarkForestBird(serverLevel, pos);
                }
            }
        }
    }

    private static void handleStargaze(Player player, ServerLevel level) {
        boolean usingSpyglass = player.isUsingItem()
                && (player.getUseItem().is(Items.SPYGLASS));
        boolean nightSky = level.isNight()
                && level.canSeeSky(player.blockPosition().above())
                && player.getXRot() < -45f; // 视角朝上看天

        if (usingSpyglass && nightSky) {
            int ticks = player.getPersistentData().getInt(STARGAZE_TAG) + 1;
            player.getPersistentData().putInt(STARGAZE_TAG, ticks);
            if (ticks >= STARGAZE_REQUIRED_TICKS) {
                player.getPersistentData().putInt(STARGAZE_TAG, 0);
                BlockPos pos = player.blockPosition();
                if (!AbnormalitySpawnHelper.existsNearby(level, pos,
                        AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityChildrenGalaxy.class)) {
                    BlockPos spawnPos = findGroundNear(level, pos);
                    AbnormalitySpawnHelper.spawnPersistent(level,
                            ModEntities.children_galaxy.get(), spawnPos);
                    player.displayClientMessage(Component.literal("§9你凝视的星空中,「银河之子」回望了你……"), false);
                }
            }
        } else {
            if (player.getPersistentData().getInt(STARGAZE_TAG) != 0) {
                player.getPersistentData().putInt(STARGAZE_TAG, 0);
            }
        }
    }

    /** 玩家所在区块是否处于废弃矿井结构内 */
    private static boolean isInMineshaft(ServerLevel level, BlockPos pos) {
        return level.structureManager()
                .getStructureWithPieceAt(pos, net.minecraft.world.level.levelgen.structure.BuiltinStructures.MINESHAFT)
                .isValid()
                || level.structureManager()
                .getStructureWithPieceAt(pos, net.minecraft.world.level.levelgen.structure.BuiltinStructures.MINESHAFT_MESA)
                .isValid();
    }

    /** 废弃矿井:在快乐泰迪与"我们可以改变一切"之间随机生成一只(各自局部去重) */
    private static void trySpawnMineshaftAbnormality(ServerLevel level, BlockPos pos) {
        boolean spawnTeddy = level.random.nextBoolean();
        BlockPos spawnPos = findGroundNear(level, pos);
        if (spawnTeddy) {
            if (!AbnormalitySpawnHelper.existsNearby(level, pos, STRUCTURE_DEDUP_RADIUS, EntityHappyTeddy.class)) {
                AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.happy_teddy.get(), spawnPos);
            }
        } else {
            if (!AbnormalitySpawnHelper.existsNearby(level, pos, STRUCTURE_DEDUP_RADIUS, EntityIronMaiden.class)) {
                AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.iron_maiden.get(), spawnPos);
            }
        }
    }

    /** 黑森林:在大鸟/惩戒鸟/审判鸟之间随机生成一只(各自局部去重) */
    private static void trySpawnDarkForestBird(ServerLevel level, BlockPos pos) {
        BlockPos spawnPos = findGroundNear(level, pos);
        int start = level.random.nextInt(3);
        for (int i = 0; i < 3; i++) {
            switch ((start + i) % 3) {
                case 0 -> {
                    if (!AbnormalitySpawnHelper.existsNearby(level, pos, STRUCTURE_DEDUP_RADIUS, EntityLargeBird.class)) {
                        AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.large_bird.get(), spawnPos);
                        return;
                    }
                }
                case 1 -> {
                    if (!AbnormalitySpawnHelper.existsNearby(level, pos, STRUCTURE_DEDUP_RADIUS, EntityPunishingBird.class)) {
                        AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.punishing_bird.get(), spawnPos);
                        return;
                    }
                }
                default -> {
                    if (!AbnormalitySpawnHelper.existsNearby(level, pos, STRUCTURE_DEDUP_RADIUS, EntityApprovalBird.class)) {
                        AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.approval_bird.get(), spawnPos);
                        return;
                    }
                }
            }
        }
    }

    /** 在玩家附近偏移一段距离寻找安全地面,避免直接生成在玩家脸上 */
    private static BlockPos findGroundNear(ServerLevel level, BlockPos center) {
        int ox = level.random.nextInt(13) - 6;
        int oz = level.random.nextInt(13) - 6;
        BlockPos target = center.offset(ox, 0, oz);
        BlockPos safe = com.wzz.lobotocraft.util.EntityUtil.findSafeGroundPosition(level, target, 4);
        return safe != null ? safe : target;
    }
}
