package com.wzz.lobotocraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

public class ParticleUtil {

    /**
     * 在实体周围生成粒子
     */
    public static void spawnParticlesAroundEntity(Entity entity, ParticleType<?> type, int count, double speed) {
        spawnParticlesAroundEntity(entity, type, count, speed, 0, 1, 0);
    }

    public static void spawnParticlesAroundEntity(Entity entity, ParticleType<?> type, int count, double speed, double xO, double yO, double zO) {
        if (entity.level instanceof ServerLevel serverLevel) {
            Random random = new Random();
            for (int i = 0; i < count; i++) {
                serverLevel.sendParticles(
                        (SimpleParticleType) type,
                        entity.getX() + (random.nextFloat() * entity.getBbWidth() * 2.0F) - entity.getBbWidth(),
                        entity.getY() + (random.nextFloat() * entity.getBbHeight()),
                        entity.getZ() + (random.nextFloat() * entity.getBbWidth() * 2.0F) - entity.getBbWidth(),
                        1, // 数量
                        xO, yO, zO,
                        speed
                );
            }
        }
    }

    /**
     * 在实体周围范围内生成粒子（类似下雪效果）
     * @param living 中心实体
     * @param particleType 粒子类型
     * @param count 粒子数量
     * @param range 生成范围半径
     * @param height 生成高度（相对于实体位置）
     */
    public static void spawnAreaParticles(Entity living, SimpleParticleType particleType, int count, float range, float height) {
        if (living.level instanceof ServerLevel serverLevel) {
            Random rand = new Random();
            AABB area = living.getBoundingBox().inflate(range, height, range);
            for (int i = 0; i < count; ++i) {
                // 在范围内随机位置
                double x = area.minX + rand.nextDouble() * (area.maxX - area.minX);
                double y = area.minY + rand.nextDouble() * (area.maxY - area.minY);
                double z = area.minZ + rand.nextDouble() * (area.maxZ - area.minZ);
                // 添加轻微下落效果
                double motionY = -0.05 - rand.nextDouble() * 0.05;
                serverLevel.sendParticles(particleType,
                        x, y, z, // 位置
                        1, // 数量
                        0, 0, 0, // 随机偏移
                        motionY); // Y轴速度（下落效果）
            }
        }
    }

    /**
     * 在方块位置上方生成下雪效果粒子
     * @param level 世界
     * @param pos 方块位置
     * @param particleType 粒子类型
     * @param count 粒子数量
     * @param range 水平范围
     * @param height 垂直范围
     */
    public static void spawnSnowLikeParticles(ServerLevel level, BlockPos pos, SimpleParticleType particleType,
                                              int count, float range, float height) {
        Random rand = new Random();
        for (int i = 0; i < count; ++i) {
            double x = pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * range;
            double z = pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * range;
            double y = pos.getY() + height + rand.nextDouble() * 0.5;
            // 下落效果
            double motionY = -0.05 - rand.nextDouble() * 0.05;
            level.sendParticles(particleType,
                    x, y, z,
                    1,
                    0, 0, 0,
                    motionY);
        }
    }

    /**
     * 在指定位置生成粒子
     */
    public static void spawnParticlesAtPosition(ServerLevel level, ParticleType<?> type,
                                                double x, double y, double z,
                                                int count, double spread, double speed) {
        if (level != null) {
            level.sendParticles(
                    (SimpleParticleType) type,
                    x, y, z,
                    count,
                    spread, spread, spread,
                    speed
            );
        }
    }


    public static void spawnLineParticles(Level world, LivingEntity living, ParticleType<?> particleType, int particleCount, double speed) {
        spawnLineParticles(world, living, (SimpleParticleType) particleType, particleCount, speed);
    }

