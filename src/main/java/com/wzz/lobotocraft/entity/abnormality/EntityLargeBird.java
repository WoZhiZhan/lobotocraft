package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityLightFollower;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlackForestDoorGoal;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.BlackForestEvent;
import com.wzz.lobotocraft.event.PlayerControlLock;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.LargeBirdBorderPacket;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EntityLargeBird extends AbstractAbnormality {
    // 数据同步器
    private static final EntityDataAccessor<Integer> DATA_PLAYER_OR_VILLAGER_DEATH_COUNT =
            SynchedEntityData.defineId(EntityLargeBird.class, EntityDataSerializers.INT);
    // 攻击动画计时器
    private int attackAnimationTimer = 0;
    private boolean attackPending = false;

    // 音效播放计时器
    private int ambientSoundCooldown = 0;
    private int attackCooldown = 0;

    // 攻击音效延迟计时器
    private int attackSoundTimer = 0;

    // 随机数生成器
    private final Random random = new Random();

    public EntityLargeBird(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.set(DATA_ANIMATION, "model.5");
        this.entityData.define(DATA_PLAYER_OR_VILLAGER_DEATH_COUNT, 0);
    }

    public int getDataPlayerOrVillagerDeathCount() {
        return this.entityData.get(DATA_PLAYER_OR_VILLAGER_DEATH_COUNT);
    }

    public void setDataPlayerOrVillagerDeathCount(int count) {
        this.entityData.set(DATA_PLAYER_OR_VILLAGER_DEATH_COUNT, count);
    }

    public void addDataPlayerOrVillagerDeathCount(int count) {
        this.entityData.set(DATA_PLAYER_OR_VILLAGER_DEATH_COUNT, getDataPlayerOrVillagerDeathCount() + count);
        if (getDataPlayerOrVillagerDeathCount() >= 5) {
            setDataPlayerOrVillagerDeathCount(0);
            decreaseQliphothCounter(1);
            if (!hasEscape() && getQliphothCounter() <= 0) {
                triggerEscape();
            }
        }
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 3, true, false, false),
                new ObservationLevelBonus(0.03f, 0, false, true, true)
        };
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "O-02-40";
        this.abnormalityName = "大鸟";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "BLACK";
        this.maxPEOutput = 20;

        // 工作偏好
        float[] basePreferences = {0.45f, 0.35f, 0.4f, 0.25f};
        // 本能，洞察，沟通，压迫
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(5);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        // 基础修正（如果不需要可以都设为0）
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.05f, 0.05f};  // 本能
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};  // 洞察
        levelModifiers[2] = new float[] {0.0f, 0.05f, 0.1f, 0.15f, 0.15f};  // 沟通
        levelModifiers[3] = new float[] {0.0f, -0.05f, -0.1f, -0.15f, -0.25f}; // 压迫
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TargetPlayerGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (entity) -> (entity instanceof Villager || entity instanceof EntityClerk || (entity instanceof Player player
                        && EntityUtil.getDistanceBetweenEntities(this, player) <= 3.1D)) && hasEscape()));
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("大鸟最引人注意的特点就是全身上下无数的眼睛以及手中那盏永不熄灭的明灯。");
        logs.add("事实上，我们并不知道大鸟杀戮的目的是什么。");
        logs.add("可以听到那只鸟零星的叫声。");
        logs.add("面对着大鸟，员工紧张地左摇右摆着。");
        logs.add("员工扭开了头，试图回避眼神接触。员工看到了那盏燃烧着的明灯。");
        logs.add("在扭过头去以避免眼神接触的员工面前，有一盏耀眼的明灯。");
        logs.add("不直视大鸟是个非常明智的选择。");
        return logs;
    }

    @Override
    public boolean canEscape() {
        return true;  // 这是出逃类异想体
    }

    @Override
    public int getBasicInfoCost() {
        return 20;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 7;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 20;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 5;
    }

    @Override
    public String name() {
        return "large_bird";
    }

    @Override
    public void onWorkComplete(ServerPlayer player,
                               WorkType workType,
                               WorkResult result) {
        if (!hasEscape() && getQliphothCounter() <= 0) {
            triggerEscape();
        }
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        if (damageSource.getEntity() instanceof AbstractAbnormality)
            return false;
        if (!level.isClientSide) {
            BlackForestEvent.BlackForestSavedData data =
                    BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            boolean doorSpawnedAndBirdInvolved = data.isDoorSpawned()
                    && data.getEscapedBirdUUIDs().contains(this.getStringUUID());
            if (doorSpawnedAndBirdInvolved) return false;
        }
        return super.hurt(damageSource, f);
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // 工作结果为差时归零计数器
        decreaseQliphothCounter(1);
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        super.onGoodWork(player);
        increaseQliphothCounter(1);
    }

    /**
     * 触发出逃机制
     */
    @Override
    public void triggerEscape() {
        super.triggerEscape();
        EntityLightFollower lightFollowerEntity = new EntityLightFollower(ModEntities.light_follower.get(), level);
        lightFollowerEntity.setLightLevel(4);
        lightFollowerEntity.setOwnerUUID(getUUID());
        if (!level.isClientSide)
            level.addFreshEntity(lightFollowerEntity);
        broadcastMessage("§c§l警告！大鸟已经出逃！");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityLargeBird((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        attackPending = false;
        setAnimation("model.5");
        if (!this.level().isClientSide) {
            for (Player player : EntityUtil.findAllPlayerWithDimension(this, 256, ModDimensions.LOBOTO_KEY)) {
                if (player.getPersistentData().getBoolean("isLargeBirdCharm")) {
                    UUID targetUUID = player.getPersistentData().getUUID("largeBirdCharmTarget");
                    if (targetUUID != null && targetUUID.equals(this.getUUID())) {
                        player.getPersistentData().remove("isLargeBirdCharm");
                        player.getPersistentData().remove("largeBirdCharmStartTime");
                        player.getPersistentData().remove("largeBirdCharmTarget");
                    }
                }
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // 检查攻击冷却
        if (attackCooldown > 0) {
            return false;
        }

        if (target instanceof LivingEntity livingTarget) {
            // 不攻击创造模式玩家
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return false;
            }

            // 开始攻击动画
            attackPending = true;
            attackAnimationTimer = 0;
            setTarget(livingTarget);
            setAnimation("model.1");
            this.playSound(ModSounds.LARGE_BIRD_OPEN_MOUTH.get());
            setDeltaMovement(0,0,0);
            // 延迟播放攻击音效
            attackSoundTimer = 15;

            return true;
        }
        return false;
    }

    public boolean isAttackPending() {
        return attackPending;
    }

    @Override
    public void die(DamageSource p_21809_) {
        setAnimation("model.4");
        super.die(p_21809_);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (hasEscape()) {
            if (this.tickCount % 40 == 0 && !this.level.isClientSide) {
                for (Player player : EntityUtil.findPlayersAround(this, 8, 16)) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        MessageLoader.getLoader().sendToPlayer(serverPlayer, new TriggerShakePacket(20));
                        serverPlayer.playSound(ModSounds.LARGE_BIRD_WALK.get());
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());
        if (!level().isClientSide && hasEscape()) {
            BlackForestEvent.BlackForestSavedData data =
                    BlackForestEvent.BlackForestSavedData.get((ServerLevel) level());
            if (data.isDoorSpawned() && data.getEscapedBirdUUIDs().contains(this.getStringUUID())) {
                // 门已生成，清除攻击状态
                setTarget(null);
                attackPending = false;
                setAnimation("model.5");
                ensureDoorGoalExists();
                return; // 跳过所有攻击/魅惑逻辑
            }
        }
        // 减少攻击冷却
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // 处理延迟播放攻击音效
        if (attackSoundTimer > 0) {
            attackSoundTimer--;
            if (attackSoundTimer == 0) {
                playAttackSound();
            }
        }

        // 处理攻击动画
        if (attackPending && !this.level().isClientSide) {
            attackAnimationTimer++;
            if (attackAnimationTimer == 20) {
                if (this.target != null && EntityUtil.getDistanceBetweenEntities(this, target) <= 3D) {
                    this.playSound(ModSounds.LARGE_BIRD_KILLER_PLAYER.get());
                    this.target.hurt(level.damageSources().fellOutOfWorld(), Float.POSITIVE_INFINITY);
                    this.target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 225));
                    this.target.getPersistentData().remove("isLargeBirdCharm");
                    boolean hasPlayer = false;
                    for (Player player : EntityUtil.findPlayersAround(this, 10, 32)) {
                        if (player != getTarget()) {
                            hasPlayer = true;
                            break;
                        }
                    }
                    if (!hasPlayer) {
                        stopEscape();
                    }
                }
            }

            // 清除攻击动画
            if (attackAnimationTimer >= 40) {
                attackPending = false;
                attackAnimationTimer = 0;
                attackCooldown = 40;
                setAnimation("model.5");
            }
        }
        if (!this.level().isClientSide && hasEscape()) {
            if (this.tickCount % 40 == 0) {
                if (this.target != null && EntityUtil.getDistanceBetweenEntities(this, target) <= 3D) {
                    lookAt(target, 0.5f, 0.5f);
                    doHurtTarget(target);
                }
            }
        }
        if (hasEscape() && this.isAlive()) {
            if (this.tickCount % 500 == 0) {
                setAnimation("model.3");
                setDeltaMovement(0,0,0);
                for (Player player : EntityUtil.findAllPlayerWithDimension(this, 256, ModDimensions.LOBOTO_KEY)) {
                    if (!player.getPersistentData().getBoolean("notLargeBirdCharm") && !player.isCreative() && !player.isSpectator() && player == getTarget()) {
                        if (!canCharm(player)) {
                            PlayerControlLock.lock(player, this, 0.1d, 2.5d);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 225));
                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 225));
                            player.getPersistentData().putBoolean("isLargeBirdCharm", true);
                            if (player instanceof ServerPlayer serverPlayer)
                                MessageLoader.getLoader().sendToPlayer(serverPlayer, new LargeBirdBorderPacket(999999));
                        } else {
                            ParticleUtil.spawnParticlesAroundEntity(player, ModParticleTypes.BLUE_GLINT.get(), 30, 0.1d);
                            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);
                        }
                    }
                }
            }
            if (this.tickCount % 20 == 0) {
                checkCharmedPlayers();
            }
        }
    }

    private boolean canCharm(Player player) {
        if (EgoArmorHelper.hasEquipmentCombination(player, "large_bird", true, false, true)) {
            return true;
        }
        return EgoArmorHelper.isFullEGO(player, "end_bird") && CuriosUtil.hasCurios(player, ModItems.LARGEBIRD_CURIO.get());
    }

    private boolean doorGoalAdded = false;

    private void ensureDoorGoalExists() {
        if (doorGoalAdded) return;

        boolean hasDoorGoal = this.goalSelector.getRunningGoals()
                .anyMatch(goal -> goal.getGoal() instanceof MoveToBlackForestDoorGoal);

        if (!hasDoorGoal) {
            this.goalSelector.addGoal(0, new MoveToBlackForestDoorGoal(this, 0.8));
            doorGoalAdded = true;
        }
    }

    /**
     * 检查被魅惑的玩家是否超时未靠近大鸟
     */
    private void checkCharmedPlayers() {
        if (this.level().isClientSide) return;
        for (Player player : EntityUtil.findAllPlayerWithDimension(this, 256, ModDimensions.LOBOTO_KEY)) {
            CompoundTag data = player.getPersistentData();
            if (!data.contains("isLargeBirdCharm", net.minecraft.nbt.Tag.TAG_BYTE)) {
                continue;
            }
            if (data.getBoolean("isLargeBirdCharm") && !player.isCreative() && !player.isSpectator()) {
                if (!data.contains("largeBirdCharmTarget", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                    continue;
                }
                UUID targetUUID = data.getUUID("largeBirdCharmTarget");
                if (targetUUID != null && targetUUID.equals(this.getUUID())) {
                    if (!data.contains("largeBirdCharmStartTime", net.minecraft.nbt.Tag.TAG_LONG)) {
                        continue;
                    }
                    long charmStartTime = data.getLong("largeBirdCharmStartTime");
                    long currentTime = player.level().getGameTime();
                    long elapsedTicks = currentTime - charmStartTime;
                    if (elapsedTicks >= 600) {
                        double distance = EntityUtil.getDistanceBetweenEntities(this, player);

                        if (distance > 3.0D) {
                            teleportPlayerNearBird(player);
                            data.putLong("largeBirdCharmStartTime", currentTime);
                        }
                    }
                }
            }
        }
    }

    /**
     * 将被魅惑的玩家传送到大鸟附近的安全位置
     */
    private void teleportPlayerNearBird(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 寻找大鸟周围16格内的安全传送点
        BlockPos birdPos = this.blockPosition();
        BlockPos safePos = findSafeTeleportLocation(birdPos, 16);

        if (safePos != null) {
            // 传送玩家
            serverPlayer.teleportTo(
                    safePos.getX() + 0.5,
                    safePos.getY(),
                    safePos.getZ() + 0.5
            );

            // 给予失明效果
            serverPlayer.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    100,  // 5秒
                    0,    // 效果等级
                    false, // 不显示粒子
                    true   // 显示图标
            ));

            // 播放音效和粒子效果
            this.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );

            ParticleUtil.spawnParticlesAroundEntity(
                    player,
                    ModParticleTypes.BLUE_GLINT.get(),
                    20,
                    0.5d
            );
        }
    }

    /**
     * 寻找合适的安全传送位置
     */
    private BlockPos findSafeTeleportLocation(BlockPos center, int radius) {
        Random random = new Random();
        int maxAttempts = 20;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // 在半径范围内随机选择位置
            int x = center.getX() + random.nextInt(radius * 2) - radius;
            int z = center.getZ() + random.nextInt(radius * 2) - radius;

            // 找到该XZ坐标的最高方块
            BlockPos topPos = this.level().getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    new BlockPos(x, 0, z)
            );

            // 检查位置是否安全（有站立空间且不是液体）
            BlockPos checkPos = topPos.above();
            if (isSafeLocation(checkPos) && isSafeLocation(checkPos.above())) {
                return checkPos;
            }

            // 如果最高点不安全，尝试在该列找到安全位置
            for (int y = topPos.getY(); y > center.getY() - 10 && y > 0; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isSafeLocation(pos) && isSafeLocation(pos.above())) {
                    return pos.above();
                }
            }
        }

        // 如果找不到安全位置，返回大鸟旁边
        BlockPos fallback = center.north();
        if (isSafeLocation(fallback) && isSafeLocation(fallback.above())) {
            return fallback.above();
        }

        return center; // 最后备选方案
    }

    /**
     * 检查位置是否安全可以站立
     */
    private boolean isSafeLocation(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        BlockState blockAbove = this.level().getBlockState(pos.above());

        // 位置不能是液体、不能是实体方块（除了地面）
        return !blockState.liquid()
                && !blockAbove.isSolid()
                && !this.level().getBlockState(pos).isAir()
                && !blockState.is(BlockTags.FIRE);
    }

    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.LARGE_BIRD_CHARM.get();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 2.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), damage);
    }

    /**
     * 播放攻击音效（音频3）
     */
    @Override
    public void playAttackSound() {
        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                getAttackSound(),
                SoundSource.HOSTILE,
                1.0f,
                1.0f
        );
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/largebird_curio.png"),
                "目灯",
                "头部",
                "largebird_curio",
                "最大生命值+3,成功率+3,工作速度+3"
        );
    }

    @Override
    public float getGiftProbability() {
        return 0.03f;
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 70;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 60;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/largebird_weapon.png"),
                "目灯",
                getRiskLevel(),
                "BLACK",           // 伤害类型
                "22-28",             // 攻击力
                "3.0",            // 攻击速度
                "近",              // 攻击距离
                getWeaponDevelopmentMaxCount(),                  // 研发总数
                "largebird_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/largebird_armor.png"),
                "目灯",
                getRiskLevel(),
                0.8f,    // RED抗性
                0.7f,    // WHITE抗性
                0.4f,    // BLACK抗性
                1.5f,    // PALE抗性
                getArmorDevelopmentMaxCount(),        // 研发总数
                "largebird"
        );
    }

    @Override
    public float[] getWeaponRenderScale() {
        return new float[] {0.8f, 0.8f, 0.8f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {20f, 0.0f, 0.0f};
    }

    /**
     * 获取当前动画
     */
    public String getCurrentAnimation() {
        return this.entityData.get(DATA_ANIMATION);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        // 控制器1：移动动画
        controllerRegistrar.add(new AnimationController<>(this, "movement", 0, this::movementPredicate));
        // 控制器2：动作动画（攻击、出逃等）
        controllerRegistrar.add(new AnimationController<>(this, "action", 0, this::actionPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntityLargeBird> event) {
        if (event.isMoving() && !isAttackPending()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.2"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionPredicate(AnimationState<EntityLargeBird> event) {
        // 从同步数据获取当前动画状态
        String anim = getCurrentAnimation();
        if ("model.3".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.model.3"));
        } else if ("model.1".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.model.1"));
        } else if ("model.4".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.model.4"));
        } else if ("model.5".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.5"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    protected void playStepSound(BlockPos p_20135_, BlockState p_20136_) {
        SoundType soundtype = p_20136_.getSoundType(this.level, p_20135_, this);
        this.playSound(ModSounds.LARGE_BIRD_WALK.get(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1600.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CurrentAnimation", this.entityData.get(DATA_ANIMATION));
        tag.putInt("AttackAnimationTimer", attackAnimationTimer);
        tag.putBoolean("AttackPending", attackPending);
        tag.putInt("AmbientSoundCooldown", ambientSoundCooldown);
        tag.putInt("AttackSoundTimer", attackSoundTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_ANIMATION, tag.getString("CurrentAnimation"));
        attackAnimationTimer = tag.getInt("AttackAnimationTimer");
        attackPending = tag.getBoolean("AttackPending");
        ambientSoundCooldown = tag.getInt("AmbientSoundCooldown");
        attackSoundTimer = tag.getInt("AttackSoundTimer");
    }

    public static class TargetPlayerGoal extends Goal {
        private final EntityLargeBird entity;

        public TargetPlayerGoal(EntityLargeBird entity) {
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            return entity.hasEscape();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            entity.setAnimation("walk");
        }

        @Override
        public void tick() {
            LivingEntity target = entity.target;
            if (!(target instanceof ServerPlayer)) {
                return;
            }
            entity.getNavigation().moveTo(target, 1.2);
            entity.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        @Override
        public void stop() {
            entity.getNavigation().stop();
        }
    }
}
