package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.listener.BlueMiddayEvent;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.SkadiBanishData;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;

/**
 * 伊莎玛拉 (S-03-07-1) —— ALEPH 级异想体,由浊心斯卡蒂计数器归零后生成。
 * 双形态(人形/巨兽),血量独立;人形抗性1.0、巨兽抗性0.4;
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
    private int beamSkillCooldown = 30 * 20;
    private boolean transitioning = false;
    private int transitionTimer = 0;
    private int pendingMonster = 0;      // 之泪清理后到进入巨兽的3秒缓冲
    private int monsterNoTargetTicks = 0;
    private int monsterIdleBeamCooldown = 0;

    // 本轮人形态召唤的之泪
    private final List<java.util.UUID> currentTears = new ArrayList<>();

    private static final int HUMAN_DURATION = 25 * 20;   // 25秒
    private static final int MONSTER_BASE_DURATION = 60 * 20; // 60秒
    private static final int TEAR_KILL_EXTEND = 12 * 20; // 每个非玩家击杀的之泪延长12秒
    private static final int HEAL_HUMAN_ANIMATION_TICKS = 30;
    private static final int BITE_MONSTER_ANIMATION_TICKS = 40;
    private static final int TAIL_MONSTER_ANIMATION_TICKS = 26;
    private static final int BEAM_ATTACK_ANIMATION_TICKS = 42;
    private static final int BEAM_SKILL_INTERVAL_TICKS = 30 * 20;
    private static final int NO_TARGET_BEAM_DELAY_TICKS = 15 * 20;
    private static final int NO_TARGET_BEAM_INTERVAL_TICKS = 7 * 20;
    private static final int MONSTER_FORCED_ESCAPE_INTERVAL = 25 * 20;
    private static final int BEAM_MOUTH_EFFECT_TICK = 12;
    private static final int SPECIAL_SHAKE_TICKS = 10;
    private static final float SPECIAL_SHAKE_INTENSITY = 20.0F;
    private static final int[] BEAM_IMPACT_TICKS = {12, 20, 28, 36};
    private static final int LAST_BEAM_IMPACT_TICK = BEAM_IMPACT_TICKS[BEAM_IMPACT_TICKS.length - 1];
    private static final double BEAM_TARGET_RADIUS = 1.5D;
    private static final double BEAM_FALL_HEIGHT = 10.0D;
    private static final double MONSTER_ATTACK_DISTANCE = 8.0D;
    private static final int SEARCH_LIMIT = 30_000_000;
    private static final double REACTOR_ACTIVITY_RADIUS = 24.0D;
    private static final double REACTOR_ACTIVITY_RADIUS_SQR = REACTOR_ACTIVITY_RADIUS * REACTOR_ACTIVITY_RADIUS;
    private int monsterForcedEscapeTimer = MONSTER_FORCED_ESCAPE_INTERVAL;
    private java.util.UUID protectedForcedEscapeId = null;
    private BlockPos anchorReactorPos;

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
    private Vec3 attackForward = new Vec3(0.0, 0.0, 1.0);
    private java.util.UUID currentAttackTargetUuid = null;
    // 光束攻击会锁定施放瞬间该维度所有玩家脚下的位置,之后可通过离开圈躲避.
    private final List<BeamTargetSpot> beamTargetSpots = new ArrayList<>();
    private final java.util.Set<Integer> completedBeamImpacts = new java.util.HashSet<>();

    private static class BeamTargetSpot {
        private final double x;
        private final double y;
        private final double z;

        private BeamTargetSpot(LivingEntity target) {
            this.x = target.getX();
            this.y = target.getY();
            this.z = target.getZ();
        }

        private BeamTargetSpot(Vec3 pos) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }
    }

    private void setForm(Form form) {
        Form old = getForm();
        this.entityData.set(DATA_FORM, form.ordinal());
        if (old != form) {
            this.refreshDimensions(); // 项4②:切换形态时刷新碰撞箱
        }
    }

    // 巨兽形态使用更大的碰撞箱,人形保持默认
    private static final net.minecraft.world.entity.EntityDimensions HUMAN_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(0.9f, 2.2f);
    private static final net.minecraft.world.entity.EntityDimensions MONSTER_SIZE =
            net.minecraft.world.entity.EntityDimensions.scalable(8.0f, 5.0f);

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
        BlockPos reactor = SpawnIsharmlaHook.findNearestReactorPublic(level, this.blockPosition());
        if (reactor != null) {
            anchorReactorPos = reactor.immutable();
            BlockPos spawnPos = EntityUtil.findReactorSpawnPositionInCompany(level, anchorReactorPos, 4);
            this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        }
        // 机制2:若正处于"深蓝色正午"考验,立刻结束考验,场上海嗣集体瞬移到身边并死亡,每只供给+50点生命值
        if (BlueMiddayEvent.isTrialActive()) {
            BlueMiddayEvent.endTrial();
            List<net.minecraft.world.entity.Entity> seaborns =
                    level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(
                                    -30000000, level.getMinBuildHeight(), -30000000,
                                    30000000, level.getMaxBuildHeight(), 30000000),
                            BlueMiddayEvent::isSeaborn)
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

    private void ensureAnchorReactor(ServerLevel level) {
        if (anchorReactorPos != null) {
            return;
        }
        BlockPos reactor = SpawnIsharmlaHook.findNearestReactorPublic(level, this.blockPosition());
        if (reactor != null) {
            anchorReactorPos = reactor.immutable();
        }
    }

    private boolean isWithinReactorActivityArea(Vec3 pos) {
        if (anchorReactorPos == null) {
            return true;
        }
        return Vec3.atCenterOf(anchorReactorPos).distanceToSqr(pos) <= REACTOR_ACTIVITY_RADIUS_SQR;
    }

    private boolean isWithinReactorActivityArea(LivingEntity entity) {
        return isWithinReactorActivityArea(entity.position());
    }

    private void keepNearReactor(ServerLevel level) {
        ensureAnchorReactor(level);
        if (anchorReactorPos == null) {
            return;
        }
        if (isWithinReactorActivityArea(this.position()) && level.noCollision(this)) {
            return;
        }
        BlockPos spawnPos = EntityUtil.findReactorSpawnPositionInCompany(level, anchorReactorPos, 4);
        this.teleportTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    // ==================== 形态切换 ====================

    private void enterHumanForm(boolean firstTime) {
        clearActiveAttack();
        resetMonsterNoTargetBeam();
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
        clearActiveAttack();
        resetMonsterNoTargetBeam();
        setForm(Form.MONSTER);
        if (this.level() instanceof ServerLevel level) {
            keepNearReactor(level);
        }
        monsterHealth = MONSTER_MAX_HEALTH; // 进入巨兽回满巨兽血量
        beamSkillCooldown = BEAM_SKILL_INTERVAL_TICKS;
        monsterForcedEscapeTimer = MONSTER_FORCED_ESCAPE_INTERVAL;
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
        keepNearReactor(level);

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
            currentAttackTargetUuid = null;
            currentAttackHitTargets.clear();
            return;
        }
        switch (attackAnimType) {
            case 0 -> { // 咬:身前较小范围
                playSound(ModSounds.ISHARMLA_BITE.get());
                hitTargetsInForwardArea(level, 8.0, 4.2, 1.2, 40f);
            }
            case 1 -> { // 甩尾:身前6x9大范围
                hitTargetsInForwardArea(level, 10.0, 5.0, 2.5, 25f);
            }
            case 2 -> tickBeamAttack(level); // 光束:见项6特效逻辑
        }
    }

    private void hitTargetsInForwardArea(ServerLevel level, double reach, double halfWidth,
                                         double verticalPadding, float damage) {
        AABB box = createForwardAttackBox(reach, halfWidth, verticalPadding);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::isHostileTarget)) {
            tryHitForwardTarget(e, reach, halfWidth, verticalPadding, damage);
        }
        if (currentAttackTargetUuid != null
                && level.getEntity(currentAttackTargetUuid) instanceof LivingEntity target) {
            tryHitForwardTarget(target, reach, halfWidth, verticalPadding, damage);
        }
    }

    private void tryHitForwardTarget(LivingEntity target, double reach, double halfWidth,
                                     double verticalPadding, float damage) {
        if (!isHostileTarget(target) || currentAttackHitTargets.contains(target.getUUID())) {
            return;
        }
        if (!isInForwardAttackArea(target, reach, halfWidth, verticalPadding)) {
            return;
        }
        currentAttackHitTargets.add(target.getUUID());
        dealDamageWithMental(target, damage);
    }

    private AABB createForwardAttackBox(double reach, double halfWidth, double verticalPadding) {
        AABB selfBox = this.getBoundingBox();
        Vec3 from = new Vec3(this.getX(), selfBox.minY, this.getZ());
        Vec3 to = new Vec3(
                this.getX() + attackForward.x * reach,
                selfBox.maxY,
                this.getZ() + attackForward.z * reach);
        return new AABB(from, to).inflate(halfWidth, verticalPadding, halfWidth);
    }

    private boolean isInForwardAttackArea(LivingEntity target, double reach, double halfWidth,
                                          double verticalPadding) {
        AABB selfBox = this.getBoundingBox();
        AABB targetBox = target.getBoundingBox();
        if (targetBox.maxY < selfBox.minY - verticalPadding
                || targetBox.minY > selfBox.maxY + verticalPadding) {
            return false;
        }

        Vec3 toTarget = target.position().subtract(this.position());
        double forwardDistance = toTarget.x * attackForward.x + toTarget.z * attackForward.z;
        double sideDistance = Math.abs(toTarget.x * attackForward.z - toTarget.z * attackForward.x);
        double targetRadius = Math.max(0.3, target.getBbWidth() * 0.5);
        return forwardDistance >= -targetRadius
                && forwardDistance <= reach + targetRadius
                && sideDistance <= halfWidth + targetRadius;
    }

    /**
     * 项6:光束攻击特效。
     * 施放时锁定该维度所有玩家脚下的3x3圆圈;没有玩家时锁定自身前方落点。
     * 声波缓慢下落后,分四段对仍停留在圈内的玩家造成15点蓝色伤害。
     */
    private void tickBeamAttack(ServerLevel level) {
        int t = this.tickCount - attackHitWindowStart; // 自光束开始的相对tick

        if (t == BEAM_MOUTH_EFFECT_TICK) {
            spawnMouthSonicBurst(level);
        }

        if (t % 5 == 0 && t <= LAST_BEAM_IMPACT_TICK) {
            for (BeamTargetSpot spot : beamTargetSpots) {
                spawnBeamWarningCircle(level, spot);
            }
        }

        if (t <= LAST_BEAM_IMPACT_TICK) {
            double progress = Math.min(1.0D, Math.max(0.0D, t / (double) LAST_BEAM_IMPACT_TICK));
            double yOffset = BEAM_FALL_HEIGHT * (1.0D - progress);
            for (BeamTargetSpot spot : beamTargetSpots) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                        spot.x, spot.y + yOffset, spot.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        for (int impactTick : BEAM_IMPACT_TICKS) {
            if (t == impactTick && completedBeamImpacts.add(impactTick)) {
                java.util.Set<java.util.UUID> hitThisImpact = new java.util.HashSet<>();
                for (BeamTargetSpot spot : beamTargetSpots) {
                    hitEntitiesInBeamSpot(level, spot, hitThisImpact);
                }
                level.playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.HOSTILE, 2.0f, 1.0f);
            }
        }

        if (t >= LAST_BEAM_IMPACT_TICK && completedBeamImpacts.size() >= BEAM_IMPACT_TICKS.length) {
            beamTargetSpots.clear();
        }
    }

    private void spawnMouthSonicBurst(ServerLevel level) {
        Vec3 mouth = getMonsterMouthPosition();
        for (int i = 0; i < 8; i++) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                    mouth.x, mouth.y + i * 0.7D, mouth.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                90, this.getBbWidth() * 0.55D, this.getBbHeight() * 0.35D, this.getBbWidth() * 0.55D, 0.1D);
        level.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 2.0F, 1.0F);
        for (ServerPlayer player : level.getPlayers(this::isBeamPlayerTarget)) {
            MessageLoader.getLoader().sendToPlayer(player,
                    new TriggerShakePacket(SPECIAL_SHAKE_TICKS, SPECIAL_SHAKE_INTENSITY));
        }
    }

    private Vec3 getMonsterMouthPosition() {
        Vec3 forward = getHorizontalFacingVector();
        return this.position()
                .add(forward.scale(3.2D))
                .add(0.0D, this.getBbHeight() * 0.72D, 0.0D);
    }

    private Vec3 getHorizontalFacingVector() {
        double yaw = this.getYRot() * Math.PI / 180.0D;
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private void spawnBeamWarningCircle(ServerLevel level, BeamTargetSpot spot) {
        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2 / 12) * i;
            double rx = spot.x + Math.cos(ang) * BEAM_TARGET_RADIUS;
            double rz = spot.z + Math.sin(ang) * BEAM_TARGET_RADIUS;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    rx, spot.y + 0.1, rz, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    private void hitEntitiesInBeamSpot(ServerLevel level, BeamTargetSpot spot,
                                       java.util.Set<java.util.UUID> hitThisImpact) {
        for (LivingEntity target : getBeamTargets(level)) {
            if (hitThisImpact.contains(target.getUUID()) || !isInBeamSpot(target, spot)) {
                continue;
            }
            hitThisImpact.add(target.getUUID());
            EntityUtil.clearHurtTime(target);
            target.hurt(com.wzz.lobotocraft.util.DamageHelper.getDamage(this, "blue"), 15f);
            if (target instanceof ServerPlayer player) {
                MentalValueUtil.reduceMentalValue(player, 15f);
            }
        }
    }

    private boolean isInBeamSpot(LivingEntity target, BeamTargetSpot spot) {
        double radius = BEAM_TARGET_RADIUS + Math.max(0.3D, target.getBbWidth() * 0.5D);
        double dx = target.getX() - spot.x;
        double dz = target.getZ() - spot.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private void tickHumanForm(ServerLevel level) {
        if (pendingMonster > 0) {
            pendingMonster--;
            if (pendingMonster == 0) {
                enterMonsterForm();
            }
            return;
        }
        // 人形态:闲逛(不仇恨、不追击玩家),只随机游走
        if (this.getNavigation().isDone() && this.random.nextInt(80) == 0) {
            net.minecraft.world.phys.Vec3 pos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this, 10, 7);
            if (pos != null && isWithinReactorActivityArea(pos)) {
                this.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.8);
            }
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
        setAnimTimed("heal_human", HEAL_HUMAN_ANIMATION_TICKS);
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
        pendingMonster = HEAL_HUMAN_ANIMATION_TICKS;
    }

    private void tickMonsterForm(ServerLevel level) {
        tickMonsterForcedEscape(level);
        if (monsterPhaseTimer > 0) {
            monsterPhaseTimer--;
            if (monsterPhaseTimer == 0) {
                enterHumanForm(false);
                return;
            }
        }

        if (beamSkillCooldown > 0) {
            beamSkillCooldown--;
        }

        //FIX 3 防止在攻击时重复攻击导致攻击无效化(光线攻击会出bug)
        if (tickCount < attackHitWindowEnd) {
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            return;
        }

        LivingEntity target = findNearestTarget(level, 24);
        if (target == null) {
            tickMonsterNoTargetBeam(level);
            return;
        }
        resetMonsterNoTargetBeam();

        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        double dist = this.distanceTo(target);
        this.getLookControl().setLookAt(target);
        // 项4④:巨兽形态会移动追击(巨兽无move动画,沿用idle_monster表现)
        if (dist > MONSTER_ATTACK_DISTANCE) {
            this.getNavigation().moveTo(target, 1.0);
            return;
        }
        // 进入攻击距离:发起一次攻击(命中由碰撞箱窗口判定)
        this.getNavigation().stop();
        performMonsterAttack(level, target);
        attackCooldown = 40;
    }

    private void tickMonsterForcedEscape(ServerLevel level) {
        if (--monsterForcedEscapeTimer > 0) {
            return;
        }
        monsterForcedEscapeTimer = MONSTER_FORCED_ESCAPE_INTERVAL;
        forceOneAbnormalityEscape(level);
    }

    private void forceOneAbnormalityEscape(ServerLevel level) {
        AABB wholeLevel = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        AbstractAbnormality chosen = null;
        double chosenDistance = Double.MAX_VALUE;
        for (ServerLevel candidateLevel : level.getServer().getAllLevels()) {
            List<AbstractAbnormality> candidates = candidateLevel.getEntitiesOfClass(
                    AbstractAbnormality.class,
                    wholeLevel,
                    abnormality -> abnormality != this
                            && abnormality.isAlive()
                            && abnormality.canEscape()
                            && !abnormality.hasEscape()
                            && abnormality.getQliphothCounter() > 0
            );
            for (AbstractAbnormality abnormality : candidates) {
                if (protectedForcedEscapeId != null && protectedForcedEscapeId.equals(abnormality.getUUID())) {
                    continue;
                }
                double distance = candidateLevel.dimension().equals(level.dimension())
                        ? abnormality.distanceToSqr(this)
                        : Double.MAX_VALUE - candidateLevel.random.nextInt(SEARCH_LIMIT);
                if (distance < chosenDistance) {
                    chosenDistance = distance;
                    chosen = abnormality;
                }
            }
        }
        if (chosen == null) {
            return;
        }
        protectedForcedEscapeId = chosen.getUUID();
        chosen.decreaseQliphothCounter(chosen.getQliphothCounter());
        this.broadcastMessage("§5伊莎玛拉的巨兽咆哮撕裂了收容，" + chosen.getAbnormalityName() + " 的计数器归零。");
    }

    // 旧的逐帧命中倒计时(保留字段以兼容,但命中改由 tickAttackHitDetection 处理)
    private int pendingAttackHit = 0;
    private int pendingAttackType = -1;

    private void performMonsterAttack(ServerLevel level, LivingEntity target) {
        currentAttackHitTargets.clear();
        currentAttackTargetUuid = target.getUUID();
        faceAttackTarget(target);

        int roll = this.random.nextInt(100);
        if (roll < 30) {
            // 撕咬:单体40黑伤,命中窗口约动画第6~12tick
            setAnimTimed("bite_monster", BITE_MONSTER_ANIMATION_TICKS);
            playSoundToAll(SoundEvents.EVOKER_FANGS_ATTACK);
            attackAnimType = 0;
            attackHitWindowStart = this.tickCount + 6;
            attackHitWindowEnd = this.tickCount + 12;
        } else if (roll < 60) {
            // 甩尾:身前6x9范围25黑伤,命中窗口约第8~16tick
            setAnimTimed("tail_monster", TAIL_MONSTER_ANIMATION_TICKS);
            playSoundToAll(ModSounds.ISHARMLA_TAIL.get());
            level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.2f, 0.9f);
            attackAnimType = 1;
            attackHitWindowStart = this.tickCount + 8;
            attackHitWindowEnd = this.tickCount + 16;
        } else {
            startPeriodicBeamSkill(level);
            beamSkillCooldown = BEAM_SKILL_INTERVAL_TICKS;
        }
    }

    private void tickMonsterNoTargetBeam(ServerLevel level) {
        this.getNavigation().stop();
        monsterNoTargetTicks++;
        if (monsterIdleBeamCooldown > 0) {
            monsterIdleBeamCooldown--;
        }
        if (monsterNoTargetTicks <= NO_TARGET_BEAM_DELAY_TICKS || monsterIdleBeamCooldown > 0) {
            return;
        }
        if (startPeriodicBeamSkill(level)) {
            monsterIdleBeamCooldown = Math.max(1, NO_TARGET_BEAM_INTERVAL_TICKS - BEAM_ATTACK_ANIMATION_TICKS);
            beamSkillCooldown = BEAM_SKILL_INTERVAL_TICKS;
            attackCooldown = 40;
        }
    }

    private void resetMonsterNoTargetBeam() {
        monsterNoTargetTicks = 0;
        monsterIdleBeamCooldown = 0;
    }

    private boolean startPeriodicBeamSkill(ServerLevel level) {
        List<LivingEntity> targets = getBeamTargets(level);

        currentAttackHitTargets.clear();
        currentAttackTargetUuid = null;
        beamTargetSpots.clear();
        completedBeamImpacts.clear();
        if (!targets.isEmpty()) {
            LivingEntity nearestTarget = targets.stream()
                    .min(java.util.Comparator.comparingDouble(target -> target.distanceToSqr(this)))
                    .orElse(null);
            if (nearestTarget != null) {
                faceAttackTarget(nearestTarget);
            }
        } else {
            attackForward = getHorizontalFacingVector();
        }
        for (LivingEntity target : targets) {
            beamTargetSpots.add(new BeamTargetSpot(target));
        }
        if (beamTargetSpots.isEmpty()) {
            beamTargetSpots.add(new BeamTargetSpot(getFallbackBeamTargetPosition()));
        }

        this.getNavigation().stop();
        setAnimTimed("attack_monster", BEAM_ATTACK_ANIMATION_TICKS);
        playSoundToAll(ModSounds.ISHARMLA_BEAM.get());
        attackAnimType = 2;
        attackHitWindowStart = this.tickCount + 1;
        attackHitWindowEnd = this.tickCount + BEAM_ATTACK_ANIMATION_TICKS;
        return true;
    }

    private Vec3 getFallbackBeamTargetPosition() {
        Vec3 forward = attackForward;
        if (forward.lengthSqr() < 1.0E-6D) {
            forward = getHorizontalFacingVector();
        }
        return this.position().add(forward.normalize().scale(8.0D));
    }

    private List<LivingEntity> getBeamTargets(ServerLevel level) {
        List<LivingEntity> targets = new ArrayList<>();
        targets.addAll(level.getPlayers(this::isBeamPlayerTarget));
        targets.addAll(level.getEntitiesOfClass(EntityClerk.class,
                this.getBoundingBox().inflate(32.0D), this::isBeamClerkTarget));
        return targets;
    }

    private boolean isBeamPlayerTarget(ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && isWithinReactorActivityArea(player);
    }

    private boolean isBeamClerkTarget(EntityClerk clerk) {
        return clerk.isAlive() && isWithinReactorActivityArea(clerk);
    }

    private void clearActiveAttack() {
        attackAnimType = -1;
        attackHitWindowStart = 0;
        attackHitWindowEnd = 0;
        currentAttackTargetUuid = null;
        currentAttackHitTargets.clear();
        beamTargetSpots.clear();
        completedBeamImpacts.clear();
    }

    private void faceAttackTarget(LivingEntity target) {
        attackForward = getHorizontalDirectionTo(target);
        float yaw = (float) (Math.atan2(attackForward.z, attackForward.x) * 180.0D / Math.PI) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private Vec3 getHorizontalDirectionTo(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            double yaw = this.getYRot() * Math.PI / 180.0D;
            return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return new Vec3(dx / length, 0.0D, dz / length);
    }

    private LivingEntity findNearestTarget(ServerLevel level, double range) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(range),
                this::isHostileTarget);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity target : targets) {
            double d = target.distanceToSqr(this);
            if (d < bestDist) { bestDist = d; best = target; }
        }
        return best;
    }

    private boolean isHostileTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (!isWithinReactorActivityArea(e)) return false;
        if (e instanceof EntityIsharmlaTear) return false;
        if (e instanceof Player p) {
            return !p.isCreative()
                    && !p.isSpectator()
                    && !p.getPersistentData().getBoolean("isharmla_panic");
        }
        if (e instanceof EntityClerk) return true;
        if (e instanceof AbstractAbnormality) return false;
        return true;
    }

    /** 造成伤害(黑色),并附带机制1的精神攻击 */
    private void dealDamageWithMental(LivingEntity target, float damage) {
        EntityUtil.clearHurtTime(target);
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

    // ==================== 抗性:人形1.0 / 巨兽0.4;双血量 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (getForm() == Form.MONSTER) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // 只有人形态血量耗尽才真正死亡
        super.die(source);
        
        if (level() instanceof ServerLevel sl) {
            SkadiBanishData data = com.wzz.lobotocraft.item.SkadiBanishData.get(sl);
            if (data.hasSkadiOrigin()) {
            	AbstractAbnormality newEntity = new EntityDarkSkadi(ModEntities.skadi_corrupted.get(), sl);
            	newEntity.moveTo(
            			data.getOriginX(),
            			data.getOriginY(),
            			data.getOriginZ(),
            			0, 0
            			);

            	// 重置状态
            	newEntity.setEscape(false);
            	newEntity.qliphothCounter = newEntity.maxQliphothCounter;

            	sl.addFreshEntity(newEntity);
            }
        }
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
        if (isHumanMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla.move_human"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla.idle_human"));
    }

    private boolean isHumanMovingForAnimation(AnimationState<EntityIsharmla> event) {
        Vec3 movement = this.getDeltaMovement();
        return event.isMoving()
                || movement.x * movement.x + movement.z * movement.z > 1.0E-6D
                || !this.getNavigation().isDone();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.125D)
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
        tag.putInt("BeamSkillCooldown", beamSkillCooldown);
        tag.putInt("MonsterNoTargetTicks", monsterNoTargetTicks);
        tag.putInt("MonsterIdleBeamCooldown", monsterIdleBeamCooldown);
        tag.putInt("MonsterForcedEscapeTimer", monsterForcedEscapeTimer);
        if (protectedForcedEscapeId != null) {
            tag.putUUID("ProtectedForcedEscapeId", protectedForcedEscapeId);
        }
        if (anchorReactorPos != null) {
            tag.put("AnchorReactorPos", NbtUtils.writeBlockPos(anchorReactorPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setForm(Form.values()[tag.getInt("Form")]);
        monsterHealth = tag.getFloat("MonsterHealth");
        humanPhaseTimer = tag.getInt("HumanPhaseTimer");
        monsterPhaseTimer = tag.getInt("MonsterPhaseTimer");
        pendingMonster = tag.getInt("PendingMonster");
        beamSkillCooldown = tag.contains("BeamSkillCooldown")
                ? tag.getInt("BeamSkillCooldown")
                : BEAM_SKILL_INTERVAL_TICKS;
        monsterNoTargetTicks = tag.getInt("MonsterNoTargetTicks");
        monsterIdleBeamCooldown = tag.getInt("MonsterIdleBeamCooldown");
        monsterForcedEscapeTimer = tag.contains("MonsterForcedEscapeTimer")
                ? tag.getInt("MonsterForcedEscapeTimer")
                : MONSTER_FORCED_ESCAPE_INTERVAL;
        protectedForcedEscapeId = tag.hasUUID("ProtectedForcedEscapeId")
                ? tag.getUUID("ProtectedForcedEscapeId")
                : null;
        anchorReactorPos = tag.contains("AnchorReactorPos")
                ? NbtUtils.readBlockPos(tag.getCompound("AnchorReactorPos"))
                : null;
    }
}
