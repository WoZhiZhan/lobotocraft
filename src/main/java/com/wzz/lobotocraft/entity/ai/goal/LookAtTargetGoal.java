package com.wzz.lobotocraft.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * 让生物一直正面看着其当前攻击目标或指定目标的AI
 * 与 LookAtPlayerGoal 不同，这个可以看着任何类型的实体
 */
public class LookAtTargetGoal extends Goal {
    protected final Mob mob;
    protected LivingEntity lookAt;
    protected final float lookDistance;
    protected int lookTime;

    protected final Predicate<LivingEntity> targetPredicate;
    protected final boolean onlyHorizontal;

    /**
     * @param mob 拥有此AI的生物
     * @param lookDistance 最大观察距离
     * @param onlyHorizontal 是否只在水平方向上看（不上下看）
     */
    public LookAtTargetGoal(Mob mob, float lookDistance, boolean onlyHorizontal) {
        this.mob = mob;
        this.lookDistance = lookDistance;
        this.onlyHorizontal = onlyHorizontal;
        this.targetPredicate = null;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.TARGET));
    }

    /**
     * @param mob 拥有此AI的生物
     * @param lookDistance 最大观察距离
     * @param onlyHorizontal 是否只在水平方向上看
     * @param targetPredicate 目标筛选条件（可选）
     */
    public LookAtTargetGoal(Mob mob, float lookDistance, boolean onlyHorizontal, Predicate<LivingEntity> targetPredicate) {
        this.mob = mob;
        this.lookDistance = lookDistance;
        this.onlyHorizontal = onlyHorizontal;
        this.targetPredicate = targetPredicate;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // 优先使用当前攻击目标
        LivingEntity target = this.mob.getTarget();
        
        if (target != null && target.isAlive()) {
            // 检查目标是否在范围内
            if (this.mob.distanceToSqr(target) <= (double)(this.lookDistance * this.lookDistance)) {
                this.lookAt = target;
                return true;
            }
        }
        
        // 如果没有攻击目标，寻找最近的非创造/旁观模式玩家
        if (this.mob.level().getNearestPlayer(this.mob, this.lookDistance) != null) {
            Player player = this.mob.level().getNearestPlayer(this.mob, this.lookDistance);
            if (player != null && !player.isSpectator() && !player.isCreative()) {
                this.lookAt = player;
                return true;
            }
        }
        
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.lookAt == null || !this.lookAt.isAlive()) {
            return false;
        }
        
        // 如果目标超出范围，停止
        if (this.mob.distanceToSqr(this.lookAt) > (double)(this.lookDistance * this.lookDistance)) {
            return false;
        }
        
        // 如果是玩家，检查是否切换为创造/旁观模式
        if (this.lookAt instanceof Player player) {
            if (player.isSpectator() || player.isCreative()) {
                return false;
            }
        }
        
        // 如果有自定义条件，检查
        if (this.targetPredicate != null && !this.targetPredicate.test(this.lookAt)) {
            return false;
        }
        
        return this.lookTime > 0;
    }

    @Override
    public void start() {
        this.lookTime = this.adjustedTickDelay(40 + this.mob.getRandom().nextInt(40));
    }

    @Override
    public void stop() {
        this.lookAt = null;
    }

    @Override
    public void tick() {
        if (this.lookAt != null && this.lookAt.isAlive()) {
            if (this.onlyHorizontal) {
                // 只在水平方向上看（用于飞行生物等）
                double dx = this.lookAt.getX() - this.mob.getX();
                double dz = this.lookAt.getZ() - this.mob.getZ();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                
                // 设置头部水平朝向
                float yaw = (float)(Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
                this.mob.getLookControl().setLookAt(
                    this.lookAt.getX(),
                    this.mob.getEyeY(), // 保持相同的Y高度
                    this.lookAt.getZ()
                );
            } else {
                // 全方位看向目标（包括上下）
                this.mob.getLookControl().setLookAt(
                    this.lookAt,
                    30.0F,  // 最大头部旋转速度
                    30.0F   // 最大身体旋转速度
                );
            }
        }
    }
}