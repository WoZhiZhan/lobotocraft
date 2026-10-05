package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.ai.FacilityAbnormalityTargetPolicy;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlockPosWithElevatorGoal;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;

public class EntityPpodae extends AbstractAbnormality {

    // ========== 数据同步器 ==========
    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(EntityPpodae.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_COLLISION_ESCAPED =
            SynchedEntityData.defineId(EntityPpodae.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDimensions CONTAINED_SIZE = EntityDimensions.scalable(0.6F, 0.9F);
    private static final EntityDimensions ESCAPED_SIZE = EntityDimensions.scalable(1.8F, 2.7F);
    private static final int DIMENSION_RETRY_INTERVAL = 10;

    // ========== 状态追踪 ==========
    private int attackCooldown = 0;
    private int attackAnimationTimer = 0; // 攻击动画计时器
    private boolean isPlayingAttack = false; // 是否正在播放攻击动画
    private LivingEntity pendingAttackTarget = null; // 待攻击的目标
    private UUID lockedTarget = null;  // 锁定的目标（工作玩家）
    private boolean hasLockedTarget = false; // 是否有锁定目标
    private BlockPos nestPosition = null; // 收容位置
    private boolean dimensionChanged = false; // 是否离开过维度

    public EntityPpodae(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityPpodae((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_RETURNING, false);
        this.entityData.define(DATA_COLLISION_ESCAPED, false);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.entityData.get(DATA_COLLISION_ESCAPED) ? ESCAPED_SIZE : CONTAINED_SIZE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_COLLISION_ESCAPED.equals(key) && this.level().isClientSide) {
            this.refreshDimensions();
        }
    }

    private boolean tryApplyEscapeDimensions(ServerLevel level, boolean escaped) {
        boolean appliedEscaped = this.entityData.get(DATA_COLLISION_ESCAPED);
        if (appliedEscaped == escaped) return true;

        EntityDimensions current = appliedEscaped ? ESCAPED_SIZE : CONTAINED_SIZE;
        EntityDimensions target = escaped ? ESCAPED_SIZE : CONTAINED_SIZE;
        Vec3 safePosition;
        if (SafeTransformationUtil.isPureShrink(current, target)) {
            safePosition = this.position();
        } else {
            safePosition = SafeTransformationUtil.findPositionForDimensions(level, this, target, 10, 3, true);
            if (safePosition == null) {
                this.setNoAi(true);
                return false;
            }
        }

        if (safePosition.distanceToSqr(this.position()) > 1.0E-6D) {
            this.moveTo(safePosition.x, safePosition.y, safePosition.z, this.getYRot(), this.getXRot());
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.entityData.set(DATA_COLLISION_ESCAPED, escaped);
        this.refreshDimensions();
        this.setNoAi(!escaped);
        return true;
    }

    private boolean retryPendingDimensions(ServerLevel level) {
        boolean desiredEscaped = hasEscape();
        if (this.entityData.get(DATA_COLLISION_ESCAPED) == desiredEscaped) return true;
        if (this.tickCount % DIMENSION_RETRY_INTERVAL == 0) {
            tryApplyEscapeDimensions(level, desiredEscaped);
        }
        boolean ready = this.entityData.get(DATA_COLLISION_ESCAPED) == desiredEscaped;
        if (!ready) {
            this.setNoAi(true);
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        }
        return ready;
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "D-02-107";
        this.abnormalityName = "波迪";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "RED";
        this.maxPEOutput = 12;

        float[] basePreferences = {0.6f, 0.4f, 0.4f, 0.4f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
        this.nestPosition = this.blockPosition();
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, -0.1f, -0.1f, -0.1f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, -0.1f, -0.1f, -0.1f};
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MoveToBlockPosWithElevatorGoal(
                this, 1.0D, () -> nestPosition, this::isReturning,
                this::finishReturningToNest));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::shouldAttackEntity));
    }

    @Override
    protected boolean usesCommonCombatTargetSelection() {
        return false;
    }

    @Override
    protected boolean canSelectCombatTarget(LivingEntity target) {
        return super.canSelectCombatTarget(target) && hasEscape() && !isReturning()
                && FacilityAbnormalityTargetPolicy.isPersonnel(target);
    }

