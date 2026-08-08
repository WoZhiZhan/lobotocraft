package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.UUID;

/**
 * 我们可以改变一切 (T-09-85)
 * 持续使用型工具异想体
 * 特殊能力：
 * - 需要村民才能使用
 * - 对玩家造成1-3点随机红色伤害，频率递增
 * - 创造模式和红色抗性≤0的玩家无法使用
 */
public class EntityIronMaiden extends AbstractAbnormality {
    public static final String SACRIFICE_LOCKED = "lobotocraft_iron_maiden_locked";
    public static final String SACRIFICE_MACHINE = "lobotocraft_iron_maiden_machine";
    public static final String SACRIFICE_LOCK_TIME = "lobotocraft_iron_maiden_lock_time";
    public static final String SACRIFICE_OLD_NO_AI = "lobotocraft_iron_maiden_old_no_ai";
    public static final String SACRIFICE_OLD_NO_GRAVITY = "lobotocraft_iron_maiden_old_no_gravity";
    public static final String SACRIFICE_OLD_NO_PHYSICS = "lobotocraft_iron_maiden_old_no_physics";

    // 正在使用的员工UUID
    private UUID usingPlayerUUID = null;

    // 被使用的牺牲目标UUID（村民或文职）
    private UUID targetVillagerUUID = null;
    private boolean sacrificeStateCaptured;
    private boolean sacrificeOriginalNoAi;
    private boolean sacrificeOriginalNoGravity;
    private boolean sacrificeOriginalNoPhysics;

    // 使用计时器（tick）
    private int usageTimer = 0;

    // Original generation pulse interval, initially 2 seconds.
    private float currentDamageInterval = 40.0f;

    // 能源产出计时器
    private int energyTimer = 0;

    private int currentEnergyInterval = 40;

    // Whole facility energy accepted by the most recent pulse.
    private float currentEnergyOutput;

    // 总能源产出（用于统计）
    private float totalEnergyProduced = 0.0F;

    // 数据同步器
    private static final EntityDataAccessor<String> DATA_ANIMATION =
            SynchedEntityData.defineId(EntityIronMaiden.class, EntityDataSerializers.STRING);

    // 动画状态（通过数据同步器访问）
    private String currentAnimation = "empty";

    // 动画阶段：0=空闲, 1=闭合, 2=造成伤害中, 3=打开
    private int animationPhase = 0;
    private int animationTimer = 0;

    // 随机数生成器
    /** Completed original-generation pulses. */
    private int generationPulseCount;

