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
}