    private boolean shouldAttackEntity(LivingEntity entity) {
        if (!canSelectCombatTarget(entity)) {
            return false;
        }

        // 如果有锁定目标且未离开维度，优先攻击锁定目标
        if (hasLockedTarget && lockedTarget != null && !dimensionChanged) {
            return entity.getUUID().equals(lockedTarget);
        }

        // 玩家（包括 AI 员工）和设施文职使用同一人员身份规则。
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        boolean dimensionsReady = retryPendingDimensions((ServerLevel) level());

        if (!hasEscape()) {
            setNoAi(true);
            nestPosition = this.blockPosition(); // 记录收容位置
            return;
        }

        if (!dimensionsReady) {
            return;
        }

        setNoAi(false);

        if (isReturning()) {
            handleReturningState();
            return;
        }

        // 攻击冷却
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // 攻击动画计时
        if (isPlayingAttack) {
            attackAnimationTimer++;

            // 0.88秒后造成伤害（18tick）
            if (attackAnimationTimer >= 18) {
                if (pendingAttackTarget != null && pendingAttackTarget.isAlive()) {
                    executeDamage(pendingAttackTarget);
                }

                // 重置状态
                isPlayingAttack = false;
                attackAnimationTimer = 0;
                pendingAttackTarget = null;
                attackCooldown = 20; // 开始冷却
            }
            return; // 播放动画期间不执行其他逻辑
        }

        // 检测锁定目标是否离开维度
        if (hasLockedTarget && lockedTarget != null && !dimensionChanged) {
            Player lockedPlayer = level().getPlayerByUUID(lockedTarget);
            if (lockedPlayer == null) {
                dimensionChanged = true;
                broadcastMessage(Component.translatable(
                    "message.lobotocraft.ppodae.target_dimension_changed"));
            }
        }
        if (!isPlayingAttack && !isReturning()) {
            boolean isMoving = this.getDeltaMovement().horizontalDistance() > 0.01D;
            String currentAnim = getAnimation();

            if (isMoving && !"walk".equals(currentAnim)) {
                setAnimation("walk");
            } else if (!isMoving && !"idle".equals(currentAnim)) {
                setAnimation("idle");
            }
        }
        // 发起攻击（播放动画）
        if (attackCooldown == 0 && !isPlayingAttack && this.getTarget() != null) {
            double distance = this.distanceTo(this.getTarget());
            if (distance <= 3.0D) {
                startAttack(this.getTarget());
            }
        }
    }

    private void startAttack(LivingEntity target) {
        if (!canSelectCombatTarget(target)) return;
        // 随机选择攻击动画
        String attackAnim = random.nextBoolean() ? "attack1" : "attack2";
        setAnimation(attackAnim);

        // 设置状态
        isPlayingAttack = true;
        attackAnimationTimer = 0;
        pendingAttackTarget = target;
    }

    private void executeDamage(LivingEntity target) {
        if (!canSelectCombatTarget(target)) return;
        // 造成伤害
        double distance = this.distanceTo(target);
        if (distance <= 3.0D && this.hasLineOfSight(target)) {
            float damage = 4.0f + random.nextFloat() * 2.0f;
            target.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
        }
        this.playSound(ModSounds.PPODAE_ATTACK.get(), 1.0F, 1.0F);
        // 检查是否击杀了目标
        if (!target.isAlive()) {
            onKillTarget(target);
        }
        if (this.getDeltaMovement().horizontalDistance() > 0.01D) {
            setAnimation("walk");
        } else {
            setAnimation("idle");
        }
    }

    @Override
    public void stopEscape() {
        boolean wasEscaped = hasEscape();
        super.stopEscape();
        if (!wasEscaped || hasEscape()) return;
        if (this.level() instanceof ServerLevel level) {
            tryApplyEscapeDimensions(level, hasEscape());
        }
        noPhysics = false;
        setReturning(false);
        setAnimation("idle");
        hasLockedTarget = false;
        lockedTarget = null;
        dimensionChanged = false;

        // 重置攻击状态
        isPlayingAttack = false;
        attackAnimationTimer = 0;
        attackCooldown = 0;
        pendingAttackTarget = null;
    }

    @Override
    protected void prepareForContainmentReturn() {
        if (this.level() instanceof ServerLevel level) {
            tryApplyEscapeDimensions(level, false);
        }
    }

    private void onKillTarget(LivingEntity killed) {
        broadcastMessage(Component.translatable(
            "message.lobotocraft.ppodae.killed_target", killed.getDisplayName()));
        startReturningToNest();
    }

    private void handleReturningState() {
        if (nestPosition == null) {
            stopEscape();
            return;
        }

        Vec3 nestVec = Vec3.atCenterOf(nestPosition);
        Vec3 currentVec = this.position();
        double distance = currentVec.distanceTo(nestVec);

        if (distance < 1.5D) {
            finishReturningToNest();
            return;
        }

        setAnimation("walk");
    }

    private void finishReturningToNest() {
        noPhysics = false;
        stopEscape();
    }

