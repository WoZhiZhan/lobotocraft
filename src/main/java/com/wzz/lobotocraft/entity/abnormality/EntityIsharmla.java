package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

/**
 * 伊莎玛拉 (S-03-07-1) —— ALEPH 级异想体,由浊心斯卡蒂计数器归零后生成。
 * 双形态(人形/巨兽),血量独立;人形抗性1.0、巨兽抗性0.1;
 * 出逃瞬移到最近再生反应堆;攻击附带精神伤害;
 * 人形态生成伊莎玛拉之泪并在25秒后进入巨兽形态,巨兽形态60秒后变回人形循环。
 */
public class EntityIsharmla extends AbstractAbnormality {

    public enum Form { HUMAN, MONSTER }

    private static final EntityDataAccessor<String> DATA_ANIM =
            SynchedEntityData.defineId(EntityIsharmla.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FORM =
            SynchedEntityData.defineId(EntityIsharmla.class, EntityDataSerializers.INT);

    // 巨兽形态独立血量
    private float monsterHealth = 5000f;
    private static final float MONSTER_MAX_HEALTH = 5000f;

    // 状态计时
    private int humanPhaseTimer = 0;     // 人形态计时(到25秒触发清理之泪→巨兽)
    private int monsterPhaseTimer = 0;   // 巨兽形态剩余时间
    private int attackCooldown = 0;
    private boolean transitioning = false;
    private int transitionTimer = 0;
    private int pendingMonster = 0;      // 之泪清理后到进入巨兽的3秒缓冲

    // 本轮人形态召唤的之泪
    private final List<java.util.UUID> currentTears = new ArrayList<>();

    private static final int HUMAN_DURATION = 25 * 20;   // 25秒
    private static final int MONSTER_BASE_DURATION = 60 * 20; // 60秒
    private static final int TEAR_KILL_EXTEND = 12 * 20; // 每个非玩家击杀的之泪延长12秒

    public EntityIsharmla(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, "idle_human");
        this.entityData.define(DATA_FORM, Form.HUMAN.ordinal());
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "S-03-07-1";
        this.abnormalityName = "伊莎玛拉";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "BLACK";
        this.maxPEOutput = 0;
        float[] basePreferences = {0.0f, 0.0f, 0.0f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    public Form getForm() {
        return Form.values()[this.entityData.get(DATA_FORM)];
    }

    // 瞬时动画播放剩余时间(>0时锁定动画,到期切回idle)——项4①
    private int animResetTimer = 0;
    // 当前正在播放的攻击动画类型与命中检测窗口——项4③(碰撞箱判定)
    private int attackAnimType = -1;  // 0=咬 1=甩尾 2=光束
    private int attackHitWindowStart = 0;
    private int attackHitWindowEnd = 0;
    private final java.util.Set<java.util.UUID> currentAttackHitTargets = new java.util.HashSet<>();
    // 光束累计命中次数(满4次造成15蓝伤)——项6
    private int beamChargeCount = 0;
    private boolean beamAttackStarted = false;
    private final java.util.List<net.minecraft.world.entity.LivingEntity> beamCircleTargets = new java.util.ArrayList<>();

    private void setForm(Form form) {
        Form old = getForm();
        this.entityData.set(DATA_FORM, form.ordinal());
        if (old != form) {
            this.refreshDimensions(); // 项4②:切换形态时刷新碰撞箱
        }
    }

    // 项4②:巨兽形态使用更大的碰撞箱(按模型约 3.5x3.5),人形保持默认
    private static final net.minecraft.world.entity.EntityDimensions HUMAN_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(0.9f, 2.2f);
    private static final net.minecraft.world.entity.EntityDimensions MONSTER_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(3.5f, 3.5f);

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return getForm() == Form.MONSTER ? MONSTER_SIZE : HUMAN_SIZE;
    }

    private void setAnim(String anim) {
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ANIM, anim);
        }
    }

    /** 播放瞬时(一次性)动画,duration tick 后自动切回 idle(项4①) */
    private void setAnimTimed(String anim, int duration) {
        setAnim(anim);
        animResetTimer = duration;
    }

    /** 切回当前形态的待机动画 */
    private void backToIdle() {
        setAnim(getForm() == Form.MONSTER ? "idle_monster" : "idle_human");
    }

    private String getAnim() {
        return this.entityData.get(DATA_ANIM);
    }

    // ==================== 出逃:瞬移到最近反应堆 ====================

    @Override
    public boolean hasEscape() {
        return true; // 伊莎玛拉是出逃形态的 boss
    }

    /** 由斯卡蒂生成时调用:瞬移到最近的再生反应堆,并初始化为人形态 */
    public void onSpawnFromSkadi(ServerLevel level) {
        net.minecraft.core.BlockPos reactor =
                SpawnIsharmlaHook.findNearestReactorPublic(level, this.blockPosition());
        if (reactor != null) {
            this.teleportTo(reactor.getX() + 0.5, reactor.getY(), reactor.getZ() + 0.5);
        }
        // 机制2:若正处于"深蓝色正午"考验,立刻结束考验,场上海嗣集体瞬移到身边并死亡,每只供给+50点生命值
        if (com.wzz.lobotocraft.event.BlueMiddayEvent.isTrialActive()) {
            com.wzz.lobotocraft.event.BlueMiddayEvent.endTrial();
            List<net.minecraft.world.entity.Entity> seaborns =
                    level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(
                                    -30000000, level.getMinBuildHeight(), -30000000,
                                    30000000, level.getMaxBuildHeight(), 30000000),
                            com.wzz.lobotocraft.event.BlueMiddayEvent::isSeaborn)
                    .stream().map(e -> (net.minecraft.world.entity.Entity) e)
                    .collect(java.util.stream.Collectors.toList());
            float bonus = 0;
            for (net.minecraft.world.entity.Entity e : seaborns) {
                e.teleportTo(this.getX(), this.getY(), this.getZ());
                e.discard();
                bonus += 50f;
            }
            if (bonus > 0) {
                float newMax = (float) this.getAttributeValue(Attributes.MAX_HEALTH) + bonus;
                var inst = this.getAttribute(Attributes.MAX_HEALTH);
                if (inst != null) inst.setBaseValue(newMax);
                this.setHealth(this.getMaxHealth());
            }
        }
        enterHumanForm(true);
    }

    // ==================== 形态切换 ====================

    private void enterHumanForm(boolean firstTime) {
        setForm(Form.HUMAN);
        setHealth(getMaxHealth());
        humanPhaseTimer = HUMAN_DURATION;
        transitioning = true;
        transitionTimer = 20;
        setAnim(firstTime ? "start" : "to_human");
        playSoundToAll(ModSounds.ISHARMLA_TO_HUMAN.get());
        // 生成5个伊莎玛拉之泪
        spawnTears();
    }

    private void enterMonsterForm() {
        setForm(Form.MONSTER);
        monsterHealth = MONSTER_MAX_HEALTH; // 进入巨兽回满巨兽血量
        transitioning = true;
        transitionTimer = 20;
        setAnim("to_monster");
        playSoundToAll(ModSounds.ISHARMLA_TO_MONSTER.get());
    }

    private void spawnTears() {
        if (!(this.level() instanceof ServerLevel level)) return;
        currentTears.clear();
        playSoundToAll(ModSounds.ISHARMLA_TEAR_SUMMON.get());
        for (int i = 0; i < 5; i++) {
            EntityIsharmlaTear tear = ModEntities.isharmla_tear.get().create(level);
            if (tear == null) continue;
            double ox = (this.random.nextDouble() - 0.5) * 7;
            double oz = (this.random.nextDouble() - 0.5) * 7;
            tear.moveTo(this.getX() + ox, this.getY(), this.getZ() + oz,
                    this.random.nextFloat() * 360f, 0f);
            tear.setAnimation("start");
            level.addFreshEntity(tear);
            currentTears.add(tear.getUUID());
        }
    }

    // ==================== tick: 形态状态机 + 攻击 AI ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();

        // 瞬时动画到期切回待机(项4①:防止停在无效默认动画/最后一帧)
        if (animResetTimer > 0) {
            animResetTimer--;
            if (animResetTimer == 0 && !transitioning) {
                backToIdle();
            }
        }

        // 攻击命中窗口:动画播放期间每tick用碰撞箱判定(项4③)
        if (attackAnimType >= 0) {
            tickAttackHitDetection(level);
        }

        if (transitioning) {
            transitionTimer--;
            if (transitionTimer <= 0) {
                transitioning = false;
                backToIdle();
            }
            return;
        }

        if (getForm() == Form.HUMAN) {
            tickHumanForm(level);
        } else {
            tickMonsterForm(level);
        }
    }

    /** 项4③:攻击动画期间,目标进入伊莎玛拉攻击碰撞箱即判定命中(每次攻击每目标仅一次) */
    private void tickAttackHitDetection(ServerLevel level) {
        if (this.tickCount < attackHitWindowStart) return;
        if (this.tickCount > attackHitWindowEnd) {
            // 窗口结束,清理
            attackAnimType = -1;
            currentAttackHitTargets.clear();
            return;
        }
        switch (attackAnimType) {
            case 0 -> { // 咬:身前较小范围
                net.minecraft.world.phys.AABB box = this.getBoundingBox().inflate(2.0, 1.0, 2.0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::isHostileTarget)) {
                    if (currentAttackHitTargets.add(e.getUUID())) dealDamageWithMental(e, 40f);
                }
            }
            case 1 -> { // 甩尾:身前6x9大范围
                net.minecraft.world.phys.AABB box = this.getBoundingBox().inflate(6, 3, 9);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::isHostileTarget)) {
                    if (currentAttackHitTargets.add(e.getUUID())) dealDamageWithMental(e, 25f);
                }
            }
            case 2 -> tickBeamAttack(level); // 光束:见项6特效逻辑
        }
    }

    /**
     * 项6:光束攻击特效。
     * 动画注释:所有(敌对)生物脚下出现白色粒子围成3x3圆圈,
     * 之后监守者声波粒子从7格高落在中心并播放监守者声波音效;
     * 累计使用这种攻击4次后,对白圈内生物造成15点蓝色伤害,然后白粒子消散。
     */
    private void tickBeamAttack(ServerLevel level) {
        int t = this.tickCount - attackHitWindowStart; // 自光束开始的相对tick

        if (!beamAttackStarted) {
            beamAttackStarted = true;
            beamChargeCount++; // 累计使用次数
            // 锁定本次受影响的生物
            beamCircleTargets.clear();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(16, 6, 16), this::isHostileTarget)) {
                beamCircleTargets.add(e);
            }
        }

        // 每5tick在每个目标脚下绘制白色粒子3x3圆圈
        if (t % 5 == 0) {
            for (LivingEntity e : beamCircleTargets) {
                if (!e.isAlive()) continue;
                double cx = e.getX(), cy = e.getY() + 0.1, cz = e.getZ();
                for (int i = 0; i < 12; i++) {
                    double ang = (Math.PI * 2 / 12) * i;
                    double rx = cx + Math.cos(ang) * 1.5; // 半径1.5≈3x3范围
                    double rz = cz + Math.sin(ang) * 1.5;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                            rx, cy, rz, 1, 0.0, 0.02, 0.0, 0.0);
                }
            }
        }

        // t=30:监守者声波粒子从7格高落在每个圆圈中心 + 监守者声波音效
        if (t == 30) {
            for (LivingEntity e : beamCircleTargets) {
                if (!e.isAlive()) continue;
                double cx = e.getX(), cz = e.getZ();
                for (int dy = 0; dy < 7; dy++) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                            cx, e.getY() + 7 - dy, cz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            level.playSound(null, this.blockPosition(),
                    net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 2.0f, 1.0f);
        }

        // t=45:若累计已达4次,对白圈内生物造成15蓝伤,然后白粒子消散(重置计数)
        if (t == 45) {
            if (beamChargeCount >= 4) {
                for (LivingEntity e : beamCircleTargets) {
                    if (!e.isAlive()) continue;
                    e.hurt(com.wzz.lobotocraft.util.DamageHelper.getDamage(this, "blue"), 15f);
                    if (e instanceof ServerPlayer sp) {
                        MentalValueUtil.reduceMentalValue(sp, 15f);
                    }
                }
                beamChargeCount = 0; // 白粒子消散,重新累计
                beamCircleTargets.clear();
            }
        }
    }

    private void tickHumanForm(ServerLevel level) {
        if (pendingMonster > 0) {
            pendingMonster--;
            if (pendingMonster == 0) {
                enterMonsterForm();
            }
            return;
        }
        // 项4④:人形态也会移动,缓慢靠近最近目标
        LivingEntity target = findNearestTarget(level, 24);
        if (target != null) {
            this.getNavigation().moveTo(target, 1.0);
            this.getLookControl().setLookAt(target);
        }
        if (humanPhaseTimer > 0) {
            humanPhaseTimer--;
            if (humanPhaseTimer == 0) {
                // 播放清理动作,击杀范围内未被玩家击杀的之泪,统计延长时间
                cleanupTears(level);
            }
        }
    }

    private void cleanupTears(ServerLevel level) {
        setAnimTimed("heal_human", 3 * 20); // 项4①:3秒后(进入巨兽前)自动切回待机
        playSoundToAll(ModSounds.ISHARMLA_TEAR_SUMMON.get());
        int naturalKills = 0;
        for (java.util.UUID uuid : currentTears) {
            if (level.getEntity(uuid) instanceof EntityIsharmlaTear tear && tear.isAlive()) {
                if (!tear.wasKilledByPlayer()) {
                    naturalKills++;
                }
                tear.discard(); // 清理(自然死亡)
            }
        }
        currentTears.clear();
        // 巨兽持续时间 = 基础60秒 + 每个非玩家击杀的之泪12秒
        monsterPhaseTimer = MONSTER_BASE_DURATION + naturalKills * TEAR_KILL_EXTEND;
        // 3秒后进入巨兽形态
        pendingMonster = 3 * 20;
    }

    private void tickMonsterForm(ServerLevel level) {
        if (monsterPhaseTimer > 0) {
            monsterPhaseTimer--;
            if (monsterPhaseTimer == 0) {
                enterHumanForm(false);
                return;
            }
        }
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }
        LivingEntity target = findNearestTarget(level, 24);
        if (target == null) return;

        double dist = this.distanceTo(target);
        this.getLookControl().setLookAt(target);
        // 项4④:巨兽形态会移动追击(巨兽无move动画,沿用idle_monster表现)
        if (dist > 6.0) {
            this.getNavigation().moveTo(target, 1.0);
            return;
        }
        // 进入攻击距离:发起一次攻击(命中由碰撞箱窗口判定)
        this.getNavigation().stop();
        performMonsterAttack(level, target);
        attackCooldown = 40;
    }

    private void performMonsterAttack(ServerLevel level, LivingEntity target) {
        float roll = this.random.nextFloat();
        currentAttackHitTargets.clear();
        if (roll < 0.40f) {
            // 撕咬:单体40黑伤,命中窗口约动画第6~12tick
            setAnimTimed("bite_monster", 20);
            playSoundToAll(ModSounds.ISHARMLA_BITE.get());
            attackAnimType = 0;
            attackHitWindowStart = this.tickCount + 6;
            attackHitWindowEnd = this.tickCount + 12;
        } else if (roll < 0.80f) {
            // 甩尾:身前6x9范围25黑伤,命中窗口约第8~16tick
            setAnimTimed("tail_monster", 24);
            playSoundToAll(ModSounds.ISHARMLA_TAIL.get());
            attackAnimType = 1;
            attackHitWindowStart = this.tickCount + 8;
            attackHitWindowEnd = this.tickCount + 16;
        } else {
            // 光束:范围攻击(项6特效),命中窗口贯穿整个动画
            setAnimTimed("attack_monster", 60);
            playSoundToAll(ModSounds.ISHARMLA_BEAM.get());
            attackAnimType = 2;
            attackHitWindowStart = this.tickCount + 1;
            attackHitWindowEnd = this.tickCount + 60;
            beamAttackStarted = false;
        }
    }

    private LivingEntity findNearestTarget(ServerLevel level, double range) {
        List<Player> players = level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        // 机制6:因伊莎玛拉攻击陷入恐慌的玩家不会被仇恨
                        && !p.getPersistentData().getBoolean("isharmla_panic"));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = p.distanceToSqr(this);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private boolean isHostileTarget(LivingEntity e) {
        if (e == this) return false;
        if (e instanceof EntityIsharmlaTear) return false;
        if (e instanceof Player p) {
            return p.isAlive() && !p.isCreative() && !p.isSpectator();
        }
        return false;
    }

    /** 造成伤害(黑色),并附带机制1的精神攻击 */
    private void dealDamageWithMental(LivingEntity target, float damage) {
        target.hurt(com.wzz.lobotocraft.util.DamageHelper.getDamage(this, "black"), damage);
        if (target instanceof ServerPlayer player) {
            // 机制1:额外强行扣除3-6%精神值(无视精神抗性)
            float maxMental = MentalValueUtil.getEffectiveMaxMentalValue(player);
            float pct = (3 + this.random.nextInt(4)) / 100f; // 3%-6%
            MentalValueUtil.reduceMentalValue(player, maxMental * pct);
            // 机制1:减少玩家50%精神值恢复效果(标记,由精神恢复系统读取)
            player.getPersistentData().putLong("isharmla_mental_recover_debuff_until",
                    player.level().getGameTime() + 200); // 10秒内恢复减半
        }
    }

    private void playSoundToAll(net.minecraft.sounds.SoundEvent sound) {
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, this.blockPosition(), sound, SoundSource.HOSTILE, 1.2f, 1.0f);
        }
    }

    // ==================== 抗性:人形1.0 / 巨兽0.1;双血量 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        // 始终允许受击表现:重置无敌帧计时,避免连续攻击被吞
        if (getForm() == Form.MONSTER) {
            // 巨兽抗性0.1(只受到10%伤害),走独立血量
            float actual = amount * 0.1f;
            monsterHealth -= actual;
            // 受击表现(红光/声音/仇恨),但不通过原版血量
            this.hurtTime = 10;
            this.hurtDuration = 10;
            this.playHurtSound(source);
            this.markHurt();
            if (source.getEntity() instanceof LivingEntity attacker && this.getTarget() == null) {
                this.setTarget(attacker);
            }
            if (monsterHealth <= 0) {
                // 巨兽血量耗尽:强制变回人形(死亡由人形血量决定)
                monsterHealth = 0;
                enterHumanForm(false);
            }
            return true;
        } else {
            // 人形抗性1.0(不变)
            return super.hurt(source, amount);
        }
    }

    @Override
    public void die(DamageSource source) {
        // 只有人形态血量耗尽才真正死亡
        super.die(source);
    }

    // ==================== 工作系统(不可工作) ====================

    @Override
    public boolean canEscape() { return true; }

    @Override
    public String getAbnormalityCode() { return "S-03-07-1"; }

    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }

    @Override
    public String name() { return "isharmla"; }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) { }

    @Override
    public void onQliphothMeltdown() { }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("她不再歌唱，它为大群发声。");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityIsharmla> event) {
        String anim = getAnim();
        switch (anim) {
            case "start" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.start")); }
            case "to_human" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.to_human")); }
            case "to_monster" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.to_monster")); }
            case "heal_human" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.heal_human")); }
            case "bite_monster" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.bite_monster")); }
            case "tail_monster" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.tail_monster")); }
            case "attack_monster" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.attack_monster")); }
            case "die" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla.die")); }
        }
        // 待机
        if (getForm() == Form.MONSTER) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla.idle_monster"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla.move_human"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla.idle_human"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Form", getForm().ordinal());
        tag.putFloat("MonsterHealth", monsterHealth);
        tag.putInt("HumanPhaseTimer", humanPhaseTimer);
        tag.putInt("MonsterPhaseTimer", monsterPhaseTimer);
        tag.putInt("PendingMonster", pendingMonster);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setForm(Form.values()[tag.getInt("Form")]);
        monsterHealth = tag.getFloat("MonsterHealth");
        humanPhaseTimer = tag.getInt("HumanPhaseTimer");
        monsterPhaseTimer = tag.getInt("MonsterPhaseTimer");
        pendingMonster = tag.getInt("PendingMonster");
    }
}
