package com.wzz.lobotocraft.event;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.*;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 异想体获取/生成途径。
 * 生成的异想体均持久化常驻;同类在局部范围内最多一只。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class AbnormalitySpawnEvent {

    // ============================================================
    //  右键触发:棘刺公交 / 波迪 / 被遗弃的杀人魔 / 深黯军团 / 老妇人 / 蕾蒂希娅 / 蜂后
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
        ItemStack stack = player.getMainHandItem();

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
            if (villager.isBaby()) {
                if (stack.is(Items.PINK_TULIP)) {
                    if (trySpawnArmyInBlack(serverLevel, player)) {
                        consumeHeldItem(player, stack);
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                    return;
                }
                if (stack.is(Items.EMERALD)) {
                    if (trySpawnLeticia(serverLevel, player)) {
                        consumeHeldItem(player, stack);
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                    return;
                }
            }

            if (stack.is(Items.WRITABLE_BOOK)) {
                if (trySpawnLadyFacingTheWall(serverLevel, player, villager)) {
                    consumeHeldItem(player, stack);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
                return;
            }

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

    private static boolean trySpawnArmyInBlack(ServerLevel level, Player player) {
        EntityArmyInBlack army = spawnNearbyIfAbsent(level, player, player.blockPosition(),
                ModEntities.army_in_black.get(), EntityArmyInBlack.class, 2,
                "§d粉色能融入人们的心");
        return army != null;
    }

    private static boolean trySpawnLeticia(ServerLevel level, Player player) {
        EntityLeticia leticia = spawnNearbyIfAbsent(level, player, player.blockPosition(),
                ModEntities.leticia.get(), EntityLeticia.class, 2,
                "§5她会与你分享她的朋友，还有她的礼物");
        return leticia != null;
    }

    private static boolean trySpawnLadyFacingTheWall(ServerLevel level, Player player, Villager villager) {
        BlockPos pos = villager.blockPosition();
        EntityLadyFacingTheWall lady = spawnNearbyIfAbsent(level, player, pos,
                ModEntities.the_lady_facing_the_wall.get(), EntityLadyFacingTheWall.class, 1,
                "§f她只能不断地诉说着自己的孤独");
        if (lady == null) {
            return false;
        }
        villager.discard();
        return true;
    }

    private static void consumeHeldItem(Player player, ItemStack stack) {
        if (!player.isCreative()) {
            stack.shrink(1);
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;
        if (!AbnormalitySpawnHelper.isOverworld(level) && !AbnormalitySpawnHelper.isCompany(level)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = event.getItemStack();

        if (AbnormalitySpawnHelper.isOverworld(level) && isBeeHive(state) && stack.getItem() instanceof PEBoxItem) {
            if (trySpawnQueenBeeFromHive(serverLevel, player, pos)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        if (state.is(ModBlocks.TOMBSTONE.get())) {
            if (stack.is(ItemTags.FLOWERS)) {
                if (trySpawnButterflyFuneral(serverLevel, player, pos)) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
                return;
            }
            if (stack.is(Items.BRUSH)) {
                if (trySpawnHelper(serverLevel, player, pos)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
                return;
            }
        }

        if (!AbnormalitySpawnHelper.isOverworld(level)) return;

        if (state.is(Blocks.NOTE_BLOCK) && hasNearbyWarden(serverLevel, pos)) {
            trySpawnBlueStarFromNoteBlock(serverLevel, player, pos);
            return;
        }

        if (state.is(Blocks.NOTE_BLOCK) && serverLevel.isNight()) {
            trySpawnFragmentFromNoteBlock(serverLevel, player, pos);
            return;
        }

        if (state.is(Blocks.JUKEBOX) && isMusicDisc(stack)
                && isRiverOrOceanBiome(level, player.blockPosition())) {
            startSkadiSongTimer(player, pos);
        }
    }

    private static boolean hasNearbyWarden(ServerLevel level, BlockPos noteBlockPos) {
        return !level.getEntitiesOfClass(Warden.class,
                new net.minecraft.world.phys.AABB(noteBlockPos).inflate(10.0D, 6.0D, 10.0D),
                Warden::isAlive).isEmpty();
    }

    private static void trySpawnBlueStarFromNoteBlock(ServerLevel level, Player player, BlockPos noteBlockPos) {
        if (level.random.nextFloat() >= 0.05f) return;
        EntityBlueStar blueStar = spawnNearbyIfAbsent(level, player, noteBlockPos,
                ModEntities.blue_star.get(), EntityBlueStar.class, 3,
                "§1许多人对它有近乎狂热的崇拜，没人知道为什么。");
        if (blueStar == null) {
            return;
        }
        level.playSound(null, noteBlockPos, ModSounds.BLUE_STAR_ATTACK.get(),
                SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    private static boolean isBeeHive(BlockState state) {
        return state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST);
    }

    private static boolean trySpawnQueenBeeFromHive(ServerLevel level, Player player, BlockPos hivePos) {
        if (!AbnormalitySpawnHelper.hasAnyPEBox(player)) {
            return false;
        }
        if (AbnormalitySpawnHelper.existsNearby(level, hivePos,
                AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, EntityQueenBee.class)) {
            player.displayClientMessage(Component.literal("§c附近已经存在「蜂后」"), true);
            return true;
        }
        if (!AbnormalitySpawnHelper.consumeAnyPEBox(player)) {
            return false;
        }

        level.destroyBlock(hivePos, true, player);
        AbnormalitySpawnHelper.spawnPersistent(level, ModEntities.queen_bee.get(), hivePos);
        player.displayClientMessage(Component.literal("蜂巢破裂，女王矗立在此处，身躯破碎不堪..."), false);
        return true;
    }

    private static boolean trySpawnButterflyFuneral(ServerLevel level, Player player, BlockPos tombstonePos) {
        EntityButterflyFuneral butterfly = spawnNearbyIfAbsent(level, player, tombstonePos,
                ModEntities.butterfly_funeral.get(), EntityButterflyFuneral.class, 3,
                "亡蝶葬仪来为员工送上救赎");
        return butterfly != null;
    }

    private static boolean trySpawnHelper(ServerLevel level, Player player, BlockPos tombstonePos) {
        EntityHelper helper = spawnNearbyIfAbsent(level, player, tombstonePos,
                ModEntities.helper.get(), EntityHelper.class, 3,
                "小帮手凝视着你，似乎希望能够提供帮助");
        return helper != null;
    }

    private static void trySpawnFragmentFromNoteBlock(ServerLevel level, Player player, BlockPos noteBlockPos) {
        if (level.random.nextFloat() >= 0.10f) return;
        spawnNearbyIfAbsent(level, player, noteBlockPos,
                ModEntities.fragment_of_the_universe.get(), EntityFragmentOfUniverse.class, 3,
                "夜晚的歌声，引来了宇宙碎片的注意");
    }

    private static boolean isMusicDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RecordItem;
    }

    private static final int SKADI_SONG_REQUIRED_TICKS = 10 * 20;
    private static final String SKADI_SONG_DUE_TICK_TAG = "lobotocraft_skadi_song_due_tick";
    private static final String SKADI_SONG_X_TAG = "lobotocraft_skadi_song_x";
    private static final String SKADI_SONG_Y_TAG = "lobotocraft_skadi_song_y";
    private static final String SKADI_SONG_Z_TAG = "lobotocraft_skadi_song_z";

    private static void startSkadiSongTimer(Player player, BlockPos jukeboxPos) {
        CompoundTag data = player.getPersistentData();
        data.putLong(SKADI_SONG_DUE_TICK_TAG, player.level().getGameTime() + SKADI_SONG_REQUIRED_TICKS);
        data.putInt(SKADI_SONG_X_TAG, jukeboxPos.getX());
        data.putInt(SKADI_SONG_Y_TAG, jukeboxPos.getY());
        data.putInt(SKADI_SONG_Z_TAG, jukeboxPos.getZ());
    }

    private static void handleSkadiSong(Player player, ServerLevel level) {
        CompoundTag data = player.getPersistentData();
        long dueTick = data.getLong(SKADI_SONG_DUE_TICK_TAG);
        if (dueTick <= 0 || level.getGameTime() < dueTick) return;

        BlockPos jukeboxPos = new BlockPos(
                data.getInt(SKADI_SONG_X_TAG),
                data.getInt(SKADI_SONG_Y_TAG),
                data.getInt(SKADI_SONG_Z_TAG));
        clearSkadiSongTimer(data);

        if (!level.getBlockState(jukeboxPos).is(Blocks.JUKEBOX)) return;
        if (!isRiverOrOceanBiome(level, jukeboxPos)) return;

        spawnNearbyIfAbsent(level, player, jukeboxPos,
                ModEntities.skadi_corrupted.get(), EntityDarkSkadi.class, 3,
                "悠扬的歌声，让她想起了身体过往的记忆");
    }

    private static void clearSkadiSongTimer(CompoundTag data) {
        data.remove(SKADI_SONG_DUE_TICK_TAG);
        data.remove(SKADI_SONG_X_TAG);
        data.remove(SKADI_SONG_Y_TAG);
        data.remove(SKADI_SONG_Z_TAG);
    }

    // ============================================================
    //  击杀触发:红舞鞋 / 破裂盔甲 / 冰雪女皇 / 血肉偶像 / 焦化少女
    // ============================================================

    private static final String CRUMBLING_ARMOR_KILLS_TAG = "lobotocraft_crumbling_armor_nether_kills";
    private static final int CRUMBLING_ARMOR_REQUIRED_KILLS = 10;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Level level = dead.level();
        if (level.isClientSide) return;
        DamageSource source = event.getSource();
        Entity killer = source != null ? source.getEntity() : null;
        Player responsiblePlayer = getResponsiblePlayer(source);

        if (responsiblePlayer != null && level.dimension() == Level.NETHER) {
            handleCrumblingArmorKill((ServerLevel) level, responsiblePlayer, dead.blockPosition());
        }

        boolean overworld = AbnormalitySpawnHelper.isOverworld(level);
        boolean company = AbnormalitySpawnHelper.isCompany(level);
        if (!overworld && !company) return;
        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos pos = dead.blockPosition();

        if (responsiblePlayer != null) {
            if (dead instanceof Pig) {
                handleWolfPigKill(serverLevel, responsiblePlayer, pos);
            }
            if (overworld && (dead instanceof Monster || dead instanceof Animal)
                    && AbnormalitySpawnHelper.isDarkForestBiome(level, pos)) {
                spawnNearbyIfAbsent(serverLevel, responsiblePlayer, pos,
                        ModEntities.punishing_bird.get(), EntityPunishingBird.class, 3,
                        "惩戒鸟会来惩罚破坏了森林的家伙");
            }
        }

        if (!overworld) return;

        // 红舞鞋:用斧头击杀一只带有生命恢复效果的小村民
        if (dead instanceof Villager villager && villager.isBaby()
                && dead.hasEffect(MobEffects.REGENERATION)
                && killer instanceof Player player
                && player.getMainHandItem().getItem() instanceof AxeItem) {
            spawnNearbyIfAbsent(serverLevel, player, pos,
                    ModEntities.red_shoes.get(), EntityRedShoes.class, 1,
                    "§c你砍下了她的脚，取下了那双邪恶的红舞鞋");
            return;
        }

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

    private static void handleCrumblingArmorKill(ServerLevel level, Player player, BlockPos killPos) {
        CompoundTag data = player.getPersistentData();
        if (!player.hasEffect(MobEffects.FIRE_RESISTANCE) || !hasEmptyArmorSlots(player)) {
            data.remove(CRUMBLING_ARMOR_KILLS_TAG);
            return;
        }

        int kills = data.getInt(CRUMBLING_ARMOR_KILLS_TAG) + 1;
        if (kills < CRUMBLING_ARMOR_REQUIRED_KILLS) {
            data.putInt(CRUMBLING_ARMOR_KILLS_TAG, kills);
            player.displayClientMessage(Component.literal("§c不惧死亡的勇气正在凝结... (" + kills + "/"
                    + CRUMBLING_ARMOR_REQUIRED_KILLS + ")"), true);
            return;
        }

        data.remove(CRUMBLING_ARMOR_KILLS_TAG);
        spawnNearbyIfAbsent(level, player, killPos,
                ModEntities.crumbling_armor.get(), EntityCrumblingArmor.class, 3,
                "§c不惧死亡的勇气将会得到它的祝福");
    }

    private static boolean hasEmptyArmorSlots(Player player) {
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Player getResponsiblePlayer(DamageSource source) {
        if (source == null) return null;
        Entity causing = source.getEntity();
        if (causing instanceof Player player) {
            return player;
        }
        if (causing instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player owner) {
            return owner;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) {
            return player;
        }
        if (direct instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
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

    private static final String WOLF_HAY_X_TAG = "lobotocraft_wolf_hay_x";
    private static final String WOLF_HAY_Y_TAG = "lobotocraft_wolf_hay_y";
    private static final String WOLF_HAY_Z_TAG = "lobotocraft_wolf_hay_z";
    private static final String WOLF_PIG_KILLS_TAG = "lobotocraft_wolf_pig_kills";
    private static final int WOLF_HAY_HORIZONTAL_RANGE = 4;
    private static final int WOLF_HAY_VERTICAL_RANGE = 3;
    private static final int WOLF_HAY_MATCH_RADIUS_SQR = 64;

    private static void handleWolfPigKill(ServerLevel level, Player player, BlockPos pigPos) {
        BlockPos hayPos = findNearbyHayBlock(level, pigPos);
        if (hayPos == null) return;

        CompoundTag data = player.getPersistentData();
        int kills = data.getInt(WOLF_PIG_KILLS_TAG);
        if (kills > 0 && !isSameWolfHayArea(data, hayPos)) {
            kills = 0;
        }

        kills++;
        data.putInt(WOLF_HAY_X_TAG, hayPos.getX());
        data.putInt(WOLF_HAY_Y_TAG, hayPos.getY());
        data.putInt(WOLF_HAY_Z_TAG, hayPos.getZ());
        data.putInt(WOLF_PIG_KILLS_TAG, kills);

        if (kills < 3) {
            player.displayClientMessage(Component.literal("§7干草旁的气味变得更浓了... §c(" + kills + "/3)"), true);
            return;
        }
        clearWolfPigKillCounter(data);

        EntityBigBadWolf wolf = spawnNearbyIfAbsent(level, player, hayPos,
                ModEntities.bigbadwolf.get(), EntityBigBadWolf.class, 2,
                "正如童话中的一样，来了一只又大又可能很坏的狼");
        if (wolf == null) return;

        EntityRedHoodMercenary redhat = spawnNearbyIfAbsent(level, player, wolf.blockPosition(),
                ModEntities.redhat_mercenary.get(), EntityRedHoodMercenary.class, 2,
                "在这只狼在的地方，就会有她的身影");
    }

    private static boolean isSameWolfHayArea(CompoundTag data, BlockPos hayPos) {
        BlockPos previous = new BlockPos(
                data.getInt(WOLF_HAY_X_TAG),
                data.getInt(WOLF_HAY_Y_TAG),
                data.getInt(WOLF_HAY_Z_TAG));
        return previous.distSqr(hayPos) <= WOLF_HAY_MATCH_RADIUS_SQR;
    }

    private static void clearWolfPigKillCounter(CompoundTag data) {
        data.remove(WOLF_HAY_X_TAG);
        data.remove(WOLF_HAY_Y_TAG);
        data.remove(WOLF_HAY_Z_TAG);
        data.remove(WOLF_PIG_KILLS_TAG);
    }

    private static BlockPos findNearbyHayBlock(Level level, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -WOLF_HAY_HORIZONTAL_RANGE; dx <= WOLF_HAY_HORIZONTAL_RANGE; dx++) {
            for (int dz = -WOLF_HAY_HORIZONTAL_RANGE; dz <= WOLF_HAY_HORIZONTAL_RANGE; dz++) {
                for (int dy = -WOLF_HAY_VERTICAL_RANGE; dy <= WOLF_HAY_VERTICAL_RANGE; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(Blocks.HAY_BLOCK)) continue;
                    double distance = pos.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!AbnormalitySpawnHelper.isOverworld(level)) return;
        if (!event.getPlacedBlock().is(Blocks.TNT)) return;
        if (!AbnormalitySpawnHelper.isDarkForestBiome(level, player.blockPosition())) return;

        Vec3 look = player.getLookAngle();
        BlockPos behind = BlockPos.containing(player.getX() - look.x * 3.0D,
                player.getY(), player.getZ() - look.z * 3.0D);
        spawnNearbyIfAbsent(level, player, behind,
                ModEntities.approval_bird.get(), EntityApprovalBird.class, 2,
                "肆意破坏森林的人，将会招来正义的裁决");
    }

    // ============================================================
    //  Tick 触发:银河之子(观星) / 大鸟(观察灯笼) / 斯卡蒂(唱片机) / 废弃矿井随机刷新
    // ============================================================

    // 观星所需的持续 tick 数(5秒)
    private static final int STARGAZE_REQUIRED_TICKS = 100;
    private static final String STARGAZE_TAG = "lobotocraft_stargaze_ticks";

    // 矿井检测节流:每隔多少 tick 检测一次
    private static final int STRUCTURE_CHECK_INTERVAL = 100;
    // 局部刷新去重半径
    private static final double STRUCTURE_DEDUP_RADIUS = 96.0;
    // 每次检测的随机生成概率
    private static final float MINESHAFT_SPAWN_CHANCE = 0.15f;

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
        handleLargeBirdObservation(player, serverLevel);
        handleSkadiSong(player, serverLevel);

        // ---- 结构相关检测(节流) ----
        if (player.tickCount % STRUCTURE_CHECK_INTERVAL == 0) {
            BlockPos pos = player.blockPosition();

            // 8. 快乐泰迪 / 9. 我们可以改变一切:废弃矿井结构附近刷新(各最多一只)
            if (isInMineshaft(serverLevel, pos)) {
                if (serverLevel.random.nextFloat() < MINESHAFT_SPAWN_CHANCE) {
                    trySpawnMineshaftAbnormality(serverLevel, pos);
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

    private static final int LARGE_BIRD_OBSERVE_REQUIRED_TICKS = 10 * 20;
    private static final String LARGE_BIRD_OBSERVE_TICKS_TAG = "lobotocraft_large_bird_observe_ticks";
    private static final String LARGE_BIRD_LANTERN_X_TAG = "lobotocraft_large_bird_lantern_x";
    private static final String LARGE_BIRD_LANTERN_Y_TAG = "lobotocraft_large_bird_lantern_y";
    private static final String LARGE_BIRD_LANTERN_Z_TAG = "lobotocraft_large_bird_lantern_z";

    private static void handleLargeBirdObservation(Player player, ServerLevel level) {
        CompoundTag data = player.getPersistentData();
        boolean canObserve = player.isUsingItem()
                && player.getUseItem().is(Items.SPYGLASS)
                && level.isNight()
                && AbnormalitySpawnHelper.isDarkForestBiome(level, player.blockPosition());
        if (!canObserve) {
            clearLargeBirdObservation(data);
            return;
        }

        BlockPos lanternPos = getLookedAtLantern(player, 48.0D);
        if (lanternPos == null) {
            clearLargeBirdObservation(data);
            return;
        }

        int ticks = data.getInt(LARGE_BIRD_OBSERVE_TICKS_TAG);
        if (data.getInt(LARGE_BIRD_LANTERN_X_TAG) != lanternPos.getX()
                || data.getInt(LARGE_BIRD_LANTERN_Y_TAG) != lanternPos.getY()
                || data.getInt(LARGE_BIRD_LANTERN_Z_TAG) != lanternPos.getZ()) {
            ticks = 0;
        }

        ticks++;
        data.putInt(LARGE_BIRD_OBSERVE_TICKS_TAG, ticks);
        data.putInt(LARGE_BIRD_LANTERN_X_TAG, lanternPos.getX());
        data.putInt(LARGE_BIRD_LANTERN_Y_TAG, lanternPos.getY());
        data.putInt(LARGE_BIRD_LANTERN_Z_TAG, lanternPos.getZ());

        if (ticks < LARGE_BIRD_OBSERVE_REQUIRED_TICKS) return;
        clearLargeBirdObservation(data);
        if (!isLantern(level.getBlockState(lanternPos))) return;
        level.removeBlock(lanternPos, false);
        spawnNearbyIfAbsent(level, player, lanternPos,
                ModEntities.large_bird.get(), EntityLargeBird.class, 3,
                "在黑暗的森林里渴求光明的人得到了回应");
    }

    private static BlockPos getLookedAtLantern(Player player, double range) {
        HitResult hit = player.pick(range, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        return isLantern(player.level().getBlockState(pos)) ? pos : null;
    }

    private static boolean isLantern(BlockState state) {
        return state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN);
    }

    private static void clearLargeBirdObservation(CompoundTag data) {
        data.remove(LARGE_BIRD_OBSERVE_TICKS_TAG);
        data.remove(LARGE_BIRD_LANTERN_X_TAG);
        data.remove(LARGE_BIRD_LANTERN_Y_TAG);
        data.remove(LARGE_BIRD_LANTERN_Z_TAG);
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

    /** 在玩家附近偏移一段距离寻找安全地面,避免直接生成在玩家脸上 */
    private static BlockPos findGroundNear(ServerLevel level, BlockPos center) {
        return findGroundNear(level, center, 6);
    }

    private static BlockPos findGroundNear(ServerLevel level, BlockPos center, int radius) {
        int ox = level.random.nextInt(radius * 2 + 1) - radius;
        int oz = level.random.nextInt(radius * 2 + 1) - radius;
        BlockPos target = center.offset(ox, 0, oz);
        BlockPos safe = com.wzz.lobotocraft.util.EntityUtil.findSafeGroundPosition(level, target, 4);
        return safe != null ? safe : target;
    }

    private static <T extends AbstractAbnormality> T spawnNearbyIfAbsent(ServerLevel level, Player player,
                                                                         BlockPos origin, EntityType<T> type,
                                                                         Class<T> clazz, int radius,
                                                                         String successMessage) {
        if (AbnormalitySpawnHelper.existsNearby(level, origin,
                AbnormalitySpawnHelper.DEFAULT_DEDUP_RADIUS, clazz)) {
            return null;
        }
        T entity = AbnormalitySpawnHelper.spawnPersistent(level, type,
                findGroundNear(level, origin, radius), MobSpawnType.EVENT);
        if (entity != null) {
            player.displayClientMessage(Component.literal(successMessage), false);
        }
        return entity;
    }

    private static boolean isRiverOrOceanBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(Biomes.RIVER)
                || biome.is(Biomes.FROZEN_RIVER)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.is(Biomes.WARM_OCEAN)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN);
    }
}
