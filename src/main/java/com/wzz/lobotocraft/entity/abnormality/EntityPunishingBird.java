package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.entity.EntityLightFollower;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlackForestDoorGoal;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlockPosWithElevatorGoal;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.BlackForestEvent;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;

public class EntityPunishingBird extends AbstractAbnormality {

    // ========== 数据同步器 ==========
    private static final EntityDataAccessor<Boolean> DATA_ANGRY =
            SynchedEntityData.defineId(EntityPunishingBird.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(EntityPunishingBird.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_CURRENT_ACTION =
            SynchedEntityData.defineId(EntityPunishingBird.class, EntityDataSerializers.STRING);

    // ========== 状态追踪 ==========
    // 普通攻击状态
    private int normalAttackCooldown = 0;
    private int normalAttackCount = 0;
    private UUID currentHelpTarget = null;

    // 愤怒状态
    private UUID angryTarget = null;
    private int angryTimer = 0;
    private static final int ANGRY_DURATION = 1200;
    private int bigBiteTimer = 0;
    private boolean bigBitePending = false;

    // 返回收容室
    private BlockPos nestPosition = null;

    private final Random random = new Random();

    public EntityPunishingBird(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 40, true);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.BLOCKED, -1.0F);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityPunishingBird((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    // ===== 飞行导航 =====
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingNav = new FlyingPathNavigation(this, level);
        flyingNav.setCanOpenDoors(false);
        flyingNav.setCanFloat(true);
        flyingNav.setCanPassDoors(true);
        return flyingNav;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANGRY, false);
        this.entityData.define(DATA_RETURNING, false);
        this.entityData.define(DATA_CURRENT_ACTION, "idle");
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-02-56";
        this.abnormalityName = "惩戒鸟";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "RED";
        this.maxPEOutput = 12;

        float[] basePreferences = {0.4f, 0.6f, 0.55f, 0.3f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(4);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    public @NotNull SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                 MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                                 @Nullable CompoundTag tag) {
        this.nestPosition = this.blockPosition();
        this.level().setBlock(this.nestPosition, ModBlocks.PUNISHING_BIRD.get().defaultBlockState(), 3);
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.05f, 0.05f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, -0.05f, -0.05f, -0.05f};
        levelModifiers[3] = new float[] {0.0f, -0.1f, -0.2f, -0.3f, -0.3f};
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new MoveToBlockPosWithElevatorGoal(
                this, 1.2D, () -> nestPosition, this::isReturning, this::finishReturningToNest));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::shouldAttackEntity));
    }

    private boolean shouldAttackEntity(LivingEntity entity) {
        if (!hasEscape() || isReturning()) {
            return false;
        }

        if (isAngry() && angryTarget != null) {
            return entity.getUUID().equals(angryTarget);
        }

        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        return false;
    }

    @Override
    public void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        // 飞行生物不受摔落伤害
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        // 飞行生物不受摔落伤害
        return false;
    }

    @Override
    public void updateFallFlying() {
        // 保持在空中
        if (!this.level().isClientSide && hasEscape()) {
            // 防止掉落
            if (this.getDeltaMovement().y < 0 && !this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
            }
        }
    }

    // ========== 工作回调 ==========

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (MentalValueUtil.getMentalValue(player) <= 0) {
            decreaseQliphothCounter(1);
            player.sendSystemMessage(Component.literal("§c你陷入了恐慌，惩戒鸟的计数器-1..."));
        }
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.0f, 5),
                new ObservationLevelBonus(0.05f, 0, true, false, false),
                new ObservationLevelBonus(0.0f, 5, false, true, true),
                new ObservationLevelBonus(0.05f, 0)
        };
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        triggerEscape();
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        decreaseQliphothCounter(1);
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        super.onGoodWork(player);
        increaseQliphothCounter(1);
    }

    @Override
    public void triggerEscape() {
        super.triggerEscape();
        this.getPersistentData().remove("NotFoundTick");
        this.getPersistentData().putInt("EscapeTime", 0);
        normalAttackCount = 0;
        currentHelpTarget = null;

        EntityLightFollower lightFollower = new EntityLightFollower(ModEntities.light_follower.get(), level());
        lightFollower.setLightLevel(4);
        lightFollower.setOwnerUUID(getUUID());
        if (!level().isClientSide) {
            level().addFreshEntity(lightFollower);
        }

        setCurrentAction("idle");
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        this.getPersistentData().remove("NotFoundTick");
        this.getPersistentData().remove("EscapeTime");
        noPhysics = false;
        setAngry(false);
        setReturning(false);
        setCurrentAction("idle");
        normalAttackCount = 0;
        currentHelpTarget = null;
        angryTarget = null;
        angryTimer = 0;
        bigBitePending = false;
        setTarget(null);

        resetMaxHealth();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isReturning()) {
            return false;
        }
        if (!level.isClientSide) {
            BlackForestEvent.BlackForestSavedData data =
                    BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            boolean doorSpawnedAndBirdInvolved = data.isDoorSpawned()
                    && data.getEscapedBirdUUIDs().contains(this.getStringUUID());
            if (doorSpawnedAndBirdInvolved) return false;
        }
        if (source.getEntity() instanceof AbstractAbnormality)
            return false;
        if (source.getEntity() instanceof ServerPlayer player) {
            boolean isPanic = MentalValueUtil.getMentalValue(player) <= 0;
            if (isPanic) {
                return super.hurt(source, amount);
            }
            if (!isAngry() && hasEscape()) {
                becomeAngry(player);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    private void becomeAngry(Player attacker) {
        setAngry(true);
        angryTarget = attacker.getUUID();
        angryTimer = ANGRY_DURATION;

        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(1000.0D);
            this.setHealth(1000.0F);
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 0.8F);

        broadcastMessage("§c§l惩戒鸟被激怒了！它开始追击 " + attacker.getName().getString() + "！");

        setTarget(attacker);
        setCurrentAction("idle");
    }

    private void resetMaxHealth() {
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(200.0D);
            if (this.getHealth() > 200.0F) {
                this.setHealth(200.0F);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (!hasEscape()) {
            setNoAi(true);
            return;
        }

        BlackForestEvent.BlackForestSavedData data =
                BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
        boolean doorSpawnedAndBirdInvolved = data.isDoorSpawned()
                && data.getEscapedBirdUUIDs().contains(this.getStringUUID());

        if (doorSpawnedAndBirdInvolved) {
            // 门已生成，清除所有战斗/返回状态
            setNoAi(false);
            setAngry(false);
            setReturning(false);
            noPhysics = false;
            angryTarget = null;
            setTarget(null);
            this.getPersistentData().remove("NotFoundTick");
            this.getPersistentData().remove("EscapeTime");

            // 确保 goalSelector 中有 MoveToBlackForestDoorGoal
            ensureDoorGoalExists();

            // 不执行其他逻辑
            return;
        }

        setNoAi(false);
        if (!level().isClientSide) {
            if (data.isDoorSpawned() && data.getEscapedBirdUUIDs().contains(this.getStringUUID())) {
                this.getPersistentData().remove("NotFoundTick");
            }
        }
        int escapeTime = this.getPersistentData().getInt("EscapeTime");
        escapeTime++;
        this.getPersistentData().putInt("EscapeTime", escapeTime);

        if (isReturning()) {
            handleReturningState();
            return;
        }

        if (isAngry()) {
            handleAngryState();
            return;
        }

        handleNormalState();

        this.getPersistentData().remove("NotFoundTick");
    }

    public void clearStateForDoor() {
        setAngry(false);
        setReturning(false);
        noPhysics = false;
        angryTarget = null;
        setTarget(null);
        this.getPersistentData().remove("NotFoundTick");
        this.getPersistentData().remove("EscapeTime");
        normalAttackCount = 0;
        currentHelpTarget = null;
    }

    private boolean doorGoalAdded = false;

    private void ensureDoorGoalExists() {
        if (doorGoalAdded) return;

        boolean hasDoorGoal = this.goalSelector.getRunningGoals()
                .anyMatch(goal -> goal.getGoal() instanceof MoveToBlackForestDoorGoal);

        if (!hasDoorGoal) {
            this.goalSelector.addGoal(0, new MoveToBlackForestDoorGoal(this, 1.2));
            doorGoalAdded = true;
        }
    }

    private void handleNormalState() {
        if (normalAttackCooldown > 0) {
            normalAttackCooldown--;
        }

        if (normalAttackCooldown == 0 && this.getTarget() instanceof Player t) {
            double distance = this.distanceTo(t);

            if (distance <= 3.0D) {
                performNormalAttack(t);
                normalAttackCooldown = 20;
            }
        }

        if (shouldReturnToNest()) {
            startReturningToNest();
        }
    }

    private void performNormalAttack(Player target) {
        setCurrentAction("peck_attack");

        target.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), 0.1F);

        boolean wasPanic = MentalValueUtil.getMentalValue(target) <= 0;
        if (wasPanic) {
            float mentalRestore = 0.01F + random.nextFloat() * 0.02F;
            target.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                float maxMental = mental.getEffectiveMaxMentalValue();
                mental.addMentalValue(maxMental * mentalRestore);

                if (mental.getMentalValue() > 0 && currentHelpTarget != null &&
                        currentHelpTarget.equals(target.getUUID())) {
                    target.sendSystemMessage(Component.literal("§a惩戒鸟帮助你恢复了精神！"));
                }
            });
        }

        if (currentHelpTarget == null) {
            currentHelpTarget = target.getUUID();
        }

        normalAttackCount++;
        this.playSound(this.getAttackSound(), 0.8F, 1.0F);
    }

    private boolean shouldReturnToNest() {
        if (normalAttackCount >= 20) {
            return true;
        }

        return false;
    }

    private void handleAngryState() {
        if (!level().isClientSide) {
            BlackForestEvent.BlackForestSavedData data = BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            if (data.isDoorSpawned() && data.getEscapedBirdUUIDs().contains(this.getStringUUID())) {
                setAngry(false);
                angryTarget = null;
                setTarget(null);
                return;
            }
        }
        angryTimer--;
        if (angryTimer <= 0) {
            broadcastMessage("§e惩戒鸟的愤怒平息了，它返回收容室...");
            startReturningToNest();
            return;
        }

        Player target = level().getPlayerByUUID(angryTarget);
        if (target == null || !target.isAlive()) {
            startReturningToNest();
            return;
        }

        setTarget(target);
        double distance = this.distanceTo(target);

        if (bigBitePending) {
            bigBiteTimer++;

            if (bigBiteTimer == 15) {
                this.playSound(ModSounds.LARGE_BIRD_KILLER_PLAYER.get(), 1.5F, 1.0F);
            }

            if (bigBiteTimer >= 22) {
                if (distance <= 3.0D) {
                    float damage = 800.0F + random.nextFloat() * 400.0F;
                    target.hurt(this.damageSources().mobAttack(this), damage);
                    startReturningToNest();
                }

                bigBitePending = false;
                bigBiteTimer = 0;
                setCurrentAction("idle");
            }
            return;
        }

        if (distance <= 2.0D && !bigBitePending) {
            bigBitePending = true;
            bigBiteTimer = 0;
            setCurrentAction("big_bite");
            setDeltaMovement(0, this.getDeltaMovement().y, 0);
        }
    }

    private void handleReturningState() {
        if (nestPosition == null) {
            stopEscape();
            return;
        }

        if (this.distanceToSqr(Vec3.atCenterOf(nestPosition)) <= 2.25D) {
            finishReturningToNest();
        }
        setCurrentAction("fly");
    }

    private void startReturningToNest() {
        setReturning(true);
        noPhysics = false;
        setTarget(null);
        this.getNavigation().stop();
        setCurrentAction("fly");
        broadcastMessage("§e惩戒鸟正在返回收容室...");
    }

    private void finishReturningToNest() {
        if (nestPosition != null) {
            this.teleportTo(nestPosition.getX() + 0.5D, nestPosition.getY(), nestPosition.getZ() + 0.5D);
        }
        stopEscape();
    }

    private List<Player> findNearbyPanicPlayers(double range) {
        List<Player> panicPlayers = new ArrayList<>();
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(range))) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
                    panicPlayers.add(player);
                }
            }
        }
        return panicPlayers;
    }

    public boolean isAngry() {
        return this.entityData.get(DATA_ANGRY);
    }

    private void setAngry(boolean angry) {
        this.entityData.set(DATA_ANGRY, angry);
    }

    public boolean isReturning() {
        return this.entityData.get(DATA_RETURNING);
    }

    private void setReturning(boolean returning) {
        this.entityData.set(DATA_RETURNING, returning);
    }

    public String getCurrentAction() {
        return this.entityData.get(DATA_CURRENT_ACTION);
    }

    private void setCurrentAction(String action) {
        this.entityData.set(DATA_CURRENT_ACTION, action);
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("因为“惩戒鸟”看起来很无聊，所以员工们在“惩戒鸟”的收容单元里放了一棵用作栖息处的树。");
        logs.add("“惩戒鸟”站在树上，回想着那片它曾经居住过的森林以及它的过去。");
        logs.add("实际上，因为“惩戒鸟”的体型娇小，所以放置这棵树可以防止它被踩到。");
        logs.add("实际上，放置这棵树的目的是防止体型娇小的“惩戒鸟”受到不可预料的伤害。");
        logs.add("大多数时候，“惩戒鸟”都像一只普通的鸟一样在收容单元内飞来飞去。");
        logs.add("“惩戒鸟”从不鸣叫，但它的腹部有时会隐隐的抽搐。");
        logs.add("没有多少人知道“惩戒鸟”那隐藏的獠牙。");
        logs.add("有多少人知道“惩戒鸟”那分为几块的，令人作呕的肉？");
        return logs;
    }

    @Override
    public boolean canEscape() {
        return true;
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
        return 3;
    }

    @Override
    public String name() {
        return "punishing_bird";
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 2.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
    }

    @Override
    public SoundEvent getAttackSound() {
        if (this.isAngry())
            return ModSounds.PUNISHING_BIRD_ANGRY_ATTACK.get();
        return ModSounds.PUNISHING_BIRD_ATTACK.get();
    }

    @Override
    public void playAttackSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                getAttackSound(), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/punishing_bird_curio.png"),
                "小喙",
                "颈部",
                "punishing_bird_curio",
                "移动速度+0.02,攻击速度+0.2"
        );
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 30;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 2;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 25;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 2;
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/punishing_bird_weapon.png"),
                "小喙",
                getRiskLevel(),
                "RED",
                "2-3",
                "0.7",
                "远程武器",
                getWeaponDevelopmentMaxCount(),
                "punishing_bird_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/punishing_bird_armor.png"),
                "小喙",
                getRiskLevel(),
                0.7f,
                0.8f,
                1.2f,
                2.0f,
                getArmorDevelopmentMaxCount(),
                "punishing_bird"
        );
    }

    @Override
    public float[] getGiftRenderOffset() {
        return new float[] {1f, 0.0f, 0.0f};
    }

    @Override
    public float[] getWeaponRenderScale() {
        return new float[] {0.8f, 0.8f, 0.8f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {15f, 0.0f, 0.0f};
    }

    // ========== 动画控制器 ==========

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityPunishingBird> event) {
        String action = getCurrentAction();

        return switch (action) {
            case "peck_attack" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.model.2"));
            case "big_bite" -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.model.4"));
            case "fly" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.3"));
            default -> {
                if (event.isMoving()) {
                    yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.3"));
                }
                yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.1"));
            }
        };
    }

    @Override
    public boolean canBeCollidedWith() {
        if (isReturning())
            return false;
        return super.canBeCollidedWith();
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (isReturning()) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
            return;
        }
        super.move(type, movement);
    }


    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.FLYING_SPEED, 0.55D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Angry", isAngry());
        tag.putBoolean("Returning", isReturning());
        tag.putString("CurrentAction", getCurrentAction());
        tag.putInt("NormalAttackCount", normalAttackCount);
        tag.putInt("AngryTimer", angryTimer);
        tag.putInt("NormalAttackCooldown", normalAttackCooldown);

        if (currentHelpTarget != null) {
            tag.putUUID("CurrentHelpTarget", currentHelpTarget);
        }
        if (angryTarget != null) {
            tag.putUUID("AngryTarget", angryTarget);
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
        setAngry(tag.getBoolean("Angry"));
        setReturning(tag.getBoolean("Returning"));
        setCurrentAction(tag.getString("CurrentAction"));
        normalAttackCount = tag.getInt("NormalAttackCount");
        angryTimer = tag.getInt("AngryTimer");
        normalAttackCooldown = tag.getInt("NormalAttackCooldown");

        if (tag.hasUUID("CurrentHelpTarget")) {
            currentHelpTarget = tag.getUUID("CurrentHelpTarget");
        }
        if (tag.hasUUID("AngryTarget")) {
            angryTarget = tag.getUUID("AngryTarget");
        }
        if (tag.contains("NestX")) {
            nestPosition = new BlockPos(tag.getInt("NestX"), tag.getInt("NestY"), tag.getInt("NestZ"));
        }
    }
}
