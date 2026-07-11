package com.wzz.lobotocraft.util;

import com.mojang.datafixers.util.Pair;
import com.wzz.lobotocraft.logger.ModLogger;
import com.wzz.lobotocraft.world.LobotoTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.wzz.lobotocraft.util.MathUtil.convertToInteger;

public class EntityUtil {
    
    /**
     * 清除实体的受伤时间
     */
    public static void clearHurtTime(LivingEntity entity) {
        clearHurtTime(entity, null);
    }

    public static void clearHurtTime(LivingEntity entity, Runnable runnable) {
        if (entity == null) return;
        entity.hurtTime = 0;
        entity.hurtDuration = 0;
        entity.hurtMarked = false;
        entity.invulnerableTime = 0;
        if (runnable != null) {
            if (!entity.level.isClientSide && entity.getServer() != null) {
                entity.getServer().execute(runnable);
            }
        }
    }
    
    /**
     * 传送玩家到指定维度和位置
     */
    public static void teleportPlayer(ServerPlayer player, ResourceKey<Level> dimension, BlockPos position) {
        if (player == null || dimension == null || position == null) {
            ModLogger.error("传送参数不能为空！");
            return;
        }
        
        MinecraftServer server = player.getServer();
        if (server == null) {
            ModLogger.error("服务器实例为空！");
            return;
        }
        
        ServerLevel targetLevel = server.getLevel(dimension);
        if (targetLevel == null) {
            ModLogger.error("目标维度不存在: " + dimension.location());
            return;
        }
        
        player.changeDimension(targetLevel, new LobotoTeleporter(position));
    }

