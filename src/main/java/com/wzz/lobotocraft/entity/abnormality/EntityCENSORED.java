package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;
import java.util.List;

public class EntityCENSORED extends AbstractAbnormality {
    private static final int ATTACK_ANIMATION_TICKS = 40;
    private static final int ATTACK_HIT_TICK = 24;
    private static final int SPECIAL_ANIMATION_TICKS = 60;
    private static final int MENTAL_HIT_COUNT = 6;
    private static final int MENTAL_HIT_INTERVAL = SPECIAL_ANIMATION_TICKS / MENTAL_HIT_COUNT;
    private static final double MENTAL_AOE_HALF_XZ = 6.0D;
    private static final double MENTAL_AOE_HALF_Y = 3.0D;
    private static final int TOMBSTONE_SCAN_RANGE_XZ = 20;
    private static final int TOMBSTONE_SCAN_RANGE_Y = 6;
    private static final int TOMBSTONE_SCAN_INTERVAL = 10;
    private static final double TOMBSTONE_TRIGGER_DISTANCE = 2.0D;
    private static final double TOMBSTONE_TRIGGER_DISTANCE_Y = 1.5D;

    private int attackTimer = 0;
    private LivingEntity attackTarget = null;
    private int specialAttackTimer = 0;
    private BlockPos specialAttackSpawnPos = null;
    private int mentalHitsDealt = 0;
    private int targetReevaluationCooldown = 0;

    private BlockPos cachedTombstone = null;
    private int lastTombstoneScan = 0;
    private boolean seekingTombstone = false;

    public EntityCENSORED(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-03-89";
        this.abnormalityName = "§4CENSORED";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 28;

        float[] basePreferences = {0.35f, 0.40f, 0.25f, 0.10f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public String name() {
        return "censored";
    }

    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.CENSORED_ATTACK.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private boolean isValidVictim(LivingEntity entity) {
        if (entity == this || !entity.isAlive()) return false;
        if (entity instanceof EntityCENSORED1) return false;
        if (entity instanceof AbstractAbnormality abnormality) return abnormality.hasEscape();
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!hasEscape() || specialAttackTimer > 0 || !(target instanceof LivingEntity living)) {
            return true;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (attackTimer > 0) {
            return true;
        }
        this.attackTimer = ATTACK_ANIMATION_TICKS;
        this.attackTarget = living;
        this.setAnimation("attack");
        playAttackSound();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!hasEscape()) {
            setNoAi(true);
            setTarget(null);
            getNavigation().stop();
            attackTimer = 0;
            attackTarget = null;
            specialAttackTimer = 0;
            specialAttackSpawnPos = null;
            seekingTombstone = false;
            setAnimation("idle");
            return;
        }

        setNoAi(false);

        if (attackTimer > 0) {
            attackTimer--;
            int elapsed = ATTACK_ANIMATION_TICKS - attackTimer;
            if (elapsed == ATTACK_HIT_TICK && canHitAttackTarget()) {
                float damage = 23 + this.random.nextInt(4);
                EntityUtil.clearHurtTime(attackTarget, () ->
                        attackTarget.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage));
                if (!attackTarget.isAlive() && !(attackTarget instanceof AbstractAbnormality)) {
                    startSpecialAttack(attackTarget.blockPosition());
                }
            }
            if (attackTimer == 0) {
                attackTarget = null;
                if (specialAttackTimer == 0) {
                    setAnimation("idle");
                }
            }
        }

        if (specialAttackTimer > 0) {
            specialAttackTimer--;
            int elapsed = SPECIAL_ANIMATION_TICKS - specialAttackTimer;
            if (elapsed > 0 && elapsed % MENTAL_HIT_INTERVAL == 0 && mentalHitsDealt < MENTAL_HIT_COUNT) {
                mentalHitsDealt++;
                applyMentalAreaDamage();
            }
            if (specialAttackTimer == 0) {
                spawnCensored1(specialAttackSpawnPos);
                specialAttackSpawnPos = null;
                setAnimation("idle");
            }
            return;
        }

        if (attackTimer == 0) {
            boolean moving = getNavigation().isInProgress()
                    || getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D;
            String anim = moving ? "move" : "idle";
            if (!anim.equals(getAnimation())) {
                setAnimation(anim);
            }
        }

        BlockPos tombstone = findNearestTombstone();
        if (tombstone != null) {
            if (isAdjacentToTombstone(tombstone)) {
                startSpecialAttack(tombstone);
                return;
            }
            if (!seekingTombstone || getNavigation().isDone()) {
                seekingTombstone = getNavigation().moveTo(
                        tombstone.getX() + 0.5D, tombstone.getY(), tombstone.getZ() + 0.5D, 1.0D);
            }
            getLookControl().setLookAt(tombstone.getX() + 0.5D, tombstone.getY(), tombstone.getZ() + 0.5D);
            setTarget(null);
            return;
        }
        seekingTombstone = false;

        if (targetReevaluationCooldown > 0) {
            targetReevaluationCooldown--;
            return;
        }
        targetReevaluationCooldown = 20;
        if (getTarget() != null && getTarget().isAlive()) {
            return;
        }
        AbstractAbnormality nearestAbnormality = this.level().getEntitiesOfClass(
                        AbstractAbnormality.class,
                        this.getBoundingBox().inflate(32.0),
                        abnormality -> abnormality != this
                                && abnormality.hasEscape()
                                && !abnormality.isBirdOfBlackForest()
                                && abnormality.isAlive()
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (nearestAbnormality != null) {
            setTarget(nearestAbnormality);
            return;
        }
        LivingEntity nearestVictim = this.level().getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(32.0),
                        this::isValidVictim
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearestVictim);
    }

