package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityLightFollower;
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
import net.minecraft.nbt.Tag;
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

public class EntitySmilingCorpseMountain extends AbstractAbnormality {
    private final Random random = new Random();

    public EntitySmilingCorpseMountain(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        setAnimation("idle1");
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

    public int getClerkDieCount() {
        return this.getPersistentData().getInt("ClerkDieCount");
    }

    public void setClerkDieCount(int count) {
        this.getPersistentData().putInt("ClerkDieCount", count);
    }

    public void addClerkDieCount(int v) {
        this.setClerkDieCount(this.getClerkDieCount() + v);
    }

    public void triggerClerkDieCount() {
        setAnimation("ready_run");
        playSound(ModSounds.SMILING_CORPSE_MOUNTAIN_COUNTER_DECREASED_TO_1.get());
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "T-01-75";
        this.abnormalityName = "微笑的尸山";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 30;

        // 工作偏好
        float[] basePreferences = {0.0f, 0.0f, 0.0f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.5f, 0.5f};  // 本能
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};  // 洞察
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};  // 沟通
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.5f, 0.55f}; // 压迫
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
        logs.add("“微笑的尸山”正带着满身的笑脸寻找死尸的气味。");
        logs.add("“微笑的尸山”的身体里保存着所有尸体的笑容，它正等待着鲜血溅出的味道。");
        return logs;
    }

    @Override
    public int getBasicInfoCost() {
        return 30;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 6;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 30;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 10;
    }

    @Override
    public String name() {
        return "smiling_corpse_mountain";
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
    public double getAbnormalityAmbientSoundRange() {
        return 10D;
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

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
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
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntitySmilingCorpseMountain((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        setAnimation("model.5");
    }

    @Override
    public void die(DamageSource p_21809_) {
        setAnimation("death1");
        super.die(p_21809_);
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());
        if (!level().isClientSide && !hasEscape()) {
            setTarget(null);
            setAnimation("idle1");
            return;
        }
    }

    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.SMILING_CORPSE_MOUNTAIN_ONE_STAGES_ATTACK.get();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 6.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), damage);
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 450;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.SMILING_CORPSE_MOUNTAIN_IDLE.get();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        // 控制器1：移动动画
        controllerRegistrar.add(new AnimationController<>(this, "movement", 0, this::movementPredicate));
        // 控制器2：动作动画（攻击、出逃等）
        controllerRegistrar.add(new AnimationController<>(this, "action", 0, this::actionPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntitySmilingCorpseMountain> event) {
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move1"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionPredicate(AnimationState<EntitySmilingCorpseMountain> event) {
        // 从同步数据获取当前动画状态
        String anim = getAnimation();
        if ("ready_run".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("ready_run").thenLoop("ready_run_idle"));
        } else if ("idle1".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle1"));
        } else if ("death1".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("death1"));
        }
        return PlayState.CONTINUE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    public static class TargetPlayerGoal extends Goal {
        private final EntitySmilingCorpseMountain entity;

        public TargetPlayerGoal(EntitySmilingCorpseMountain entity) {
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
            entity.setAnimation("move1");
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