    public EntityIronMaiden(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIMATION, "empty");
    }

    /**
     * 设置当前动画状态并同步到客户端
     */
    private void setCurrentAnimation(String animation) {
        this.currentAnimation = animation;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ANIMATION, animation);
        }
    }

    /**
     * 获取当前动画状态（优先从同步数据获取）
     */
    private String getCurrentAnimation() {
        if (this.level().isClientSide) {
            return this.entityData.get(DATA_ANIMATION);
        }
        return this.currentAnimation;
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "T-09-85";
        this.abnormalityName = "我们可以改变一切";
        this.riskLevel = RiskLevel.ZAYIN;
        this.damageType = "RED";
        this.maxPEOutput = 20; // 基础能源产出

        // 工具类型不使用传统工作偏好，设为0
        float[] basePreferences = {0f, 0f, 0f, 0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    // ==================== 工具类型标识 ====================

    @Override
    public boolean isToolType() {
        return true;
    }

    @Override
    public boolean isContinuousUseTool() {
        return true;
    }

    @Override
    public int getBasicInfoCost() {
        return 0; // 工具类型使用时间解锁，不需要PE-BOX
    }

    @Override
    public int getWorkPreferencesCost() {
        return 0;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 0;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 0;
    }

    @Override
    public String getAbnormalityCode() {
        return "T-09-85";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {

    }

    @Override
    public String name() {
        return "iron_maiden";
    }

    // ==================== 工具使用逻辑 ====================

    /**
     * 检查玩家是否可以使用此工具
     */
    public boolean canPlayerUse(ServerPlayer player) {
        // 创造模式玩家无法使用
        if (player.isCreative()) {
            player.sendSystemMessage(Component.literal("§c创造模式下无法使用此工具！"));
            return false;
        }

        // 检查红色伤害抗性
        AttributeInstance redResistance = player.getAttribute(ModAttributes.RED_DAMAGE_RESISTANCE.get());
        if (redResistance != null && redResistance.getValue() <= 0) {
            player.sendSystemMessage(Component.literal("§c你的红色伤害抗性过低，无法使用此工具！"));
            return false;
        }

        return true;
    }

    @Override
    public void onWorkTick(ServerPlayer player, WorkManager.WorkSession session, WorkType workType) {
        player.jumping = false;
        player.setDeltaMovement(0,0,0);
    }

    /**
     * 找到并锁定一个牺牲目标
     */
    private LivingEntity findAndLockSacrifice() {
        BlockPos pos = this.blockPosition();
        AABB searchBox = new AABB(pos).inflate(5.0);
        List<LivingEntity> sacrifices = this.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                EntityIronMaiden::isAvailableSacrificeCandidate);

        if (!sacrifices.isEmpty()) {
            LivingEntity sacrifice = sacrifices.get(0);
            targetVillagerUUID = sacrifice.getUUID();
            return sacrifice;
        }
        return null;
    }

    private static boolean isSacrificeCandidate(LivingEntity entity) {
        return entity instanceof Villager || entity instanceof EntityClerk;
    }

    private static boolean isAvailableSacrificeCandidate(LivingEntity entity) {
        return isSacrificeCandidate(entity)
                && !entity.getPersistentData().getBoolean(SACRIFICE_LOCKED);
    }

    private void lockSacrifice(LivingEntity sacrifice) {
        sacrificeStateCaptured = true;
        sacrificeOriginalNoGravity = sacrifice.isNoGravity();
        sacrificeOriginalNoPhysics = sacrifice.noPhysics;
        sacrificeOriginalNoAi = sacrifice instanceof Mob mob && mob.isNoAi();

        CompoundTag data = sacrifice.getPersistentData();
        data.putBoolean(SACRIFICE_LOCKED, true);
        data.putUUID(SACRIFICE_MACHINE, this.getUUID());
        data.putLong(SACRIFICE_LOCK_TIME, this.level().getGameTime());
        data.putBoolean(SACRIFICE_OLD_NO_AI, sacrificeOriginalNoAi);
        data.putBoolean(SACRIFICE_OLD_NO_GRAVITY, sacrificeOriginalNoGravity);
        data.putBoolean(SACRIFICE_OLD_NO_PHYSICS, sacrificeOriginalNoPhysics);

        sacrifice.setNoGravity(true);
        sacrifice.noPhysics = true;
        if (sacrifice instanceof Mob mob) mob.setNoAi(true);
    }

    public static void restoreSacrificeState(LivingEntity sacrifice) {
        CompoundTag data = sacrifice.getPersistentData();
        if (!data.getBoolean(SACRIFICE_LOCKED)) return;
        sacrifice.setNoGravity(data.getBoolean(SACRIFICE_OLD_NO_GRAVITY));
        sacrifice.noPhysics = data.getBoolean(SACRIFICE_OLD_NO_PHYSICS);
        if (sacrifice instanceof Mob mob) mob.setNoAi(data.getBoolean(SACRIFICE_OLD_NO_AI));
        clearSacrificeLockData(data);
    }

    private static void clearSacrificeLockData(CompoundTag data) {
        data.remove(SACRIFICE_LOCKED);
        data.remove(SACRIFICE_MACHINE);
        data.remove(SACRIFICE_LOCK_TIME);
        data.remove(SACRIFICE_OLD_NO_AI);
        data.remove(SACRIFICE_OLD_NO_GRAVITY);
        data.remove(SACRIFICE_OLD_NO_PHYSICS);
    }

    public boolean holdsSacrifice(UUID targetUuid) {
        return targetVillagerUUID != null && targetVillagerUUID.equals(targetUuid)
                && usingPlayerUUID != null && animationPhase > 0 && animationPhase < 3;
    }

    private void releaseSacrifice() {
        if (targetVillagerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            Entity target = serverLevel.getEntity(targetVillagerUUID);
            if (target instanceof LivingEntity sacrifice) {
                CompoundTag data = sacrifice.getPersistentData();
                boolean ownedLock = data.getBoolean(SACRIFICE_LOCKED)
                        && data.hasUUID(SACRIFICE_MACHINE)
                        && this.getUUID().equals(data.getUUID(SACRIFICE_MACHINE));
                if (ownedLock) {
                    restoreSacrificeState(sacrifice);
                } else if (!data.getBoolean(SACRIFICE_LOCKED) && sacrificeStateCaptured) {
                    sacrifice.setNoGravity(sacrificeOriginalNoGravity);
                    sacrifice.noPhysics = sacrificeOriginalNoPhysics;
                    if (sacrifice instanceof Mob mob) mob.setNoAi(sacrificeOriginalNoAi);
                }
            }
        }
        targetVillagerUUID = null;
        sacrificeStateCaptured = false;
        sacrificeOriginalNoAi = false;
        sacrificeOriginalNoGravity = false;
        sacrificeOriginalNoPhysics = false;
    }

    /**
     * 开始使用工具
     */
    public boolean startUsing(ServerPlayer player) {
        if (usingPlayerUUID != null) {
            player.sendSystemMessage(Component.literal("§c已有员工正在使用此工具！"));
            return false;
        }

        // 检查是否可以使用
        if (!canPlayerUse(player)) {
            return false;
        }

        // 尝试找到并锁定牺牲目标（可选）
        LivingEntity sacrifice = findAndLockSacrifice();

        this.usingPlayerUUID = player.getUUID();
        this.usageTimer = 0;
        this.energyTimer = 0;
        this.generationPulseCount = 0;
        this.currentEnergyInterval = IronMaidenGenerationPolicy.intervalTicks(0);
        this.currentDamageInterval = this.currentEnergyInterval;
        this.currentEnergyOutput = 0.0F;
        this.animationPhase = 1; // 闭合动画
        this.animationTimer = 0;
        setCurrentAnimation("closing"); // 闭合动画

        // 如果有牺牲目标，传送到机器内部并完全控制
        if (sacrifice != null) {
            sacrifice.teleportTo(this.getX(), this.getY() + 1, this.getZ());
            lockSacrifice(sacrifice);
            sacrifice.setNoGravity(true);
            sacrifice.setDeltaMovement(0, 0, 0);
            sacrifice.noPhysics = true; // 设置为无碰撞，防止被挤出来
            if (sacrifice instanceof Mob mob) {
                mob.setNoAi(true);
            }

            player.sendSystemMessage(Component.literal(
                    "§e牺牲目标被放入了'我们可以改变一切'..."
            ));
        } else {
            // 没有村民，玩家自己进去
            // 传送玩家到机器内部
            player.teleportTo(this.getX(), this.getY() + 1, this.getZ());
            player.setDeltaMovement(0, 0, 0);

            player.sendSystemMessage(Component.literal(
                    "§e你进入了'我们可以改变一切'..."
            ));
        }

        // 播放闭合音效
        playSound("closing");

        player.sendSystemMessage(Component.literal(
                "§4§l警告：机器开始运转！"
        ));
        player.sendSystemMessage(Component.literal(
                "§c现在，一切都会好起来哒！"
        ));

        return true;
    }

    /**
     * 播放音效
     */
    private void playSound(String soundType) {
        SoundEvent sound = null;
        switch (soundType) {
            case "closing" -> sound = com.wzz.lobotocraft.init.ModSounds.IRON_MAIDEN_CLOSING.get();
            case "damage" -> sound = com.wzz.lobotocraft.init.ModSounds.IRON_MAIDEN_DAMAGE.get();
            case "opening" -> sound = com.wzz.lobotocraft.init.ModSounds.IRON_MAIDEN_OPENING.get();
            case "end_music" -> sound = com.wzz.lobotocraft.init.ModSounds.IRON_MAIDEN_END_MUSIC.get();
        }

        if (sound != null) {
            this.level().playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    sound,
                    SoundSource.HOSTILE,
                    1.0f,
                    1.0f
            );
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 处理动画阶段
        if (animationPhase > 0 && !this.level().isClientSide) {
            animationTimer++;

            // 阶段1：闭合动画 → 造成伤害中
            if (animationPhase == 1 && animationTimer >= 40) { // 2秒后
                animationPhase = 2;
                animationTimer = 0;
                setCurrentAnimation("damaging"); // 造成伤害动画
            }
            // 阶段3：打开动画 → 重置
            else if (animationPhase == 3 && animationTimer >= 40) { // 2秒后
                resetUsage();
            }
        }

        if (usingPlayerUUID != null && !this.level().isClientSide) {
            ServerPlayer player = ((ServerLevel)this.level()).getServer()
                    .getPlayerList().getPlayer(usingPlayerUUID);

            if (player == null || !player.isAlive()) {
                // 员工离线或死亡，触发结束动画
                if (player != null && !player.isAlive()) {
                    player.sendSystemMessage(Component.literal(
                            "§c你已死亡，机器停止运转..."
                    ));
                }
                triggerEndAnimation();
                return;
            }

            // 检查牺牲目标是否还存在
            LivingEntity sacrifice = null;
            if (targetVillagerUUID != null) {
                Entity targetEntity = ((ServerLevel)this.level()).getEntity(targetVillagerUUID);
                if (targetEntity instanceof LivingEntity livingEntity) {
                    sacrifice = livingEntity;
                }
                if (sacrifice == null || !sacrifice.isAlive()) {
                    // 牺牲目标死亡，触发结束动画
                    player.sendSystemMessage(Component.literal(
                            "§c牺牲目标已经死亡，机器停止运转..."
                    ));
                    triggerEndAnimation();
                    return;
                }

                // 持续控制牺牲目标位置，防止被推动或移动
                sacrifice.teleportTo(this.getX(), this.getY() + 1, this.getZ());
                sacrifice.setDeltaMovement(0, 0, 0);
                sacrifice.setYRot(0);
                sacrifice.setXRot(0);
            } else {
                // 没有村民，控制玩家位置，防止玩家移动出机器
                if (player.distanceToSqr(this.getX(), this.getY() + 1, this.getZ()) > 1.5D) {
                    // 如果玩家离机器太远，传送回来
                    player.teleportTo(this.getX(), this.getY() + 1, this.getZ());
                    player.setDeltaMovement(0, 0, 0);
                }
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 1));
            }

            // 增加使用时间
            usageTimer++;

            // 检查玩家恐慌状态
            if (isPlayerPanicking(player)) {
                // 恐慌状态下无法使用工具，但不会中断
                if (usageTimer % 100 == 0) {
                    player.sendSystemMessage(Component.literal(
                            "§c§l你陷入恐慌状态，但机器仍在继续运转..."
                    ));
                }
            }

            // 每秒发送一次状态更新到客户端UI
            if (usageTimer % 20 == 0) {
                sendStatusUpdate(player);
            }

            // The original script starts its timer on entry. The first pulse
            // therefore coincides with the end of this two-second closing phase.
            energyTimer++;
            if (animationPhase == 2 && energyTimer >= currentEnergyInterval) {
                energyTimer = 0;
                runOriginalGenerationPulse(player, sacrifice);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            releaseSacrifice();
            usingPlayerUUID = null;
        }
        super.remove(reason);
    }

    /**
     * 触发结束动画
     */
    private void triggerEndAnimation() {
        if (animationPhase == 3) return; // 已经在结束阶段

        animationPhase = 3;
        animationTimer = 0;
        setCurrentAnimation("opening"); // 打开动画

        // 播放打开音效和结束音乐
        playSound("opening");
        playSound("end_music");

        // 清理牺牲目标引用并恢复所有状态
        releaseSacrifice();
        if (targetVillagerUUID != null) {
            Entity targetEntity = ((ServerLevel)this.level()).getEntity(targetVillagerUUID);
            if (targetEntity instanceof LivingEntity sacrifice && sacrifice.isAlive()) {
                sacrifice.setNoGravity(false);
                sacrifice.noPhysics = false; // 恢复碰撞
                if (sacrifice instanceof Mob mob) {
                    mob.setNoAi(false);
                }
            }
            targetVillagerUUID = null;
        }

        // 清理使用中的玩家引用
        if (usingPlayerUUID != null) {
            ServerPlayer player = ((ServerLevel)this.level()).getServer()
                    .getPlayerList().getPlayer(usingPlayerUUID);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        String.format("§a机器运转结束，共向当日能源池提供 %.2f 点能源",
                                totalEnergyProduced)
                ));
            }
            usingPlayerUUID = null;
        }
    }

    private void runOriginalGenerationPulse(ServerPlayer operator,
                                            @Nullable LivingEntity lockedSacrifice) {
        IronMaidenGenerationPolicy.Pulse pulse =
                IronMaidenGenerationPolicy.nextPulse(generationPulseCount);
        LivingEntity victim = lockedSacrifice != null ? lockedSacrifice : operator;
        float damage = (float) pulse.damage();
        if (damage > 0.0F && victim.isAlive()) {
            EntityUtil.clearHurtTime(victim);
            if (victim instanceof EntityClerk clerk && damage >= clerk.getHealth()) {
                EntityClerk.markNoTombstone(clerk);
            }
            victim.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
        }

        generationPulseCount = pulse.completedPulses();
        currentEnergyInterval = pulse.nextIntervalTicks();
        currentDamageInterval = currentEnergyInterval;
        currentEnergyOutput = (float) pulse.generatedEnergy();
        if (this.level() instanceof ServerLevel serverLevel) {
            double credited = FacilityManager.creditToolEnergy(
                    serverLevel, pulse.generatedEnergy());
            totalEnergyProduced += (float) credited;
        }
        playSound("damage");
        sendStatusUpdate(operator);
    }

    /**
     * 发送状态更新到客户端
     */
    private void sendStatusUpdate(ServerPlayer player) {
        com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(
                player,
                new com.wzz.lobotocraft.network.packet.ToolUsageUpdatePacket(
                        this.getId(),
                        usageTimer / 20,
                        (float) IronMaidenGenerationPolicy.damageForPulse(generationPulseCount),
                        currentDamageInterval / 20.0f,
                        totalEnergyProduced,
                        currentEnergyOutput,
                        currentEnergyInterval / 20.0f,
                        player.getHealth(),
                        player.getMaxHealth()
                )
        );
    }

    @Override
    public boolean isUsing(ServerPlayer player) {
        return player != null && player.getUUID().equals(usingPlayerUUID);
    }

    @Override
    public void stopUsing(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§c取消失败..."));
    }

    /**
     * 检查员工是否恐慌
     */
    private boolean isPlayerPanicking(ServerPlayer player) {
        return MentalValueUtil.isPanic(player);
    }

    /**
     * 重置使用状态（在结束动画播放完后调用）
     */
    private void resetUsage() {
        releaseSacrifice();
        usingPlayerUUID = null;
        targetVillagerUUID = null;
        usageTimer = 0;
        energyTimer = 0;
        generationPulseCount = 0;
        currentEnergyInterval = IronMaidenGenerationPolicy.intervalTicks(0);
        currentDamageInterval = currentEnergyInterval;
        currentEnergyOutput = 0.0F;
        totalEnergyProduced = 0.0F;
        setCurrentAnimation("empty");
        animationPhase = 0;
        animationTimer = 0;
    }

    // ==================== 逆卡巴拉计数器 ====================

    @Override
    public void onQliphothMeltdown() {
        // 此异想体不会突破收容
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // 工具类型不使用传统工作
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityIronMaiden> event) {
        // 从同步数据获取当前动画状态
        String anim = getCurrentAnimation();

        // 根据当前动画状态播放对应动画
        if ("closing".equals(anim)) {
            // 闭合动画 - hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("1.model.new"));
        } else if ("damaging".equals(anim)) {
            // 造成伤害动画 - loop: true
            return event.setAndContinue(RawAnimation.begin().thenLoop("2.model.new"));
        } else if ("opening".equals(anim)) {
            // 打开动画 - hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("3.model.new"));
        }

        // 空闲状态，不播放动画但保持CONTINUE
        return PlayState.CONTINUE;
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    // ==================== 危险警告系统 ====================

    @Override
    public String getToolWarningTitle() {
        return "§4§l员工使用后可能会产生未知风险，请谨慎使用";
    }

    @Override
    public String[] getToolWarningMessages() {
        return new String[]{
                "§c机器会对被关入的员工或文职造成按原版公式递增的红色伤害",
                "§c伤害与能源脉冲会持续加速，最低间隔为0.1秒",
                "§c创造模式和红色抗性≤0的玩家无法使用"
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.02D);
        builder = builder.add(Attributes.MAX_HEALTH, 200f);
        builder = builder.add(Attributes.ARMOR, 20);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 0);
        builder = builder.add(Attributes.FOLLOW_RANGE, 0);
        builder = builder.add(Attributes.FLYING_SPEED, 0.02D);
        return builder;
    }

    // ==================== NBT存储 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (usingPlayerUUID != null) {
            tag.putUUID("UsingPlayerUUID", usingPlayerUUID);
        }
        if (targetVillagerUUID != null) {
            tag.putUUID("TargetSacrificeUUID", targetVillagerUUID);
            tag.putUUID("TargetVillagerUUID", targetVillagerUUID);
        }
        tag.putBoolean("SacrificeStateCaptured", sacrificeStateCaptured);
        tag.putBoolean("SacrificeOriginalNoAi", sacrificeOriginalNoAi);
        tag.putBoolean("SacrificeOriginalNoGravity", sacrificeOriginalNoGravity);
        tag.putBoolean("SacrificeOriginalNoPhysics", sacrificeOriginalNoPhysics);
        tag.putInt("UsageTimer", usageTimer);
        tag.putInt("EnergyTimer", energyTimer);
        tag.putFloat("CurrentDamageInterval", currentDamageInterval);
        tag.putInt("CurrentEnergyInterval", currentEnergyInterval);
        tag.putFloat("CurrentEnergyOutput", currentEnergyOutput);
        tag.putFloat("TotalEnergyProduced", totalEnergyProduced);
        tag.putInt("GenerationPulseCount", generationPulseCount);
        tag.putString("CurrentAnimation", currentAnimation);
        tag.putInt("AnimationPhase", animationPhase);
        tag.putInt("AnimationTimer", animationTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("UsingPlayerUUID")) {
            usingPlayerUUID = tag.getUUID("UsingPlayerUUID");
        }
        if (tag.hasUUID("TargetSacrificeUUID")) {
            targetVillagerUUID = tag.getUUID("TargetSacrificeUUID");
        } else if (tag.hasUUID("TargetVillagerUUID")) {
            targetVillagerUUID = tag.getUUID("TargetVillagerUUID");
        }
        sacrificeStateCaptured = tag.getBoolean("SacrificeStateCaptured");
        sacrificeOriginalNoAi = tag.getBoolean("SacrificeOriginalNoAi");
        sacrificeOriginalNoGravity = tag.getBoolean("SacrificeOriginalNoGravity");
        sacrificeOriginalNoPhysics = tag.getBoolean("SacrificeOriginalNoPhysics");
        usageTimer = tag.getInt("UsageTimer");
        energyTimer = tag.getInt("EnergyTimer");
        currentDamageInterval = tag.getFloat("CurrentDamageInterval");
        currentEnergyInterval = tag.getInt("CurrentEnergyInterval");
        currentEnergyOutput = tag.getFloat("CurrentEnergyOutput");
        totalEnergyProduced = tag.getFloat("TotalEnergyProduced");
        if (tag.contains("GenerationPulseCount")) {
            generationPulseCount = Math.max(0, tag.getInt("GenerationPulseCount"));
        } else {
            generationPulseCount = 0;
            currentEnergyInterval = IronMaidenGenerationPolicy.intervalTicks(0);
            currentDamageInterval = currentEnergyInterval;
            currentEnergyOutput = 0.0F;
        }
        currentAnimation = tag.getString("CurrentAnimation");
        animationPhase = tag.getInt("AnimationPhase");
        animationTimer = tag.getInt("AnimationTimer");
    }
}
