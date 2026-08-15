package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.ClerkTombstoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;
import java.util.List;

/** Warm-Hearted Woodsman / Lumberjack (F-05-32). */
public class EntityLumberjack extends AbstractAbnormality {
    private static final int STATE_CONTAINED = 0;
    private static final int STATE_ESCAPE_PREP = 1;
    private static final int STATE_ESCAPED = 2;
    private static final int STATE_ATTACK = 3;
    private static final int STATE_INTERACT = 4;
    private static final int STATE_DYING = 5;

    private static final int ESCAPE_PREP_TICKS = 70;
    private static final int INTERACT_TICKS = 100;
    private static final int DYING_TICKS = 80;
    private static final int POST_ACTION_DELAY_TICKS = 20;
    private static final double SEARCH_RANGE = 20.0D;
    private static final double ATTACK_START_RANGE = 3.5D;
    private static final double TOMBSTONE_RADIUS = 4.0D;
    private static final int SPECIAL_ATTACK_CHANCE_PERCENT = 35;

    private int state = STATE_CONTAINED;
    private int actionTimer;
    private int actionElapsed;
    private int actionCooldown;
    private int specialCooldown;
    private int lastProcessedHit = -1;
    private float lockedYaw;
    private String activeAttack = "";
    private BlockPos selectedTombstone;
    private boolean sacrificeArmed;
    private boolean deathReturnRequested;
    private String lastClientAnimation = "";

    public EntityLumberjack(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        setResistance(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.5D);
        setResistance(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D);
        setResistance(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D);
        setResistance(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }

    private void setResistance(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "F-05-32";
        this.abnormalityName = "热心的樵夫";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "WHITE";
        this.maxPEOutput = 18;
        this.workPreferences = new float[]{0.5F, 0.5F, 0.5F, 0.5F};
        initializeQliphothCounter(1);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public float[][] getFullWorkPreferences() {
        return new float[][]{
                {0.5F, 0.5F, 0.5F, 0.5F, 0.5F},
                {0.5F, 0.5F, 0.5F, 0.5F, 0.5F},
                {0.5F, 0.5F, 0.7F, 0.7F, 0.9F},
                {0.5F, 0.5F, 0.5F, 0.5F, 0.5F}
        };
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        // E.G.O unlock flags stay disabled until the missing equipment is supplied.
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0F, 4),
                new ObservationLevelBonus(0.04F, 0),
                new ObservationLevelBonus(0.0F, 4),
                new ObservationLevelBonus(0.04F, 0)
        };
    }

