package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeStopEvent;
import com.wzz.lobotocraft.item.TargetMarkerItem;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityArmyInBlack extends AbstractAbnormality {
    public static final String PROTECTION_UNTIL_TAG = "lobotocraft_army_in_black_protection_until";
    private static final int PROTECTION_DURATION_TICKS = 20 * 120;
    private static final int PROTECTION_HEAL_INTERVAL = 20 * 10;
    private static final double PROTECTION_FOLLOW_DISTANCE_SQR = 25.0D;
    private static final double PROTECTION_TELEPORT_DISTANCE_SQR = 100.0D;
    private static final double PROTECTION_HEAL_RADIUS = 10.0D;
    private static final float PROTECTION_HEAL_AMOUNT = 12.0F;
    private static final int PROTECTION_ATTACK_ANIMATION_TICKS = 30;
    private static final int PROTECTION_HEAL_WINDUP_TICKS = 12;
    private static final int ESCAPE_ATTACK_CHARGE_TICKS = 30;
    private static final int ESCAPE_ATTACK_COOLDOWN_TICKS = 20 * 6;
    private static final double ESCAPE_ATTACK_RADIUS = 12.0D;
    private static final double REACTOR_DETONATE_DISTANCE_SQR = 9.0D;
    private static final double ESCAPE_REACTOR_SPEED = 0.9D;
    private static final double ARMY_ESCAPE_BLOCK_SEARCH_RADIUS_SQR = 24.0D * 24.0D;
    private static final int SELF_DETONATION_TICKS = 60;
    private static final int SELF_DETONATION_SOUND_LEAD_TICKS = 4;

    private UUID protectedPlayerId = null;
    private BlockPos protectionReturnPos = null;
    private long protectionEndsAt = 0L;
    private int protectionHealTimer = 0;
    private int protectionAttackAnimationTicks = 0;
    private boolean protectionHealPending = false;
    private BlockPos targetReactorPos = null;
    private UUID retaliateTargetId = null;
    private int attackChargeTicks = 0;
    private int attackCooldownTicks = 0;
    private int reactorRepathTicks = 0;
    private int clerkOrVillagerDeathCount = 0;
    private int selfDetonationTicks = 0;
    private boolean selfDetonationSoundPlayed = false;
    private boolean escapeSpawnedCopy = false;
    private UUID escapeGroupId = null;
    private int escapeGroupSize = 0;
    private boolean selfDetonated = false;
    private boolean suppressGroupStop = false;

    private static final Map<UUID, EscapeGroupState> ESCAPE_GROUPS = new ConcurrentHashMap<>();

    public EntityArmyInBlack(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "D-01-106";
        this.abnormalityName = "深黯“军团”";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 30;

        float[] basePreferences = {0.45f, 0.45f, 1.0f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(3);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0, true, true, true)
        };
    }

    @Override
    public int getBasicInfoCost() { return 30; }

    @Override
    public int getSensitiveInfoCost() { return 30; }

    @Override
    public int getManualCost(int manualIndex) { return 6; }

    @Override
    public int getWorkPreferencesCost() { return 10; }

    @Override
    public String name() {
        return "army_in_black";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        // 出逃后的寻路与反击由 tickEscapedState 管理，避免继承的主动索敌覆盖机制。
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!hasEscape()) {
            if (tickProtection()) {
                return;
            }
            setNoAi(true);
            this.noPhysics = false;
            setTarget(null);
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }

        setNoAi(false);
        this.noPhysics = false;
        tickEscapedState((ServerLevel) this.level());
    }

    @Override
    public boolean shouldGivePEBox(ServerPlayer player, WorkType workType, WorkResult result, int peOutput) {
        return workType != WorkType.ATTACHMENT;
    }

    @Override
    public float[] getArmorRenderScale() {
        return new float[] {1.5f, 1.0f, 1.5f};
    }

    @Override
    public float[] getArmorRenderOffset() {
        return new float[] {-20.0f, 1.0f, 1.0f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {5.0f, 1.0f, 1f};
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 222;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 120;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public float getGiftProbability() {
        return 0.01f;
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/army_in_black_curio.png"),
                "粉红军备",
                "头部",
                "army_in_black_curio",
                "最大精神值+5",
                "攻击速度+5",
                "装备全套“粉红军备”时，E.G.O武器“粉红军备”的攻击力将提高15点"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/army_in_black_weapon.png"),
                "粉红军备",
                getRiskLevel(),
                "WHITE",
                "24",
                "6s",
                "35格",
                getWeaponDevelopmentMaxCount(),
                "army_in_black_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/army_in_black_armor.png"),
                "粉红军备",
                getRiskLevel(),
                0.5f,
                0.3f,
                0.4f,
                1.5f,
                getArmorDevelopmentMaxCount(),
                "army_in_black_armor"
        );
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (workType == WorkType.ATTACHMENT) {
            protectEmployee(player);
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§d深黯“军团”正在守护你。"), true);
        }
        if (workType == WorkType.REPRESSION) {
            decreaseQliphothCounter(1);
        }
    }

    private void protectEmployee(ServerPlayer player) {
        stopProtection();
        this.protectedPlayerId = player.getUUID();
        this.protectionReturnPos = this.blockPosition();
        this.protectionEndsAt = player.level().getGameTime() + PROTECTION_DURATION_TICKS;
        this.protectionHealTimer = 0;
        refreshProtectionTag(player);
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), ModSounds.ARMY_IN_BLACK_PROTECT_START.get(),
                    SoundSource.NEUTRAL, 1.2F, 1.0F);
        }
    }

    private boolean tickProtection() {
        if (this.protectedPlayerId == null) {
            return false;
        }
        ServerPlayer player = ((ServerLevel) this.level()).getServer().getPlayerList().getPlayer(this.protectedPlayerId);
        if (player == null
                || player.level() != this.level()
                || !player.isAlive()
                || MentalValueUtil.getMentalValue(player) <= 0.0F
                || this.level().getGameTime() >= this.protectionEndsAt) {
            stopProtection();
            return false;
        }

        refreshProtectionTag(player);
        setNoAi(false);
        this.noPhysics = false;
        setTarget(null);

        if (this.protectionAttackAnimationTicks > 0) {
            tickProtectionHealAttack(player);
        } else {
            followProtectedPlayer(player);
        }

        this.protectionHealTimer++;
        if (this.protectionHealTimer >= PROTECTION_HEAL_INTERVAL && this.protectionAttackAnimationTicks <= 0) {
            startProtectionHealAttack(player);
            this.protectionHealTimer = 0;
        }
        return true;
    }

    private void tickProtectionHealAttack(ServerPlayer protectedPlayer) {
        this.getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        getLookControl().setLookAt(protectedPlayer, 30.0F, 30.0F);
        setAnimation("attack");
        if (this.protectionAttackAnimationTicks > 0) {
            this.protectionAttackAnimationTicks--;
            if (this.protectionHealPending
                    && this.protectionAttackAnimationTicks <= PROTECTION_ATTACK_ANIMATION_TICKS - PROTECTION_HEAL_WINDUP_TICKS) {
                this.protectionHealPending = false;
                applyProtectionHeal(protectedPlayer);
            }
            if (this.protectionAttackAnimationTicks == 0 && "attack".equals(getAnimation())) {
                setAnimation("idle");
            }
        }
    }

    private void followProtectedPlayer(ServerPlayer player) {
        double distanceSqr = this.distanceToSqr(player);
        if (distanceSqr > PROTECTION_TELEPORT_DISTANCE_SQR) {
            Vec3 followPos = player.position().subtract(player.getLookAngle().scale(1.5D));
            this.moveTo(followPos.x, player.getY(), followPos.z, this.getYRot(), this.getXRot());
            this.getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (distanceSqr > PROTECTION_FOLLOW_DISTANCE_SQR) {
            this.getNavigation().moveTo(player, 0.9D);
            return;
        }

        this.getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    private void startProtectionHealAttack(ServerPlayer protectedPlayer) {
        setAnimation("attack");
        this.protectionAttackAnimationTicks = PROTECTION_ATTACK_ANIMATION_TICKS;
        this.protectionHealPending = true;
        this.getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        getLookControl().setLookAt(protectedPlayer, 30.0F, 30.0F);
    }

    private void applyProtectionHeal(ServerPlayer protectedPlayer) {
        if (protectedPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, protectedPlayer.blockPosition(), ModSounds.ARMY_IN_BLACK_ATTACK.get(),
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        for (ServerPlayer nearby : protectedPlayer.level().getEntitiesOfClass(ServerPlayer.class,
                protectedPlayer.getBoundingBox().inflate(PROTECTION_HEAL_RADIUS), ServerPlayer::isAlive)) {
            nearby.heal(PROTECTION_HEAL_AMOUNT);
            MentalValueUtil.addMentalValue(nearby, PROTECTION_HEAL_AMOUNT);
            if (nearby.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        nearby.getX(), nearby.getY() + 1.0D, nearby.getZ(),
                        5, 0.35D, 0.35D, 0.35D, 0.02D);
            }
        }
    }

    private void refreshProtectionTag(ServerPlayer player) {
        player.getPersistentData().putLong(PROTECTION_UNTIL_TAG, this.protectionEndsAt);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
    }

    private void stopProtection() {
        BlockPos returnPos = this.protectionReturnPos;
        if (this.protectedPlayerId != null && this.level() instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.protectedPlayerId);
            if (player != null) {
                boolean ownsProtectionTag = player.getPersistentData().getLong(PROTECTION_UNTIL_TAG) <= this.protectionEndsAt;
                if (ownsProtectionTag) {
                    player.getPersistentData().remove(PROTECTION_UNTIL_TAG);
                    player.removeEffect(MobEffects.GLOWING);
                    player.displayClientMessage(Component.literal("§d深谙军团对玩家的保护结束了"), true);
                    serverLevel.playSound(null, player.blockPosition(), ModSounds.ARMY_IN_BLACK_PROTECT_END.get(),
                            SoundSource.NEUTRAL, 1.2F, 1.0F);
                }
            }
        }
        this.protectedPlayerId = null;
        this.protectionReturnPos = null;
        this.protectionEndsAt = 0L;
        this.protectionHealTimer = 0;
        this.protectionAttackAnimationTicks = 0;
        this.protectionHealPending = false;
        this.noPhysics = false;
        this.getNavigation().stop();
        if (!hasEscape() && "attack".equals(getAnimation())) {
            setAnimation("idle");
        }
        if (returnPos != null) {
            this.moveTo(returnPos.getX() + 0.5D, returnPos.getY(), returnPos.getZ() + 0.5D,
                    this.getYRot(), this.getXRot());
            setDeltaMovement(Vec3.ZERO);
        }
    }

    public static boolean hasActiveProtection(ServerPlayer player) {
        long until = player.getPersistentData().getLong(PROTECTION_UNTIL_TAG);
        if (until <= player.level().getGameTime()
                || !player.isAlive()
                || MentalValueUtil.getMentalValue(player) <= 0.0F) {
            player.getPersistentData().remove(PROTECTION_UNTIL_TAG);
            return false;
        }
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.protectedPlayerId == null && super.canBeCollidedWith();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 6.0F + this.random.nextInt(5);
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:white"), damage);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        boolean wasEscaped = hasEscape();
        if (!wasEscaped) {
            stopProtection();
        }
        List<ArmySpawnPoint> spawnPoints = List.of();
        if (!wasEscaped && !escapeSpawnedCopy && canEscape() && this.level() instanceof ServerLevel serverLevel) {
            spawnPoints = selectArmySpawnPoints(serverLevel);
        }
        super.triggerEscape();
        if (!wasEscaped && hasEscape() && this.level() instanceof ServerLevel serverLevel) {
            resetEscapeState();
            if (!spawnPoints.isEmpty()) {
                UUID groupId = UUID.randomUUID();
                int groupSize = spawnPoints.size();
                createEscapeGroup(groupId, groupSize);
                spawnPoints.sort((a, b) -> Boolean.compare(
                        b.level.dimension().equals(serverLevel.dimension()),
                        a.level.dimension().equals(serverLevel.dimension())));
                if (spawnPoints.get(0).level.dimension().equals(serverLevel.dimension())) {
                    ArmySpawnPoint firstPoint = spawnPoints.get(0);
                    initializeEscapeGroup(groupId, groupSize);
                    moveToArmySpawnPoint(firstPoint);
                    targetReactorPos = firstPoint.reactorPos;
                    spawnEscapedArmies(spawnPoints, 1, groupId, groupSize);
                } else {
                    spawnEscapedArmies(spawnPoints, 0, groupId, groupSize);
                    stopEscape();
                }
            } else {
                UUID groupId = UUID.randomUUID();
                createEscapeGroup(groupId, 1);
                initializeEscapeGroup(groupId, 1);
                targetReactorPos = findNearestReactor(serverLevel, blockPosition());
            }
        }
    }

    @Override
    public void stopEscape() {
        TargetMarkerItem.clearTargetMark(this);
        if (escapeSpawnedCopy) {
            stopSpawnedEscape();
            return;
        }
        boolean wasEscaped = hasEscape();
        UUID groupId = escapeGroupId;
        if (wasEscaped && groupId != null && !selfDetonated && !suppressGroupStop
                && this.level() instanceof ServerLevel level) {
            stopEscapeGroup(level, groupId);
            return;
        }
        super.stopEscape();
        if (wasEscaped && !hasEscape()) {
            resetEscapeState();
            clearEscapeGroup();
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        TargetMarkerItem.clearTargetMark(this);
        if (!selfDetonated && escapeGroupId != null && this.level() instanceof ServerLevel level) {
            removeUndetonatedGroupMember(level);
        }
        if (escapeSpawnedCopy) {
            escapePosition = null;
        }
        super.die(damageSource);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        boolean hurt = super.hurt(damageSource, amount);
        if (!hurt || !hasEscape() || !isAlive() || attackChargeTicks > 0 || attackCooldownTicks > 0) {
            return hurt;
        }
        Entity attacker = damageSource.getEntity();
        if (attacker instanceof LivingEntity living && isValidRetaliationTarget(living)) {
            retaliateTargetId = living.getUUID();
            beginEscapeAttack();
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    private void tickEscapedState(ServerLevel level) {
        setTarget(null);
        if (selfDetonated) {
            waitForGroupDetonation();
            return;
        }
        if (selfDetonationTicks > 0) {
            tickSelfDetonation(level);
            return;
        }
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
        if (targetReactorPos == null || tickCount % 100 == 0 || !(level.getBlockEntity(targetReactorPos) instanceof RegenerationReactorBlockEntity)) {
            targetReactorPos = findNearestReactor(level, blockPosition());
        }
        if (attackChargeTicks > 0) {
            tickEscapeAttackCharge(level);
            return;
        }
        if (targetReactorPos != null && blockPosition().distSqr(targetReactorPos) <= REACTOR_DETONATE_DISTANCE_SQR) {
            beginSelfDetonation();
            return;
        }
        walkToReactor(level);
    }

    private void beginEscapeAttack() {
        attackChargeTicks = ESCAPE_ATTACK_CHARGE_TICKS;
        setAnimation("attack");
        getNavigation().stop();
        setTarget(null);
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
    }

    private void tickEscapeAttackCharge(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        spawnWitherEffectParticles(level, 12);
        attackChargeTicks--;
        if (attackChargeTicks <= 0) {
            performEscapeAttack(level);
        }
    }

    private void performEscapeAttack(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.4F, 0.7F);
        spawnWitherEffectParticles(level, 48);
        spawnAngryGroundParticles(level);
        AABB area = getBoundingBox().inflate(ESCAPE_ATTACK_RADIUS, 4.0D, ESCAPE_ATTACK_RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, this::isValidEscapeAttackTarget)) {
            EntityUtil.clearHurtTime(living, () ->
                    living.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 25.0F + random.nextInt(4)));
        }
        attackCooldownTicks = ESCAPE_ATTACK_COOLDOWN_TICKS;
        retaliateTargetId = null;
        setAnimation("idle");
    }

    private void walkToReactor(ServerLevel level) {
        if (targetReactorPos == null) {
            getNavigation().stop();
            return;
        }
        if (reactorRepathTicks > 0 && !getNavigation().isDone()) {
            reactorRepathTicks--;
            return;
        }
        BlockPos walkPos = EntityUtil.findReactorSpawnPositionInCompany(level, targetReactorPos, 128);
        if (walkPos == null)
            return;
        getNavigation().moveTo(walkPos.getX() + 0.5D, walkPos.getY(), walkPos.getZ() + 0.5D, ESCAPE_REACTOR_SPEED);
        reactorRepathTicks = 20;
    }

    private void beginSelfDetonation() {
        selfDetonationTicks = SELF_DETONATION_TICKS;
        selfDetonationSoundPlayed = false;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        setAnimation("attack");
    }

    private void tickSelfDetonation(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        setAnimation("attack");
        selfDetonationTicks--;
        if (!selfDetonationSoundPlayed && selfDetonationTicks <= SELF_DETONATION_SOUND_LEAD_TICKS) {
            selfDetonationSoundPlayed = true;
            level.playSound(null, blockPosition(), ModSounds.ARMY_IN_BLACK_EXPLODE.get(),
                    SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        if (selfDetonationTicks <= 0) {
            completeSelfDetonation(level);
        }
    }

    private void completeSelfDetonation(ServerLevel level) {
        TargetMarkerItem.clearTargetMark(this);
        spawnSelfDetonationCloud(level);
        DamageSource whiteDamage = DamageHelper.getDamage(this, "lobotocraft:white");
        float damage = 30.0F + random.nextInt(11);
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
                player.hurt(whiteDamage, damage);
            }
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityClerk clerk && clerk.isAlive()) {
                clerk.hurt(whiteDamage, damage);
            }
        }
        List<AbstractAbnormality> abnormalities = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractAbnormality abnormality
                    && abnormality != this
                    && abnormality.canEscape()
                    && abnormality.isAlive()
                    && abnormality.getQliphothCounter() > 0) {
                abnormalities.add(abnormality);
            }
        }
        for (int i = 0; i < 2 && !abnormalities.isEmpty(); i++) {
            AbstractAbnormality abnormality = abnormalities.remove(random.nextInt(abnormalities.size()));
            abnormality.decreaseQliphothCounter(1);
        }
        selfDetonated = true;
        selfDetonationTicks = 0;
        selfDetonationSoundPlayed = false;
        boolean groupFinished = markSelfDetonated(level);
        if (groupFinished) {
            finishEscapeGroup(level, escapeGroupId);
            return;
        }
        if (escapeSpawnedCopy) {
            stopSpawnedEscape();
        } else {
            waitForGroupDetonation();
        }
    }

    private List<ArmySpawnPoint> selectArmySpawnPoints(ServerLevel originLevel) {
        List<ServerPlayer> players = new ArrayList<>(originLevel.getServer().getPlayerList().getPlayers());
        players.removeIf(player -> !player.isAlive() || player.isSpectator());
        Collections.shuffle(players, new java.util.Random(originLevel.getRandom().nextLong()));

        int desiredCount = Math.min(4, Math.max(1, players.size()));
        List<ArmySpawnPoint> selected = new ArrayList<>();
        Set<String> usedEscapeBlocks = new HashSet<>();

        for (ServerPlayer player : players) {
            if (selected.size() >= desiredCount) {
                break;
            }
            ArmySpawnPoint playerPoint = findPlayerArmySpawnPoint(player);
            if (playerPoint != null && usedEscapeBlocks.add(dimensionPosKey(playerPoint.level, playerPoint.escapeBlockPos))) {
                selected.add(playerPoint);
            }
        }

        if (selected.size() >= desiredCount) {
            return selected;
        }

        List<ArmySpawnPoint> candidates = collectArmySpawnCandidates(originLevel);
        Collections.shuffle(candidates, new java.util.Random(originLevel.getRandom().nextLong()));
        for (ArmySpawnPoint candidate : candidates) {
            if (selected.size() >= desiredCount) {
                break;
            }
            if (usedEscapeBlocks.add(dimensionPosKey(candidate.level, candidate.escapeBlockPos))) {
                selected.add(candidate);
            }
        }

        return selected;
    }

    private ArmySpawnPoint findPlayerArmySpawnPoint(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
        if (escapeBlocks.isEmpty()) {
            return null;
        }

        List<BlockPos> reactors = collectReactorPositions(level);
        if (reactors.isEmpty()) {
            return null;
        }

        BlockPos playerPos = player.blockPosition();
        BlockPos bestEscapeBlock = null;
        BlockPos bestReactor = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos escapeBlock : escapeBlocks) {
            BlockPos nearestReactor = findNearestReactorPos(reactors, escapeBlock, ARMY_ESCAPE_BLOCK_SEARCH_RADIUS_SQR);
            if (nearestReactor == null) {
                continue;
            }
            double distance = escapeBlock.distSqr(playerPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestEscapeBlock = escapeBlock;
                bestReactor = nearestReactor;
            }
        }

        return bestEscapeBlock == null ? null : new ArmySpawnPoint(level, bestEscapeBlock, bestReactor);
    }

    private List<ArmySpawnPoint> collectArmySpawnCandidates(ServerLevel originLevel) {
        List<ArmySpawnPoint> candidates = new ArrayList<>();
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            List<BlockPos> escapeBlocks = new ArrayList<>(EscapeBlockEntity.getEscapeBlocks(level.dimension()));
            if (escapeBlocks.isEmpty()) {
                continue;
            }

            List<BlockPos> reactors = collectReactorPositions(level);
            if (reactors.isEmpty()) {
                continue;
            }

            for (BlockPos escapeBlock : escapeBlocks) {
                BlockPos nearestReactor = findNearestReactorPos(reactors, escapeBlock, ARMY_ESCAPE_BLOCK_SEARCH_RADIUS_SQR);
                if (nearestReactor != null) {
                    candidates.add(new ArmySpawnPoint(level, escapeBlock, nearestReactor));
                }
            }
        }
        return candidates;
    }

    private List<BlockPos> collectReactorPositions(ServerLevel level) {
        List<BlockPos> reactors = new ArrayList<>();
        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level)) {
            if (blockEntity instanceof RegenerationReactorBlockEntity) {
                reactors.add(blockEntity.getBlockPos());
            }
        }
        return reactors;
    }

    private String dimensionPosKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    private BlockPos findNearestReactorPos(List<BlockPos> reactors, BlockPos origin, double maxDistanceSqr) {
        BlockPos best = null;
        double bestDistance = maxDistanceSqr;
        for (BlockPos reactor : reactors) {
            double distance = reactor.distSqr(origin);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = reactor;
            }
        }
        return best;
    }

    private void moveToArmySpawnPoint(ArmySpawnPoint spawnPoint) {
        BlockPos pos = spawnPoint.escapeBlockPos;
        moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                level().getRandom().nextFloat() * 360.0F, 0.0F);
        setDeltaMovement(Vec3.ZERO);
        hurtMarked = true;
    }

    private void spawnEscapedArmies(List<ArmySpawnPoint> spawnPoints, int startIndex, UUID groupId, int groupSize) {
        for (int i = startIndex; i < spawnPoints.size(); i++) {
            ArmySpawnPoint spawnPoint = spawnPoints.get(i);
            ServerLevel level = spawnPoint.level;
            EntityArmyInBlack army = ModEntities.army_in_black.get().create(level);
            if (army == null) {
                continue;
            }
            BlockPos pos = spawnPoint.escapeBlockPos;
            army.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            army.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            army.setPersistenceRequired();
            army.initializeSpawnedEscape(spawnPoint, groupId, groupSize);
            if (level.addFreshEntity(army)) {
                army.decreaseAllPlayersTodayWorkCount();
                EscapeTracker.getInstance().onEscapeStart(army);
            }
        }
    }

    private void initializeSpawnedEscape(ArmySpawnPoint spawnPoint, UUID groupId, int groupSize) {
        this.escapeSpawnedCopy = true;
        this.escapePosition = spawnPoint.escapeBlockPos;
        setEscape(true);
        resetEscapeState();
        initializeEscapeGroup(groupId, groupSize);
        this.targetReactorPos = spawnPoint.reactorPos;
        setNoAi(false);
        this.noPhysics = false;
    }

    private void stopSpawnedEscape() {
        boolean wasEscaped = hasEscape();
        UUID groupId = escapeGroupId;
        if (wasEscaped && groupId != null && !selfDetonated && !suppressGroupStop
                && this.level() instanceof ServerLevel level) {
            stopEscapeGroup(level, groupId);
            return;
        }
        setEscape(false);
        escapePosition = null;
        resetEscapeState();
        clearEscapeGroup();
        if (wasEscaped) {
            MinecraftForge.EVENT_BUS.post(new AbnormalityEscapeStopEvent(this, false));
            EscapeTracker.getInstance().onEscapeStop(this);
        }
        discard();
    }

    private void spawnWitherEffectParticles(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            double radius = Math.sqrt(random.nextDouble()) * ESCAPE_ATTACK_RADIUS;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            double y = getY() + 0.12D;
            level.sendParticles(ParticleTypes.ENTITY_EFFECT, x, y + 0.15D, z,
                    0, 0.08D, 0.0D, 0.12D, 1.0D);
            level.sendParticles(ParticleUtil.getDustParticle(0.0F, 0.0F, 0.0F, 1.4F), x, y, z,
                    2, 0.18D, 0.01D, 0.18D, 0.0D);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z,
                    1, 0.08D, 0.01D, 0.08D, 0.01D);
        }
    }

    private void spawnAngryGroundParticles(ServerLevel level) {
        for (int i = 0; i < 42; i++) {
            double radius = Math.sqrt(random.nextDouble()) * ESCAPE_ATTACK_RADIUS;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            double y = getY() + 0.35D + random.nextDouble() * 0.35D;
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z,
                    1, 0.04D, 0.04D, 0.04D, 0.02D);
        }
    }

    private void spawnSelfDetonationCloud(ServerLevel level) {
        Vec3 center = position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, getY() + 1.0D, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        for (int i = 0; i < 320; i++) {
            double radius = 2.0D + random.nextDouble() * 12.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = getY() + 0.25D + random.nextDouble() * 1.8D;
            double xSpeed = Math.cos(angle) * (0.12D + random.nextDouble() * 0.16D);
            double zSpeed = Math.sin(angle) * (0.12D + random.nextDouble() * 0.16D);
            double ySpeed = 0.04D + random.nextDouble() * 0.08D;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z,
                    1, xSpeed, ySpeed, zSpeed, 0.06D);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.25D, z,
                        1, xSpeed * 0.7D, ySpeed + 0.03D, zSpeed * 0.7D, 0.04D);
            }
        }

        for (int i = 0; i < 260; i++) {
            double height = random.nextDouble() * 10.0D;
            double columnRadius = 0.8D + height * 0.18D + random.nextDouble() * 1.4D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = center.x + Math.cos(angle) * columnRadius;
            double z = center.z + Math.sin(angle) * columnRadius;
            double y = getY() + 0.6D + height;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                    1, Math.cos(angle) * 0.05D, 0.16D + random.nextDouble() * 0.12D, Math.sin(angle) * 0.05D, 0.04D);
        }

        for (int i = 0; i < 220; i++) {
            double radius = 3.0D + random.nextDouble() * 7.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = getY() + 8.0D + random.nextDouble() * 4.0D;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                    1, Math.cos(angle) * 0.12D, 0.08D + random.nextDouble() * 0.08D, Math.sin(angle) * 0.12D, 0.05D);
        }
    }

    private BlockPos findNearestReactor(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level, origin, 128)) {
            if (blockEntity instanceof RegenerationReactorBlockEntity) {
                double distance = blockEntity.getBlockPos().distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = blockEntity.getBlockPos();
                }
            }
        }
        return best;
    }

    private boolean isValidRetaliationTarget(LivingEntity living) {
        if (living == this || !living.isAlive()) {
            return false;
        }
        if (living instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private boolean isValidEscapeAttackTarget(LivingEntity living) {
        return living != this && living.isAlive()
                && (!(living instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
    }

    private void resetEscapeState() {
        targetReactorPos = null;
        retaliateTargetId = null;
        attackChargeTicks = 0;
        attackCooldownTicks = 0;
        reactorRepathTicks = 0;
        selfDetonationTicks = 0;
        selfDetonationSoundPlayed = false;
        selfDetonated = false;
        setInvisible(false);
        getNavigation().stop();
        if (!"idle".equals(getAnimation())) {
            setAnimation("idle");
        }
    }

    private static void createEscapeGroup(UUID groupId, int groupSize) {
        ESCAPE_GROUPS.put(groupId, new EscapeGroupState(groupSize));
    }

    private void initializeEscapeGroup(UUID groupId, int groupSize) {
        this.escapeGroupId = groupId;
        this.escapeGroupSize = groupSize;
        this.selfDetonated = false;
        this.setInvisible(false);
        EscapeGroupState state = ESCAPE_GROUPS.computeIfAbsent(groupId, id -> new EscapeGroupState(groupSize));
        state.groupSize = Math.max(state.groupSize, groupSize);
        state.members.add(this.getUUID());
    }

    private void clearEscapeGroup() {
        this.escapeGroupId = null;
        this.escapeGroupSize = 0;
        this.selfDetonated = false;
        this.suppressGroupStop = false;
        this.setInvisible(false);
    }

    private void waitForGroupDetonation() {
        setNoAi(true);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setAnimation("idle");
        setInvisible(true);
    }

    private boolean markSelfDetonated(ServerLevel level) {
        if (escapeGroupId == null) {
            return true;
        }
        EscapeGroupState state = ESCAPE_GROUPS.computeIfAbsent(escapeGroupId,
                id -> new EscapeGroupState(Math.max(1, escapeGroupSize)));
        state.groupSize = Math.max(state.groupSize, Math.max(1, escapeGroupSize));
        state.members.add(getUUID());
        state.detonated.add(getUUID());
        return state.detonated.size() >= state.groupSize;
    }

    private void removeUndetonatedGroupMember(ServerLevel level) {
        if (escapeGroupId == null) {
            return;
        }
        EscapeGroupState state = ESCAPE_GROUPS.get(escapeGroupId);
        if (state == null || state.detonated.contains(getUUID())) {
            return;
        }
        state.members.remove(getUUID());
        state.groupSize = Math.max(0, state.groupSize - 1);
        if (state.groupSize <= 0 || state.detonated.size() >= state.groupSize) {
            finishEscapeGroup(level, escapeGroupId);
        }
    }

    private static void finishEscapeGroup(ServerLevel level, UUID groupId) {
        if (groupId == null) {
            return;
        }
        ESCAPE_GROUPS.remove(groupId);
        for (EntityArmyInBlack army : findGroupMembers(level, groupId)) {
            army.stopAsGroupMember();
        }
    }

    private static void stopEscapeGroup(ServerLevel level, UUID groupId) {
        if (groupId == null) {
            return;
        }
        ESCAPE_GROUPS.remove(groupId);
        for (EntityArmyInBlack army : findGroupMembers(level, groupId)) {
            army.stopAsGroupMember();
        }
    }

    private static List<EntityArmyInBlack> findGroupMembers(ServerLevel originLevel, UUID groupId) {
        List<EntityArmyInBlack> members = new ArrayList<>();
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityArmyInBlack army && groupId.equals(army.escapeGroupId)) {
                    members.add(army);
                }
            }
        }
        return members;
    }

    private void stopAsGroupMember() {
        suppressGroupStop = true;
        try {
            if (escapeSpawnedCopy) {
                stopSpawnedEscape();
            } else {
                stopEscape();
            }
        } finally {
            suppressGroupStop = false;
        }
    }

    public void addClerkOrVillagerDeathCount(int count) {
        if (count <= 0) {
            return;
        }
        clerkOrVillagerDeathCount += count;
        while (clerkOrVillagerDeathCount >= 5) {
            clerkOrVillagerDeathCount -= 5;
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“深黯“军团””正在向员工讲述有关“爱”的一切。");
        logs.add("“深黯“军团””致力于守护拥有善良之心的人。");
        logs.add("“深黯“军团””的粉红迷彩让员工感受到了希望。");
        return logs;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityArmyInBlack> event) {
        if ("attack".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.army_in_black.heal_large"));
        }
        if (isMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.army_in_black.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.army_in_black.idle"));
    }

    private boolean isMovingForAnimation(AnimationState<EntityArmyInBlack> event) {
        Vec3 movement = this.getDeltaMovement();
        return hasEscape() && (event.isMoving()
                || movement.x * movement.x + movement.z * movement.z > 1.0E-6D
                || !this.getNavigation().isDone());
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityArmyInBlack((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.protectedPlayerId != null) {
            tag.putUUID("ProtectedPlayer", this.protectedPlayerId);
        }
        if (this.protectionReturnPos != null) {
            tag.putLong("ProtectionReturnPos", this.protectionReturnPos.asLong());
        }
        tag.putLong("ProtectionEndsAt", this.protectionEndsAt);
        tag.putInt("ProtectionHealTimer", this.protectionHealTimer);
        tag.putInt("ProtectionAttackAnimationTicks", this.protectionAttackAnimationTicks);
        tag.putBoolean("ProtectionHealPending", this.protectionHealPending);
        if (this.targetReactorPos != null) {
            tag.putLong("TargetReactorPos", this.targetReactorPos.asLong());
        }
        if (this.retaliateTargetId != null) {
            tag.putUUID("RetaliateTarget", this.retaliateTargetId);
        }
        tag.putInt("AttackChargeTicks", this.attackChargeTicks);
        tag.putInt("AttackCooldownTicks", this.attackCooldownTicks);
        tag.putInt("ReactorRepathTicks", this.reactorRepathTicks);
        tag.putInt("ClerkOrVillagerDeathCount", this.clerkOrVillagerDeathCount);
        tag.putInt("SelfDetonationTicks", this.selfDetonationTicks);
        tag.putBoolean("SelfDetonationSoundPlayed", this.selfDetonationSoundPlayed);
        tag.putBoolean("EscapeSpawnedCopy", this.escapeSpawnedCopy);
        if (this.escapeGroupId != null) {
            tag.putUUID("EscapeGroupId", this.escapeGroupId);
        }
        tag.putInt("EscapeGroupSize", this.escapeGroupSize);
        tag.putBoolean("SelfDetonated", this.selfDetonated);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ProtectedPlayer")) {
            this.protectedPlayerId = tag.getUUID("ProtectedPlayer");
        }
        if (tag.contains("ProtectionReturnPos")) {
            this.protectionReturnPos = BlockPos.of(tag.getLong("ProtectionReturnPos"));
        }
        this.protectionEndsAt = tag.getLong("ProtectionEndsAt");
        this.protectionHealTimer = tag.getInt("ProtectionHealTimer");
        this.protectionAttackAnimationTicks = tag.getInt("ProtectionAttackAnimationTicks");
        this.protectionHealPending = tag.getBoolean("ProtectionHealPending");
        if (tag.contains("TargetReactorPos")) {
            this.targetReactorPos = BlockPos.of(tag.getLong("TargetReactorPos"));
        }
        if (tag.hasUUID("RetaliateTarget")) {
            this.retaliateTargetId = tag.getUUID("RetaliateTarget");
        }
        this.attackChargeTicks = tag.getInt("AttackChargeTicks");
        this.attackCooldownTicks = tag.getInt("AttackCooldownTicks");
        this.reactorRepathTicks = tag.getInt("ReactorRepathTicks");
        this.clerkOrVillagerDeathCount = tag.getInt("ClerkOrVillagerDeathCount");
        this.selfDetonationTicks = tag.getInt("SelfDetonationTicks");
        this.selfDetonationSoundPlayed = tag.getBoolean("SelfDetonationSoundPlayed");
        this.escapeSpawnedCopy = tag.getBoolean("EscapeSpawnedCopy");
        if (tag.hasUUID("EscapeGroupId")) {
            this.escapeGroupId = tag.getUUID("EscapeGroupId");
        }
        this.escapeGroupSize = tag.getInt("EscapeGroupSize");
        this.selfDetonated = tag.getBoolean("SelfDetonated");
        if (this.escapeGroupId != null && this.escapeGroupSize > 0) {
            EscapeGroupState state = ESCAPE_GROUPS.computeIfAbsent(this.escapeGroupId,
                    id -> new EscapeGroupState(this.escapeGroupSize));
            state.groupSize = Math.max(state.groupSize, this.escapeGroupSize);
            state.members.add(this.getUUID());
            if (this.selfDetonated) {
                state.detonated.add(this.getUUID());
            }
        }
    }

    private static class EscapeGroupState {
        private int groupSize;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Set<UUID> detonated = ConcurrentHashMap.newKeySet();

        private EscapeGroupState(int groupSize) {
            this.groupSize = Math.max(0, groupSize);
        }
    }

    private static class ArmySpawnPoint {
        private final ServerLevel level;
        private final BlockPos escapeBlockPos;
        private final BlockPos reactorPos;

        private ArmySpawnPoint(ServerLevel level, BlockPos escapeBlockPos, BlockPos reactorPos) {
            this.level = level;
            this.escapeBlockPos = escapeBlockPos;
            this.reactorPos = reactorPos;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 450.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 22.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.2D);
    }
}
