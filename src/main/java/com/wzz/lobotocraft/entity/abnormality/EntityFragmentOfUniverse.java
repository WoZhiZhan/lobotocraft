package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

/**
 * 宇宙碎片 (O-03-60) —— TETH 级异想体。
 * 须知:工作结果良→50%倒计时后计数器-1;差→70%倒计时后-1;工作员工恐慌→立刻-1。
 * 出逃(230血/行走速1/抗性红1.0白1.5黑1.0蓝2.0):
 *  触手攻击 2-4 黑伤(attack1/attack2 各40%);
 *  特殊攻击(20%):停止移动歌唱,每秒对20x20范围生物造成5点白伤,头顶音符粒子扩散。
 */
public class EntityFragmentOfUniverse extends AbstractAbnormality {

    private int attackCooldown = 0;
    private int pendingHit = 0;
    private LivingEntity pendingTarget = null;
    private int singTimer = 0;      // >0 表示歌唱中
    private boolean transformed = false; // 出逃变形动画是否播完

    public EntityFragmentOfUniverse(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-03-60";
        this.abnormalityName = "宇宙碎片";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "BLACK";
        this.maxPEOutput = 12;
        float[] basePreferences = {0.30f, 0.40f, 0.60f, 0.50f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.30f, 0.30f, 0.20f, 0.20f, 0.20f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.40f, 0.40f, 0.30f, 0.30f, 0.30f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.60f, 0.60f, 0.50f, 0.50f, 0.50f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.50f, 0.50f, 0.40f, 0.40f, 0.40f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 5),                       // Ⅰ 速度+5
                new ObservationLevelBonus(0.05f, 0, true, false, false),  // Ⅱ 成功率+5%、饰品
                new ObservationLevelBonus(0.0f, 5, false, true, true),    // Ⅲ 速度+5、护甲武器
                new ObservationLevelBonus(0.05f, 0)                       // Ⅳ 成功率+5%
        };
    }

    @Override
    public int getBasicInfoCost() { return 12; }
    @Override
    public int getSensitiveInfoCost() { return 12; }
    @Override
    public int getManualCost(int manualIndex) { return 4; }
    @Override
    public int getWorkPreferencesCost() { return 4; }

    @Override
    public String getAbnormalityCode() { return "O-03-60"; }
    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }
    @Override
    public String name() { return "fragment_of_the_universe"; }

    // ==================== 管理须知 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // Ⅰ:良→50% 倒计时后减少 / Ⅱ:差→70% 倒计时后减少
        if (result == WorkResult.NORMAL && this.random.nextFloat() < 0.50f) {
            decreaseQliphothCounter(1);
        } else if (result == WorkResult.BAD && this.random.nextFloat() < 0.70f) {
            decreaseQliphothCounter(1);
        }
        // Ⅲ:工作员工恐慌→立刻减少
        if (MentalValueUtil.getMentalValue(player) <= 0) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float dmg = 1 + this.random.nextInt(3); // 黑伤1-3
        player.hurt(DamageHelper.getDamage(this, "black"), dmg);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        boolean wasEscaped = hasEscape();
        super.triggerEscape();
        if (!wasEscaped && hasEscape()) {
            // 播放变形动画
            transformed = false;
            setAnimation("transform");
        }
    }

    // ==================== 出逃抗性:红1.0 白1.5 黑1.0 蓝2.0 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (hasEscape()) {
            float m = 1.0f;
            if (DamageHelper.isWhiteDamage(source)) m = 1.5f;
            else if (DamageHelper.isBlueDamage(source)) m = 2.0f;
            amount *= m;
        }
        return super.hurt(source, amount);
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();
        if (!hasEscape()) return;

        // 变形动画(约2秒)
        if (!transformed) {
            if (this.tickCount % 40 == 0) {
                transformed = true;
                setAnimation("idle");
            } else {
                return;
            }
        }

        if (attackCooldown > 0) attackCooldown--;

        // 触手攻击出伤帧
        if (pendingHit > 0) {
            pendingHit--;
            if (pendingHit == 0 && pendingTarget != null && pendingTarget.isAlive()
                    && this.distanceToSqr(pendingTarget) <= 16) {
                level.playSound(null, blockPosition(), ModSounds.FRAGMENT_ATTACK.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
                pendingTarget.hurt(DamageHelper.getDamage(this, "black"), 2 + random.nextInt(3));
                pendingTarget = null;
                setAnimation("idle");
            }
        }

        // 歌唱中
        if (singTimer > 0) {
            singTimer--;
            this.setDeltaMovement(0, getDeltaMovement().y, 0);
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 255, false, false));
            // 音符粒子从头顶触角向外扩散
            if (singTimer % 4 == 0) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.sendParticles(ParticleTypes.NOTE,
                        getX(), getY() + getBbHeight() + 0.3, getZ(),
                        1, Math.cos(angle) * 0.5, 0.2, Math.sin(angle) * 0.5, 1.0);
            }
            // 每秒对20x20范围5点白伤
            if (singTimer % 20 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(10, 5, 10), this::isValidTarget)) {
                    if (e instanceof ServerPlayer sp) {
                        MentalValueUtil.reduceMentalValue(sp, 5f);
                    } else {
                        e.hurt(DamageHelper.getDamage(this, "white"), 5f);
                    }
                }
            }
            if (singTimer == 0) setAnimation("idle");
            return;
        }

        // 索敌:遭遇玩家或其他出逃异想体
        if (attackCooldown <= 0 && pendingHit <= 0) {
            LivingEntity target = findTarget(level);
            if (target != null && this.distanceToSqr(target) <= 9) {
                float roll = random.nextFloat();
                if (roll < 0.40f) {
                    setAnimation("attack1");
                    beginAttack(target);
                } else if (roll < 0.80f) {
                    setAnimation("attack2");
                    beginAttack(target);
                } else {
                    // 特殊攻击:歌唱5秒
                    singTimer = 5 * 20;
                    setAnimation("sing");
                    level.playSound(null, blockPosition(), ModSounds.FRAGMENT_SING.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
                    attackCooldown = 8 * 20;
                }
            } else if (target != null) {
                this.getNavigation().moveTo(target, 1.0);
            }
        }
    }

    private void beginAttack(LivingEntity target) {
        pendingTarget = target;
        pendingHit = 8;
        attackCooldown = 40;
        this.getLookControl().setLookAt(target);
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e instanceof AbstractAbnormality ab) return ab.hasEscape(); // 攻击其他出逃异想体
        return false;
    }

    private LivingEntity findTarget(ServerLevel level) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(16), this::isValidTarget)) {
            double d = e.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("眼前响起了悦耳的歌声，它正慢慢朝你靠近...");
        logs.add("员工听到了一段从未听过却又无比熟悉的旋律。");
        logs.add("它的歌声让人想起遥远的故乡。");
        logs.add("没有人知道它来自宇宙的哪个角落。");
        return logs;
    }

    // ==================== 动画 ====================

    private static final String P = "animation.fragmentoftheuniverse.";

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityFragmentOfUniverse> event) {
        String anim = getAnimation();
        switch (anim) {
            case "transform" -> { return event.setAndContinue(RawAnimation.begin().thenPlay(P + "transform")); }
            case "attack1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay(P + "attack1")); }
            case "attack2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay(P + "attack2")); }
            case "sing" -> { return event.setAndContinue(RawAnimation.begin().thenLoop(P + "sing")); }
        }
        if (!hasEscape()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop(P + "closeup"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop(P + "move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop(P + "idle"));
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityFragmentOfUniverse((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 230.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Transformed", transformed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        transformed = tag.getBoolean("Transformed");
    }
}