    private void startReturningToNest() {
        setReturning(true);
        noPhysics = false;
        setTarget(null);
        setAnimation("walk");
        this.playSound(ModSounds.PPODAE_RETURN.get(), 1.0F, 1.0F);
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (this.qliphothCounter <= 0 && !this.hasEscape()) {
            // 工作导致出逃，锁定工作玩家
            hasLockedTarget = true;
            lockedTarget = player.getUUID();
            dimensionChanged = false;

            if (player.getServer() != null) {
                player.getServer().sendSystemMessage(Component.translatable(
                        "message.lobotocraft.abnormality.ppodae.escaped_target", player.getDisplayName()));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.abnormality.ppodae.escaped_you"));
            }

            triggerEscape();
            this.setTarget(player);
        }
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        super.triggerEscape();
        if (this.level() instanceof ServerLevel level && hasEscape()) {
            tryApplyEscapeDimensions(level, true);
        }
        if (!this.level.isClientSide) {
            for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
                if (EgoArmorHelper.isFullEGO(player, "ppodae")) {
                    ParticleUtil.spawnParticlesAroundEntity(player, ParticleTypes.HEART, 15, 0.1D);
                }
            }
        }
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        this.playSound(ModSounds.PPODAE_WORK_START.get());
        return super.onWorkStart(player, workType);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return entity instanceof LivingEntity living && canSelectCombatTarget(living);
    }

    // ========== 数据同步器 Getter/Setter ==========
    public boolean isReturning() {
        return this.entityData.get(DATA_RETURNING);
    }

    private void setReturning(boolean returning) {
        this.entityData.set(DATA_RETURNING, returning);
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 25;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 20;
    }

    @Override
    public int getBasicInfoCost() {
        return 12;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 4;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 12;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 4;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        if (EgoArmorHelper.isWearingFullSet(player, "ppodae")) return;
        float baseDamage = 2 + this.random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), baseDamage);
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“波迪”的皮毛蓬松又柔软，当你情绪低落的时候，不妨来摸摸它吧！");
        logs.add("迄今为止，员工们至少拍下了55张有关“波迪”的照片。");
        logs.add("如果你觉得“波迪”不可爱，那么这足以成为你被降级的理由。");
        logs.add("有时，“波迪”的身边会多出几根来源不明的骨头，可又有谁会在乎呢？");
        return logs;
    }

    @Override
    public String name() {
        return "ppodae";
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.05f, 0),
                new ObservationLevelBonus(0.0f, 5, true, false, false),
                new ObservationLevelBonus(0.05f, 0, false, true, true),
                new ObservationLevelBonus(0.0f, 5)
        };
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
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/ppodae_curio.png"),
                "超特么可爱！！！",
                "头饰",
                "ppodae_curio",
                """
                        最大生命值+4
                        成功率+2
                        工作速度+2"""
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/ppodae_weapon.png"),
                "超特么可爱！！！",
                getRiskLevel(),
                "RED",
                "5-7",
                "1.0",
                "2格",
                getWeaponDevelopmentMaxCount(),
                "ppodae_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/ppodae_armor.png"),
                "超特么可爱！！！",
                getRiskLevel(),
                0.8f,
                1.5f,
                0.8f,
                2.0f,
                getArmorDevelopmentMaxCount(),
                "ppodae"
        );
    }

    @Override
    public void die(DamageSource p_21809_) {
        setAnimation("die");
        playSound(ModSounds.PPODAE_DEATH.get());
        super.die(p_21809_);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "dj", 0, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntityPpodae> event) {
        if (this.hasEscape()) {
            String action = getAnimation();
            // 死亡动画
            if ("die".equals(action)) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("animation.ppodae_escape.death"));
            }
            // 攻击动画
            if ("attack1".equals(action)) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("animation.ppodae_escape.attack1"));
            }
            if ("attack2".equals(action)) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("animation.ppodae_escape.attack2"));
            }
            // 返回收容室或移动 - 走路动画
            boolean isMoving = this.getDeltaMovement().horizontalDistance() > 0.01D;
            if (isReturning() || "walk".equals(action) || isMoving) {
                return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ppodae_escape.walk"));
            }
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ppodae_escape.idle"));
        }

        // 未出逃的待机动画
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ppodae.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 230.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CollisionEscaped", this.entityData.get(DATA_COLLISION_ESCAPED));
        tag.putBoolean("Returning", isReturning());
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("AttackAnimationTimer", attackAnimationTimer);
        tag.putBoolean("IsPlayingAttack", isPlayingAttack);
        tag.putBoolean("HasLockedTarget", hasLockedTarget);
        tag.putBoolean("DimensionChanged", dimensionChanged);

        if (lockedTarget != null) {
            tag.putUUID("LockedTarget", lockedTarget);
        }
        if (nestPosition != null) {
            tag.putInt("NestX", nestPosition.getX());
            tag.putInt("NestY", nestPosition.getY());
            tag.putInt("NestZ", nestPosition.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean collisionEscaped = tag.contains("CollisionEscaped") && tag.getBoolean("CollisionEscaped");
        this.entityData.set(DATA_COLLISION_ESCAPED, collisionEscaped);
        this.refreshDimensions();
        this.setNoAi(collisionEscaped != hasEscape() || !hasEscape());
        setReturning(tag.getBoolean("Returning"));
        attackCooldown = tag.getInt("AttackCooldown");
        attackAnimationTimer = tag.getInt("AttackAnimationTimer");
        isPlayingAttack = tag.getBoolean("IsPlayingAttack");
        hasLockedTarget = tag.getBoolean("HasLockedTarget");
        dimensionChanged = tag.getBoolean("DimensionChanged");

        if (tag.hasUUID("LockedTarget")) {
            lockedTarget = tag.getUUID("LockedTarget");
        }
        if (tag.contains("NestX")) {
            nestPosition = new BlockPos(tag.getInt("NestX"), tag.getInt("NestY"), tag.getInt("NestZ"));
        }
    }
}
