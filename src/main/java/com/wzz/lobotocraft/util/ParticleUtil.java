package com.wzz.lobotocraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
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