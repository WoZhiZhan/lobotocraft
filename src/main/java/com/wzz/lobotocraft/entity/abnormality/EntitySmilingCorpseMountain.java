package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityLightFollower;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.BlackForestEvent;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ShockwaveEffectPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EntitySmilingCorpseMountain extends AbstractAbnormality {
    private final Random random = new Random();

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(EntitySmilingCorpseMountain.class, EntityDataSerializers.INT);

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("6f3d9c1e-2a4b-4c8e-9f10-7c2e5a1b3d40");

    private static final double BASE_MOVE_SPEED = 0.2D;
    private static final double[] PHASE_MAX_HP    = {500, 500, 1000, 2000};
    // 所有阶段移动速度 x2（MULTIPLY_TOTAL 加成 1.0 => x2；收容态不加速）
    private static final double[] PHASE_SPEED_MUL = {0.0, 1.0, 1.0, 1.0};

    // 攻击时序（tick，20t=1s）。伤害触发帧来自策划给定触发时间。
    private static final int P1_DMG = 20,  P1_TOTAL = 28,  P1_CD = 10;
    private static final int P2_DMG = 16,  P2_TOTAL = 24,  P2_CD = 20;
    private static final int P3_1_DMG = 20, P3_1_TOTAL = 28;
    private static final int P3_2_DMG = 30, P3_2_TOTAL = 40;
    private static final int P3S_DMG = 17,  P3S_TOTAL = 26;
    private static final int P3_CD = 15;

    // 攻击范围（横向半宽为策划未指定项，取合理默认，可调）
    private static final double P1_FWD = 3, P1_BACK = 2, P1_HALF = 2.0;
    private static final double P2_TRIGGER = 10, P2_RADIUS = 12;
    private static final double P3_FWD = 6, P3_BACK = 3, P3_HALF = 2.5;
    private static final double P3S_FWD = 10, P3S_BACK = 2, P3S_HALF = 3.0;

    // 移动追击范围
    private static final double CHASE_RANGE_LOW = 16.0;   // 一/二阶段
    private static final double CHASE_RANGE_P3  = 32.0;   // 三阶段（区块级仇恨）

    private static final int TOMBSTONE_PER_PHASE = 3;
    // 二阶段嚎叫冲击波颜色（黑紫色，0xRRGGBB，可调）
    private static final int SHOCKWAVE_COLOR = 0x4B0082;
    private int tombstoneEaten = 0;
    private BlockPos cachedTombstone = null;
    private int lastTombstoneScan = -100;

    private int transitionCooldown = 0;

    private ParticleOptions[] vomitParticles;

    public EntitySmilingCorpseMountain(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, 0);
        setAnimation("idle1");
    }

    // ============================================================
    //  阶段
    // ============================================================
    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    private void applyPhaseAttributes(int phase) {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(PHASE_MAX_HP[phase]);

        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_UUID);
            speed.addPermanentModifier(new AttributeModifier(
                    SPEED_MODIFIER_UUID, "corpse_phase_speed",
                    PHASE_SPEED_MUL[phase], AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void advancePhase() {
        int p = getPhase();
        if (p >= 3) return;
        setPhase(p + 1);
        applyPhaseAttributes(getPhase());
        setHealth(getMaxHealth());
        setAnimation(getPhase() == 2 ? "phase_two" : "phase_three");
        transitionCooldown = 30;
        getNavigation().stop();
        playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_RISE_PHASE.get());
    }

    private void downgradePhase() {
        int p = getPhase();
        if (p <= 1) return;
        setPhase(p - 1);
        applyPhaseAttributes(getPhase());
        setHealth(getMaxHealth());
        EntityUtil.clearHurtTime(this);
        this.invulnerableTime = 20; // 注意：必须在 clearHurtTime 之后设置，否则会被清零
        playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_DECLINE_PHASE.get());
    }

    public boolean canAct() {
        return transitionCooldown <= 0 && !isDeadOrDying() && hasEscape();
    }

    private String phaseIdle() {
        return switch (getPhase()) {
            case 1 -> "ready_run_idle";
            case 2 -> "idle2";
            case 3 -> "idle3";
            default -> "idle1";
        };
    }

    private boolean isBusyAnim(String a) {
        return a.startsWith("attack") || "phase_two".equals(a) || "phase_three".equals(a)
                || "death1".equals(a) || "ready_run".equals(a) || "ready_run_idle".equals(a);
    }

    // ============================================================
    //  观察 / 工作偏好
    // ============================================================
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

    /**
     * 机制1：每累计死亡 10 个文职/村民/玩家，计数器 -1；降到 1 时播放准备出逃过渡动画+音效。
     * 你的死亡检测代码每次死亡调用一次 addClerkDieCount(1) 即可，不要再手动 decreaseQliphothCounter。
     */
    public void addClerkDieCount(int v) {
        if (level().isClientSide || hasEscape()) return;
        int c = getClerkDieCount() + v;
        while (c >= 10) {
            c -= 10;
            decreaseQliphothCounter(1);
            if (getQliphothCounter() == 1) {
                triggerClerkDieCount();
            }
        }
        setClerkDieCount(c);
    }

    /** 计数器降到 1：过渡动画 ready_run -> ready_run_idle + 音效 */
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
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.5f, 0.5f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.5f, 0.55f};
        return levelModifiers;
    }

    // ============================================================
    //  AI
    // ============================================================
    @Override
    protected void registerGoals() {
        super.registerGoals(); // 含 HurtByTargetGoal（被打反击）
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 1: 统一移动（去墓碑 / 追最近有效目标），MOVE
        this.goalSelector.addGoal(1, new CorpseMoveGoal(this));
        // 2: 各阶段攻击（LOOK，每 tick 主动转向；移动由 CorpseMoveGoal 并行处理）
        this.goalSelector.addGoal(2, new Phase1AttackGoal(this));
        this.goalSelector.addGoal(2, new Phase2HowlGoal(this));
        this.goalSelector.addGoal(2, new Phase3AttackGoal(this));
        // 4+: 游荡 / 注视
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // 目标选择：三阶段=区块级仇恨（除不会出逃类异想体外一切生物）；一/二阶段=文职/村民/贴身玩家
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (entity) -> {
                    if (!hasEscape()) return false;
                    if (getPhase() >= 3) return isPhase3Target(entity);
                    return entity instanceof Villager || entity instanceof EntityClerk
                            || (entity instanceof Player player
                            && EntityUtil.getDistanceBetweenEntities(this, player) <= 3.1D);
                }));
    }

    /** 尸山不参与通用异想体互斗，目标逻辑完全交给自身体系 */
    @Override
    protected void customServerAiStep() {
        // 故意置空
    }

    // ============================================================
    //  目标判定
    // ============================================================
    private boolean isValidVictim(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof AbstractAbnormality ab) return ab.hasEscape();
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return true;
    }

    /** 三阶段仇恨：除了“不会出逃类异想体”之外的所有生物 */
    private boolean isPhase3Target(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof AbstractAbnormality ab) return ab.canEscape();
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return true;
    }

    private boolean isHowlTrigger(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof AbstractAbnormality ab) return ab.hasEscape();
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof Villager || e instanceof EntityClerk;
    }

    private java.util.function.Predicate<LivingEntity> victimPredicate() {
        return getPhase() >= 3 ? this::isPhase3Target : this::isValidVictim;
    }

    private LivingEntity findNearestVictim(double range) {
        java.util.function.Predicate<LivingEntity> pred = victimPredicate();
        LivingEntity best = null;
        double bestSq = range * range;
        for (LivingEntity e : EntityUtil.findLivingEntitiesAround(this, 6, range)) {
            if (!pred.test(e)) continue;
            double dsq = distanceToSqr(e);
            if (dsq <= bestSq) { bestSq = dsq; best = e; }
        }
        return best;
    }

    private boolean hasHowlTarget(double range) {
        for (LivingEntity e : EntityUtil.findLivingEntitiesAround(this, 6, range)) {
            if (isHowlTrigger(e)) return true;
        }
        return false;
    }

    // ============================================================
    //  伤害
    // ============================================================
    private void dealDirectionalDamage(double forward, double back, double halfWidth, double yRange,
                                       float min, float max, String dmgId, boolean slow) {
        if (level().isClientSide) return;
        java.util.function.Predicate<LivingEntity> pred = victimPredicate();
        Vec3 self = position();
        double rad = Math.toRadians(getYRot());
        Vec3 fwd = new Vec3(-Math.sin(rad), 0, -Math.cos(rad));
        double reach = Math.max(forward, Math.max(back, halfWidth)) + 1;
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(reach, yRange + 1, reach), pred::test);
        for (LivingEntity e : list) {
            Vec3 d = e.position().subtract(self);
            double proj = d.x * fwd.x + d.z * fwd.z;
            if (proj > forward || proj < -back) continue;
            double latx = d.x - proj * fwd.x, latz = d.z - proj * fwd.z;
            if (Math.sqrt(latx * latx + latz * latz) > halfWidth) continue;
            if (Math.abs(d.y) > yRange) continue;
            float dmg = min + random.nextFloat() * (max - min);
            e.hurt(DamageHelper.getDamage(this, dmgId), dmg);
            if (slow) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255));
        }
    }

    private void dealRadiusDamage(double radius, float min, float max, String dmgId) {
        if (level().isClientSide) return;
        for (LivingEntity e : EntityUtil.findLivingEntitiesAround(this, 6, radius)) {
            if (!isValidVictim(e)) continue;
            float dmg = min + random.nextFloat() * (max - min);
            e.hurt(DamageHelper.getDamage(this, dmgId), dmg);
        }
    }

    private void faceTarget(LivingEntity target) {
        if (target == null) return;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        getLookControl().setLookAt(target, 60, 60);
    }

    private void playPositionalSound(SoundEvent s) {
        if (s == null || level().isClientSide) return;
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /** 向附近玩家发送冲击波渲染包（复用碧蓝新星的 ShockwaveEffectManager） */
    private void spawnShockwave(float maxRadius, int color) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) <= 64 * 64) {
                MessageLoader.getLoader().sendToPlayer(p,
                        new ShockwaveEffectPacket(getX(), getY(), getZ(), maxRadius, color));
            }
        }
    }

    private ParticleOptions[] vomitParticles() {
        if (vomitParticles == null) {
            vomitParticles = new ParticleOptions[] {
                    (SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_1.get(),
                    (SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_2.get(),
                    (SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_3.get(),
                    (SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_4.get(),
                    (SimpleParticleType) ModParticleTypes.SMILING_CORPSE_MOUNTAIN_VOMITUS_5.get(),
                    // 混入黑色粒子特效
                    ParticleUtil.getDustParticle(0.02f, 0.02f, 0.02f, 1.6f),
                    ParticleUtil.getDustParticle(0.06f, 0.0f, 0.10f, 1.4f) // 一点暗紫增加层次
            };
        }
        return vomitParticles;
    }

    // ============================================================
    //  墓碑
    // ============================================================
    private BlockPos findNearestTombstone() {
        if (level().isClientSide) return null;
        if (cachedTombstone != null) {
            if (level().getBlockState(cachedTombstone).is(ModBlocks.TOMBSTONE.get())) {
                if (tickCount - lastTombstoneScan < 10) return cachedTombstone;
            } else {
                cachedTombstone = null;
            }
        }
        if (tickCount - lastTombstoneScan < 10) return cachedTombstone;
        lastTombstoneScan = tickCount;

        BlockPos origin = blockPosition();
        final int rH = 20, rV = 6;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -rH; dx <= rH; dx++) {
            for (int dz = -rH; dz <= rH; dz++) {
                for (int dy = -rV; dy <= rV; dy++) {
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

    private void consumeTombstone(BlockPos pos) {
        level().destroyBlock(pos, false);
        cachedTombstone = null;
        lastTombstoneScan = tickCount - 100;
        tombstoneEaten++;
        if (tombstoneEaten % TOMBSTONE_PER_PHASE == 0) {
            advancePhase();
        }
    }

    private boolean playerWithin(double d) {
        Player p = level().getNearestPlayer(this, d);
        return p != null && !p.isCreative() && !p.isSpectator();
    }

    // ============================================================
    //  出逃 / 生命周期
    // ============================================================
    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // 机制1补充：血量不满的玩家完成工作 -> 计数器 -1
        if (!hasEscape() && player != null && player.getHealth() < player.getMaxHealth()) {
            decreaseQliphothCounter(1);
            if (getQliphothCounter() == 1) triggerClerkDieCount();
        }
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

        boolean hurt = super.hurt(damageSource, f);

        // 机制5：非致命的“血量过半”降阶（致命伤在 die() 拦截）
        if (hurt && !level.isClientSide && hasEscape()
                && getPhase() >= 2 && !isDeadOrDying()
                && getHealth() <= getMaxHealth() / 2.0f) {
            downgradePhase();
        }
        return hurt;
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
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        super.triggerEscape();
        setPhase(1);
        applyPhaseAttributes(1);
        setHealth(getMaxHealth());
        tombstoneEaten = 0;
        setAnimation(phaseIdle()); // ready_run_idle，清掉容器态残留 ready_run

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
        setPhase(0);
        applyPhaseAttributes(0);
        setAnimation("idle1");
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide && hasEscape() && getPhase() >= 2) {
            downgradePhase();
            return;
        }
        setAnimation("death1");
        super.die(src);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!hasEscape()) {
            setNoAi(true);
            setTarget(null);
            if (getPhase() != 0) { setPhase(0); applyPhaseAttributes(0); }
            if (getQliphothCounter() >= getMaxQliphothCounter()
                    && !"ready_run".equals(getAnimation())
                    && !"ready_run_idle".equals(getAnimation())) {
                setAnimation("idle1");
            }
            return;
        }

        setNoAi(false);
        if (getPhase() == 0) {
            setPhase(1);
            applyPhaseAttributes(1);
            setHealth(getMaxHealth());
        }

        if (transitionCooldown > 0) {
            transitionCooldown--;
            getNavigation().stop();
            if (transitionCooldown == 0) setAnimation(phaseIdle());
            return;
        }

        if (!isBusyAnim(getAnimation())) {
            setAnimation(phaseIdle());
        }
    }

    // ============================================================
    //  音效 / 攻击接口
    // ============================================================
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
    public double getAbnormalityAmbientSoundRange() {
        return 10D;
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 450;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.SMILING_CORPSE_MOUNTAIN_IDLE.get();
    }

    // ============================================================
    //  动画控制器
    // ============================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "movement", 0, this::movementPredicate));
        controllerRegistrar.add(new AnimationController<>(this, "action", 0, this::actionPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntitySmilingCorpseMountain> event) {
        if (event.isMoving()) {
            String move = switch (getPhase()) {
                case 2 -> "move2";
                case 3 -> "move3";
                default -> "move1";
            };
            return event.setAndContinue(RawAnimation.begin().thenLoop(move));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop(phaseIdle()));
    }

    private PlayState actionPredicate(AnimationState<EntitySmilingCorpseMountain> event) {
        String anim = getAnimation();
        return switch (anim) {
            case "ready_run" ->
                    event.setAndContinue(RawAnimation.begin().thenPlay("ready_run").thenLoop("ready_run_idle"));
            case "death1" -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold("death1"));
            case "phase_two" -> event.setAndContinue(RawAnimation.begin().thenPlay("phase_two"));
            case "phase_three" -> event.setAndContinue(RawAnimation.begin().thenPlay("phase_three"));
            case "attack1" -> event.setAndContinue(RawAnimation.begin().thenPlay("attack1"));
            case "attack2" -> event.setAndContinue(RawAnimation.begin().thenPlay("attack2"));
            case "attack3-1" -> event.setAndContinue(RawAnimation.begin().thenPlay("attack3-1"));
            case "attack3-2" -> event.setAndContinue(RawAnimation.begin().thenPlay("attack3-2"));
            case "attack3-3" -> event.setAndContinue(RawAnimation.begin().thenPlay("attack3-3"));
            default -> PlayState.STOP; // idle 交给 movement 控制器，避免覆盖走路
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVE_SPEED)
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
    public String name() {
        return "smiling_corpse_mountain";
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“微笑的尸山”正带着满身的笑脸寻找死尸的气味。");
        logs.add("“微笑的尸山”的身体里保存着所有尸体的笑容，它正等待着鲜血溅出的味道。");
        return logs;
    }

    @Override public int getBasicInfoCost() { return 30; }
    @Override public int getWorkPreferencesCost() { return 6; }
    @Override public int getSensitiveInfoCost() { return 30; }
    @Override public int getManualCost(int manualIndex) { return 10; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SCM_Phase", getPhase());
        tag.putInt("SCM_TombstoneEaten", tombstoneEaten);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int phase = tag.getInt("SCM_Phase");
        tombstoneEaten = tag.getInt("SCM_TombstoneEaten");
        setPhase(phase);
        if (phase >= 1) applyPhaseAttributes(phase);
    }

    // ============================================================
    //  Goals
    // ============================================================

    /** 统一移动：优先吞噬墓碑（阶段<3），否则追击最近有效目标；攻击期间也持续移动 */
    private static class CorpseMoveGoal extends Goal {
        private final EntitySmilingCorpseMountain e;
        private BlockPos tomb;

        CorpseMoveGoal(EntitySmilingCorpseMountain e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean wantTombstone() {
            if (e.getPhase() >= 3) return false;
            if (e.getPhase() == 1 && e.playerWithin(2.5)) return false; // 一阶段贴身玩家优先缠斗
            tomb = e.findNearestTombstone();
            return tomb != null;
        }

        private LivingEntity chaseTarget() {
            LivingEntity t = e.getTarget();
            if (t != null && t.isAlive() && e.victimPredicate().test(t)) return t;
            double range = e.getPhase() >= 3 ? CHASE_RANGE_P3 : CHASE_RANGE_LOW;
            return e.findNearestVictim(range);
        }

        @Override
        public boolean canUse() {
            return e.canAct() && (wantTombstone() || chaseTarget() != null);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (wantTombstone() && tomb != null) {
                e.getNavigation().moveTo(tomb.getX() + 0.5, tomb.getY(), tomb.getZ() + 0.5, 1.0);
                e.getLookControl().setLookAt(tomb.getX() + 0.5, tomb.getY(), tomb.getZ() + 0.5);
                // 用水平距离判定，放宽到 2.6，兼容贴墙无法寻路到位的墓碑
                double dx = (tomb.getX() + 0.5) - e.getX();
                double dz = (tomb.getZ() + 0.5) - e.getZ();
                double dy = Math.abs((tomb.getY() + 0.5) - e.getY());
                if (dx * dx + dz * dz <= 2.6 * 2.6 && dy <= 3.0) {
                    e.consumeTombstone(tomb);
                    tomb = null;
                }
                return;
            }
            LivingEntity t = chaseTarget();
            if (t != null) {
                e.getNavigation().moveTo(t, 1.0);
                e.getLookControl().setLookAt(t, 30, 30);
            }
        }

        @Override
        public void stop() {
            e.getNavigation().stop();
            tomb = null;
        }
    }

    /** 一阶段近战锥形（前3后2，红7-11，节奏快） */
    private static class Phase1AttackGoal extends Goal {
        private final EntitySmilingCorpseMountain e;
        private int t;
        private boolean active;
        private int cd;

        Phase1AttackGoal(EntitySmilingCorpseMountain e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return e.canAct() && e.getPhase() == 1 && e.tickCount >= cd
                    && e.findNearestVictim(P1_FWD + 0.5) != null;
        }

        @Override
        public boolean canContinueToUse() { return active; }

        @Override
        public void start() {
            active = true;
            t = 0;
            e.faceTarget(e.findNearestVictim(P1_FWD + 1));
            e.setAnimation("attack1");
            e.playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_ONE_STAGES_ATTACK.get());
        }

        @Override
        public void tick() {
            e.faceTarget(e.findNearestVictim(P1_FWD + 1.5)); // 攻击期间持续转向
            t++;
            if (t == P1_DMG) {
                e.dealDirectionalDamage(P1_FWD, P1_BACK, P1_HALF, 2.5, 7, 11, "lobotocraft:red", false);
            }
            if (t >= P1_TOTAL) {
                active = false;
                cd = e.tickCount + P1_CD;
                e.setAnimation(e.phaseIdle());
            }
        }

        @Override
        public void stop() {
            active = false;
            if ("attack1".equals(e.getAnimation())) e.setAnimation(e.phaseIdle());
        }
    }

    /** 二阶段嚎叫（10格内有目标触发，12格半径黑20-27）+ 深紫黑冲击波环 */
    private static class Phase2HowlGoal extends Goal {
        private final EntitySmilingCorpseMountain e;
        private int t;
        private boolean active;
        private int cd;

        Phase2HowlGoal(EntitySmilingCorpseMountain e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return e.canAct() && e.getPhase() == 2 && e.tickCount >= cd && e.hasHowlTarget(P2_TRIGGER);
        }

        @Override
        public boolean canContinueToUse() { return active; }

        @Override
        public void start() {
            active = true;
            t = 0;
            e.faceTarget(e.findNearestVictim(P2_TRIGGER));
            e.setAnimation("attack2");
            e.playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_TWO_STAGE_HOWLING.get());
        }

        @Override
        public void tick() {
            t++;
            if (t == P2_DMG) {
                e.dealRadiusDamage(P2_RADIUS, 20, 27, "lobotocraft:black");
                // 黑紫色冲击波（客户端 ShockwaveEffectManager 负责扩张动画）
                e.spawnShockwave((float) P2_RADIUS, SHOCKWAVE_COLOR);
            }
            if (t >= P2_TOTAL) {
                active = false;
                cd = e.tickCount + P2_CD;
                e.setAnimation(e.phaseIdle());
            }
        }

        @Override
        public void stop() {
            active = false;
            if ("attack2".equals(e.getAnimation())) e.setAnimation(e.phaseIdle());
        }
    }

    /** 三阶段普攻（前6后3，黑15-19+缓慢255/2s）+ 30%特殊喷吐（前10后2，黑120-150，从上倒落） */
    private static class Phase3AttackGoal extends Goal {
        private final EntitySmilingCorpseMountain e;
        private int t;
        private boolean active;
        private int cd;
        private boolean special;
        private boolean useSecond;
        private int dmgTick;
        private int totalTick;

        Phase3AttackGoal(EntitySmilingCorpseMountain e) {
            this.e = e;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return e.canAct() && e.getPhase() == 3 && e.tickCount >= cd
                    && e.findNearestVictim(P3_FWD + 0.5) != null;
        }

        @Override
        public boolean canContinueToUse() { return active; }

        @Override
        public void start() {
            active = true;
            t = 0;
            e.faceTarget(e.findNearestVictim(P3S_FWD));
            special = e.random.nextFloat() < 0.30f;
            if (special) {
                dmgTick = P3S_DMG;
                totalTick = P3S_TOTAL;
                e.setAnimation("attack3-3");
            } else {
                if (useSecond) { dmgTick = P3_2_DMG; totalTick = P3_2_TOTAL; e.setAnimation("attack3-2"); }
                else           { dmgTick = P3_1_DMG; totalTick = P3_1_TOTAL; e.setAnimation("attack3-1"); }
                useSecond = !useSecond;
            }
        }

        @Override
        public void tick() {
            e.faceTarget(e.findNearestVictim(P3S_FWD)); // 持续转向
            t++;
            if (t == dmgTick) {
                if (special) {
                    // 音效在喷吐帧播放，与动画对齐
                    e.playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_THREE_STAGES_SPECIAL_ATTACK.get());
                    if (e.level() instanceof ServerLevel sl) {
                        double rad = Math.toRadians(e.getYRot());
                        Vec3 dir = new Vec3(-Math.sin(rad), 0, -Math.cos(rad));
                        ParticleUtil.spawnFallingVomit(sl, e.position(), dir,
                                P3S_FWD, P3S_BACK, P3S_HALF,
                                e.getEyeHeight() + 2.0, 160, e.vomitParticles(), 0.35);
                    }
                    e.dealDirectionalDamage(P3S_FWD, P3S_BACK, P3S_HALF, 2.5, 120, 150, "lobotocraft:black", false);
                } else {
                    e.playPositionalSound(ModSounds.SMILING_CORPSE_MOUNTAIN_THREE_STAGES_NORMAL_ATTACK.get());
                    e.dealDirectionalDamage(P3_FWD, P3_BACK, P3_HALF, 2.5, 15, 19, "lobotocraft:black", true);
                }
            }
            if (t >= totalTick) {
                active = false;
                cd = e.tickCount + P3_CD;
                e.setAnimation(e.phaseIdle());
            }
        }

        @Override
        public void stop() {
            active = false;
            if (e.getAnimation().startsWith("attack3")) e.setAnimation(e.phaseIdle());
        }
    }
}