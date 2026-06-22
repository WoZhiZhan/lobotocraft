package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.item.PEBoxItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 异想体生成辅助工具。
 * 统一处理:消耗任意异想体能源(PE-BOX)、在主世界生成并持久化异想体、
 * 局部范围去重计数、地形(生物群系)判断。
 */
public class AbnormalitySpawnHelper {

    /** 默认局部去重半径(方块) */
    public static final double DEFAULT_DEDUP_RADIUS = 64.0;

    // ===================== 维度判断 =====================

    /** 是否处于主世界 */
    public static boolean isOverworld(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    /** 是否处于公司维度 */
    public static boolean isCompany(Level level) {
        return level.dimension() == ModDimensions.LOBOTO_KEY;
    }

    // ===================== PE-BOX(异想体能源) =====================

    /** 玩家是否持有任意一个异想体能源(PE-BOX) */
    public static boolean hasAnyPEBox(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PEBoxItem) {
                return true;
            }
        }
        return false;
    }

    /**
     * 消耗任意一个异想体能源(PE-BOX),不限异想体编号。
     * @return 成功消耗返回 true
     */
    public static boolean consumeAnyPEBox(Player player) {
        if (player.isCreative()) return true;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PEBoxItem) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ===================== 局部去重计数 =====================

    /**
     * 统计某坐标附近 radius 范围内指定类型异想体的数量。
     */
    public static <T extends AbstractAbnormality> int countNearby(
            ServerLevel level, BlockPos center, double radius, Class<T> clazz) {
        AABB box = new AABB(center).inflate(radius);
        List<T> list = level.getEntitiesOfClass(clazz, box, e -> e.isAlive());
        return list.size();
    }

    /**
     * 局部范围内是否已存在该类型异想体(用于"每个区块/局部最多一只")。
     */
    public static <T extends AbstractAbnormality> boolean existsNearby(
            ServerLevel level, BlockPos center, double radius, Class<T> clazz) {
        return countNearby(level, center, radius, clazz) > 0;
    }

    // ===================== 生成并持久化 =====================

    /**
     * 在指定位置生成异想体并设为持久化(常驻、可被收容)。
     * @return 生成的实体,失败返回 null
     */
    public static <T extends AbstractAbnormality> T spawnPersistent(
            ServerLevel level, EntityType<T> type, BlockPos pos) {
        return spawnPersistent(level, type, pos, MobSpawnType.EVENT);
    }

    /**
     * 在指定位置附近寻找安全落点后生成异想体并设为持久化。
     * @return 生成的实体,失败返回 null
     */
    public static <T extends AbstractAbnormality> T spawnPersistent(
            ServerLevel level, EntityType<T> type, BlockPos pos, MobSpawnType spawnType) {
        T entity = type.create(level);
        if (entity == null) return null;

        BlockPos spawnPos = findSafeSpawnPosition(level, entity, pos);
        entity.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                level.random.nextFloat() * 360f, 0f);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                spawnType, null, null);
        // 持久化:不因离玩家过远而消失
        entity.setPersistenceRequired();
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static <T extends AbstractAbnormality> BlockPos findSafeSpawnPosition(
            ServerLevel level, T entity, BlockPos pos) {
        BlockPos direct = findSpawnGround(level, pos, 0);
        if (canSpawnAt(level, entity, direct)) {
            return direct;
        }

        int radius = Math.max(4,
                (int) Math.ceil(Math.max(entity.getBbWidth(), entity.getBbHeight())) + 2);
        for (int attempt = 0; attempt < 32; attempt++) {
            int offsetX = level.random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = findSpawnGround(level, pos.offset(offsetX, 0, offsetZ), 0);
            if (canSpawnAt(level, entity, candidate)) {
                return candidate;
            }
        }

        BlockPos fallback = findSpawnGround(level, pos, radius);
        return fallback != null ? fallback : pos.above();
    }

    private static BlockPos findSpawnGround(ServerLevel level, BlockPos pos, int horizontalRange) {
        if (isCompany(level)) {
            return EntityUtil.findSafeGroundPositionInCompany(level, pos, horizontalRange);
        }
        return EntityUtil.findSafeGroundPosition(level, pos, horizontalRange);
    }

    private static boolean canSpawnAt(ServerLevel level, Entity entity, BlockPos pos) {
        if (pos == null) return false;
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                entity.getYRot(), entity.getXRot());
        return level.noCollision(entity);
    }

    // ===================== 地形(生物群系)判断 =====================

    /**
     * 是否为冰原/冰刺类寒冷地形(用于冰雪女皇)。
     */
    public static boolean isFrozenBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(Biomes.ICE_SPIKES)
                || biome.is(Biomes.SNOWY_PLAINS)
                || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.SNOWY_SLOPES)
                || biome.is(Biomes.SNOWY_BEACH)
                || biome.is(Biomes.FROZEN_RIVER)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.FROZEN_PEAKS)
                || biome.is(Biomes.GROVE)
                || biome.value().coldEnoughToSnow(pos);
    }

    /**
     * 是否为黑森林(原版"黑森林"=dark_forest)地形(用于三鸟)。
     */
    public static boolean isDarkForestBiome(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(Biomes.DARK_FOREST);
    }
}
