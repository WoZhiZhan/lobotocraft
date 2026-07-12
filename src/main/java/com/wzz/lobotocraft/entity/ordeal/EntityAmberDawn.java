package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.AmberDawnEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 琥珀色的黎明 - 新鲜的食物。
 */
public class EntityAmberDawn extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityAmberDawn.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityAmberDawn.class, EntityDataSerializers.INT);

    private static final int TELEPORT_INTERVAL_TICKS = 40 * 20;
    private static final int ATTACK_ANIM_TICKS = 20;
    private static final int ATTACK_HIT_TICK = 12;
    private static final int ATTACK_COOLDOWN_TICKS = 22;
    private static final int DIG_IN_TICKS = 21;
    private static final int DIG_OUT_TICKS = 20;
    private static final int DEATH_ANIM_TICKS = 20;
    private static final double HOME_RANGE = 16.0D;
    private static final double HOME_RANGE_SQR = HOME_RANGE * HOME_RANGE;
    private static final double HARD_HOME_RANGE_SQR = 22.0D * 22.0D;
    private static final double ATTACK_RANGE_SQR = 2.25D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private BlockPos homePos;
    private int teleportTimer = TELEPORT_INTERVAL_TICKS;
    private int attackTick = 0;
    private int attackCooldown = 0;
    private LivingEntity attackTarget;
    private int digTick = 0;
    private boolean diggingOut = false;
    private boolean countedDeath = false;
    private boolean ordealSpawn = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;

    public EntityAmberDawn(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "amber_dawn";
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::isValidTarget));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, EntityClerk.class, 10, true, false,
                this::isValidTarget));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        if (homePos == null) {
            homePos = this.blockPosition();
        }
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    public boolean isOrdealSpawn() {
        return ordealSpawn;
    }

    public void setOrdealSpawn(boolean ordealSpawn) {
        this.ordealSpawn = ordealSpawn;
    }

    public void setHomePos(BlockPos homePos) {
        this.homePos = homePos == null ? null : homePos.immutable();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (deathAnimationStarted || isBusy() || attackCooldown > 0) {
            return false;
        }
        if (!(target instanceof LivingEntity living) || !isValidTarget(living)) {
            return false;
        }
        attackTarget = living;
        attackTick = 1;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        stopMovement();
        setAnim("attack");
        return true;
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
        }
        if (homePos == null) {
            homePos = this.blockPosition();
        }
        if (attackCooldown > 0) attackCooldown--;

        if (digTick > 0) {
            tickDig(level);
            return;
        }
        if (attackTick > 0) {
            tickAttack();
            return;
        }

        keepInsideHome(level);
        teleportTimer--;
        if (teleportTimer <= 0) {
            beginDig(level);
            return;
        }

        setAnimIfChanged(getMoveAnim());
    }

    private boolean isValidTarget(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (!(target instanceof Player) && !(target instanceof EntityClerk)) {
            return false;
        }
        return homePos == null || target.blockPosition().distSqr(homePos) <= HOME_RANGE_SQR;
    }

    private boolean isBusy() {
        return attackTick > 0 || digTick > 0;
    }

    private void keepInsideHome(ServerLevel level) {
        if (homePos == null) return;
        LivingEntity target = this.getTarget();
        if (!isValidTarget(target)) {
            this.setTarget(null);
        }
        double distance = this.blockPosition().distSqr(homePos);
        if (distance <= HOME_RANGE_SQR) {
            return;
        }
        BlockPos returnPos = AmberDawnEvent.findAmberSpawnPosition(level, this, homePos, 3);
        if (returnPos == null) {
            returnPos = homePos;
        }
        if (distance >= HARD_HOME_RANGE_SQR) {
            this.teleportTo(returnPos.getX() + 0.5D, returnPos.getY(), returnPos.getZ() + 0.5D);
        } else {
            this.getNavigation().moveTo(returnPos.getX() + 0.5D, returnPos.getY(), returnPos.getZ() + 0.5D, 1.2D);
        }
        setAnimIfChanged("move");
    }

    private void tickAttack() {
        stopMovement();
        if (attackTick == ATTACK_HIT_TICK && canHitTarget(attackTarget)) {
            if (attackTarget.hurt(DamageHelper.getDamage(this, "red"), 1.0F + this.random.nextInt(2))) {
                this.level().playSound(null, this.blockPosition(), ModSounds.AMBER_DAWN_ATTACK.get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
                moveBehindTarget(attackTarget);
            }
        }
        attackTick++;
        if (attackTick > ATTACK_ANIM_TICKS) {
            attackTick = 0;
            attackTarget = null;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private boolean canHitTarget(@Nullable LivingEntity target) {
        return target != null && isValidTarget(target) && this.distanceToSqr(target) <= ATTACK_RANGE_SQR;
    }

    private void moveBehindTarget(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 behind = new Vec3(target.getX() - look.x * 1.1D, target.getY(), target.getZ() - look.z * 1.1D);
        double halfWidth = this.getBbWidth() / 2.0D;
        AABB box = new AABB(
                behind.x - halfWidth, behind.y, behind.z - halfWidth,
                behind.x + halfWidth, behind.y + this.getBbHeight(), behind.z + halfWidth);
        if (this.level().noCollision(this, box)) {
            this.teleportTo(behind.x, behind.y, behind.z);
        }
    }

    private void beginDig(ServerLevel level) {
        BlockPos destination = AmberDawnEvent.chooseOtherEscapeSpawnPosition(level, this, homePos);
        if (destination == null) {
            teleportTimer = TELEPORT_INTERVAL_TICKS;
            return;
        }
        stopMovement();
        this.setTarget(null);
        this.setNoAi(true);
        digTick = 1;
        diggingOut = false;
        setHomePos(destination);
        setAnim("digging_in");
        level.playSound(null, this.blockPosition(), ModSounds.AMBER_DAWN_DIG.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickDig(ServerLevel level) {
        stopMovement();
        if (!diggingOut && digTick >= DIG_IN_TICKS) {
            BlockPos destination = homePos == null ? this.blockPosition() : homePos;
            this.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
            level.playSound(null, this.blockPosition(), ModSounds.AMBER_DAWN_DIG.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            diggingOut = true;
            digTick = 1;
            setAnim("digging_out");
            return;
        }
        if (diggingOut && digTick >= DIG_OUT_TICKS) {
            digTick = 0;
            diggingOut = false;
            teleportTimer = TELEPORT_INTERVAL_TICKS;
            this.setNoAi(false);
            setAnimIfChanged(getMoveAnim());
            return;
        }
        digTick++;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
        }
        if (this.level() instanceof ServerLevel level && ordealSpawn && !countedDeath) {
            countedDeath = true;
            AmberDawnEvent.onAmberDawnKilled(level);
        }
        deathAnimationStarted = true;
        deathAnimationTick = 0;
        deathFinalized = false;
        delayedDeathSource = source;
        attackTick = 0;
        digTick = 0;
        attackTarget = null;
        this.setHealth(1.0F);
        this.setTarget(null);
        this.setNoAi(true);
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
        return this.getNavigation().isInProgress() || this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D
                ? "move"
                : "idle";
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityAmberDawn> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "attack" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.amber_dawn.attack"));
            case "digging_in" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.amber_dawn.digging_in"));
            case "digging_out" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.amber_dawn.digging_out"));
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.amber_dawn.death"));
            case "move" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.amber_dawn.move"));
            default -> PlayState.STOP;
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 35.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 18.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.putInt("HomeX", homePos.getX());
            tag.putInt("HomeY", homePos.getY());
            tag.putInt("HomeZ", homePos.getZ());
        }
        tag.putInt("TeleportTimer", teleportTimer);
        tag.putInt("AttackTick", attackTick);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("DigTick", digTick);
        tag.putBoolean("DiggingOut", diggingOut);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("OrdealSpawn", ordealSpawn);
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted);
        tag.putInt("DeathAnimationTick", deathAnimationTick);
        tag.putString("Anim", getAnim());
        tag.putInt("AnimVersion", getAnimVersion());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            homePos = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        }
        teleportTimer = tag.contains("TeleportTimer") ? tag.getInt("TeleportTimer") : TELEPORT_INTERVAL_TICKS;
        attackTick = tag.getInt("AttackTick");
        attackCooldown = tag.getInt("AttackCooldown");
        digTick = tag.getInt("DigTick");
        diggingOut = tag.getBoolean("DiggingOut");
        countedDeath = tag.getBoolean("CountedDeath");
        ordealSpawn = tag.getBoolean("OrdealSpawn");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        if (deathAnimationStarted || digTick > 0) {
            this.setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
