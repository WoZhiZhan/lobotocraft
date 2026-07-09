package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.block.entity.ElevatorBlockEntity;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.TargetMarkerItem;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 小红帽雇佣兵 (F-01-57) —— WAW 级异想体。
 * 沟通工作:立刻填满负面能量(无伤害惩罚),结束后给予"目标"道具。
 * 委托状态:玩家用"目标"道具标记出逃异想体后,小红帽前往并只攻击该目标,
 *   镇压完成后自行回到出逃位置并重置计数器。
 * 出逃状态:无差别攻击所有目标,火铳贯穿前方20格。
 * 五种攻击:劈砍(30%)/火铳(30%)/连续劈砍(20%)/掷刀(20%)/远程走射(12-24格,每秒30%)。
 * 任何异想体突破收容时计数器-1(惩戒鸟除外)。
 * 狼(F-02-58)相关机制(须知Ⅱ/Ⅳ/Ⅴ/Ⅵ、狂暴)留有接口,待狼实装后接入。
 */
public class EntityRedHoodMercenary extends AbstractAbnormality {

    // Mob movement attributes are not 1:1 with player movement_speed values; these are gameplay-calibrated.
    private static final double BASE_MOVEMENT_SPEED = 0.45D; // effective sprinting player with Speed II
    private static final double WOLF_MODE_MOVEMENT_SPEED = BASE_MOVEMENT_SPEED * 1.5D;
    private static final double CHASE_SPEED_MODIFIER = 1.2D;
    private static final double GUN_RUN_MOVEMENT_MULTIPLIER = 0.25D;

    private int attackCooldown = 0;
    private int actionTimer = 0;        // 当前攻击动作剩余时间
    private int actionType = -1;        // 0劈砍 1火铳 2双劈 3掷刀 4走射
    private int actionHits = 0;         // 多段攻击剩余段数
    private LivingEntity actionTarget = null;
    private UUID commissionTargetId = null; // 委托目标
    private boolean commissionMode = false;
    private boolean wolfMode = false;   // 委托目标为狼:伤害与移速提升至150%
    private boolean rageMode = false;   // 狂暴:伤害200%、移速150%、受伤+35%
    private int rangedRollTimer = 0;
    private BlockPos targetElevatorPos = null;
    private int elevatorWaitTimer = 0;
    private int chaseStateCheckTimer = 0;
    private int chaseStuckTimer = 0;
    private UUID chaseTargetId = null;

    private enum ChaseState {
        FINDING_PATH,
        MOVING_TO_ELEVATOR,
        WAITING_ON_ELEVATOR,
        DIRECT_MOVE
    }
    private ChaseState chaseState = ChaseState.FINDING_PATH;