    public static List<BlockEntity> findBlockEntities(Level level) {
        List<BlockEntity> result = new ArrayList<>();
        for (TickingBlockEntity ticker : level.blockEntityTickers) {
            BlockPos pos = ticker.getPos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                result.add(blockEntity);
            }
        }
        return result;
    }

    /**
     * 智能查找方块实体：优先在Y轴范围内搜索，找不到则返回最近的那个
     * @param level 世界
     * @param centerPos 中心位置
     * @param yRange Y轴搜索半径
     * @return 方块实体列表（优先Y轴范围内，如果没有则只包含最近的那个）
     */
    public static List<BlockEntity> findBlockEntities(Level level, BlockPos centerPos, int yRange) {
        List<BlockEntity> nearbyResult = new ArrayList<>();
        BlockEntity nearestBlockEntity = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        int centerY = centerPos.getY();
        int centerX = centerPos.getX();
        int centerZ = centerPos.getZ();
        int minY = centerY - yRange;
        int maxY = centerY + yRange;
        for (TickingBlockEntity ticker : level.blockEntityTickers) {
            BlockPos pos = ticker.getPos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                // 检查是否在Y轴范围内
                int y = pos.getY();
                if (y >= minY && y <= maxY) {
                    nearbyResult.add(blockEntity);
                }
                // 计算距离并更新最近的那个
                double distanceSq = pos.distSqr(centerPos);
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearestBlockEntity = blockEntity;
                }
            }
        }
        // 返回结果
        if (!nearbyResult.isEmpty()) {
            return nearbyResult;
        } else if (nearestBlockEntity != null) {
            List<BlockEntity> result = new ArrayList<>(1);
            result.add(nearestBlockEntity);
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 根据最大生命值百分比计算效果强度等级
     * @param target 目标实体
     * @param percentage 百分比（如0.2表示20%）
     * @return 效果强度等级（最大生命值 * 百分比 / 4 - 1）
     */
    public static int calculateAbsorptionLevel(LivingEntity target, float percentage) {
        return convertToInteger(target.getMaxHealth() * percentage) / 4 - 1;
    }

    public static List<LivingEntity> findAllEntities(Entity source, double radius) {
        if (source.level == null) {
            return Collections.emptyList();
        }
        final Vec3 center = source.position();
        AABB boundingBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        List<LivingEntity> entities = source.level.getEntitiesOfClass(
                LivingEntity.class,
                boundingBox,
                e -> e != source && e.isAlive()
        );
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return entities;
    }

    /**
     * 将实体传送到实体面前一格
     * @param living 实体
     * @param entity 目标实体
     * @param yOffset Y轴偏移量
     */
    public static void teleportToFront(LivingEntity living, LivingEntity entity, double yOffset) {
        float yaw = entity.getYRot();
        double rad = Math.toRadians(yaw);
        double offsetX = -Math.sin(rad);
        double offsetZ = -Math.cos(rad);
        living.setPos(
                entity.getX() + offsetX,
                entity.getY() + yOffset,
                entity.getZ() + offsetZ
        );
    }

    public static <T extends LivingEntity> List<T> findAllEntities(Entity source, double radius, Class<T> entityClass) {
        final Vec3 center = source.position();
        AABB boundingBox = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        List<T> entities = source.level.getEntitiesOfClass(entityClass, boundingBox, e -> e != source && e.isAlive());
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return entities;
    }

    /**
     * 以实体为中心，按Y轴和三维范围查找实体
     * @param source 源实体（中心）
     * @param yRange Y轴范围（上下各多少格）
     * @param horizontalRange 水平范围（半径）
     * @param entityClass 要查找的实体类型
     * @return 找到的实体列表
     */
    public static <T extends Entity> List<T> findEntitiesAround(Entity source, int yRange, double horizontalRange, Class<T> entityClass) {
        List<T> nearbyResult = new ArrayList<>();
        List<T> allResult = new ArrayList<>();

        // 获取源实体的位置
        Vec3 sourcePos = source.position();
        int centerY = (int) Math.floor(sourcePos.y);
        int minY = centerY - yRange;
        int maxY = centerY + yRange;

        // 创建三维边界盒
        AABB boundingBox = new AABB(
                sourcePos.x - horizontalRange, sourcePos.y - yRange, sourcePos.z - horizontalRange,
                sourcePos.x + horizontalRange, sourcePos.y + yRange, sourcePos.z + horizontalRange
        );

        // 获取范围内的所有指定类型实体
        List<T> entities = source.level().getEntitiesOfClass(entityClass, boundingBox);

        for (T entity : entities) {
            // 排除源实体自身
            if (entity == source) continue;

            Vec3 entityPos = entity.position();
            int entityY = (int) Math.floor(entityPos.y);

            // 检查Y轴范围
            boolean inYRange = entityY >= minY && entityY <= maxY;

            // 计算水平距离
            double dx = entityPos.x - sourcePos.x;
            double dz = entityPos.z - sourcePos.z;
            double horizontalDistanceSq = dx * dx + dz * dz;
            boolean inHorizontalRange = horizontalDistanceSq <= horizontalRange * horizontalRange;

            // 同时满足Y轴范围和水平范围
            if (inYRange && inHorizontalRange) {
                nearbyResult.add(entity);
            }

            // 只在Y轴范围内
            if (inYRange) {
                allResult.add(entity);
            }
        }

        return nearbyResult.isEmpty() ? allResult : nearbyResult;
    }

    /**
     * 查找附近所有类型的实体（默认查找所有实体）
     */
    public static List<Entity> findEntitiesAround(Entity source, int yRange, double horizontalRange) {
        return findEntitiesAround(source, yRange, horizontalRange, Entity.class);
    }

    /**
     * 查找附近的生物实体
     */
    public static List<LivingEntity> findLivingEntitiesAround(Entity source, int yRange, double horizontalRange) {
        return findEntitiesAround(source, yRange, horizontalRange, LivingEntity.class);
    }

    /**
     * 查找附近的玩家
     */
    public static List<Player> findPlayersAround(Entity source, int yRange, double horizontalRange) {
        return findEntitiesAround(source, yRange, horizontalRange, Player.class);
    }

    public static double getDistanceBetweenEntities(Entity entity1, Entity entity2) {
        if (entity1 == null || entity2 == null) {
            return -1;
        }
        return entity1.position().distanceTo(entity2.position());
    }

    public static void removeAttribute(LivingEntity entity, UUID uuid) {
        var entityAttribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (entityAttribute == null) return;
        AttributeModifier existingModifier = entityAttribute.getModifier(uuid);
        if (existingModifier != null) {
            entityAttribute.removeModifier(uuid);
        }
    }

    public static <T extends Entity> T findEntityInLookDirection(Player player, double maxDistance,
                                                                 double range, Class<T> entityClass) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.x * maxDistance, lookVec.y * maxDistance, lookVec.z * maxDistance);

        // 创建两个搜索区域：一个用于近处，一个用于正常检测
        AABB searchBox = new AABB(eyePos, endPos).inflate(range);

        List<T> entities = player.level().getEntitiesOfClass(entityClass, searchBox,
                entity -> entity != player && entity.isAlive());

        T closestEntity = null;
        double closestDistance = maxDistance;

        for (T entity : entities) {
            // 对每个实体创建一个更宽松的检测区域
            AABB entityBox = entity.getBoundingBox().inflate(0.5 + range); // 增加基础扩展

            // 直接检查玩家眼睛是否在实体的扩展范围内
            if (entityBox.contains(eyePos)) {
                // 如果眼睛已经在碰撞箱内，直接返回这个实体
                double distance = eyePos.distanceTo(entity.position());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
                continue;
            }

            // 计算射线与实体的交点
            Optional<Vec3> clipResult = entityBox.clip(eyePos, endPos);

            if (clipResult.isPresent()) {
                double distance = eyePos.distanceTo(clipResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
            // 如果 clip 失败，检查最近点
            else {
                // 计算实体中心到射线的最短距离
                Vec3 entityCenter = entity.position();
                Vec3 toEntity = entityCenter.subtract(eyePos);

                // 计算在视线方向上的投影长度
                double projection = toEntity.dot(lookVec);

                // 如果投影在合理范围内（0到maxDistance之间）
                if (projection > 0 && projection < maxDistance) {
                    // 计算视线上的最近点
                    Vec3 nearestOnRay = eyePos.add(lookVec.scale(projection));
                    double distanceToRay = entityCenter.distanceTo(nearestOnRay);

                    // 如果距离足够近
                    if (distanceToRay < range) {
                        double distance = eyePos.distanceTo(entityCenter);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestEntity = entity;
                        }
                    }
                }
            }
        }

        return closestEntity;
    }

    /**
     * 查找玩家视线方向上的最近实体（使用默认range=2.0）
     */
    public static <T extends Entity> T findEntityInLookDirection(Player player, double maxDistance,
                                                                 Class<T> entityClass) {
        return findEntityInLookDirection(player, maxDistance, 2.0D, entityClass);
    }

    /**
     * 查找玩家视线方向上的最近生物实体
     * @param player 玩家
     * @param maxDistance 最大检测距离
     * @param range 检测范围（实体碰撞箱的扩展范围）
     * @return 找到的最近生物实体，如果没有则返回null
     */
    public static LivingEntity findLivingEntityInLookDirection(Player player, double maxDistance, double range) {
        return findEntityInLookDirection(player, maxDistance, range, LivingEntity.class);
    }

    /**
     * 查找玩家视线方向上的最近生物实体（使用默认range=2.0）
     */
    public static LivingEntity findLivingEntityInLookDirection(Player player, double maxDistance) {
        return findLivingEntityInLookDirection(player, maxDistance, 2.0D);
    }

    /**
     * 根据目标最大生命值百分比造成随机伤害（有限制）
     *
     * @param target      目标实体
     * @param minPercent  最小伤害百分比（如0.1 = 10%最大生命值）
     * @param maxPercent  最大伤害百分比（如0.2 = 20%最大生命值）
     * @param damageCap   伤害上限（绝对数值，防止单次伤害过高）
     * @return 最终伤害值
     *
     * &#064;example
     * // 造成目标最大生命值 5% ~ 15% 的伤害，但不超过20点
     * float damage = addMaxHealthPercentageDamage(entity, 0.05f, 0.15f, 20f);
     *
     * @implNote 伤害值在 minPercent 和 maxPercent 之间随机，然后受 damageCap 限制
     */
    public static float addMaxHealthPercentageDamage(LivingEntity target, float minPercent, float maxPercent, float damageCap) {
        // 计算随机百分比伤害（minPercent ~ maxPercent）
        float randomPercent = minPercent + target.getRandom().nextFloat() * (maxPercent - minPercent);
        float damage = target.getMaxHealth() * randomPercent;
        // 限制最大伤害
        return Math.min(damage, damageCap);
    }

    /**
     * 查找玩家视线方向上的所有实体（按距离排序）
     * @param player 玩家
     * @param maxDistance 最大检测距离
     * @param range 检测范围（实体碰撞箱的扩展范围）
     * @param entityClass 实体类型
     * @return 找到的所有实体列表，按距离从近到远排序
     */
    public static <T extends Entity> List<T> findAllEntitiesInLookDirection(Player player, double maxDistance,
                                                                            double range, Class<T> entityClass) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.x * maxDistance, lookVec.y * maxDistance, lookVec.z * maxDistance);

        // 创建搜索区域
        AABB searchBox = new AABB(eyePos, endPos).inflate(range);

        List<T> entities = player.level().getEntitiesOfClass(entityClass, searchBox,
                entity -> entity != player && entity.isAlive());

        // 用于存储实体和对应距离的列表
        List<Pair<T, Double>> entityDistances = new ArrayList<>();

        for (T entity : entities) {
            // 对每个实体创建一个更宽松的检测区域
            AABB entityBox = entity.getBoundingBox().inflate(0.5 + range);

            // 直接检查玩家眼睛是否在实体的扩展范围内
            if (entityBox.contains(eyePos)) {
                double distance = eyePos.distanceTo(entity.position());
                entityDistances.add(Pair.of(entity, distance));
                continue;
            }

            // 计算射线与实体的交点
            Optional<Vec3> clipResult = entityBox.clip(eyePos, endPos);

            if (clipResult.isPresent()) {
                double distance = eyePos.distanceTo(clipResult.get());
                entityDistances.add(Pair.of(entity, distance));
            }
            // 如果 clip 失败，检查最近点
            else {
                // 计算实体中心到射线的最短距离
                Vec3 entityCenter = entity.position();
                Vec3 toEntity = entityCenter.subtract(eyePos);

                // 计算在视线方向上的投影长度
                double projection = toEntity.dot(lookVec);

                // 如果投影在合理范围内（0到maxDistance之间）
                if (projection > 0 && projection < maxDistance) {
                    // 计算视线上的最近点
                    Vec3 nearestOnRay = eyePos.add(lookVec.scale(projection));
                    double distanceToRay = entityCenter.distanceTo(nearestOnRay);

                    // 如果距离足够近
                    if (distanceToRay < range) {
                        double distance = eyePos.distanceTo(entityCenter);
                        entityDistances.add(Pair.of(entity, distance));
                    }
                }
            }
        }

        // 按距离排序（从近到远）
        entityDistances.sort(Comparator.comparingDouble(Pair::getSecond));

        // 提取实体列表
        return entityDistances.stream()
                .map(Pair::getFirst)
                .collect(Collectors.toList());
    }

    /**
     * 查找玩家视线方向上的所有实体（使用默认range=2.0）
     */
    public static <T extends Entity> List<T> findAllEntitiesInLookDirection(Player player, double maxDistance,
                                                                            Class<T> entityClass) {
        return findAllEntitiesInLookDirection(player, maxDistance, 2.0D, entityClass);
    }

    /**
     * 查找玩家视线方向上的所有生物实体
     * @param player 玩家
     * @param maxDistance 最大检测距离
     * @param range 检测范围（实体碰撞箱的扩展范围）
     * @return 找到的所有生物实体列表，按距离从近到远排序
     */
    public static List<LivingEntity> findAllLivingEntitiesInLookDirection(Player player, double maxDistance,
                                                                          double range) {
        return findAllEntitiesInLookDirection(player, maxDistance, range, LivingEntity.class);
    }

    /**
     * 查找玩家视线方向上的所有生物实体（使用默认range=2.0）
     */
    public static List<LivingEntity> findAllLivingEntitiesInLookDirection(Player player, double maxDistance) {
        return findAllLivingEntitiesInLookDirection(player, maxDistance, 2.0D);
    }

    public static LivingEntity[] findAllEntitiesWithDimension(Entity source, double range, ResourceKey<Level> levelResourceKey) {
        final Vec3 _center = new Vec3(source.getX(), source.getY(), source.getZ());
        AABB boundingBox = new AABB(_center.x - range, _center.y - range, _center.z - range,
                _center.x + range, _center.y + range, _center.z + range);
        List<LivingEntity> _entfound = source.level.getEntitiesOfClass(LivingEntity.class, boundingBox, e -> true).stream()
                .filter(entity -> entity != source && entity.level.dimension == levelResourceKey)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(_center)))
                .toList();
        return _entfound.toArray(new LivingEntity[0]);
    }

    public static Player[] findAllPlayerWithDimension(Entity source, double range, ResourceKey<Level> levelResourceKey) {
        final Vec3 center = new Vec3(source.getX(), source.getY(), source.getZ());
        AABB boundingBox = new AABB(center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);
        List<Player> array = source.level.getEntitiesOfClass(Player.class, boundingBox, e -> true).stream()
                .filter(entity -> entity != source && entity.level.dimension == levelResourceKey && entity instanceof Player)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)))
                .toList();
        return array.toArray(new Player[0]);
    }

    public static ServerPlayer[] findAllPlayer(Entity source) {
        if (source == null || source.level.isClientSide) return new ServerPlayer[] {};
        List<ServerPlayer> array = source.level().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player != source)
                .toList();
        return array.toArray(new ServerPlayer[0]);
    }

    /**
     * 让实体高速飞向目标实体，到达后执行回调
     * @param entity 飞行的实体
     * @param target 目标实体
     * @param speed 每 tick 移动距离（格数）
     * @param onReach 到达后的回调
     */
    public static void flyToEntityWithCallback(LivingEntity entity, Entity target, double speed, Runnable onReach) {
        if (entity == null || target == null) return;
        TimerEntry<LivingEntity> flyTimer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull LivingEntity living) {
                double dx = target.getX() - living.getX();
                double dy = target.getY() + target.getBbHeight() / 2 - living.getY();
                double dz = target.getZ() - living.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.6) {
                    if (onReach != null) onReach.run();
                    this.removeTimer(living.getUUID());
                    return;
                }
                double vx = dx / distance * speed;
                double vy = dy / distance * speed;
                double vz = dz / distance * speed;
                living.setPos(
                        living.getX() + vx,
                        living.getY() + vy,
                        living.getZ() + vz
                );
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                living.setYRot(yaw);
            }
        };
        flyTimer.addSkillTimer(entity, 0, 10000, 20);
    }

    /**
     * 在目标位置附近找到安全的地面位置
     * @param level 世界
     * @param targetPos 目标位置（方块坐标）
     * @param horizontalRange 水平随机范围
     * @return 安全的地面位置，找不到则返回目标位置上移1格
     */
    public static BlockPos findSafeGroundPosition(Level level, BlockPos targetPos, int horizontalRange) {
        if (level == null || targetPos == null) return targetPos;

        RandomSource random = level.getRandom();

        // 尝试10次找到合适位置
        for (int attempt = 0; attempt < 10; attempt++) {
            int offsetX = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
            int offsetZ = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;

            BlockPos checkPos = targetPos.offset(offsetX, 0, offsetZ);

            // 从目标Y轴向上找，找到第一个非空气方块，然后取其上方
            BlockPos groundPos = findGround(level, checkPos);
            if (groundPos != null) {
                return groundPos;
            }
        }

        // 默认返回目标位置上方1格
        return targetPos.above();
    }

    /**
     * 从给定位置向下找地面
     */
    private static BlockPos findGround(Level level, BlockPos pos) {
        int startY = pos.getY();
        int minY = Math.max(startY - 16, level.getMinBuildHeight());
        int maxY = Math.min(startY + 6, level.getMaxBuildHeight() - 2);

        // 从 startY+1 向下找（跳过反应堆本身，直接从地板层开始扫）
        // 反应堆在 Y=0，地板在 Y=1，所以从 startY+1 开始能立即命中地板
        for (int y = startY + 1; y >= minY; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (level.getBlockState(checkPos).isSolid()) {
                BlockPos above = checkPos.above();
                if (level.isEmptyBlock(above) && level.isEmptyBlock(above.above())) {
                    return above;
                }
            }
        }

        // 向上找（范围控制在+6格内，避免跨层到天花板上方）
        for (int y = startY + 2; y <= maxY; y++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (level.getBlockState(checkPos).isSolid()) {
                BlockPos above = checkPos.above();
                if (level.isEmptyBlock(above) && level.isEmptyBlock(above.above())) {
                    return above;
                }
            }
        }

        return null;
    }

    /**
     * 根据已损失血量计算伤害加成倍率
     * @param player 玩家
     * @param bonusPerLostPercent 每损失1%血量增加的伤害倍率（如0.02表示每损失1%提高2%）
     * @param maxBonusPercent 最大加成百分比（如1.0表示最高100%），传0表示无上限
     * @return 伤害倍率（默认1.0）
     */
    public static float getDamageMultiplierByLostHealth(Player player, float bonusPerLostPercent, float maxBonusPercent) {
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthLostPercent = (maxHealth - currentHealth) / maxHealth;
        float bonus = healthLostPercent * bonusPerLostPercent;
        if (maxBonusPercent > 0 && bonus > maxBonusPercent) {
            bonus = maxBonusPercent;
        }
        return 1 + bonus;
    }

    /**
     * 在指定区域内查找实体
     */
    public static <T extends LivingEntity> List<T> findEntitiesInArea(
            Player player, double radius, double height, Class<T> entityClass, Predicate<T> filter) {
        AABB aabb = player.getBoundingBox().inflate(radius, height, radius);
        return player.level().getEntitiesOfClass(entityClass, aabb,
                e -> e != player && filter.test(e));
    }

    /**
     * 判断某坐标是否在公司(loboto 结构)范围内。
     * 范围由 GenerationBiggerData 记录的 centerX/centerZ/currentRadius 定义。
     * 若公司尚未生成或无法获取数据,返回 true(不做限制,保持兼容)。
     */
    public static boolean isInCompany(Level level, BlockPos pos) {
        if (pos == null) return false;
        if (!(level instanceof ServerLevel serverLevel)) return true;
        com.wzz.lobotocraft.world.data.GenerationBiggerData.Entry data =
                com.wzz.lobotocraft.world.structure.Structures.LOBOTO.data(serverLevel);
        if (data == null || !data.generated || data.currentRadius <= 0) return true;
        int dx = pos.getX() - data.centerX;
        int dz = pos.getZ() - data.centerZ;
        return Math.abs(dx) <= data.currentRadius && Math.abs(dz) <= data.currentRadius;
    }

    /**
     * 在公司范围内查找安全地面位置。
     * 先尝试在 targetPos 附近(公司内)随机寻找安全地面;若多次都落在公司外,
     * 则只允许返回仍在公司内的兜底点。
     */
    public static BlockPos findSafeGroundPositionInCompany(Level level, BlockPos targetPos, int horizontalRange) {
        if (level == null || targetPos == null) return targetPos;
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int offsetX = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
            int offsetZ = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
            BlockPos checkPos = targetPos.offset(offsetX, 0, offsetZ);
            // 越出公司范围的候选位置直接跳过
            if (!isInCompany(level, checkPos)) continue;
            BlockPos ground = findSafeGroundPosition(level, checkPos, 0);
            if (ground != null && isInCompany(level, ground)) {
                return ground;
            }
        }
        // 兜底也必须保持在公司范围内,否则调用方应放弃本次生成。
        BlockPos fallback = findSafeGroundPosition(level, targetPos, 0);
        if (fallback != null && isInCompany(level, fallback)) {
            return fallback;
        }
        return isInCompany(level, targetPos) ? targetPos.above() : null;
    }

    /**
     * 再生反应堆派生生成点。
     * 如果反应堆正上方已有方块,从低 4 格的位置开始找地面,避免实体刷到上层房间。
     */
    public static BlockPos findReactorSpawnPositionInCompany(Level level, BlockPos reactorPos, int horizontalRange) {
        if (level == null || reactorPos == null) return reactorPos;
        BlockPos basePos = level.getBlockState(reactorPos.above()).isAir()
                ? reactorPos
                : reactorPos.below(4);
        return findSafeGroundPositionInCompany(level, basePos, horizontalRange);
    }
}
