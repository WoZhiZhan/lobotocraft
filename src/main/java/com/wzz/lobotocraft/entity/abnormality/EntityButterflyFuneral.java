package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

/**
 * 亡蝶葬仪 (T-01-68) —— HE 级异想体。
 * 出逃后在设施内游荡,攻击除出逃异想体、不会出逃异想体外的所有单位。
 * 普攻(65%,冷却7秒):射击手势后出伤,蝴蝶绽放在目标上,10-15点白色伤害。
 * 特殊攻击(35%):停止移动放下棺材,持续15秒向前方50格、42°扇形喷出蝴蝶群,
 *   身处其中的单位每秒受3-4点白色伤害,村民被定身。
 * 处决:恐慌的玩家被其攻击 → "蝴蝶缠身"6秒后死亡(被"救赎")。
 * 空血:播放死亡音频与死亡动画后才回到出逃位置重置。
 */
public class EntityButterflyFuneral extends AbstractAbnormality {

    private static final double NORMAL_ATTACK_RANGE = 16.0D;
    private static final double SKILL_RANGE = 50.0D;
    private static final double SKILL_CONE_DEGREES = 42.0D;
    private static final double SKILL_CONE_HALF_DOT = Math.cos(Math.toRadians(SKILL_CONE_DEGREES / 2.0D));
    private static final int NORMAL_ATTACK_ANIMATION_TICKS = 49;
    private static final int NORMAL_ATTACK_HIT_TICKS_ATTACK = 34;
    private static final int NORMAL_ATTACK_HIT_TICKS_ATTACK2 = 24;
    private static final int SKILL_WINDUP_TICKS = 20;
    private static final int SKILL_DURATION_TICKS = 15 * 20;
    private static final int SKILL_RECOVERY_TICKS = 9;
    private static final int SKILL_PARTICLES_PER_BURST = 12;
    private static final double COFFIN_PARTICLE_FORWARD_OFFSET = 0.95D;
    private static final double COFFIN_PARTICLE_WIDTH = 1.2D;
    private static final double COFFIN_PARTICLE_BOTTOM = 0.1D;
    private static final double COFFIN_PARTICLE_HEIGHT = 2.75D;
    private static final double SKILL_PARTICLE_SPEED = (SKILL_RANGE - COFFIN_PARTICLE_FORWARD_OFFSET) / SKILL_DURATION_TICKS;
    private static final double SKILL_PARTICLE_VERTICAL_SPEED = 0.01D;
    private static final int DYING_ANIMATION_TICKS = 34;

    private int attackCooldown = 0;
    private int pendingAttackHit = 0;       // 普攻出伤帧倒计时
    private int normalAttackAnimationTimer = 0;
    private LivingEntity pendingTarget = null;
    private int skillPhase = 0;             // 0=无 1=skill前摇 2=skillAB持续 3=skillB收尾
    private int skillTimer = 0;
    private Vec3 skillDirection = null;
    private float skillYaw = 0.0F;
    private int dyingTimer = 0;             // 死亡动画计时(>0表示濒死中)