    public EntityRedHoodMercenary(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "F-01-57";
        this.abnormalityName = "小红帽雇佣兵";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "RED";
        this.maxPEOutput = 20;
        float[] basePreferences = {0.0f, 0.45f, 0.0f, 0.30f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(3);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.0f, 0.0f, 0.45f, 0.45f, 0.50f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.30f, 0.30f, 0.30f, 0.30f, 0.30f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 3),                       // Ⅰ 速度+3
                new ObservationLevelBonus(0.03f, 0),                      // Ⅱ 成功率+3%
                new ObservationLevelBonus(0.0f, 3, true, false, false),   // Ⅲ 速度+3、解锁饰品
                new ObservationLevelBonus(0.03f, 0, false, true, true)    // Ⅳ 成功率+3%、解锁护甲武器
        };
    }

    @Override
    public int getBasicInfoCost() { return 20; }
    @Override
    public int getSensitiveInfoCost() { return 20; }
    @Override
    public int getManualCost(int manualIndex) { return 5; }
    @Override
    public int getWorkPreferencesCost() { return 7; }

    @Override
    public String getAbnormalityCode() { return "F-01-57"; }
    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }
    @Override
    public String name() { return "redhat_mercenary"; }

    // ==================== 机制1:沟通工作给"目标"道具 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // 记录"同一天内工作过小红帽"(供又大又可能很坏的狼的吞人机制判定)
        player.getCapability(com.wzz.lobotocraft.capability.CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> player.getPersistentData()
                        .putInt("lobotocraft_worked_redhat_day", data.getCurrentDay()));
        // 须知Ⅱ:拥有狼的饰品"羊皮"的员工完成工作 → 计数器在倒计时结束后减少
        boolean hasSheepskin = false;
        for (ItemStack st : player.getInventory().items) {
            if (st.getItem() == ModItems.SHEEPSKIN_CURIO.get()) { hasSheepskin = true; break; }
        }
        if (hasSheepskin) {
            decreaseQliphothCounter(1);
        }
        if (workType == WorkType.ATTACHMENT) {
            if (!canAcceptCommission()) {
                player.sendSystemMessage(Component.literal("§c[小红帽雇佣兵] §7我现在没空接你的委托。"));
                return;
            }
            // 给予"目标"道具并触发对话(负面能量填满由工作偏好沟通0%自然导致)
            ItemStack target = new ItemStack(ModItems.TARGET_MARKER.get());
            if (!player.getInventory().add(target)) {
                player.level().addFreshEntity(new ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), target));
            }
            player.sendSystemMessage(Component.literal("§c[小红帽雇佣兵] §7想雇佣我的话，希望你付得起代价"));
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // 机制1:沟通工作不受伤害惩罚
        if (workType == WorkType.ATTACHMENT) return;
        float dmg = 4 + this.random.nextInt(3);
        player.hurt(DamageHelper.getDamage(this, "red"), dmg);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    // ==================== 委托状态 ====================

    /** 由"目标"道具调用:进入委托状态,前往并只攻击标记目标 */
    public void startCommission(LivingEntity target) {
        if (!canAcceptCommission()) {
            return;
        }
        if (isNonEscapedAbnormality(target)) {
            return;
        }
        this.commissionTargetId = target.getUUID();
        this.commissionMode = true;
        if (!hasEscape()) {
            // 委托出动不算出逃惩罚,但需要离开收容(复用出逃状态机)
            triggerEscape();
        }
    }

    public boolean canAcceptCommission() {
        return isAlive() && !hasEscape() && !commissionMode;
    }

    private LivingEntity getCommissionTarget(ServerLevel level) {
        if (commissionTargetId == null) return null;
        if (level.getEntity(commissionTargetId) instanceof LivingEntity living && living.isAlive()) {
            if (isNonEscapedAbnormality(living)) {
                return null;
            }
            return living;
        }
        return null;
    }

    /** 伤害倍率:狂暴200%、对狼委托150%、常态100% */
    private float dmgMul() {
        return rageMode ? 2.0f : (wolfMode ? 1.5f : 1.0f);
    }

    private void syncMovementSpeed() {
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double expectedSpeed = (wolfMode || rageMode) ? WOLF_MODE_MOVEMENT_SPEED : BASE_MOVEMENT_SPEED;
        if (Math.abs(speed.getBaseValue() - expectedSpeed) > 1.0E-4D) {
            speed.setBaseValue(expectedSpeed);
        }
    }

    private String idleAnimation() {
        return hasEscape() ? "idle2" : "idle1";
    }

    private void faceTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz <= 1.0E-6D) {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private LivingEntity getActionFacingTarget(ServerLevel level) {
        if (actionTarget != null && actionTarget.isAlive() && isValidTarget(actionTarget)) {
            return actionTarget;
        }
        actionTarget = commissionMode ? getCommissionTarget(level) : findTarget(level);
        return actionTarget;
    }

    /** 目标机制2:遇见狼或受狼嚎伤害 → 强制委托目标为狼,伤害移速提升至150% */
    public void forceWolfTarget(EntityBigBadWolf wolf) {
        if (rageMode) return;
        this.commissionTargetId = wolf.getUUID();
        this.commissionMode = true;
        this.wolfMode = true;
        syncMovementSpeed();
        if (!hasEscape()) triggerEscape();
    }

    /**
     * 狼被镇压时回调。
     * @param byPlayerWhileMarked 狼在被"目标"标记期间被玩家击杀 → 目标机制3:狂暴(回满血,200%伤害,受伤+35%)
     *                            否则 → 须知Ⅵ:陷入狂暴并无差别攻击
     */
    public void onWolfSuppressed(boolean byPlayerWhileMarked) {
        this.commissionMode = false;
        this.commissionTargetId = null;
        this.wolfMode = true; // 移速保持150%
        this.rageMode = true;
        if (byPlayerWhileMarked) {
            this.setHealth(this.getMaxHealth()); // 生命恢复至最大值一次
        }
        syncMovementSpeed();
        if (!hasEscape()) triggerEscape();
    }

    /** 委托完成:回到出逃位置并重置计数器 */
    private void completeCommission(ServerLevel level) {
        LivingEntity target = getCommissionTarget(level);
        if (target != null) {
            TargetMarkerItem.clearTargetMark(target);
        }
        commissionMode = false;
        commissionTargetId = null;
        // 回到出逃位置重置(复用基类die的回归逻辑:直接死亡重置)
        this.setHealth(0f);
        this.die(this.damageSources().fellOutOfWorld());
        initializeQliphothCounter(3);
    }

    // ==================== 出逃抗性:红0.9 白0.6 黑0.8 蓝1.2 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (rageMode) amount *= 1.35f; // 狂暴状态受到的伤害额外提高35%
        if (hasEscape()) {
            float m = 1.0f;
            if (DamageHelper.isRedDamage(source)) m = 0.9f;
            else if (DamageHelper.isWhiteDamage(source)) m = 0.6f;
            else if (DamageHelper.isBlackDamage(source)) m = 0.8f;
            else if (DamageHelper.isBlueDamage(source)) m = 1.2f;
            amount *= m;
        }
        return super.hurt(source, amount);
    }

    // ==================== tick: 攻击 AI ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();
        LivingEntity currentTarget = getTarget();
        if (currentTarget != null && isNonEscapedAbnormality(currentTarget)) {
            setTarget(null);
            getNavigation().stop();
        }
        if (!hasEscape()) return;
        syncMovementSpeed();

        // 委托目标为狼:红色渲染环绕;狂暴:红色+金色环绕
        if ((wolfMode || rageMode) && this.tickCount % 4 == 0) {
            double ang = (this.tickCount % 40) / 40.0 * Math.PI * 2;
            level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE,
                    getX() + Math.cos(ang) * 0.9, getY() + 1.0, getZ() + Math.sin(ang) * 0.9,
                    1, 0, 0, 0, 0);
            if (rageMode) {
                level.sendParticles(ParticleTypes.WAX_OFF,
                        getX() + Math.cos(ang + Math.PI) * 0.9, getY() + 1.2, getZ() + Math.sin(ang + Math.PI) * 0.9,
                        1, 0, 0, 0, 0);
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        if (rangedRollTimer > 0) rangedRollTimer--;

        // 当前动作进行中
        if (actionTimer > 0) {
            actionTimer--;
            tickAction(level);
            if (actionTimer == 0) {
                actionType = -1;
                actionTarget = null;
                setAnimation(idleAnimation());
            }
            return;
        }

        // 选目标
        LivingEntity target;
        if (commissionMode) {
            target = getCommissionTarget(level);
            if (target == null) {
                // 委托目标已被镇压:回家重置
                completeCommission(level);
                return;
            }
        } else {
            target = findTarget(level);
        }
        if (target == null) return;

        double dist = this.distanceTo(target);
        this.getLookControl().setLookAt(target);

        if (dist <= 6) {
            if (attackCooldown <= 0) chooseMeleeAttack(level, target);
        } else if (dist >= 12 && dist <= 24) {
            // 每秒30%概率使用走射
            if (rangedRollTimer <= 0) {
                rangedRollTimer = 20;
                if (this.random.nextFloat() < 0.30f) {
                    beginAction(level, 4, target);
                    return;
                }
            }
            chaseTarget(level, target);
        } else {
            chaseTarget(level, target);
        }
    }

    private void chaseTarget(ServerLevel level, LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        if (chaseTargetId == null || !chaseTargetId.equals(target.getUUID())) {
            resetChasePath(target);
        }
        this.getLookControl().setLookAt(target);

        chaseStateCheckTimer++;
        if (chaseState == ChaseState.FINDING_PATH || chaseStateCheckTimer >= 40) {
            chaseStateCheckTimer = 0;
            evaluateChaseState(level, target);
        }

        switch (chaseState) {
            case MOVING_TO_ELEVATOR -> tickMovingToElevator(level, target);
            case WAITING_ON_ELEVATOR -> tickWaitingOnElevator(target);
            case DIRECT_MOVE -> tickDirectMove(target);
            default -> evaluateChaseState(level, target);
        }
    }

    private void resetChasePath(LivingEntity target) {
        chaseTargetId = target.getUUID();
        targetElevatorPos = null;
        elevatorWaitTimer = 0;
        chaseStateCheckTimer = 0;
        chaseStuckTimer = 0;
        chaseState = ChaseState.FINDING_PATH;
    }

    private void evaluateChaseState(ServerLevel level, LivingEntity target) {
        double yDiff = target.getY() - this.getY();
        if (Math.abs(yDiff) <= 5.0D) {
            chaseState = ChaseState.DIRECT_MOVE;
            targetElevatorPos = null;
            navigateDirectlyToTarget(target);
            return;
        }

        BlockPos elevator = findBestElevator(level, target, yDiff);
        if (elevator == null) {
            chaseState = ChaseState.DIRECT_MOVE;
            targetElevatorPos = null;
            navigateDirectlyToTarget(target);
            return;
        }

        targetElevatorPos = elevator;
        elevatorWaitTimer = 0;
        chaseStuckTimer = 0;
        chaseState = ChaseState.MOVING_TO_ELEVATOR;
        navigateToElevator(elevator);
    }

    private BlockPos findBestElevator(ServerLevel level, LivingEntity target, double yDiffToTarget) {
        BlockPos redHoodPos = this.blockPosition();
        boolean needGoUp = yDiffToTarget > 0;
        int targetY = target.blockPosition().getY();
        int currentY = redHoodPos.getY();
        BlockPos bestElevator = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockEntity blockEntity : EntityUtil.findBlockEntities(level, redHoodPos, 30)) {
            if (!(blockEntity instanceof ElevatorBlockEntity elevator)) continue;
            BlockPos elevatorPos = blockEntity.getBlockPos();
            if (needGoUp != elevator.isTeleportUp()) {
                continue;
            }
            BlockPos destinationElevator = elevator.findDestinationElevator(level);
            if (destinationElevator == null) continue;
            int destY = destinationElevator.getY() + 1;

            int currentDiff = Math.abs(targetY - currentY);
            int diffAfterRide = Math.abs(targetY - destY);
            if (diffAfterRide >= currentDiff) continue;

            double score = Math.sqrt(redHoodPos.distSqr(elevatorPos)) + diffAfterRide * 2.0D;
            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevatorPos;
            }
        }

        return bestElevator;
    }

    private void navigateToElevator(BlockPos elevatorPos) {
        Path path = this.getNavigation().createPath(
                elevatorPos.getX() + 0.5D,
                elevatorPos.getY() + 1.0D,
                elevatorPos.getZ() + 0.5D,
                1
        );
        if (path != null && path.canReach()) {
            this.getNavigation().moveTo(path, CHASE_SPEED_MODIFIER);
        } else {
            this.getNavigation().moveTo(
                    elevatorPos.getX() + 0.5D,
                    elevatorPos.getY() + 1.0D,
                    elevatorPos.getZ() + 0.5D,
                    CHASE_SPEED_MODIFIER
            );
        }
    }

    private void tickMovingToElevator(ServerLevel level, LivingEntity target) {
        if (targetElevatorPos == null) {
            evaluateChaseState(level, target);
            return;
        }

        elevatorWaitTimer++;
        double distToElevator = this.distanceToSqr(
                targetElevatorPos.getX() + 0.5D,
                targetElevatorPos.getY() + 1.0D,
                targetElevatorPos.getZ() + 0.5D
        );
        if (distToElevator <= 2.5D) {
            this.getNavigation().stop();
            if (level.getBlockEntity(targetElevatorPos) instanceof ElevatorBlockEntity elevator) {
                elevator.onStep(this);
            }
            elevatorWaitTimer = 0;
            chaseStuckTimer = 0;
            chaseState = ChaseState.WAITING_ON_ELEVATOR;
            return;
        }

        if (elevatorWaitTimer > 160) {
            evaluateChaseState(level, target);
            return;
        }

        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            chaseStuckTimer++;
            if (chaseStuckTimer > 40) {
                chaseStuckTimer = 0;
                Vec3 dir = new Vec3(
                        targetElevatorPos.getX() + 0.5D - this.getX(),
                        targetElevatorPos.getY() + 1.0D - this.getY(),
                        targetElevatorPos.getZ() + 0.5D - this.getZ()
                ).normalize();
                this.setDeltaMovement(dir.scale(CHASE_SPEED_MODIFIER * 0.4D));
                navigateToElevator(targetElevatorPos);
            }
        } else {
            chaseStuckTimer = 0;
        }
    }

    private void tickWaitingOnElevator(LivingEntity target) {
        elevatorWaitTimer++;
        this.getNavigation().stop();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);

        if (Math.abs(target.getY() - this.getY()) <= 5.0D) {
            chaseState = ChaseState.DIRECT_MOVE;
            elevatorWaitTimer = 0;
            targetElevatorPos = null;
            navigateDirectlyToTarget(target);
            return;
        }

        if (elevatorWaitTimer > 100) {
            if (targetElevatorPos != null
                    && this.level().getBlockEntity(targetElevatorPos) instanceof ElevatorBlockEntity elevator) {
                elevator.onStep(this);
            }
            elevatorWaitTimer = 60;
        }
    }

    private void tickDirectMove(LivingEntity target) {
        if (Math.abs(target.getY() - this.getY()) > 5.0D) {
            chaseState = ChaseState.FINDING_PATH;
            return;
        }

        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            chaseStuckTimer++;
            if (chaseStuckTimer > 30) {
                chaseStuckTimer = 0;
                Vec3 dir = target.position().subtract(this.position()).normalize();
                this.setDeltaMovement(dir.scale(CHASE_SPEED_MODIFIER * 0.3D));
                navigateDirectlyToTarget(target);
            }
        } else {
            chaseStuckTimer = 0;
        }
    }

    private void navigateDirectlyToTarget(LivingEntity target) {
        Path path = this.getNavigation().createPath(target.blockPosition(), 1);
        if (path != null && path.canReach()) {
            this.getNavigation().moveTo(path, CHASE_SPEED_MODIFIER);
        } else {
            this.getNavigation().moveTo(target, CHASE_SPEED_MODIFIER);
        }
    }

    private void chooseMeleeAttack(ServerLevel level, LivingEntity target) {
        float roll = this.random.nextFloat();
        int type;
        if (roll < 0.30f) type = 0;        // 劈砍
        else if (roll < 0.60f) type = 1;   // 火铳
        else if (roll < 0.80f) type = 2;   // 连续劈砍
        else type = 3;                     // 掷刀
        beginAction(level, type, target);
    }

    private void beginAction(ServerLevel level, int type, LivingEntity target) {
        actionType = type;
        actionTarget = target;
        faceTarget(target);
        attackCooldown = 30;
        switch (type) {
            case 0 -> { // 劈砍:2x3范围 9-12红伤
                setAnimation("attack1");
                actionTimer = 14;
                actionHits = 1;
                level.playSound(null, blockPosition(), ModSounds.REDHAT_KNIFE.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
            }
            case 1 -> { // 火铳:停止移动,前方15格(出逃时贯穿20格) 15-18红伤
                setAnimation("attack2");
                actionTimer = 16;
                actionHits = 1;
                level.playSound(null, blockPosition(), ModSounds.REDHAT_GUN.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
            }
            case 2 -> { // 连续劈砍两次
                setAnimation("skill1");
                actionTimer = 24;
                actionHits = 2;
                level.playSound(null, blockPosition(), ModSounds.REDHAT_KNIFE.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
            }
            case 3 -> { // 掷刀:黑色粒子直线飞行 7-13红伤
                setAnimation("skill2");
                actionTimer = 16;
                actionHits = 1;
                level.playSound(null, blockPosition(), ModSounds.REDHAT_THROW.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
            }
            case 4 -> { // 走射:15秒走射,移速25%,每段2次15-18红伤
                setAnimation("move2");
                actionTimer = 60;
                actionHits = 6;
                level.playSound(null, blockPosition(), ModSounds.REDHAT_GUN.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
            }
        }
    }

    private void tickAction(ServerLevel level) {
        LivingEntity facingTarget = getActionFacingTarget(level);
        if (facingTarget != null && actionType >= 0 && actionType <= 3) {
            faceTarget(facingTarget);
        }
        switch (actionType) {
            case 0 -> { // 劈砍命中帧
                if (actionTimer == 7) meleeSweep(level, (9 + random.nextInt(4)) * dmgMul());
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
            }
            case 1 -> { // 火铳命中帧
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
                if (actionTimer == 8) gunShot(level, hasEscape() && !commissionMode ? 20 : 15);
            }
            case 2 -> { // 双劈:两个命中帧
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
                if (actionTimer == 16 || actionTimer == 6) meleeSweep(level, (9 + random.nextInt(4)) * dmgMul());
            }
            case 3 -> { // 掷刀
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
                if (actionTimer == 8) throwKnife(level);
            }
            case 4 -> { // 走射:缓慢移动+周期射击
                LivingEntity t = commissionMode ? getCommissionTarget(level) : findTarget(level);
                if (t != null) {
                    actionTarget = t;
                    faceTarget(t);
                    Vec3 dir = t.position().subtract(position()).normalize()
                            .scale(this.getAttributeValue(Attributes.MOVEMENT_SPEED)
                                    * CHASE_SPEED_MODIFIER
                                    * GUN_RUN_MOVEMENT_MULTIPLIER);
                    this.setDeltaMovement(dir.x, getDeltaMovement().y, dir.z);
                }
                if (actionTimer % 10 == 0) {
                    gunShot(level, 999); // 同房间内前方所有目标(用大射程近似)
                    level.playSound(null, blockPosition(), ModSounds.REDHAT_GUN.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
                }
            }
        }
    }

    /** 劈砍:身前2x3范围 */
    private void meleeSweep(ServerLevel level, float damage) {
        Vec3 forward = Vec3.directionFromRotation(0, getYRot()).normalize();
        Vec3 center = position().add(forward.scale(1.5));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center.x - 1.5, getY() - 1, center.z - 1.5,
                        center.x + 1.5, getY() + 2, center.z + 1.5), this::isValidTarget)) {
            e.hurt(DamageHelper.getDamage(this, "red"), damage);
            markWounded(e);
        }
    }

    /** 标记被小红帽所伤的单位(流血,持续60秒;供狼的须知Ⅳ判定) */
    private void markWounded(LivingEntity e) {
        e.getPersistentData().putLong("lobotocraft_redhat_wounded",
                this.level().getGameTime() + 1200);
    }

    /** 火铳:前方直线范围,枪口白烟粒子 */
    private void gunShot(ServerLevel level, double range) {
        Vec3 forward = Vec3.directionFromRotation(0, getYRot()).normalize();
        Vec3 muzzle = position().add(forward.scale(1.0)).add(0, 1.4, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
        double maxRange = Math.min(range, 48);
        float dmg = (15 + random.nextInt(4)) * dmgMul();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(maxRange), this::isValidTarget)) {
            Vec3 rel = e.position().subtract(position());
            double along = rel.x * forward.x + rel.z * forward.z;
            if (along < 0 || along > maxRange) continue;
            double px = rel.x - forward.x * along, pz = rel.z - forward.z * along;
            if (Math.sqrt(px * px + pz * pz) > 1.2) continue;
            e.hurt(DamageHelper.getDamage(this, "red"), dmg);
            markWounded(e);
        }
    }

    /** 掷刀:黑色粒子直线飞到尽头,接触目标7-13红伤 */
    private void throwKnife(ServerLevel level) {
        Vec3 forward = Vec3.directionFromRotation(0, getYRot()).normalize();
        float dmg = (7 + random.nextInt(7)) * dmgMul();
        for (int i = 1; i <= 32; i++) {
            Vec3 p = position().add(forward.scale(i)).add(0, 1.2, 0);
            if (!level.getBlockState(net.minecraft.core.BlockPos.containing(p)).isAir()) break;
            level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(p.x - 0.8, p.y - 0.8, p.z - 0.8, p.x + 0.8, p.y + 0.8, p.z + 0.8),
                    this::isValidTarget)) {
                e.hurt(DamageHelper.getDamage(this, "red"), dmg);
                markWounded(e);
            }
        }
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (isNonEscapedAbnormality(e)) return false;
        // 委托状态:只攻击标记目标
        if (commissionMode) {
            return commissionTargetId != null && commissionTargetId.equals(e.getUUID());
        }
        return true;
    }

    private boolean isNonEscapedAbnormality(LivingEntity entity) {
        return entity instanceof AbstractAbnormality abnormality && !abnormality.hasEscape();
    }

    private LivingEntity findTarget(ServerLevel level) {
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(24), this::isValidTarget);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“小红帽雇佣兵”一如既往地等待着委托，等待着血的味道。");
        logs.add("“如果你看见那个长毛的杂种，一定要告诉我。等我哪天把那混蛋的脑袋砍下之后，不收你的钱也没问题。”");
        logs.add("实际上，“小红帽雇佣兵”比员工更有能力镇压异想体。");
        logs.add("大多数的时间，“小红帽雇佣兵”都在那里磨她的斧头。");
        logs.add("“小红帽雇佣兵”想找个能够练习射击的场地，但被员工们坚定地驳回了。");
        logs.add("当“小红帽雇佣兵”加入战斗时，你能从那扬起的斗篷下，看到她的皮肤上布满了大大小小的伤疤。");
        logs.add("许多员工都很好奇“小红帽雇佣兵”的伤疤是从何而来的，可是没人够胆去问她。");
        logs.add("当她找到那匹狼后，一切都将结束。");
        logs.add("这场斗争已经持续了很长时间，她的愤恨随着时间的流逝一点点加深着。");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityRedHoodMercenary> event) {
        String anim = getAnimation();
        switch (anim) {
            case "attack1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack1")); }
            case "attack2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack2")); }
            case "skill1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skill1")); }
            case "skill2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skill2")); }
            case "move2" -> { return event.setAndContinue(RawAnimation.begin().thenLoop("move2")); }
            case "die" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("die")); }
        }
        if (hasEscape() && event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        if (hasEscape()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle2"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle1"));
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityRedHoodMercenary((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CommissionMode", commissionMode);
        tag.putBoolean("WolfMode", wolfMode);
        tag.putBoolean("RageMode", rageMode);
        if (commissionTargetId != null) tag.putUUID("CommissionTarget", commissionTargetId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        commissionMode = tag.getBoolean("CommissionMode");
        wolfMode = tag.getBoolean("WolfMode");
        rageMode = tag.getBoolean("RageMode");
        if (tag.hasUUID("CommissionTarget")) commissionTargetId = tag.getUUID("CommissionTarget");
        syncMovementSpeed();
    }
}
