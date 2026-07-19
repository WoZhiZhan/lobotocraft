package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.listener.VioletNoonEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 紫罗兰的正午 - 请给我们爱！！！
 */
public class EntityVioletNoon extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityVioletNoon.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityVioletNoon.class, EntityDataSerializers.INT);

    private static final int LAND_ANIM_TICKS = 50;
    private static final int DEATH_ANIM_TICKS = 10;
    private static final int NEARBY_ATTACK_COOLDOWN_TICKS = 40;
    private static final int SPECIAL_ATTACK_INTERVAL_TICKS = 30 * 20;
    private static final int SPECIAL_ATTACK_ANIM_TICKS = 70;
    private static final int SEARCH_LIMIT = 30_000_000;
    private static final double LAND_DAMAGE_HORIZONTAL_RANGE = 6.0D;
    private static final double LAND_DAMAGE_VERTICAL_RANGE = 6.0D;
    private static final double NEARBY_ATTACK_HORIZONTAL_RANGE = 6.0D;
    private static final double NEARBY_ATTACK_VERTICAL_RANGE = 4.0D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private boolean landed = false;
    private int landAnimationTick = 0;
    private int nearbyAttackCooldown = NEARBY_ATTACK_COOLDOWN_TICKS;
    private int specialAttackCooldown = SPECIAL_ATTACK_INTERVAL_TICKS;
    private int specialAttackTick = 0;
    private boolean countedDeath = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;
    private boolean ordealSpawn = false;

    public EntityVioletNoon(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "violet_noon";
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
        this.entityData.define(ANIM, "fall");
        this.entityData.define(ANIM_VERSION, 0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        this.setGlowingTag(true);
        setAnim("fall");
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    public boolean isOrdealSpawn() {
        return ordealSpawn;
    }

    public void setOrdealSpawn(boolean ordealSpawn) {
        this.ordealSpawn = ordealSpawn;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (deathAnimationStarted) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel level)) return;

        if (deathAnimationStarted) {
            tickDeathAnimation();
            return;
        }

        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            this.setGlowingTag(true);
        }

        if (!landed) {
            tickFalling(level);
            return;
        }

        stopMovement();
        if (landAnimationTick > 0) {
            landAnimationTick--;
            if (landAnimationTick <= 0) {
                setAnimIfChanged("idle");
            }
            return;
        }

        if (specialAttackTick > 0) {
            tickSpecialAttack(level);
            return;
        }

        if (specialAttackCooldown > 0) {
            specialAttackCooldown--;
        }
        if (specialAttackCooldown <= 0) {
            beginSpecialAttack();
            return;
        }

        if (nearbyAttackCooldown > 0) {
            nearbyAttackCooldown--;
        }
        if (nearbyAttackCooldown <= 0) {
            nearbyAttackCooldown = NEARBY_ATTACK_COOLDOWN_TICKS;
            hurtNearbyTargets(level);
        }
        setAnimIfChanged("idle");
    }

    private void tickFalling(ServerLevel level) {
        setAnimIfChanged("fall");
        if (!this.onGround()) {
            return;
        }

        landed = true;
        landAnimationTick = LAND_ANIM_TICKS;
        nearbyAttackCooldown = NEARBY_ATTACK_COOLDOWN_TICKS;
        specialAttackCooldown = SPECIAL_ATTACK_INTERVAL_TICKS;
        this.setNoGravity(true);
        stopMovement();
        setAnim("land");
        spawnLandParticles(level);
        hurtLandingTargets(level);
        this.level().playSound(null, this.blockPosition(), ModSounds.VIOLET_DAWN_DEATH.get(),
                SoundSource.HOSTILE, 1.4F, 0.8F);
    }

    private void hurtLandingTargets(ServerLevel level) {
        AABB range = this.getBoundingBox().inflate(LAND_DAMAGE_HORIZONTAL_RANGE, LAND_DAMAGE_VERTICAL_RANGE,
                LAND_DAMAGE_HORIZONTAL_RANGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, range, this::isValidLandingTarget)) {
            target.hurt(DamageHelper.getDamage(this, "red"), 100.0F);
        }
    }

    private void hurtNearbyTargets(ServerLevel level) {
        boolean hit = false;
        AABB range = this.getBoundingBox().inflate(NEARBY_ATTACK_HORIZONTAL_RANGE, NEARBY_ATTACK_VERTICAL_RANGE,
                NEARBY_ATTACK_HORIZONTAL_RANGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, range, this::isValidNearbyTarget)) {
            if (target.hurt(DamageHelper.getDamage(this, "black"), 1.0F)) {
                spawnAttackParticles(level, target);
                hit = true;
            }
        }
        if (hit) {
            this.level().playSound(null, this.blockPosition(), ModSounds.VIOLET_DAWN_ATTACK.get(),
                    SoundSource.HOSTILE, 0.8F, 0.9F);
        }
    }

    private boolean isValidLandingTarget(LivingEntity target) {
        if (target == this || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private boolean isValidNearbyTarget(LivingEntity target) {
        if (target == this || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return target instanceof EntityClerk;
    }

    private void beginSpecialAttack() {
        specialAttackTick = 1;
        specialAttackCooldown = SPECIAL_ATTACK_INTERVAL_TICKS;
        setAnim("charge");
        stopMovement();
    }

    private void tickSpecialAttack(ServerLevel level) {
        stopMovement();
        if (specialAttackTick % 5 == 0) {
            spawnChargeParticles(level);
        }
        specialAttackTick++;
        if (specialAttackTick <= SPECIAL_ATTACK_ANIM_TICKS) {
            return;
        }

        decreaseRandomAbnormalityCounter(level);
        specialAttackTick = 0;
        nearbyAttackCooldown = NEARBY_ATTACK_COOLDOWN_TICKS;
        setAnim("idle");
    }

    private void decreaseRandomAbnormalityCounter(ServerLevel level) {
        AABB whole = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        List<AbstractAbnormality> candidates = new ArrayList<>(level.getEntitiesOfClass(AbstractAbnormality.class,
                whole, abnormality -> abnormality.isAlive()
                        && abnormality.canEscape()
                        && !abnormality.hasEscape()
                        && abnormality.getQliphothCounter() > 0));
        if (candidates.isEmpty()) {
            return;
        }
        Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        candidates.get(0).decreaseQliphothCounter(1);
    }

    private void spawnLandParticles(ServerLevel level) {
        double y = this.getY() + 0.3D;
        level.sendParticles(ParticleTypes.SMOKE,
                this.getX(), y, this.getZ(), 90, 4.0D, 0.35D, 4.0D, 0.05D);
        level.sendParticles(ParticleUtil.getDustParticle(0.45F, 0.0F, 0.75F, 1.8F),
                this.getX(), y, this.getZ(), 140, 4.0D, 0.4D, 4.0D, 0.04D);
    }

    private void spawnAttackParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleUtil.getDustParticle(0.55F, 0.08F, 0.9F, 1.1F),
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                10, 0.25D, 0.35D, 0.25D, 0.02D);
    }

    private void spawnChargeParticles(ServerLevel level) {
        double y = this.getY() + this.getBbHeight() * 0.5D;
        level.sendParticles(ParticleUtil.getDustParticle(0.6F, 0.0F, 0.95F, 1.6F),
                this.getX(), y, this.getZ(), 28, 2.8D, 1.6D, 2.8D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD,
                this.getX(), y, this.getZ(), 8, 2.2D, 1.2D, 2.2D, 0.01D);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            notifyKilled(level);
        }
        beginDeathAnimation(source);
    }

    private void notifyKilled(ServerLevel level) {
        if (ordealSpawn && !countedDeath) {
            countedDeath = true;
            VioletNoonEvent.onVioletNoonKilled(level);
        }
    }

    private void beginDeathAnimation(DamageSource source) {
        deathAnimationStarted = true;
        deathAnimationTick = 0;
        deathFinalized = false;
        delayedDeathSource = source;
        this.setHealth(1.0F);
        this.setNoAi(true);
        this.setNoGravity(true);
        stopMovement();
        setAnim("death");
    }

    private void tickDeathAnimation() {
        stopMovement();
        deathAnimationTick++;
        if (deathFinalized || deathAnimationTick < DEATH_ANIM_TICKS) {
            return;
        }
        deathFinalized = true;
        this.setHealth(0.0F);
        super.die(delayedDeathSource == null ? damageSources().generic() : delayedDeathSource);
        this.remove(Entity.RemovalReason.KILLED);
    }

    private void stopMovement() {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, landed ? 0.0D : this.getDeltaMovement().y, 0.0D);
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

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityVioletNoon> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "fall" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.violet_noon.fall"));
            case "land" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.violet_noon.land"));
            case "charge" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.violet_noon.charge"));
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.violet_noon.death"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.violet_noon.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 350.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Landed", landed);
        tag.putInt("LandAnimationTick", landAnimationTick);
        tag.putInt("NearbyAttackCooldown", nearbyAttackCooldown);
        tag.putInt("SpecialAttackCooldown", specialAttackCooldown);
        tag.putInt("SpecialAttackTick", specialAttackTick);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted);
        tag.putInt("DeathAnimationTick", deathAnimationTick);
        tag.putBoolean("OrdealSpawn", ordealSpawn);
        tag.putString("Anim", getAnim());
        tag.putInt("AnimVersion", getAnimVersion());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        landed = tag.getBoolean("Landed");
        landAnimationTick = tag.getInt("LandAnimationTick");
        nearbyAttackCooldown = tag.contains("NearbyAttackCooldown")
                ? tag.getInt("NearbyAttackCooldown")
                : NEARBY_ATTACK_COOLDOWN_TICKS;
        specialAttackCooldown = tag.contains("SpecialAttackCooldown")
                ? tag.getInt("SpecialAttackCooldown")
                : SPECIAL_ATTACK_INTERVAL_TICKS;
        specialAttackTick = tag.getInt("SpecialAttackTick");
        countedDeath = tag.getBoolean("CountedDeath");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        ordealSpawn = tag.getBoolean("OrdealSpawn");
        if (landed || deathAnimationStarted) {
            this.setNoGravity(true);
        }
        if (deathAnimationStarted) {
            this.setHealth(1.0F);
            this.setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