    public EntityButterflyFuneral(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "T-01-68";
        this.abnormalityName = "亡蝶葬仪";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "WHITE";
        this.maxPEOutput = 16;
        float[] basePreferences = {0.50f, 0.50f, 0.0f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        // 本能: 50/45/40/0/0
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.50f, 0.45f, 0.40f, 0.0f, 0.0f};
        // 洞察: 全50
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.50f, 0.50f, 0.50f, 0.50f, 0.50f};
        // 沟通: 全0
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        // 压迫: 0/0/60/60/60
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.0f, 0.0f, 0.60f, 0.60f, 0.60f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 4),                      // Ⅰ 工作速度+4
                new ObservationLevelBonus(0.04f, 0, true, false, false), // Ⅱ 成功率+4%、解锁饰品
                new ObservationLevelBonus(0.0f, 4),                      // Ⅲ 工作速度+4
                new ObservationLevelBonus(0.04f, 0, false, true, true)   // Ⅳ 成功率+4%、解锁护甲武器
        };
    }

    @Override
    public int getBasicInfoCost() { return 16; }
    @Override
    public int getSensitiveInfoCost() { return 16; }
    @Override
    public int getManualCost(int manualIndex) { return 6; }
    @Override
    public int getWorkPreferencesCost() { return 5; }

    @Override
    public String getAbnormalityCode() { return "T-01-68"; }
    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }
    @Override
    public String name() { return "butterfly_funeral"; }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityButterflyFuneral((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    // ==================== 管理须知:计数器减少 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        boolean reduce = false;
        // 须知Ⅰ:正义等级<3 / 须知Ⅱ:勇气等级>3
        var cap = player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS);
        if (cap.isPresent()) {
            var stats = cap.orElse(null);
            if (stats != null) {
                if (stats.getJusticeLevel() < 3) reduce = true;
                if (stats.getFortitudeLevel() > 3) reduce = true;
            }
        }
        // 须知Ⅲ:工作结果为差时 80% 概率
        if (result == WorkResult.BAD && this.random.nextFloat() < 0.80f) {
            reduce = true;
        }
        if (reduce) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // 工作失误:白色伤害 4-6
        float dmg = 4 + this.random.nextInt(3);
        player.hurt(DamageHelper.getDamage(this, "white"), dmg);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    // ==================== 出逃抗性:红0.5 白1.5 黑1.0 蓝2.0 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (dyingTimer > 0) return false; // 濒死动画期间不再受伤
        if (hasEscape()) {
            float m = 1.0f;
            if (DamageHelper.isRedDamage(source)) m = 0.5f;
            else if (DamageHelper.isWhiteDamage(source)) m = 1.5f;
            else if (DamageHelper.isBlackDamage(source)) m = 1.0f;
            else if (DamageHelper.isBlueDamage(source)) m = 2.0f;
            amount *= m;
            // 机制6:空血不立刻死亡,先播放死亡音频与死亡动画
            if (this.getHealth() - amount <= 0) {
                this.setHealth(1f);
                startDying();
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    private void startDying() {
        dyingTimer = DYING_ANIMATION_TICKS;
        pendingAttackHit = 0;
        normalAttackAnimationTimer = 0;
        pendingTarget = null;
        skillPhase = 0;
        skillTimer = 0;
        skillDirection = null;
        setAnimation("die");
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), ModSounds.BUTTERFLY_DEATH.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        }
    }

    // ==================== tick: 出逃 AI ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();

        if (dyingTimer > 0) {
            dyingTimer--;
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            if (dyingTimer == 0) {
                // 死亡动画播放完毕,正式死亡(基类die会回到出逃位置重置)
                this.setHealth(0f);
                this.die(this.damageSources().fellOutOfWorld());
            }
            return;
        }

        if (!hasEscape()) {
            resetCombatAnimationState();
            return;
        }

        if (attackCooldown > 0) attackCooldown--;

        // 普攻出伤帧
        if (pendingAttackHit > 0) {
            pendingAttackHit--;
            if (pendingAttackHit == 0) {
                if (pendingTarget != null && pendingTarget.isAlive()) {
                    resolveNormalAttack(level, pendingTarget);
                }
                pendingTarget = null;
            }
        }

        if (normalAttackAnimationTimer > 0) {
            normalAttackAnimationTimer--;
            if (normalAttackAnimationTimer == 0) {
                setAnimation("idle");
            }
        }

        // 特殊攻击阶段机
        if (skillPhase > 0) {
            tickSkill(level);
            return;
        }

        // 索敌
        if (attackCooldown <= 0 && pendingAttackHit <= 0) {
            LivingEntity target = findTarget(level);
            if (target != null && this.distanceToSqr(target) <= SKILL_RANGE * SKILL_RANGE) {
                if (this.distanceToSqr(target) <= NORMAL_ATTACK_RANGE * NORMAL_ATTACK_RANGE
                        && this.random.nextFloat() < 0.65f) {
                    beginNormalAttack(target);
                } else {
                    beginSkill(level, target);
                }
            }
        }
    }

    private void resetCombatAnimationState() {
        attackCooldown = 0;
        pendingAttackHit = 0;
        normalAttackAnimationTimer = 0;
        pendingTarget = null;
        skillPhase = 0;
        skillTimer = 0;
        skillDirection = null;
        if (!"idle".equals(getAnimation())) {
            setAnimation("idle");
        }
    }

    /** 攻击除"出逃状态异想体"、"不会出逃异想体"之外的所有单位 */
    private LivingEntity findTarget(ServerLevel level) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(SKILL_RANGE), e -> isValidTarget(e));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.distanceToSqr(this);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof AbstractAbnormality ab) {
            return ab.canEscape() && !ab.hasEscape();
        }
        return true;
    }

    // ==================== 普通攻击 ====================

    private void beginNormalAttack(LivingEntity target) {
        String attackAnimation = this.random.nextBoolean() ? "attack" : "attack2";
        setAnimation(attackAnimation);
        pendingTarget = target;
        pendingAttackHit = "attack".equals(attackAnimation)
                ? NORMAL_ATTACK_HIT_TICKS_ATTACK
                : NORMAL_ATTACK_HIT_TICKS_ATTACK2;
        normalAttackAnimationTimer = NORMAL_ATTACK_ANIMATION_TICKS;
        attackCooldown = 7 * 20; // 冷却7秒
        this.getLookControl().setLookAt(target);
    }

    private void resolveNormalAttack(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.blockPosition(), ModSounds.BUTTERFLY_ATTACK.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        // 少量蝴蝶绽放在目标身上(2秒后消失)
        level.sendParticles((SimpleParticleType) ModParticleTypes.BUTTERFLY.get(),
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                5, 0.3, 0.3, 0.3, 0.0);
        // 10-15点白色伤害
        dealWhiteDamage(target, 10 + this.random.nextInt(6));
    }

    /** 白色伤害:通过统一伤害流程结算玩家精神值,并检查处决条件 */
    private void dealWhiteDamage(LivingEntity target, float amount) {
        if (target instanceof ServerPlayer player) {
            boolean wasPanic = isPanicking(player);
            player.hurt(DamageHelper.getDamage(this, "white"), amount);
            boolean nowPanic = isPanicking(player);
            // 处决:恐慌状态下受到攻击,或因本次攻击而恐慌
            if (wasPanic || nowPanic) {
                triggerSalvation(player);
            }
        } else if (target instanceof Villager villager) {
            villager.hurt(DamageHelper.getDamage(this, "white"), amount);
            // 蝴蝶使村民无法移动
            villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false));
        } else {
            target.hurt(DamageHelper.getDamage(this, "white"), amount);
        }
    }

    private boolean isPanicking(ServerPlayer player) {
        return MentalValueUtil.getMentalValue(player) <= 0;
    }

    /** 处决:蝴蝶缠身 */
    private void triggerSalvation(ServerPlayer player) {
        if (player.hasEffect(com.wzz.lobotocraft.init.ModEffects.BUTTERFLY_SHROUD.get())) return;
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(), ModSounds.BUTTERFLY_DEATH.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
        }
        // 立刻解除恐慌并回满精神值
        MentalValueUtil.setMentalValue(player, MentalValueUtil.getEffectiveMaxMentalValue(player));
        // 蝴蝶缠身 6 秒(到期死亡由 ButterflyFuneralEvent 处理)
        player.addEffect(new MobEffectInstance(
                com.wzz.lobotocraft.init.ModEffects.BUTTERFLY_SHROUD.get(), 6 * 20, 0, false, false, true));
    }

    // ==================== 特殊攻击 ====================

    private void beginSkill(ServerLevel level, LivingEntity target) {
        skillPhase = 1;
        skillTimer = SKILL_WINDUP_TICKS;
        faceTargetForSkill(target);
        setAnimation("skill");
        level.playSound(null, this.blockPosition(), ModSounds.BUTTERFLY_SKILL_START.get(), SoundSource.HOSTILE, 1.3f, 1.0f);
        attackCooldown = 7 * 20;
    }

    private void faceTargetForSkill(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz > 1.0E-6D) {
            skillYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        } else {
            skillYaw = this.getYRot();
        }
        lockSkillRotation();
        skillDirection = Vec3.directionFromRotation(0.0F, skillYaw).normalize();
    }

    private void lockSkillRotation() {
        this.setYRot(skillYaw);
        this.yRotO = skillYaw;
        this.setYHeadRot(skillYaw);
        this.yBodyRot = skillYaw;
    }

    private void tickSkill(ServerLevel level) {
        // 释放期间不可移动不可转向
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        lockSkillRotation();
        skillTimer--;

        if (skillPhase == 1 && skillTimer <= 0) {
            skillPhase = 2;
            skillTimer = SKILL_DURATION_TICKS;
            setAnimation("skillAB");
            level.playSound(null, this.blockPosition(), ModSounds.BUTTERFLY_SKILL_LOOP.get(), SoundSource.HOSTILE, 1.3f, 1.0f);
        } else if (skillPhase == 2) {
            // 持续召唤蝴蝶群冲向前方42°扇形(移动速度2单位)
            if (skillTimer % 4 == 0 && skillDirection != null) {
                spawnButterflyConeParticles(level);
            }
            // 扇形范围每秒判伤(3-4点白色伤害,1秒冷却)
            if (skillTimer % 20 == 0 && skillDirection != null) {
                damageButterflyCone(level);
            }
            if (skillTimer <= 0) {
                skillPhase = 3;
                skillTimer = SKILL_RECOVERY_TICKS;
                setAnimation("skillB");
                level.playSound(null, this.blockPosition(), ModSounds.BUTTERFLY_SKILL_END.get(), SoundSource.HOSTILE, 1.3f, 1.0f);
            }
        } else if (skillPhase == 3 && skillTimer <= 0) {
            skillPhase = 0;
            skillDirection = null;
            setAnimation("idle");
        }
    }

    private void spawnButterflyConeParticles(ServerLevel level) {
        Vec3 forward = skillDirection.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 origin = this.position().add(forward.scale(COFFIN_PARTICLE_FORWARD_OFFSET));
        SimpleParticleType particleType = (SimpleParticleType) ModParticleTypes.BUTTERFLY.get();
        for (int i = 0; i < SKILL_PARTICLES_PER_BURST; i++) {
            double angle = Math.toRadians((this.random.nextDouble() - 0.5D) * SKILL_CONE_DEGREES);
            Vec3 direction = rotateHorizontal(forward, angle);
            double lateral = (this.random.nextDouble() - 0.5D) * COFFIN_PARTICLE_WIDTH;
            double vertical = COFFIN_PARTICLE_BOTTOM + this.random.nextDouble() * COFFIN_PARTICLE_HEIGHT;
            Vec3 particlePos = origin.add(right.scale(lateral)).add(0.0D, vertical, 0.0D);
            level.sendParticles(particleType,
                    particlePos.x, particlePos.y, particlePos.z,
                    0, direction.x * SKILL_PARTICLE_SPEED,
                    (this.random.nextDouble() - 0.5D) * SKILL_PARTICLE_VERTICAL_SPEED,
                    direction.z * SKILL_PARTICLE_SPEED, 1.0D);
        }
    }

    private Vec3 rotateHorizontal(Vec3 direction, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
                direction.x * cos - direction.z * sin,
                0.0D,
                direction.x * sin + direction.z * cos
        ).normalize();
    }

    /** 蝴蝶群扇形范围伤害:前方50格、总角度42° */
    private void damageButterflyCone(ServerLevel level) {
        Vec3 start = this.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(SKILL_RANGE), this::isValidTarget)) {
            Vec3 rel = e.position().subtract(start);
            double horizontalDistance = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
            if (horizontalDistance <= 0.0D || horizontalDistance > SKILL_RANGE) continue;
            double dot = (rel.x * skillDirection.x + rel.z * skillDirection.z) / horizontalDistance;
            if (dot < SKILL_CONE_HALF_DOT) continue;
            dealWhiteDamage(e, 3 + this.random.nextInt(2));
        }
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("自己的死亡若能被人铭记乃一大幸事。");
        logs.add("这里的人们没有时间去铭记死者，只是麻木地等待下一场死亡。");
        logs.add("“亡蝶葬仪”一声不响地凝视着员工。");
        logs.add("“亡蝶葬仪”幻想着那镜花水月的希望以及锐挫望绝的终末。");
        logs.add("巨大的棺材无法取代成百上千座坟墓。");
        logs.add("员工看到一长列由白色蝴蝶组成的送葬者。");
        logs.add("蝴蝶拍打着翅膀，以一种熟悉却又陌生的方式向我们靠近。");
        logs.add("这里寸草不生，所以那些蝴蝶是从哪里来的？");
        logs.add("员工们别无选择，他们无路可回，只能继续工作。");
        logs.add("如同一场永无止境的葬礼，员工依然平静地哀悼着。");
        logs.add("员工最后一次思虑着自己的人生。");
        logs.add("想要摆脱公司回到家中？真是无稽之谈。");
        logs.add("在最好的时刻，以最好的面貌死去，乃是令人难以想象的幸福。");
        logs.add("实际上，死于此地的员工大多都希望尽可能久得活下去。");
        logs.add("有些人认为，死亡意味着全新的开始，然而死后只剩下一片空无。");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityButterflyFuneral> event) {
        String anim = getAnimation();
        switch (anim) {
            case "attack", "attack1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack")); }
            case "attack2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack2")); }
            case "skill" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skill")); }
            case "skillAB" -> { return event.setAndContinue(RawAnimation.begin().thenLoop("skillAB")); }
            case "skillB" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skillB")); }
            case "die" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("die")); }
        }
        if (hasEscape() && event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)   // 行走玩家
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkillPhase", skillPhase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skillPhase = tag.getInt("SkillPhase");
    }
}