    private boolean canHitAttackTarget() {
        return attackTarget != null
                && attackTarget.isAlive()
                && this.distanceToSqr(attackTarget) <= 16.0D;
    }

    private void startSpecialAttack(BlockPos spawnPos) {
        if (spawnPos == null) return;
        this.specialAttackTimer = SPECIAL_ANIMATION_TICKS;
        this.mentalHitsDealt = 0;
        this.specialAttackSpawnPos = spawnPos.immutable();
        this.seekingTombstone = false;
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAnimation("special_attack");

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.CENSORED_SACRIFICE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

        BlockState state = this.level().getBlockState(spawnPos);
        if (state.is(ModBlocks.TOMBSTONE.get())) {
            this.level().destroyBlock(spawnPos, false);
        }
        cachedTombstone = null;
        lastTombstoneScan = this.tickCount - TOMBSTONE_SCAN_INTERVAL;
    }

    private void applyMentalAreaDamage() {
        float damage = 12 + this.random.nextInt(6);
        DamageSource white = DamageHelper.getDamage(this, "lobotocraft:white");
        AABB box = this.getBoundingBox().inflate(MENTAL_AOE_HALF_XZ, MENTAL_AOE_HALF_Y, MENTAL_AOE_HALF_XZ);
        List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity != this
                        && entity.isAlive()
                        && !(entity instanceof AbstractAbnormality)
                        && !(entity instanceof EntityCENSORED1)
                        && (!(entity instanceof Player player) || (!player.isCreative() && !player.isSpectator())));
        for (LivingEntity victim : victims) {
            EntityUtil.clearHurtTime(victim, () -> victim.hurt(white, damage));
        }
    }

    private void spawnCensored1(BlockPos spawnPos) {
        if (!(this.level() instanceof ServerLevel serverLevel) || spawnPos == null) return;
        EntityCENSORED1 child = ModEntities.censored1.get().create(serverLevel);
        if (child == null) return;
        child.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                this.random.nextFloat() * 360.0F, 0.0F);
        child.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        child.setPersistenceRequired();
        serverLevel.addFreshEntity(child);
    }

    private BlockPos findNearestTombstone() {
        if (level().isClientSide) return null;
        if (cachedTombstone != null) {
            if (level().getBlockState(cachedTombstone).is(ModBlocks.TOMBSTONE.get())) {
                if (tickCount - lastTombstoneScan < TOMBSTONE_SCAN_INTERVAL) return cachedTombstone;
            } else {
                cachedTombstone = null;
            }
        }
        if (tickCount - lastTombstoneScan < TOMBSTONE_SCAN_INTERVAL) return cachedTombstone;
        lastTombstoneScan = tickCount;

        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -TOMBSTONE_SCAN_RANGE_XZ; dx <= TOMBSTONE_SCAN_RANGE_XZ; dx++) {
            for (int dz = -TOMBSTONE_SCAN_RANGE_XZ; dz <= TOMBSTONE_SCAN_RANGE_XZ; dz++) {
                for (int dy = -TOMBSTONE_SCAN_RANGE_Y; dy <= TOMBSTONE_SCAN_RANGE_Y; dy++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level().getBlockState(m).is(ModBlocks.TOMBSTONE.get())) {
                        double dsq = m.distSqr(origin);
                        if (dsq < bestSq) { bestSq = dsq; best = m.immutable(); }
                    }
                }
            }
        }
        cachedTombstone = best;
        return best;
    }

    private boolean isAdjacentToTombstone(BlockPos tombstone) {
        double dx = this.getX() - (tombstone.getX() + 0.5D);
        double dy = this.getY() - tombstone.getY();
        double dz = this.getZ() - (tombstone.getZ() + 0.5D);
        return dx * dx + dz * dz <= TOMBSTONE_TRIGGER_DISTANCE * TOMBSTONE_TRIGGER_DISTANCE
                && Math.abs(dy) <= TOMBSTONE_TRIGGER_DISTANCE_Y;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 23 + this.random.nextInt(4));
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void die(DamageSource source) {
        setAnimation("idle");
        super.die(source);
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityCENSORED((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCENSORED> event) {
        String anim = getAnimation();
        if ("attack".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("attack"));
        }
        if ("special_attack".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("special_attack"));
        }
        if ("move".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 24.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(com.wzz.lobotocraft.init.ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(com.wzz.lobotocraft.init.ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.4D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.8D);
    }
}
