package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.UUID;

public class EntityNothingThere extends AbstractAbnormality {
    private static final int PHASE_1 = 0;
    private static final int PHASE_2 = 1;
    private static final int PHASE_3 = 2;

    private static final EntityDataAccessor<String> DATA_ANIM =
            SynchedEntityData.defineId(EntityNothingThere.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(EntityNothingThere.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_COUNTDOWN =
            SynchedEntityData.defineId(EntityNothingThere.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_ESCAPED_DISGUISED =
            SynchedEntityData.defineId(EntityNothingThere.class, EntityDataSerializers.BOOLEAN);

    private static final int PHASE_DURATION = 30 * 20;
    private static final double PHASE1_MOVEMENT_SPEED = 0.44D;
    private static final double PHASE3_MOVEMENT_SPEED = 0.264D;
    private static final float PHASE1_HP = 2000f;
    private static final float PHASE3_HP = 3000f;
    private static final int ATTACK_COOLDOWN_TICKS = 30;
    private static final int SPECIAL_ATTACK_CHECK_INTERVAL = 120;
    private static final int PHASE23_AMBIENT_INTERVAL = 967;
    private static final int PHASE3_REGEN_DELAY = 15 * 20;
    private static final int PHASE3_REGEN_AMOUNT = 50;
    private static final int IDLE_SOUND_INTERVAL = 20 * 53;

    private int currentPhase = PHASE_1;
    private int phaseTimer = 0;
    private int attackCooldown = 0;
    private int specialAttackCheckTimer = 0;
    private float specialAttackProbability = 0.40f;
    private int currentAttackType = -1;
    private int attackAnimationTimer = 0;
    private int attackAnimationMaxTimer = 0;
    private int pendingHitCount = 0;
    private int pendingHitIndex = 0;
    private int[] pendingHitTimestamps = null;
    private int[] pendingDamageAmounts = null;
    private boolean disguisedAsPlayer = false;
    private UUID disguisePlayerUUID = null;
    private String disguisePlayerName = "";
    private boolean escapedDisguised = false;
    private int executionCountdown = 0;
    private UUID executionTargetUUID = null;
    private int ambientSoundTimer = 0;
    private boolean phase23AmbientStarted = false;
    private int phase23AmbientFallbackTimer = 0;

    private int lastDamagedTick = 0;
    private int animResetTimer = 0;

    public EntityNothingThere(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, "idle");
        this.entityData.define(DATA_PHASE, PHASE_1);
        this.entityData.define(DATA_COUNTDOWN, 0);
        this.entityData.define(DATA_ESCAPED_DISGUISED, false);
    }

    private void setAnim(String anim) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ANIM, anim);
        }
    }

    private void setAnimTimed(String anim, int duration) {
        setAnim(anim);
        animResetTimer = duration;
    }

    private void backToIdle() {
        setAnim("idle");
    }

    private String getAnim() {
        return this.entityData.get(DATA_ANIM);
    }

    private static final net.minecraft.world.entity.EntityDimensions PHASE_1_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(1.8f, 2f);

    private static final net.minecraft.world.entity.EntityDimensions PHASE_2_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(3f, 4.0f);

    private static final net.minecraft.world.entity.EntityDimensions PHASE_3_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(2.5f, 5.5f);

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return switch (getCurrentPhase()) {
            case PHASE_1 -> PHASE_1_SIZE;
            case PHASE_2 -> PHASE_2_SIZE;
            case PHASE_3 -> PHASE_3_SIZE;
            default -> PHASE_1_SIZE;
        };
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key) && this.level().isClientSide) {
            this.refreshDimensions();
        }
    }
    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-06-20";
        this.abnormalityName = "一无所有";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "RED";
        this.maxPEOutput = 33;

        this.workPreferences = new float[]{0.00f, 0.00f, 0.50f, 0.00f};
        this.fullWorkPreferences = new float[4][5];
        this.fullWorkPreferences[WorkType.INSTINCT.ordinal()] = new float[]{0.00f, 0.00f, 0.35f, 0.40f, 0.45f};
        this.fullWorkPreferences[WorkType.INSIGHT.ordinal()] = new float[]{0.00f, 0.00f, 0.00f, 0.00f, 0.00f};
        this.fullWorkPreferences[WorkType.ATTACHMENT.ordinal()] = new float[]{0.50f, 0.50f, 0.50f, 0.50f, 0.50f};
        this.fullWorkPreferences[WorkType.REPRESSION.ordinal()] = new float[]{0.00f, 0.00f, 0.00f, 0.00f, 0.00f};
        initializeQliphothCounter(1);
    }

    @Override
    public int getBasicInfoCost() {
        return 30;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 30;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 6;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 10;
    }

    @Override
    public int getGoodWorkResultMin() {
        return 22;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 11;
    }
    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 3),
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 3, true, false, false),
                new ObservationLevelBonus(0.03f, 0, false, true, true)
        };
    }

    @Override
    public List<String> getWorkLogs() {
        return List.of(
                "尽管“一无所有”有着酷似人类的外表，然而，目光敏锐的员工会察觉到一丝不对劲。",
                "“一无所有”和其他异想体有所不同，最大的差异就是，“一无所有”从来没有表现出真正的本质。",
                "“一无所有”并没有“活着”的概念，只有“存在”的事实。",
                "你知道“壳”和“皮”的区别吗？",
                "“一无所有”一边紧盯着员工，一边毫无意义地磨着牙。",
                "“一无所有”的碎骨正在嘎吱作响。",
                "“一无所有”呆呆地望着员工，它的外皮破裂了，正发出不自然的声音。",
                "“一无所有”那嘴一样的器官中偶尔会流出一些人体内脏。",
                "员工小心翼翼地走着，不想踩到这异想体流到地上的分泌物。",
                "虽然这个异想体没有做出任何威胁到员工的举动，可员工依然不由自主地浑身打颤。",
                "虽然员工知道这个异想体不会立刻攻击自己，但是在这收容单元所发生的一切本身就会让人感到极度的恐惧。",
                "员工尽最大的努力不去看“一无所有”。",
                "进行工作时，员工发现“一无所有”的某些部位看上去有点眼熟。",
                "员工在“一无所有”的体内发现了些什么。",
                "最后，员工发现了隐藏在“一无所有”外皮里的真相。",
                "可怜的员工终于意识到“一无所有”套着什么了。",
                "她是员工的前辈。虽然他们从未搭过一句话，但他们肯定能成为好拍档的。",
                "员工曾看过她的简历，她脸上的黑痣给员工留下了深刻的印象，这也是为什么员工能如此清楚地记住她。",
                "她在公司工作的时间远超员工，大约在四年前她就加入了公司。员工曾听许多人谈论到她。"
        );
    }
    @Override
    public String name() {
        return "no_things_have";
    }

    @Override
    public String getTexture() {
        if (disguisedAsPlayer || escapedDisguised) {
            return "no_things_have";
        }
        return this.entityData.get(TEXTURE);
    }

    public String getCurrentModelName() {
        if (disguisedAsPlayer || escapedDisguised) {
            return "no_things_have";
        }
        return switch (getCurrentPhase()) {
            case PHASE_2 -> "nothings_egg";
            case PHASE_3 -> "no_things_have_man";
            default -> "no_things_have";
        };
    }

    public int getCurrentPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isEscapedDisguised() {
        return this.entityData.get(DATA_ESCAPED_DISGUISED);
    }

    public int getExecutionCountdownSeconds() {
        return this.entityData.get(DATA_COUNTDOWN);
    }

    public float getSpecialAttackProbability() {
        return specialAttackProbability;
    }

    public void setCurrentPhase(int phase) {
        this.currentPhase = phase;
        this.entityData.set(DATA_PHASE, phase);
        refreshDimensions();
        this.setTexture(switch (phase) {
            case 1 -> "nothings_egg";
            case 2 -> "no_things_have_man";
            default -> "no_things_have";
        });
    }

    public void setPhaseTimer(int timer) {
        this.phaseTimer = timer;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, PHASE1_HP)
                .add(Attributes.MOVEMENT_SPEED, PHASE1_MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.2D);
    }
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (currentPhase == PHASE_3 && DamageHelper.isRedDamage(source)) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result) {
            lastDamagedTick = this.tickCount;
        }
        return result;
    }
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (attackAnimationTimer > 0) {
            this.setYRot(this.yRotO);
            this.yBodyRot = this.yBodyRotO;
            this.yHeadRot = this.yHeadRotO;
        }

        if (animResetTimer > 0) {
            animResetTimer--;
            if (animResetTimer == 0) {
                backToIdle();
            }
        }
        if (hasEscape()) {
            if (!escapedDisguised) {
                tickEscapePhases();
            } else {
                tickDisguisedEscape();
            }
        } else {

            tickContainedAmbient();
        }
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
                ResourceUtil.createInstance("textures/item/nothing_there_curio.png"),
                "拟态",
                "脸颊",
                "nothing_there_curio",
                "使用任意EGO造成伤害时恢复等同于伤害值5%的生命值",
                "最大生命值+10",
                "使用近战EGO攻速+10%"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/nothing_there_weapon.png"),
                "拟态",
                getRiskLevel(),
                "RED",
                "10~14",
                "1.1",
                "3格",
                getWeaponDevelopmentMaxCount(),
                "nothing_there_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/nothing_there_armor.png"),
                "拟态",
                getRiskLevel(),
                0.2f,
                0.5f,
                0.5f,
                1.0f,
                getArmorDevelopmentMaxCount(),
                "nothing_there_armor"
        );
    }

    private void tickEscapePhases() {

        if (attackAnimationTimer > 0) {
            attackAnimationTimer--;
            playScheduledSounds();
            processPendingHits();
            if (attackAnimationTimer == 0) {
                onAttackAnimationEnd();
            }
        }

        switch (currentPhase) {
            case PHASE_1 -> tickPhase1();
            case PHASE_2 -> tickPhase2();
            case PHASE_3 -> tickPhase3Ambient();
        }
    }

    private void tickPhase1() {
        phaseTimer++;
        if (phaseTimer >= PHASE_DURATION) {
            transitionToPhase2();
        }
    }

    private void startPhase1Attack(LivingEntity target) {
        this.setTarget(target);
        currentAttackType = 0;
        attackAnimationMaxTimer = 17;
        attackAnimationTimer = 17;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack", 7);

        scheduleSound(0, ModSounds.NOTHING_THERE_PHASE1_ATTACK.get());

        pendingHitCount = 1;
        pendingHitTimestamps = new int[]{5};
        pendingDamageAmounts = new int[]{18 + this.random.nextInt(4)};
        pendingHitIndex = 0;
    }

    private void tickPhase2() {
        this.getNavigation().stop();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        tickPhase23AmbientFallback();

        phaseTimer++;
        if (phaseTimer >= PHASE_DURATION) {
            phase23AmbientStarted = false;
            transitionToPhase3();
        }
    }

    private void tickPhase3Ambient() {
        tickPhase23AmbientFallback();
        if (this.tickCount - lastDamagedTick >= PHASE3_REGEN_DELAY
                && this.tickCount % 20 == 0
                && this.getHealth() < this.getMaxHealth()) {
            this.heal(PHASE3_REGEN_AMOUNT);
        }
    }

    private void tickPhase23AmbientFallback() {
        if (!phase23AmbientStarted) {
            playLocalSound(ModSounds.NOTHING_THERE_PHASE23_AMBIENT.get());
            phase23AmbientStarted = true;
            phase23AmbientFallbackTimer = 0;
            return;
        }
        phase23AmbientFallbackTimer++;
        if (phase23AmbientFallbackTimer >= PHASE23_AMBIENT_INTERVAL) {
            playLocalSound(ModSounds.NOTHING_THERE_PHASE23_AMBIENT.get());
            phase23AmbientFallbackTimer = 0;
        }
    }

    private void trySpecialAttack() {
        LivingEntity target = getTarget();
        if (target == null) return;

        float distance = this.distanceTo(target);
        float healthPercent = this.getHealth() / this.getMaxHealth();
        float cannonProb = distance > 6.0f ? 1.0f : specialAttackProbability;
        float bladeProb = specialAttackProbability * 0.75f;
        float derivedRoll = this.random.nextFloat();
        float mainRoll = this.random.nextFloat();

        if (healthPercent < 0.10f && derivedRoll < 0.15f && distance <= 3.0f) {
            startGrandCombo();
            specialAttackProbability = 0.40f;
        } else if (healthPercent < 0.50f && derivedRoll < 0.30f && distance <= 3.0f) {
            startHammerBladeHammer();
            specialAttackProbability = 0.40f;
        } else if (healthPercent < 0.80f && derivedRoll < 0.30f && distance <= 3.0f) {
            startBladeCannon();
            specialAttackProbability = 0.40f;
        } else if (mainRoll < bladeProb && distance <= 3.0f) {
            startBladeAttack();
            specialAttackProbability = 0.40f;
        } else if (mainRoll < cannonProb + bladeProb && distance <= 24.0f) {
            startCannonAttack();
            specialAttackProbability = 0.40f;
        } else if (distance <= 3.0f) {
            startMeatHammerAttack();
            specialAttackProbability += 0.15f;
        }
    }

    private void startMeatHammerAttack() {
        currentAttackType = 0;
        attackAnimationMaxTimer = 34;
        attackAnimationTimer = 34;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack3", 40);

        scheduleSound(18, ModSounds.NOTHING_THERE_HAMMER.get());

        pendingHitCount = 1;
        pendingHitTimestamps = new int[]{18};
        pendingDamageAmounts = new int[]{25 + this.random.nextInt(11)};
        pendingHitIndex = 0;
    }

    private void startCannonAttack() {
        currentAttackType = 1;
        attackAnimationMaxTimer = 34;
        attackAnimationTimer = 34;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack1", 45);

        scheduleSound(10, ModSounds.NOTHING_THERE_CANNON1.get());
        scheduleSound(18, ModSounds.NOTHING_THERE_CANNON2.get());

        pendingHitCount = 1;
        pendingHitTimestamps = new int[]{18};
        pendingDamageAmounts = new int[]{50 + this.random.nextInt(11)};
        pendingHitIndex = 0;
    }

    private void startBladeAttack() {
        currentAttackType = 2;
        attackAnimationMaxTimer = 40;
        attackAnimationTimer = 40;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack2", 50);

        scheduleSound(14, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(24, ModSounds.NOTHING_THERE_BLADE2.get());

        pendingHitCount = 1;
        pendingHitTimestamps = new int[]{24};
        pendingDamageAmounts = new int[]{300};
        pendingHitIndex = 0;
    }

    private void startBladeCannon() {
        currentAttackType = 3;
        attackAnimationMaxTimer = 40;
        attackAnimationTimer = 40;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack2-1", 55);

        scheduleSound(14, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(20, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(28, ModSounds.NOTHING_THERE_CANNON1.get());
        scheduleSound(36, ModSounds.NOTHING_THERE_CANNON2.get());

        pendingHitCount = 2;
        pendingHitTimestamps = new int[]{20, 36};
        pendingDamageAmounts = new int[]{240, 50};
        pendingHitIndex = 0;
    }

    private void startHammerBladeHammer() {
        currentAttackType = 4;
        attackAnimationMaxTimer = 36;
        attackAnimationTimer = 36;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        setAnimTimed("attack3-2-3", 55);

        scheduleSound(18, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(22, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(26, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(32, ModSounds.NOTHING_THERE_HAMMER.get());

        pendingHitCount = 3;
        pendingHitTimestamps = new int[]{18, 26, 32};
        pendingDamageAmounts = new int[]{25, 200, 25};
        pendingHitIndex = 0;
    }

    private void startGrandCombo() {
        currentAttackType = 5;
        attackAnimationMaxTimer = 120;
        attackAnimationTimer = 120;
        attackCooldown = ATTACK_COOLDOWN_TICKS * 2;
        setAnimTimed("attack3-2-3-2-3-2-3-2-3", 115);

        pendingHitCount = 11;
        pendingHitTimestamps = new int[]{
                16, 26, 32, 44, 50, 60, 66, 76, 84, 94, 100
        };
        pendingDamageAmounts = new int[]{
                20, 150, 20, 150, 20, 150, 20, 150, 20, 150, 20
        };
        pendingHitIndex = 0;
        scheduleSound(16, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(32, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(50, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(66, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(84, ModSounds.NOTHING_THERE_HAMMER.get());
        scheduleSound(100, ModSounds.NOTHING_THERE_HAMMER.get());

        scheduleSound(20, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(38, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(54, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(72, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(88, ModSounds.NOTHING_THERE_BLADE1.get());
        scheduleSound(26, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(44, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(60, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(76, ModSounds.NOTHING_THERE_BLADE2.get());
        scheduleSound(94, ModSounds.NOTHING_THERE_BLADE2.get());
    }
    private void processPendingHits() {
        if (pendingHitTimestamps == null || pendingHitCount <= 0) return;
        if (pendingHitIndex >= pendingHitCount) return;

        int elapsed = getAttackAnimationElapsed();
        int nextHitTime = pendingHitTimestamps[pendingHitIndex];

        if (elapsed >= nextHitTime) {
            applyAttackDamage(pendingDamageAmounts[pendingHitIndex]);
            pendingHitIndex++;
        }
    }

    private int getAttackAnimationElapsed() {
        return attackAnimationMaxTimer - attackAnimationTimer;
    }

    private void applyAttackDamage(int damage) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        switch (currentAttackType) {
            case 0 -> {
                if (currentPhase == PHASE_1) {

                    AABB box = this.getBoundingBox().inflate(5.0D, 2.0D, 5.0D);
                    List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != this && e.isAlive());
                    for (LivingEntity t : targets) {
                        t.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                    }
                } else {

                    AABB box = getForwardBox(3.0D, 3.0D);
                    List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != this && e.isAlive());
                    for (LivingEntity t : targets) {
                        t.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                    }
                }
            }
            case 4, 5 -> {
                AABB box = getForwardBox(3.0D, 3.0D);
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != this && e.isAlive());
                for (LivingEntity t : targets) {
                    t.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                }
            }
            case 1, 3 -> {
                AABB box = getForwardBox(24.0D, 5.0D);
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != this && e.isAlive());
                for (LivingEntity t : targets) {
                    t.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                }
            }
            case 2 -> {
                AABB box = getForwardBox(3.0D, 3.0D);
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != this && e.isAlive());
                for (LivingEntity t : targets) {
                    t.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                }
            }
        }
    }

    private AABB getForwardBox(double range, double width) {
        double yawRad = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double hw = width / 2.0D;
        double startOffset = -1.0D;

        double startX = this.getX() + fx * startOffset;
        double startZ = this.getZ() + fz * startOffset;
        double endX = this.getX() + fx * range;
        double endZ = this.getZ() + fz * range;

        double absFz = Math.abs(fz);
        double absFx = Math.abs(fx);

        return new AABB(
                Math.min(startX, endX) - hw * absFz,
                this.getY() - 0.2D,
                Math.min(startZ, endZ) - hw * absFx,
                Math.max(startX, endX) + hw * absFz,
                this.getY() + 3.0D,
                Math.max(startZ, endZ) + hw * absFx
        );
    }

    private void onAttackAnimationEnd() {
        currentAttackType = -1;
        attackAnimationMaxTimer = 0;
        
        pendingHitCount = 0;
        pendingHitTimestamps = null;
        pendingDamageAmounts = null;
        pendingHitIndex = 0;
        soundScheduleElapsed = 0;
        scheduledSounds.clear();
    }
    private final java.util.Map<Integer, java.util.List<SoundEvent>> scheduledSounds = new java.util.HashMap<>();
    private int soundScheduleElapsed = 0;

    private void scheduleSound(int tickOffset, SoundEvent sound) {
        scheduledSounds.computeIfAbsent(tickOffset, k -> new java.util.ArrayList<>()).add(sound);
    }

    private void playScheduledSounds() {
        if (soundScheduleElapsed >= 0) {
            java.util.List<SoundEvent> sounds = scheduledSounds.remove(soundScheduleElapsed);
            if (sounds != null) {
                for (SoundEvent sound : sounds) {
                    playLocalSound(sound);
                }
            }
        }
        soundScheduleElapsed++;
    }

    private void playLocalSound(SoundEvent sound) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                sound, SoundSource.HOSTILE, 1.0F, 1.0F);
    }
    public void transitionToPhase2() {
        setCurrentPhase(PHASE_2);
        phaseTimer = 0;
        this.setHealth(this.getMaxHealth());
        clearAttackState();

        updatePhase2Attributes();
        

        phase23AmbientStarted = false;
        phase23AmbientFallbackTimer = 0;
    }

    public void transitionToPhase3() {
        setCurrentPhase(PHASE_3);
        phaseTimer = 0;
        clearAttackState();

        var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(PHASE3_HP);
        }
        this.setHealth(PHASE3_HP);

        updatePhase3Attributes();

        
        this.setNoAi(false);
        lastDamagedTick = this.tickCount;

        phase23AmbientStarted = false;
        phase23AmbientFallbackTimer = 0;
    }

    public void clearAttackState() {
        currentAttackType = -1;
        attackAnimationTimer = 0;
        attackAnimationMaxTimer = 0;
        pendingHitCount = 0;
        pendingHitIndex = 0;
        pendingHitTimestamps = null;
        pendingDamageAmounts = null;
        soundScheduleElapsed = 0;
        scheduledSounds.clear();
    }

    private void updatePhase1Attributes() {
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(PHASE1_MOVEMENT_SPEED);
        var redResist = this.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        if (redResist != null) redResist.setBaseValue(0.3D);
        var whiteResist = this.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        if (whiteResist != null) whiteResist.setBaseValue(0.8D);
        var blackResist = this.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        if (blackResist != null) blackResist.setBaseValue(0.8D);
        var blueResist = this.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        if (blueResist != null) blueResist.setBaseValue(1.2D);
    }

    private void updatePhase2Attributes() {
        var redResist = this.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        if (redResist != null) redResist.setBaseValue(0.3D);
        var whiteResist = this.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        if (whiteResist != null) whiteResist.setBaseValue(0.6D);
        var blackResist = this.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        if (blackResist != null) blackResist.setBaseValue(0.6D);
        var blueResist = this.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        if (blueResist != null) blueResist.setBaseValue(1.0D);
    }

    private void updatePhase3Attributes() {
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(PHASE3_MOVEMENT_SPEED);
        var redResist = this.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        if (redResist != null) redResist.setBaseValue(0.3D);
        var whiteResist = this.getAttribute(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        if (whiteResist != null) whiteResist.setBaseValue(0.4D);
        var blackResist = this.getAttribute(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        if (blackResist != null) blackResist.setBaseValue(0.4D);
        var blueResist = this.getAttribute(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        if (blueResist != null) blueResist.setBaseValue(0.8D);
    }
    private void tickDisguisedEscape() {
        if (executionCountdown > 0) {
            executionCountdown--;

            if (executionCountdown % 20 == 0) {
                this.entityData.set(DATA_COUNTDOWN, executionCountdown / 20);
            }
            if (executionCountdown == 0) {

                escapedDisguised = false;
                this.entityData.set(DATA_COUNTDOWN, 0);
                this.entityData.set(DATA_ESCAPED_DISGUISED, false);
                removeExecutionItems();
                startNormalEscape();
            }
        }
    }

    private void tickContainedAmbient() {

        ambientSoundTimer++;
        if (ambientSoundTimer >= IDLE_SOUND_INTERVAL) {
            ambientSoundTimer = 0;
            playLocalSound(ModSounds.NOTHING_THERE_IDLE.get());
        }
    }

    public void escapeDisguised() {
        escapedDisguised = true;
        executionCountdown = 200;
        this.setEscape(true);
        this.entityData.set(DATA_COUNTDOWN, 10);
        this.entityData.set(DATA_ESCAPED_DISGUISED, true);
        playGlobalSound(ModSounds.NOTHING_THERE_ESCAPE.get());
        broadcastMessage("§c§l警告！一无所有伪装成文职出逃了！你有10秒时间找到并处决它！");
        BlockPos reactorPos = findNearestReactor();
        if (reactorPos != null) {
            this.teleportTo(reactorPos.getX() + 0.5, reactorPos.getY() + 1, reactorPos.getZ() + 0.5);
        }
        giveExecutionItems();
    }

    public void startNormalEscape() {
        escapedDisguised = false;
        this.entityData.set(DATA_ESCAPED_DISGUISED, false);
        this.entityData.set(DATA_COUNTDOWN, 0);
        this.setEscape(true);
        playGlobalSound(ModSounds.NOTHING_THERE_ESCAPE.get());

        setCurrentPhase(PHASE_1);
        phaseTimer = 0;
        specialAttackProbability = 0.40f;
        clearAttackState();

        phase23AmbientStarted = false;
        phase23AmbientFallbackTimer = 0;

        updatePhase1Attributes();

        var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(PHASE1_HP);
        }
        this.setHealth(PHASE1_HP);
        this.setNoAi(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityNothingThere((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        escapeImmediately();
    }

    @Override
    public void onBadWork(net.minecraft.server.level.ServerPlayer player) {
        escapeImmediately();
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {

        float damage = 6.0F + this.random.nextInt(4);
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
        if (!player.isAlive() && player instanceof ServerPlayer serverPlayer) {
            int justiceLevel = getJusticeLevel(serverPlayer);
            if (justiceLevel > 4) {
                disguisedAsPlayer = true;
                disguisePlayerUUID = serverPlayer.getUUID();
                disguisePlayerName = serverPlayer.getName().getString();
            }
        }
    }

    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {

        int courageLevel = getCourageLevel(player);
        float bonus = courageLevel * 0.05f;
        return Math.min(0.95f, baseRate + bonus);
    }

    private int getCourageLevel(ServerPlayer player) {
        final int[] level = {0};
        player.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> level[0] = stats.getFortitudeLevel());
        return level[0];
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (result == WorkResult.BAD) {

            killAndDisguise(player);
            return;
        }

        int justiceLevel = getJusticeLevel(player);
        if (justiceLevel < 3) {

            escapeImmediately();
        }
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        int justiceLevel = getJusticeLevel(player);
        if (justiceLevel < 3) {

            return true;
        }
        if (disguisedAsPlayer) {
            int courageLevel = getCourageLevel(player);
            if (courageLevel < 4) {

                MentalValueUtil.setMentalValue(player, 0);

                spawnDisguisedClerk();
            }
        }

        return true;
    }

    private int getJusticeLevel(ServerPlayer player) {
        final int[] level = {0};
        player.getCapability(com.wzz.lobotocraft.capability.EmployeeStatsProvider.EMPLOYEE_STATS)
                .ifPresent(stats -> level[0] = stats.getJusticeLevel());
        return level[0];
    }

    private void killAndDisguise(ServerPlayer player) {
        int justiceLevel = getJusticeLevel(player);
        if (justiceLevel > 3) {

            disguisedAsPlayer = true;
            disguisePlayerUUID = player.getUUID();
            disguisePlayerName = player.getName().getString();
        }

        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), Float.MAX_VALUE);
    }

    private void escapeImmediately() {
        if (!hasEscape()) {
            if (disguisedAsPlayer) {

                escapeDisguised();
            } else {
                startNormalEscape();
            }
        }
    }

    private void spawnDisguisedClerk() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        BlockPos reactorPos = findNearestReactor();
        BlockPos spawnPos;
        if (reactorPos != null) {
            spawnPos = reactorPos.offset(
                    this.random.nextInt(5) - 2,
                    1,
                    this.random.nextInt(5) - 2
            );
        } else {

            spawnPos = this.blockPosition().offset(
                    this.random.nextInt(5) - 2,
                    0,
                    this.random.nextInt(5) - 2
            );
        }

        EntityClerk clerk = new EntityClerk(
                ModEntities.clerk.get(), this.level());
        clerk.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                this.random.nextFloat() * 360.0F, 0.0F);
        clerk.finalizeSpawn(serverLevel,
                serverLevel.getCurrentDifficultyAt(clerk.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        serverLevel.addFreshEntity(clerk);
    }

    @javax.annotation.Nullable
    private BlockPos findNearestReactor() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return null;
        BlockPos center = this.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-32, -16, -32), center.offset(32, 16, 32))) {
            if (serverLevel.getBlockEntity(pos) instanceof com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity) {
                double dist = pos.distSqr(center);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }

    private void giveExecutionItems() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModItems.STOP_ESCAPE.get()))) {
                player.addItem(new net.minecraft.world.item.ItemStack(ModItems.STOP_ESCAPE.get()));
            }
            player.sendSystemMessage(Component.literal("§c你获得了处决道具！找到并攻击伪装的文职来阻止出逃！"));
        }
    }

    private void removeExecutionItems() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).getItem() == ModItems.STOP_ESCAPE.get()) {
                    inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public void stopEscape() {

        if (escapedDisguised) {
            escapedDisguised = false;
            executionCountdown = 0;
            this.entityData.set(DATA_COUNTDOWN, 0);
            this.entityData.set(DATA_ESCAPED_DISGUISED, false);
            removeExecutionItems();
            broadcastMessage("§a一无所有的伪装出逃已被阻止！");
        }
        super.stopEscape();
    }
    @Override
    public boolean hasAbnormalityAmbientSound() {
        return !hasEscape();
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.NOTHING_THERE_IDLE.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return IDLE_SOUND_INTERVAL;
    }

    @Override
    public double getAbnormalityAmbientSoundRange() {
        return 8.0D;
    }

    @Override
    public SoundSource getAbnormalityAmbientSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public boolean hasEscapeAmbientSound() {
        return false;
    }

    @Override
    public SoundEvent getEscapeAmbientSound() {
        return ModSounds.NOTHING_THERE_PHASE23_AMBIENT.get();
    }

    @Override
    public int getEscapeAmbientSoundInterval() {
        return 60;
    }

    @Override
    public SoundEvent getEscapeSound() {
        return ModSounds.NOTHING_THERE_ESCAPE.get();
    }
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new FloatGoal(this));

        this.targetSelector.addGoal(1,
                new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                        e -> !(e instanceof AbstractAbnormality)));
    }
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (!hasEscape() || level().isClientSide) return;
        if (currentPhase == PHASE_2) {
            this.getNavigation().stop();
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            this.setTarget(null);
            return;
        }
        if (attackAnimationTimer > 0) {
            this.getNavigation().stop();
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        this.getLookControl().setLookAt(target);
        double dist = this.distanceTo(target);

        if (currentPhase == PHASE_1) {

            if (dist <= 4.0 && attackCooldown <= 0) {
                startPhase1Attack(target);
            } else if (dist > 4.0) {
                this.getNavigation().moveTo(target, 1.0D);
            }
        } else if (currentPhase == PHASE_3) {

            if (attackCooldown <= 0) {

                if (specialAttackCheckTimer <= 0) {
                    trySpecialAttack();
                    specialAttackCheckTimer = SPECIAL_ATTACK_CHECK_INTERVAL;
                } else {

                    if (dist <= 3.0) {
                        startMeatHammerAttack();
                        specialAttackProbability += 0.15f;
                    }
                }
            }

            if (specialAttackCheckTimer > 0) {
                specialAttackCheckTimer--;
            }
            if (dist > 2.5 && attackAnimationTimer <= 0) {
                this.getNavigation().moveTo(target, 1.0D);
            }
            if (attackAnimationTimer > 0) {
                this.getNavigation().stop();
                this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            }
        }
    }
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityNothingThere> event) {
        String anim = getAnim();
        int phase = getCurrentPhase();
        if (anim.equals("idle")) {
            if (event.isMoving()) return event.setAndContinue(RawAnimation.begin().thenLoop(switch (phase) {
                case PHASE_1 -> "move";
                case PHASE_3 -> "walk";
                default -> "idle";
            }));
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        } else {
            return event.setAndContinue(RawAnimation.begin().thenPlay(anim));
        }
    }
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CurrentPhase", getCurrentPhase());
        tag.putInt("PhaseTimer", phaseTimer);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putFloat("SpecialAttackProbability", specialAttackProbability);
        tag.putBoolean("DisguisedAsPlayer", disguisedAsPlayer);
        if (disguisePlayerUUID != null) {
            tag.putUUID("DisguisePlayerUUID", disguisePlayerUUID);
        }
        tag.putString("DisguisePlayerName", disguisePlayerName);
        tag.putBoolean("EscapedDisguised", escapedDisguised);
        tag.putInt("ExecutionCountdown", executionCountdown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCurrentPhase(tag.getInt("CurrentPhase"));
        phaseTimer = tag.getInt("PhaseTimer");
        attackCooldown = tag.getInt("AttackCooldown");
        specialAttackProbability = tag.getFloat("SpecialAttackProbability");
        disguisedAsPlayer = tag.getBoolean("DisguisedAsPlayer");
        if (tag.hasUUID("DisguisePlayerUUID")) {
            disguisePlayerUUID = tag.getUUID("DisguisePlayerUUID");
        }
        disguisePlayerName = tag.getString("DisguisePlayerName");
        escapedDisguised = tag.getBoolean("EscapedDisguised");
        executionCountdown = tag.getInt("ExecutionCountdown");
    }
}
