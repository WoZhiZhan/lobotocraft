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

    private static final int NORMAL_ATTACK_ANIM_TICKS = 18;
    private static final int NORMAL_ATTACK_HIT_TICK = 8;
    private static final int SPECIAL_ATTACK_ANIM_TICKS = 44;
    private static final int SPECIAL_ATTACK_COOLDOWN_TICKS = 140;
    private static final int[] SPECIAL_HIT_TICKS = {6, 12, 18, 24, 34};
    private static final double ATTACK_RANGE_SQR = 6.0D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private LivingEntity normalAttackTarget;
    private int normalAttackTick = 0;
    private int specialAttackTick = 0;
    private int specialAttackCooldown = 80;
    private boolean countedDeath = false;

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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9D));
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

        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        if (specialAttackCooldown > 0) specialAttackCooldown--;

        if (specialAttackTick > 0) {
            tickSpecialAttack();
            return;
        }
        if (normalAttackTarget != null) {
            tickNormalAttack();
        }
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
        setAnim(this.random.nextBoolean() ? "attack" : "attack2");
    }

    private void tickNormalAttack() {
        normalAttackTick++;
        if (normalAttackTick == NORMAL_ATTACK_HIT_TICK && canHitTarget(normalAttackTarget)) {
            normalAttackTarget.hurt(DamageHelper.getDamage(this, "red"), 3.0F + this.random.nextInt(3));
            playAttackSound();
        }
        if (normalAttackTick >= NORMAL_ATTACK_ANIM_TICKS) {
            normalAttackTarget = null;
            normalAttackTick = 0;
            setAnim(getMoveAnim());
        }
    }

    private void beginSpecialAttack() {
        normalAttackTarget = null;
        normalAttackTick = 0;
        specialAttackTick = 1;
        specialAttackCooldown = SPECIAL_ATTACK_COOLDOWN_TICKS;
        setAnim("attack3");
        playAttackSound();
    }

    private void tickSpecialAttack() {
        for (int hitTick : SPECIAL_HIT_TICKS) {
            if (specialAttackTick == hitTick) {
                hurtPlayersInSpecialRange();
                playAttackSound();
                break;
            }
        }

        specialAttackTick++;
        if (specialAttackTick >= SPECIAL_ATTACK_ANIM_TICKS) {
            specialAttackTick = 0;
            setAnim(getMoveAnim());
        }
    }

    private void hurtPlayersInSpecialRange() {
        if (!(this.level() instanceof ServerLevel level)) return;
        for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0D, 5.0D, 10.0D),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator())) {
            player.hurt(DamageHelper.getDamage(this, "white"), 3.0F);
        }
    }

    private boolean canHitTarget(LivingEntity target) {
        return target != null && target.isAlive() && this.distanceToSqr(target) <= ATTACK_RANGE_SQR;
    }

    private void playAttackSound() {
        this.level().playSound(null, this.blockPosition(), ModSounds.GREEN_DAWN_ATTACK.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void setAnim(String animation) {
        if (this.level().isClientSide) return;
        this.entityData.set(ANIM, animation);
        this.entityData.set(ANIM_VERSION, this.entityData.get(ANIM_VERSION) + 1);
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
        if (!this.level().isClientSide && !countedDeath && this.level() instanceof ServerLevel level) {
            countedDeath = true;
            setAnim("death");
            GreenDawnEvent.onGreenDawnKilled(level);
        }
        super.die(source);
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
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
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
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putString("Anim", getAnim());
        tag.putInt("AnimVersion", getAnimVersion());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        normalAttackTick = tag.getInt("NormalAttackTick");
        specialAttackTick = tag.getInt("SpecialAttackTick");
        specialAttackCooldown = tag.getInt("SpecialAttackCooldown");
        countedDeath = tag.getBoolean("CountedDeath");
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
