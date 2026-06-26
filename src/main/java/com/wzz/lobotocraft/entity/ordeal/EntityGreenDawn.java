package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.GreenDawnEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 绿色的黎明 - 疑问。
 */
public class EntityGreenDawn extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityGreenDawn.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityGreenDawn.class, EntityDataSerializers.INT);

    private static final int NORMAL_ATTACK_ANIM_TICKS = 40;
    private static final int NORMAL_ATTACK_HIT_TICK = 16;
    private static final int SPECIAL_ATTACK_ANIM_TICKS = 45;
    private static final int SPECIAL_ATTACK_COOLDOWN_TICKS = 140;
    private static final int[] SPECIAL_HIT_TICKS = {12, 24, 36};
    private static final int IDLE_SOUND_INTERVAL_TICKS = 60;
    private static final int DEATH_ANIM_TICKS = 30;
    private static final double ATTACK_RANGE_SQR = 6.0D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private LivingEntity normalAttackTarget;
    private int normalAttackTick = 0;
    private int specialAttackTick = 0;
    private int specialAttackCooldown = 80;
    private int idleSoundTimer = IDLE_SOUND_INTERVAL_TICKS;
    private boolean countedDeath = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;

    public EntityGreenDawn(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "green_dawn";
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, "idle");
        this.entityData.define(ANIM_VERSION, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.5D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.45D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::isValidPlayerTarget));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, EntityClerk.class, 10, true, false,
                Entity::isAlive));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (deathAnimationStarted) return false;
        if (!(target instanceof LivingEntity living) || isBusyAttacking()) return true;
        if (specialAttackCooldown <= 0 && this.random.nextFloat() < 0.35f) {
            beginSpecialAttack();
            return true;
        }
        beginNormalAttack(living);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (deathAnimationStarted) {
            tickDeathAnimation();
            return;
        }

        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        tickIdleSound();

        if (specialAttackCooldown > 0) specialAttackCooldown--;

        if (specialAttackTick > 0) {
            tickSpecialAttack();
            return;
        }
        if (normalAttackTarget != null) {
            tickNormalAttack();
            return;
        }
        setAnimIfChanged(getMoveAnim());
    }

    private boolean isValidPlayerTarget(LivingEntity living) {
        return living instanceof Player player
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator();
    }

    private boolean isBusyAttacking() {
        return normalAttackTarget != null || specialAttackTick > 0;
    }

    private void beginNormalAttack(LivingEntity target) {
        normalAttackTarget = target;
        normalAttackTick = 0;
        stopAttackMovement();
        setAnim(this.random.nextBoolean() ? "attack" : "attack2");
    }

    private void tickNormalAttack() {
        stopAttackMovement();
        normalAttackTick++;
        if (normalAttackTick == NORMAL_ATTACK_HIT_TICK && canHitTarget(normalAttackTarget)) {
            if (normalAttackTarget.hurt(DamageHelper.getDamage(this, "red"), 3.0F + this.random.nextInt(3))) {
                playDamageSound();
            }
        }
        if (normalAttackTick >= NORMAL_ATTACK_ANIM_TICKS) {
            normalAttackTarget = null;
            normalAttackTick = 0;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private void beginSpecialAttack() {
        normalAttackTarget = null;
        normalAttackTick = 0;
        specialAttackTick = 1;
        specialAttackCooldown = SPECIAL_ATTACK_COOLDOWN_TICKS;
        stopAttackMovement();
        setAnim("attack3");
    }

    private void tickSpecialAttack() {
        stopAttackMovement();
        for (int hitTick : SPECIAL_HIT_TICKS) {
            if (specialAttackTick == hitTick) {
                if (hurtSpecialTargets()) {
                    playDamageSound();
                }
                break;
            }
        }

        specialAttackTick++;
        if (specialAttackTick >= SPECIAL_ATTACK_ANIM_TICKS) {
            specialAttackTick = 0;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private boolean hurtSpecialTargets() {
        if (!(this.level() instanceof ServerLevel level)) return false;
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(10.0D, 5.0D, 10.0D), this::isValidSpecialTarget)) {
            hit |= target.hurt(DamageHelper.getDamage(this, "white"), 3.0F);
        }
        return hit;
    }

    private boolean isValidSpecialTarget(LivingEntity target) {
        if (target == this || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return target instanceof EntityClerk;
    }

    private boolean canHitTarget(LivingEntity target) {
        return target != null && target.isAlive() && this.distanceToSqr(target) <= ATTACK_RANGE_SQR;
    }

    private void playDamageSound() {
        this.level().playSound(null, this.blockPosition(), ModSounds.FRAGMENT_ATTACK.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickIdleSound() {
        idleSoundTimer--;
        if (idleSoundTimer > 0) {
            return;
        }
        idleSoundTimer = IDLE_SOUND_INTERVAL_TICKS;
        this.level().playSound(null, this.blockPosition(), ModSounds.GREEN_DAWN_ATTACK.get(),
                SoundSource.HOSTILE, 0.8F, 1.0F);
    }

    private void stopAttackMovement() {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.hurtMarked = true;
    }

    private void setAnim(String animation) {
        if (this.level().isClientSide) return;
        this.entityData.set(ANIM, animation);
        this.entityData.set(ANIM_VERSION, this.entityData.get(ANIM_VERSION) + 1);
    }

    private void setAnimIfChanged(String animation) {
        if (!animation.equals(getAnim())) {
            setAnim(animation);
        }
    }

    private String getAnim() {
        return this.entityData.get(ANIM);
    }

    private int getAnimVersion() {
        return this.entityData.get(ANIM_VERSION);
    }

    private String getMoveAnim() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 0.001D ? "walk" : "idle";
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
        }
        deathAnimationStarted = true;
        deathAnimationTick = 0;
        deathFinalized = false;
        delayedDeathSource = source;
        normalAttackTarget = null;
        normalAttackTick = 0;
        specialAttackTick = 0;
        setNoAi(true);
        stopAttackMovement();
        if (!this.level().isClientSide && !countedDeath && this.level() instanceof ServerLevel level) {
            countedDeath = true;
            setAnim("death");
            GreenDawnEvent.onGreenDawnKilled(level);
        }
    }

    private void tickDeathAnimation() {
        stopAttackMovement();
        deathAnimationTick++;
        if (deathFinalized || deathAnimationTick < DEATH_ANIM_TICKS) {
            return;
        }
        deathFinalized = true;
        super.die(delayedDeathSource == null ? damageSources().generic() : delayedDeathSource);
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityGreenDawn> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "attack" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_dawn.attack"));
            case "attack2" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_dawn.attack2"));
            case "attack3" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_dawn.attack3"));
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_dawn.death"));
            case "walk" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.green_dawn.walk"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.green_dawn.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.155D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.3D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("NormalAttackTick", normalAttackTick);
        tag.putInt("SpecialAttackTick", specialAttackTick);
        tag.putInt("SpecialAttackCooldown", specialAttackCooldown);
        tag.putInt("IdleSoundTimer", idleSoundTimer);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted);
        tag.putInt("DeathAnimationTick", deathAnimationTick);
        tag.putString("Anim", getAnim());
        tag.putInt("AnimVersion", getAnimVersion());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        normalAttackTick = tag.getInt("NormalAttackTick");
        specialAttackTick = tag.getInt("SpecialAttackTick");
        specialAttackCooldown = tag.getInt("SpecialAttackCooldown");
        idleSoundTimer = tag.contains("IdleSoundTimer") ? tag.getInt("IdleSoundTimer") : IDLE_SOUND_INTERVAL_TICKS;
        countedDeath = tag.getBoolean("CountedDeath");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        if (deathAnimationStarted) {
            setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
