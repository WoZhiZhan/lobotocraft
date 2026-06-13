package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 野怪清道夫 (TETH) —— 主世界夜间刷新的小怪。
 * 80血;僵尸移速/攻击距离;三种外观;攻击玩家/动物/村民,击杀的生物无掉落;
 * 70%普通攻击(attack/attack2,3黑伤),30%特殊攻击(skill1连播5次,每次7黑伤);
 * 每次造成伤害回复等量血量;抗性 红1.0/白1.2/黑0.5/蓝0.8。
 */
public class EntityCleaner extends BaseGeoEntity {

    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityCleaner.class, EntityDataSerializers.STRING);

    private int ambientTimer = 0;
    private int skillCombo = 0;       // 特殊攻击剩余连击次数
    private int skillTickTimer = 0;
    private int animHoldTimer = 0;

    public EntityCleaner(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, "idle");
    }

    @Override
    public String name() { return "cleaner"; }

    private void setAnim(String a) {
        if (!this.level().isClientSide) this.entityData.set(ANIM, a);
    }

    private String getAnim() { return this.entityData.get(ANIM); }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType spawnType,
            net.minecraft.world.entity.SpawnGroupData data,
            CompoundTag tag) {
        // 随机三种外观之一
        String[] skins = {"cleaner", "cleaner_2", "cleaner_3"};
        this.setTexture(skins[this.random.nextInt(skins.length)]);
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // 攻击同类之外的生物:Animal 目标排除清道夫自身类型(项5:不攻击同类)
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                (living) -> !(living instanceof EntityCleaner)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
    }

    // ==================== 抗性 红1.0/白1.2/黑0.5/蓝0.8 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float multiplier = 1.0f;
        if (DamageHelper.isWhiteDamage(source)) multiplier = 1.2f;
        else if (DamageHelper.isBlackDamage(source)) multiplier = 0.5f;
        else if (DamageHelper.isBlueDamage(source)) multiplier = 0.8f;
        else if (DamageHelper.isRedDamage(source)) multiplier = 1.0f;

        boolean result = super.hurt(source, amount * multiplier);
        if (!this.level().isClientSide && result) {
            // 受伤随机播放语音
            playRandomVoice();
        }
        return result;
    }

    // ==================== 攻击 ====================

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;

        // 30%触发特殊攻击(skill1连播5次,每次7黑伤),否则普通攻击(3黑伤)
        if (skillCombo <= 0 && this.random.nextFloat() < 0.30f) {
            skillCombo = 5;
            skillTickTimer = 0;
            setAnim("skill1");
            return true;
        }

        // 普通攻击
        String atkAnim = this.random.nextBoolean() ? "attack" : "attack2";
        setAnim(atkAnim);
        animHoldTimer = 12;
        dealDamageAndHeal(living, 3f);
        playAttackSound();
        return true;
    }

    /** 造成黑色伤害并回复等量血量;击杀的生物不掉落 */
    private void dealDamageAndHeal(LivingEntity target, float damage) {
        DamageSource src = DamageHelper.getDamage(this, "black");
        boolean wasAlive = target.isAlive();
        target.hurt(src, damage);
        this.heal(damage);
        // 标记由清道夫击杀,死亡时取消掉落
        target.getPersistentData().putBoolean("cleaner_no_loot", true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // 特殊攻击连击:每12tick结算一次,共5次
        if (skillCombo > 0) {
            skillTickTimer++;
            if (skillTickTimer >= 12) {
                skillTickTimer = 0;
                skillCombo--;
                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()
                        && this.distanceToSqr(target) <= 6.0) {
                    dealDamageAndHeal(target, 7f);
                }
                // skill 音效
                this.level().playSound(null, this.blockPosition(),
                        this.random.nextBoolean() ? ModSounds.CLEANER_SKILL1.get() : ModSounds.CLEANER_SKILL2.get(),
                        SoundSource.HOSTILE, 1.0f, 1.0f);
                if (skillCombo <= 0) {
                    // 特殊攻击结束:彻底复位到待机,清掉残留的攻击动画保持计时
                    // (修复放完特殊攻击后概率"双手伸直"停留在无效默认动画的问题)
                    skillTickTimer = 0;
                    animHoldTimer = 0;
                    setAnim("idle");
                }
            }
            return;
        }

        // 攻击动画保持
        if (animHoldTimer > 0) {
            animHoldTimer--;
            if (animHoldTimer == 0 && !"skill1".equals(getAnim())) {
                setAnim(this.getDeltaMovement().horizontalDistanceSqr() > 0.001 ? "move" : "idle");
            }
        }

        // 每15秒随机播放环境语音
        ambientTimer++;
        if (ambientTimer >= 300) {
            ambientTimer = 0;
            playRandomVoice();
        }
    }

    private void playAttackSound() {
        playRandomVoice();
    }

    private void playRandomVoice() {
        SoundEvent[] voices = {
                ModSounds.CLEANER_VOICE_1.get(), ModSounds.CLEANER_VOICE_2.get(),
                ModSounds.CLEANER_VOICE_3.get(), ModSounds.CLEANER_VOICE_4.get(),
                ModSounds.CLEANER_VOICE_5.get()
        };
        this.level().playSound(null, this.blockPosition(),
                voices[this.random.nextInt(voices.length)], SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCleaner> event) {
        String anim = getAnim();
        switch (anim) {
            case "attack" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack")); }
            case "attack2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack2")); }
            case "skill1" -> { return event.setAndContinue(RawAnimation.begin().thenLoop("skill1")); }
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)    // ≈僵尸
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", getTexture());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Skin")) this.setTexture(tag.getString("Skin"));
    }
}
