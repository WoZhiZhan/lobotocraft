package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class KissEffect extends MobEffect {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("76f4b1f4-a691-4099-99e1-173b77fd896f");
    private static final float SPEED_REDUCTION_PER_LEVEL = 0.4f; // 每层减速40%
    private static final int FREEZE_AMPLIFIER = 2; // 3层时(amplifier=2)触发冰冻

    public KissEffect() {
        super(MobEffectCategory.HARMFUL, 0xAEE6FF);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (living instanceof Player) return;
        Level level = living.level();

        // 每层减速40%：amplifier 0=1层减速40%, 1=2层减速80%, 2=3层减速120%(超过100%)
        double speedReduction = Math.min(1.0, (amplifier + 1) * SPEED_REDUCTION_PER_LEVEL);

        // 动态添加减速属性
        applySpeedModifier(living, speedReduction);

        // 3层时触发冰冻效果
        if (amplifier >= FREEZE_AMPLIFIER) {
            applyFreeze(living, level);
        }

        // 客户端粒子特效
        if (level.isClientSide()) {
            spawnSnowParticles(living, level, amplifier);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity p_19469_, AttributeMap p_19470_, int p_19471_) {
        super.removeAttributeModifiers(p_19469_, p_19470_, p_19471_);
        onEffectRemoved(p_19469_);
    }

    private void applySpeedModifier(LivingEntity living, double speedReduction) {
        // 移除旧的修饰符
        living.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_UUID);

        if (speedReduction > 0) {
            AttributeModifier modifier = new AttributeModifier(
                    SPEED_MODIFIER_UUID,
                    "Kiss effect speed reduction",
                    -speedReduction, // 负值表示减速
                    AttributeModifier.Operation.MULTIPLY_TOTAL // 乘法运算
            );
            living.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(modifier);
        }
    }

    private void applyFreeze(LivingEntity living, Level level) {
        // 冰冻：禁止移动、攻击、转向
        living.setDeltaMovement(Vec3.ZERO); // 停止移动
        living.setYRot(living.yRotO); // 锁定转向（保持上一tick的旋转角度）
        living.yBodyRot = living.yBodyRotO;
        living.yHeadRot = living.yHeadRotO;

        // 禁止跳跃
        living.setOnGround(true);

        // 如果有攻击目标，清除
        if (living.getLastHurtByMob() != null) {
            living.setLastHurtByMob(null);
        }

        // 如果是服务端，额外处理
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            // 移除AI（如果适用）
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(true);
                // 6秒后恢复AI（通过调度任务）
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                        serverLevel.getServer().getTickCount() + 120, // 6秒 = 120 ticks
                        () -> mob.setNoAi(false)
                ));
            }
        }
    }

    private void spawnSnowParticles(LivingEntity living, Level level, int amplifier) {
        // 基础粒子
        if (level.isClientSide()) {
            for (int i = 0; i < 2 + amplifier; i++) {
                level.addParticle(
                        ParticleTypes.SNOWFLAKE,
                        living.getX() + (living.getRandom().nextDouble() - 0.5) * living.getBbWidth(),
                        living.getY() + living.getRandom().nextDouble() * living.getBbHeight(),
                        living.getZ() + (living.getRandom().nextDouble() - 0.5) * living.getBbWidth(),
                        0.0D, -0.1D, 0.0D
                );
            }

            // 3层时额外粒子环绕
            if (amplifier >= FREEZE_AMPLIFIER) {
                spawnFreezeParticles(living, level);
            }
        }
    }

    private void spawnFreezeParticles(LivingEntity living, Level level) {
        // 雪粒子环绕效果
        double radius = living.getBbWidth() * 1.2;
        double height = living.getBbHeight();
        double speed = 0.1;

        for (int i = 0; i < 8; i++) {
            double angle = (living.tickCount * 0.2 + i * Math.PI * 2 / 8) % (Math.PI * 2);
            double x = living.getX() + Math.cos(angle) * radius;
            double z = living.getZ() + Math.sin(angle) * radius;
            double y = living.getY() + (i / 8.0) * height;

            level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    Math.cos(angle + Math.PI / 2) * speed,
                    0.0,
                    Math.sin(angle + Math.PI / 2) * speed
            );
        }
    }

    // 效果结束时清理
    public void onEffectRemoved(LivingEntity living) {
        // 移除速度修饰符
        if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            living.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_UUID);
        }

        // 恢复AI
        if (living instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(false);
        }
    }
}