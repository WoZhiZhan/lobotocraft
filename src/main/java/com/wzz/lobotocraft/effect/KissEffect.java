package com.wzz.lobotocraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class KissEffect extends MobEffect {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("76f4b1f4-a691-4099-99e1-173b77fd896f");
    private static final float SPEED_REDUCTION_PER_LEVEL = 0.1f; // 每层减速10%，第三层另外冻结
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

        // Display levels I/II/III correspond to amplifiers 0/1/2.
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
        if (living.getAttribute(Attributes.MOVEMENT_SPEED) == null) return;
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

        if (!level.isClientSide() && living instanceof net.minecraft.world.entity.Mob mob) {
            // aiStep is already cancelled while the three-stack effect is active.
            // Do not take ownership of NoAI: containment and other mechanics may
            // legitimately require it to remain enabled after the kiss is removed.
            mob.setTarget(null);
            mob.getPersistentData().putBoolean("isFreezeKiss", true);
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
        clearFreezeState(living);
    }

    /** Clears every transient state owned by the three-stack kiss freeze. */
    public static void clearFreezeState(LivingEntity living) {
        if (living == null) return;
        // 移除速度修饰符
        if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            living.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_UUID);
        }

        // Only clear state owned by this effect. The mob's NoAI value belongs to
        // its containment or other controlling mechanic and must be preserved.
        if (living instanceof net.minecraft.world.entity.Mob mob) {
            mob.getPersistentData().remove("isFreezeKiss");
        }
    }
}