    @Override public int getBasicInfoCost() { return 16; }
    @Override public int getSensitiveInfoCost() { return 16; }
    @Override public int getManualCost(int manualIndex) { return 6; }
    @Override public int getWorkPreferencesCost() { return 5; }
    @Override public int getGoodWorkResultMin() { return 15; }
    @Override public int getNormalWorkResultMin() { return 9; }
    @Override public String name() { return "lumberjack"; }
    @Override public boolean canEscape() { return true; }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        if (state == STATE_ESCAPE_PREP || state == STATE_DYING || hasEscape()) return false;
        if (sacrificeArmed && AbnormalityWorkRules.isAiEmployee(player)) {
            beginSacrifice(player);
            return false;
        }
        return super.onWorkStart(player, workType);
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        int[] temperanceLevel = {1};
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> temperanceLevel[0] = stats.getTemperanceLevel());
        if (getQliphothCounter() > 0 && (result == WorkResult.BAD || temperanceLevel[0] > 2)) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // WorkManager invokes this before onWorkComplete. The woodsman's bad-result
        // rule is handled there so the counter and meltdown event fire exactly once.
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        player.hurt(DamageHelper.getDamage(this, "white"), 3.0F + this.random.nextInt(3));
    }

    @Override
    public void onQliphothMeltdown() {
        sacrificeArmed = true;
    }

    private void beginSacrifice(ServerPlayer employee) {
        sacrificeArmed = false;
        state = STATE_ESCAPE_PREP;
        actionTimer = ESCAPE_PREP_TICKS;
        actionElapsed = 0;
        setAnimation("attack1");
        stopActionMovement();
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), ModSounds.LUMBERJACK_ESCAPE.get(),
                    SoundSource.HOSTILE, 1.4F, 1.0F);
        }
        employee.hurt(employee.damageSources().genericKill(), Float.MAX_VALUE);
        if (employee.isAlive()) employee.kill();
    }

    @Override
    public void triggerEscape() {
        if (hasEscape() || state == STATE_DYING) return;
        startActualEscape();
    }

    private void startActualEscape() {
        super.triggerEscape();
        if (!hasEscape()) return;
        state = STATE_ESCAPED;
        actionTimer = 0;
        actionElapsed = 0;
        actionCooldown = POST_ACTION_DELAY_TICKS;
        specialCooldown = randomSpecialCooldown();
        activeAttack = "";
        selectedTombstone = null;
        deathReturnRequested = false;
        setAnimation("idle2");
    }

    @Override
    public void stopEscape() {
        boolean wasEscaped = hasEscape();
        super.stopEscape();
        if (!wasEscaped || hasEscape()) return;
        resetContainmentState();
    }

    private void resetContainmentState() {
        state = STATE_CONTAINED;
        actionTimer = 0;
        actionElapsed = 0;
        actionCooldown = 0;
        specialCooldown = 0;
        lastProcessedHit = -1;
        activeAttack = "";
        selectedTombstone = null;
        sacrificeArmed = false;
        deathReturnRequested = false;
        lastClientAnimation = "";
        setAnimation("idle1");
        stopActionMovement();
        setTarget(null);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && hasEscape() && state != STATE_DYING) {
            setHealth(1.0F);
            beginDying();
            return;
        }
        super.die(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state == STATE_DYING) return false;
        return super.hurt(source, amount);
    }

    private void beginDying() {
        state = STATE_DYING;
        actionTimer = DYING_TICKS;
        actionElapsed = 0;
        deathReturnRequested = false;
        activeAttack = "";
        selectedTombstone = null;
        setAnimation("die");
        stopActionMovement();
        setTarget(null);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), random.nextBoolean()
                            ? ModSounds.LUMBERJACK_DEATH_1.get() : ModSounds.LUMBERJACK_DEATH_2.get(),
                    SoundSource.HOSTILE, 1.5F, 1.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();

        if (actionCooldown > 0) actionCooldown--;
        if (specialCooldown > 0 && hasEscape()) specialCooldown--;

        switch (state) {
            case STATE_CONTAINED -> tickContained();
            case STATE_ESCAPE_PREP -> tickEscapePreparation();
            case STATE_ESCAPED -> tickEscaped(level);
            case STATE_ATTACK -> tickAttack(level);
            case STATE_INTERACT -> tickInteract(level);
            case STATE_DYING -> tickDying();
            default -> resetContainmentState();
        }
    }

    private void tickContained() {
        stopActionMovement();
        if (!"idle1".equals(getAnimation())) setAnimation("idle1");
    }

    private void tickEscapePreparation() {
        stopActionMovement();
        actionElapsed++;
        if (actionTimer > 0) actionTimer--;
        if (actionTimer <= 0) startActualEscape();
    }

    private void tickEscaped(ServerLevel level) {
        if (!hasEscape()) {
            resetContainmentState();
            return;
        }
        if (actionCooldown > 0) {
            stopActionMovement();
            return;
        }
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()
                && random.nextFloat() < 0.5F) {
            BlockPos tombstone = findNearestTombstone(level);
            if (tombstone != null) {
                beginInteract(tombstone);
                return;
            }
        }

        LivingEntity target = findTarget(level);
        if (target == null) {
            setTarget(null);
            if (!getNavigation().isInProgress() && random.nextInt(80) == 0) {
                Vec3 destination = DefaultRandomPos.getPos(this, 10, 6);
                if (destination != null) {
                    getNavigation().moveTo(destination.x, destination.y, destination.z, 0.9D);
                }
            }
            if (!getNavigation().isInProgress() && !"idle2".equals(getAnimation())) {
                setAnimation("idle2");
            }
            return;
        }

        setTarget(target);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceTo(target);
        if (distance <= ATTACK_START_RANGE) {
            getNavigation().stop();
            chooseAttack(target);
        } else {
            getNavigation().moveTo(target, 1.0D);
            if (!"walk".equals(getAnimation())) setAnimation("walk");
        }
    }

    private void chooseAttack(LivingEntity target) {
        if (specialCooldown <= 0 && random.nextInt(100) < SPECIAL_ATTACK_CHANCE_PERCENT) {
            beginAttack("attack4", 165, target);
            specialCooldown = randomSpecialCooldown();
            return;
        }
        String[] normal = {"attack2", "attack3", "attack5"};
        String animation = normal[random.nextInt(normal.length)];
        int duration = switch (animation) {
            case "attack2" -> 70;
            case "attack3" -> 75;
            default -> 60;
        };
        beginAttack(animation, duration, target);
    }

    private void beginAttack(String animation, int duration, LivingEntity target) {
        state = STATE_ATTACK;
        activeAttack = animation;
        actionTimer = duration;
        actionElapsed = 0;
        lastProcessedHit = -1;
        lockedYaw = getYRot();
        getLookControl().setLookAt(target, 90.0F, 90.0F);
        lockedYaw = getYRot();
        setAnimation(animation);
        stopActionMovement();
        if ("attack4".equals(animation) && level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), ModSounds.LUMBERJACK_SPECIAL.get(),
                    SoundSource.HOSTILE, 1.4F, 1.0F);
        }
    }

    private void tickAttack(ServerLevel level) {
        lockActionPose();
        actionElapsed++;
        if (actionTimer > 0) actionTimer--;
        switch (activeAttack) {
            case "attack2" -> processNormalHit(level, 33, 7, 15, 3.0D, 3.0D);
            case "attack3" -> processNormalHit(level, 32, 15, 20, 3.0D, 3.0D);
            case "attack5" -> processNormalHit(level, 27, 6, 13, 3.0D, 3.0D);
            case "attack4" -> processSpecialHits(level);
            default -> actionTimer = 0;
        }
        if (actionTimer <= 0) finishAction();
    }

    private void processNormalHit(ServerLevel level, int hitTick, int minDamage, int maxDamage,
                                  double width, double depth) {
        if (actionElapsed != hitTick || lastProcessedHit == hitTick) return;
        lastProcessedHit = hitTick;
        performAreaHit(level, width, 3.0D, depth, minDamage, maxDamage, false);
        level.playSound(null, blockPosition(), ModSounds.LUMBERJACK_ATTACK.get(),
                SoundSource.HOSTILE, 1.3F, 1.0F);
    }

    private void processSpecialHits(ServerLevel level) {
        int[] hitTicks = {33, 53, 65, 75, 85, 115};
        for (int index = 0; index < hitTicks.length; index++) {
            int hitTick = hitTicks[index];
            if (actionElapsed != hitTick || lastProcessedHit == hitTick) continue;
            lastProcessedHit = hitTick;
            boolean heavy = index == hitTicks.length - 1;
            performAreaHit(level, 5.0D, 3.0D, 5.0D,
                    heavy ? 20 : 5, heavy ? 28 : 8, !heavy);
            if (index == 0) {
                level.playSound(null, blockPosition(), ModSounds.LUMBERJACK_SPECIAL_ATTACK.get(),
                        SoundSource.HOSTILE, 1.4F, 1.0F);
            }
            if (heavy) {
                level.playSound(null, blockPosition(), ModSounds.LUMBERJACK_ATTACK.get(),
                        SoundSource.HOSTILE, 1.6F, 0.9F);
            }
        }
    }

    private void performAreaHit(ServerLevel level, double width, double height, double depth,
                                int minDamage, int maxDamage, boolean addSlowness) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, lockedYaw).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = position().add(forward.scale(depth * 0.5D)).add(0.0D, height * 0.5D, 0.0D);
        double radius = Math.max(width, depth) + 2.0D;
        AABB search = new AABB(center, center).inflate(radius, height, radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, search,
                this::isValidTarget);
        boolean hit = false;
        for (LivingEntity target : targets) {
            Vec3 relative = target.position().subtract(position());
            double forwardDistance = relative.dot(forward);
            double sideDistance = Math.abs(relative.dot(right));
            double verticalDistance = Math.abs((target.getY() + target.getBbHeight() * 0.5D)
                    - (getY() + height * 0.5D));
            if (forwardDistance < 0.0D || forwardDistance > depth
                    || sideDistance > width * 0.5D + target.getBbWidth() * 0.5D
                    || verticalDistance > height * 0.5D + target.getBbHeight() * 0.5D) continue;
            EntityUtil.clearHurtTime(target);
            float damage = minDamage + random.nextInt(maxDamage - minDamage + 1);
            if (target.hurt(DamageHelper.getDamage(this, "red"), damage)) {
                hit = true;
                if (addSlowness && target.isAlive()) increaseSlowness(target);
            }
        }
        spawnAttackParticles(level, center, width, height, depth);
        if (hit) shakeNearbyPlayers(level, center);
    }

    private void increaseSlowness(LivingEntity target) {
        MobEffectInstance current = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int amplifier = current == null ? 0 : Math.min(255, current.getAmplifier() + 1);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                10 * 20, amplifier, false, true, true));
    }

    private void spawnAttackParticles(ServerLevel level, Vec3 center,
                                      double width, double height, double depth) {
        int count = Math.max(18, (int) (width * depth * 2));
        level.sendParticles((SimpleParticleType) ModParticleTypes.BLACK.get(), center.x, center.y, center.z,
                count, width * 0.45D, height * 0.4D, depth * 0.45D, 0.03D);
    }

    private void shakeNearbyPlayers(ServerLevel level, Vec3 center) {
        AABB range = new AABB(center, center).inflate(12.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, range)) {
            MessageLoader.getLoader().sendToPlayer(player, new TriggerShakePacket(10, 2.5F));
        }
    }

    private void beginInteract(BlockPos tombstone) {
        state = STATE_INTERACT;
        selectedTombstone = tombstone.immutable();
        actionTimer = INTERACT_TICKS;
        actionElapsed = 0;
        lockedYaw = getYRot();
        setAnimation("interact");
        stopActionMovement();
    }

    private void tickInteract(ServerLevel level) {
        lockActionPose();
        actionElapsed++;
        if (actionTimer > 0) actionTimer--;
        if (actionTimer > 0) return;
        if (selectedTombstone != null
                && blockPosition().distSqr(selectedTombstone) <= TOMBSTONE_RADIUS * TOMBSTONE_RADIUS
                && level.getBlockState(selectedTombstone).is(ModBlocks.TOMBSTONE.get())) {
            level.setBlock(selectedTombstone, Blocks.AIR.defaultBlockState(), 3);
            ClerkTombstoneData.get(level).remove(selectedTombstone);
            setHealth(getMaxHealth());
        }
        selectedTombstone = null;
        finishAction();
    }

    private BlockPos findNearestTombstone(ServerLevel level) {
        BlockPos center = blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = (int) TOMBSTONE_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            double distance = pos.distSqr(center);
            if (distance > TOMBSTONE_RADIUS * TOMBSTONE_RADIUS || distance >= bestDistance) continue;
            if (level.getBlockState(pos).is(ModBlocks.TOMBSTONE.get())) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    private void tickDying() {
        stopActionMovement();
        if (deathReturnRequested) return;
        actionElapsed++;
        if (actionTimer > 0) actionTimer--;
        if (actionTimer <= 0) {
            deathReturnRequested = true;
            setHealth(0.0F);
            super.die(damageSources().genericKill());
        }
    }

    private void finishAction() {
        state = STATE_ESCAPED;
        actionTimer = 0;
        actionElapsed = 0;
        actionCooldown = POST_ACTION_DELAY_TICKS;
        lastProcessedHit = -1;
        activeAttack = "";
        setAnimation("idle2");
        stopActionMovement();
    }

    private void stopActionMovement() {
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
    }

    private void lockActionPose() {
        stopActionMovement();
        setYRot(lockedYaw);
        setYHeadRot(lockedYaw);
        yBodyRot = lockedYaw;
    }

    private LivingEntity findTarget(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(SEARCH_RANGE), this::isValidTarget).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == this || !target.isAlive()) return false;
        if (target instanceof Player player) return !player.isCreative() && !player.isSpectator();
        if (target instanceof AbstractAbnormality abnormality) return abnormality.hasEscape();
        return target instanceof EntityClerk || FacilityCombatRules.isOrdealCombatant(target);
    }

    private int randomSpecialCooldown() {
        return 8 * 20 + random.nextInt(7 * 20 + 1);
    }

    @Override
    public boolean hasEscapeAmbientSound() { return true; }

    @Override
    public SoundEvent getEscapeAmbientSound() {
        return ModSounds.LUMBERJACK_AMBIENT.get();
    }

    @Override public int getEscapeAmbientSoundInterval() { return 80; }
    @Override public float getEscapeAmbientSoundVolume() { return 1.2F; }

    @Override
    public SoundEvent getEscapeSound() {
        // The escape cue is played when the sacrifice starts. Do not repeat it
        // after attack1 finishes and the common escape lifecycle begins.
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityLumberjack> event) {
        String animation = getAnimation();
        if (!animation.equals(lastClientAnimation)) {
            event.getController().forceAnimationReset();
            lastClientAnimation = animation;
        }
        return switch (animation) {
            case "idle1", "idle2", "walk" ->
                    event.setAndContinue(RawAnimation.begin().thenLoop(animation));
            case "interact", "die", "attack1", "attack2", "attack3", "attack4", "attack5" ->
                    event.setAndContinue(RawAnimation.begin().thenPlay(animation));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop(hasEscape() ? "idle2" : "idle1"));
        };
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityLumberjack((EntityType<? extends TamableAnimal>) getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 430.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, SEARCH_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LumberjackState", state);
        tag.putInt("LumberjackActionTimer", actionTimer);
        tag.putInt("LumberjackActionElapsed", actionElapsed);
        tag.putInt("LumberjackActionCooldown", actionCooldown);
        tag.putInt("LumberjackSpecialCooldown", specialCooldown);
        tag.putFloat("LumberjackLockedYaw", lockedYaw);
        tag.putString("LumberjackActiveAttack", activeAttack);
        tag.putBoolean("LumberjackSacrificeArmed", sacrificeArmed);
        tag.putBoolean("LumberjackDeathReturnRequested", deathReturnRequested);
        if (selectedTombstone != null) tag.putLong("LumberjackTombstone", selectedTombstone.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = tag.getInt("LumberjackState");
        actionTimer = tag.getInt("LumberjackActionTimer");
        actionElapsed = tag.getInt("LumberjackActionElapsed");
        actionCooldown = tag.getInt("LumberjackActionCooldown");
        specialCooldown = tag.getInt("LumberjackSpecialCooldown");
        lockedYaw = tag.getFloat("LumberjackLockedYaw");
        activeAttack = tag.getString("LumberjackActiveAttack");
        sacrificeArmed = tag.getBoolean("LumberjackSacrificeArmed");
        deathReturnRequested = tag.getBoolean("LumberjackDeathReturnRequested");
        selectedTombstone = tag.contains("LumberjackTombstone")
                ? BlockPos.of(tag.getLong("LumberjackTombstone")) : null;
        if (!hasEscape() && state != STATE_ESCAPE_PREP) state = STATE_CONTAINED;
        if (hasEscape() && state == STATE_CONTAINED) state = STATE_ESCAPED;
        if (state == STATE_ATTACK) {
            state = STATE_ESCAPED;
            activeAttack = "";
            actionTimer = 0;
            actionElapsed = 0;
            actionCooldown = POST_ACTION_DELAY_TICKS;
            setAnimation("idle2");
        }
    }

    boolean isSacrificeArmedForTest() { return sacrificeArmed; }
}