    public static void spawnLineParticles(Level world, LivingEntity living, SimpleParticleType particleType, int particleCount, double speed) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 viewVector = living.getLookAngle();
            Vec3 playerPos = living.position();
            for (int i = 0; i < particleCount; i++) {
                double factor = i * 0.1;
                double x = playerPos.x + viewVector.x * factor;
                double y = playerPos.y + living.getEyeHeight() + viewVector.y * factor;
                double z = playerPos.z + viewVector.z * factor;
                serverLevel.sendParticles(particleType, x, y, z, 1, 0, 0, 0, speed);
            }
        }
    }

    public static void spawnLineParticles(Level world, LivingEntity living, ParticleType<?> particleType, int particleCount, double speed, double distance) {
        spawnLineParticles(world, living, (SimpleParticleType) particleType, particleCount, speed, distance);
    }

    public static void spawnLineParticles(Level world, LivingEntity living, SimpleParticleType particleType, int particleCount, double speed, double distance) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 viewVector = living.getLookAngle().normalize(); // 正规化方向向量
            Vec3 playerPos = living.position().add(0, living.getEyeHeight(), 0); // 获取玩家眼睛的位置
            for (int i = 0; i <= particleCount; i++) {
                double factor = (double)i / (double)particleCount * distance; // 计算粒子的插值位置
                double x = playerPos.x + viewVector.x * factor;
                double y = playerPos.y + viewVector.y * factor;
                double z = playerPos.z + viewVector.z * factor;
                serverLevel.sendParticles(particleType, x, y, z, 1, 0, 0, 0, speed);
            }
        }
    }

    /**
     * 在实体前方生成一条线状粒子（可控制向外扩散）
     * @param world 世界
     * @param living 实体
     * @param particleType 粒子类型
     * @param particleCount 粒子数量
     * @param speed 粒子速度
     * @param distance 粒子线的总长度
     * @param spreadRadius 向外扩散半径（0=直线，值越大越向外扩散）
     */
    public static void spawnLineParticles(Level world, LivingEntity living, SimpleParticleType particleType,
                                          int particleCount, double speed, double distance, double spreadRadius) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 viewVector = living.getLookAngle().normalize();
            Vec3 playerPos = living.position().add(0, living.getEyeHeight(), 0);

            // 计算垂直于视线方向的向量（用于向外扩散）
            Vec3 rightVector = new Vec3(-viewVector.z, 0, viewVector.x).normalize();
            Vec3 upVector = viewVector.cross(rightVector).normalize();

            for (int i = 0; i <= particleCount; i++) {
                double factor = (double) i / (double) particleCount * distance;

                // 基础位置（沿视线方向）
                double baseX = playerPos.x + viewVector.x * factor;
                double baseY = playerPos.y + viewVector.y * factor;
                double baseZ = playerPos.z + viewVector.z * factor;

                // 向外扩散的偏移
                double spreadFactor = spreadRadius * factor; // 越远扩散越大
                double angle = (2 * Math.PI * i) / particleCount; // 环绕角度
                double offsetX = (Math.cos(angle) * rightVector.x + Math.sin(angle) * upVector.x) * spreadFactor;
                double offsetY = (Math.cos(angle) * rightVector.y + Math.sin(angle) * upVector.y) * spreadFactor;
                double offsetZ = (Math.cos(angle) * rightVector.z + Math.sin(angle) * upVector.z) * spreadFactor;

                serverLevel.sendParticles(particleType,
                        baseX + offsetX, baseY + offsetY, baseZ + offsetZ,
                        1, 0, 0, 0, speed);
            }
        }
    }

    /**
     * 在实体前方生成粒子线（随机向外扩散）
     * @param spreadStrength 扩散强度（0=直线，1=轻微扩散，3=强扩散）
     */
    public static void spawnLineParticlesSpread(Level world, LivingEntity living, SimpleParticleType particleType,
                                                int particleCount, double speed, double distance, double spreadStrength) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 viewVector = living.getLookAngle().normalize();
            Vec3 playerPos = living.position().add(0, living.getEyeHeight(), 0);
            RandomSource random = living.getRandom();

            // 垂直和水平扩散方向
            Vec3 right = new Vec3(-viewVector.z, 0, viewVector.x).normalize();
            Vec3 up = right.cross(viewVector).normalize();

            for (int i = 0; i <= particleCount; i++) {
                double factor = (double) i / (double) particleCount * distance;

                // 基础位置
                Vec3 basePos = playerPos.add(viewVector.scale(factor));

                // 随机向外扩散（越远扩散越大）
                double spread = factor * spreadStrength;
                double randomRight = (random.nextDouble() - 0.5) * 2 * spread;
                double randomUp = (random.nextDouble() - 0.5) * 2 * spread;

                Vec3 finalPos = basePos
                        .add(right.scale(randomRight))
                        .add(up.scale(randomUp));

                serverLevel.sendParticles(particleType,
                        finalPos.x, finalPos.y, finalPos.z,
                        1, 0, 0, 0, speed);
            }
        }
    }

    public static void spawnLineParticles(Level world, LivingEntity living, ParticleOptions particleType, int particleCount, double speed, double distance) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 viewVector = living.getLookAngle().normalize(); // 正规化方向向量
            Vec3 playerPos = living.position().add(0, living.getEyeHeight(), 0); // 获取玩家眼睛的位置
            for (int i = 0; i <= particleCount; i++) {
                double factor = (double)i / (double)particleCount * distance; // 计算粒子的插值位置
                double x = playerPos.x + viewVector.x * factor;
                double y = playerPos.y + viewVector.y * factor;
                double z = playerPos.z + viewVector.z * factor;
                serverLevel.sendParticles(particleType, x, y, z, 1, 0, 0, 0, speed);
            }
        }
    }

    public static void spawnParticles(Entity living, ParticleOptions particleOptions, int number, double speed) {
        if (living.level instanceof ServerLevel serverLevel) {
            Random rand = new Random();
            for (int k = 0; k < number; ++k) {
                serverLevel.sendParticles(
                        particleOptions,
                        living.getX() + (rand.nextDouble() - 0.5) * living.getBbWidth(),
                        living.getY() + rand.nextDouble() * living.getBbHeight(),
                        living.getZ() + (rand.nextDouble() - 0.5) * living.getBbWidth(),
                        1,
                        0, 0, 0,
                        speed
                );
            }
        }
    }

    public static DustParticleOptions getDustParticle(float r, float g, float b, float scale) {
        return new DustParticleOptions(new Vector3f(r,g,b), scale);
    }

    /**
     * 向实体前方喷吐锥形混合粒子（多种粒子类型随机混合，越远越发散、带向前初速度）。
     * 用于「微笑的尸山」三阶段特殊攻击的喷吐液体效果。
     *
     * @param living      喷吐实体（以其朝向 getLookAngle 为喷射方向）
     * @param types       混合的粒子类型数组（每个粒子随机挑一种）
     * @param distance    喷吐最大距离
     * @param count       粒子总数
     * @param coneRadius  末端锥形半径（越大越发散，0=直线）
     * @param speed       粒子速度倍率
     * @param yOffset     起始点相对实体脚部的高度偏移（一般给嘴部高度）
     */
    public static void spawnConeSprayMixed(LivingEntity living, ParticleOptions[] types, double distance,
                                           int count, double coneRadius, double speed, double yOffset) {
        if (!(living.level instanceof ServerLevel serverLevel) || types == null || types.length == 0) return;

        Vec3 dir = living.getLookAngle().normalize();
        Vec3 origin = living.position().add(0, yOffset, 0).add(dir.scale(0.5));
        // 垂直于喷射方向的两个基向量
        Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 up = dir.cross(right).normalize();
        RandomSource random = living.getRandom();

        for (int i = 0; i < count; i++) {
            double factor = random.nextDouble();               // 0..1 沿喷射方向的位置
            double dist = factor * distance;
            double spread = coneRadius * factor;               // 越远发散越大
            double angle = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * spread; // 均匀分布在圆面

            Vec3 pos = origin.add(dir.scale(dist))
                    .add(right.scale(Math.cos(angle) * r))
                    .add(up.scale(Math.sin(angle) * r));

            ParticleOptions type = types[random.nextInt(types.length)];
            // count=0 时 (dx,dy,dz) 作为粒子初速度方向，给一点向前+向上的喷溅感
            serverLevel.sendParticles(type,
                    pos.x, pos.y, pos.z,
                    0,
                    dir.x, dir.y + 0.05, dir.z,
                    speed);
        }
    }

    public static void spawnFallingVomit(ServerLevel level, Vec3 origin, Vec3 dir,
                                         double forward, double back, double halfWidth,
                                         double spawnHeight, int count,
                                         ParticleOptions[] types, double fallSpeed) {
        if (level == null || types == null || types.length == 0) return;
        Vec3 f = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x).normalize();
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double along = -back + random.nextDouble() * (forward + back); // 前后铺开
            double lat = (random.nextDouble() - 0.5) * 2 * halfWidth;      // 横向铺开
            double h = spawnHeight + random.nextDouble() * 1.5;            // 在上方随机高度生成
            Vec3 p = origin.add(f.scale(along)).add(right.scale(lat)).add(0, h, 0);
            ParticleOptions type = types[random.nextInt(types.length)];
            // count=0 时 (dx,dy,dz) 为初速度：纯向下 → 从上往下倒落
            level.sendParticles(type, p.x, p.y, p.z, 0, 0, -Math.abs(fallSpeed), 0, 1.0);
        }
    }

}