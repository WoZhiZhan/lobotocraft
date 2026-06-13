package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.AnimationRunnable;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class EntityThornBus extends AbstractAbnormality {
    private final Random random = new Random();

    public EntityThornBus(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityThornBus((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIMATION, "idle");
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-02-98";
        this.abnormalityName = "棘刺公交";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "BLACK";
        this.maxPEOutput = 18;

        float[] basePreferences = {0.6f, 0.4f, 0.5f, 0.3f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        return super.onWorkStart(player, workType);
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.0f, 4),
                new ObservationLevelBonus(0.04f, 0, true, false, false),
                new ObservationLevelBonus(0.0f, 4, false, true, false),
                new ObservationLevelBonus(0.04f, 0, false, false, true)
        };
    }

    @Override
    public float getGiftProbability() {
        return 0.04f;
    }

    @Override public int getWeaponDevelopmentCost() { return 45; }
    @Override public int getWeaponDevelopmentMaxCount() { return 1; }
    @Override public int getArmorDevelopmentCost()  { return 30; }
    @Override public int getArmorDevelopmentMaxCount() { return 1; }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/thorn_bus_curio.png"),
                "因乐癫狂", "颈部", "thorn_bus_curio",
                "最大精神值+10,成功率+6,工作速度+6",
                "玩家受到任何伤害时回复2-4点精神值");
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/thorn_bus_weapon.png"),
                "因乐癫狂", RiskLevel.HE, "BLACK", "1-4", "快", "3格",
                getWeaponDevelopmentMaxCount(), "thorn_bus_weapon");
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/thorn_bus_armor.png"),
                "因乐癫狂", RiskLevel.HE,
                1.2f, 0.8f, 0.8f, 1.5f,
                getArmorDevelopmentMaxCount(), "thorn_bus");
    }

    @Override public float[] getGiftRenderOffset()   { return new float[]{1f,    0.0f, 0.0f}; }
    @Override public float[] getWeaponRenderScale()  { return new float[]{0.9f,  0.7f, 0.7f}; }
    @Override public float[] getWeaponRenderOffset() { return new float[]{13.5f, 0.0f, 0.0f}; }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        triggerEscape();
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        increaseQliphothCounter(1);
        AtomicBoolean noTemperance = new AtomicBoolean(false);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
            if (data.getTemperanceLevel() < 4) {
                noTemperance.set(true);
            }
        });
        if (noTemperance.get()) {
           killMob(player);
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        decreaseQliphothCounter(1);
    }

    @Override
    public void die(DamageSource damageSource) {
        setAnimation("death");
        playSound(ModSounds.THORN_BUS_DEATH.get());
        super.die(damageSource);
    }

    private void killMob(LivingEntity living) {
        if (getAnimation().contains("attack")) return;
        new AnimationTimerEntry(this, "attack", "idle", 2000, () -> {
            if (!(living instanceof Player)) return false;
            boolean hasPlayer = false;
            for (Player player : EntityUtil.findPlayersAround(EntityThornBus.this, 10, 32)) {
                if (player != getTarget()) {
                    hasPlayer = true;
                    stopEscape();
                    break;
                }
            }
            return !hasPlayer;
        });
        playSound(ModSounds.THORN_BUS_PUT_TO_DEATH_ATTACK.get());
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10000, 255));
        living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10000, 255));
        TimerEntry timerEntry = new TimerEntry() {
            @Override
            public void onEnd(@NotNull LivingEntity living) {
                if (EntityUtil.getDistanceBetweenEntities(EntityThornBus.this, living) <= 3D) {
                    living.kill();
                    playSound(ModSounds.THORN_BUS_PARTICLE.get());
                }
            }
        };
        timerEntry.addSkillTimer(living, 0, 1200, 1);
    }

    @Override
    public String getAnimation() {
        String action = super.getAnimation();
        if (action == null || action.isEmpty()) {
            setAnimation("idle");
            action = "idle";
        }
        return action;
    }

    private void attackMob(LivingEntity living) {
        if (getAnimation().contains("attack2")) return;
        new AnimationTimerEntry(this, "attack2", "idle", 2000);
        if (random.nextInt(2) == 0) {
            playSound(ModSounds.THORN_BUS_FACE_ATTACK_1.get());
        } else playSound(ModSounds.THORN_BUS_FACE_ATTACK_2.get());
        TimerEntry timerEntry = new TimerEntry() {
            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (getExecutions() == 8) {
                    attack(living);
                }
                if (getExecutions() == 14) {
                    attack(living);
                }
                if (getExecutions() == 25) {
                    attack(living);
                }
            }
        };
        timerEntry.addSkillTimer(living, 0, 1200, 20);
    }

    private void attack(@NotNull LivingEntity living) {
        AtomicReference<Float> damage = new AtomicReference<>(9f);
        living.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
            if (data.getTemperanceLevel() <= 3) {
                damage.set(9f + random.nextInt(16));
            }
            if (data.getTemperanceLevel() <= 4) {
                damage.set(3f + random.nextInt(6));
            }
            if (data.getTemperanceLevel() <= 5) {
                damage.set(1f + random.nextInt(3));
            }
        });
        living.hurt(DamageHelper.getDamage(EntityThornBus.this, "black"), damage.get());
    }

    @Override
    public @NotNull SpawnGroupData finalizeSpawn(ServerLevelAccessor p_146746_, DifficultyInstance p_146747_, MobSpawnType p_146748_, @Nullable SpawnGroupData p_146749_, @Nullable CompoundTag p_146750_) {
        new AnimationTimerEntry(this, "release", "idle", 2500);
        return super.finalizeSpawn(p_146746_, p_146747_, p_146748_, p_146749_, p_146750_);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (hasEscape() && getTarget() != null) {
            this.yBodyRot = this.getYRot();
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());
        if (hasEscape() && getTarget() != null && getTarget().isAlive()) {
            // 获取目标方向
            double dx = getTarget().getX() - getX();
            double dz = getTarget().getZ() - getZ();
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);

            // 平滑转向
            float currentYaw = getYRot();
            float deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);
            float newYaw = currentYaw + Mth.clamp(deltaYaw, -10.0F, 10.0F);

            setYRot(newYaw);
            setYHeadRot(newYaw);
            yBodyRot = newYaw;
        }
        if (level().isClientSide) {
            return;
        }
        if (hasEscape() && this.tickCount % 40 == 0) {
            if (getAnimation().contains("idle")) {
                for (LivingEntity e : EntityUtil.findAllEntities(this, 1.8D)) {
                    if (e instanceof Villager) {
                        killMob(e);
                        break;
                    }
                    if (e instanceof Player player) {
                        if (MentalValueUtil.getMentalValue(player) <= 0f) {
                            playSound(ModSounds.THORN_BUS_USE_PUT_TO_DEATH.get());
                            killMob(player);
                            break;
                        }
                        attackMob(player);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("当与“棘刺公交”的“信任游戏”达到高潮时，它会用尾巴上的刺抚弄你的小脑瓜。");
        logs.add("虽然有说法称：它提供的快感远非人类所能忍受，但没有人能描述这到底是种什么感觉。");
        logs.add("突破收容时，“棘刺公交”上绑着一条类似镣铐的项链。是谁给它绑上的？又是为了什么？没人知道。");
        logs.add("一些痴迷于快感的职员试图从“棘刺公交”身上拔下几根刺，但它的刺并非外力所能拔下。");
        return logs;
    }

    @Override
    public int getBasicInfoCost() {
        return 16;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 5;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 16;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 4;
    }

    @Override
    public String name() {
        return "thorn_bus";
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 1.0f + random.nextInt(2) + 6;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        if (DamageHelper.isRangedAttack(damageSource))
            return false;
        return super.hurt(damageSource, f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityThornBus> event) {
        String action = getAnimation();
        return switch (action) {
            case "release" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("release"));
            case "attack" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("attack"));
            case "attack2" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("attack2"));
            case "death" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("death"));
            default ->
                    event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 830D)
                .add(Attributes.FLYING_SPEED, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1024D);
    }
}