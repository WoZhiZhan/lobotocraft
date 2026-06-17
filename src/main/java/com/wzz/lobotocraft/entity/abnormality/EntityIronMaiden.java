package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.util.EntityUtil;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.Random;
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

    // 正在使用的员工UUID
    private UUID usingPlayerUUID = null;

    // 被使用的牺牲目标UUID（村民或文职）
    private UUID targetVillagerUUID = null;

    // 使用计时器（tick）
    private int usageTimer = 0;

    // 伤害计时器
    private int damageTimer = 0;

    // 当前伤害间隔（tick）初始20tick = 1秒
    private float currentDamageInterval = 20.0f;

    // 能源产出计时器
    private int energyTimer = 0;

    // 当前能源间隔（初始5秒）
    private int currentEnergyInterval = 100;

    // 当前能源产出
    private int currentEnergyOutput = 1;

    // 总能源产出（用于统计）
    private int totalEnergyProduced = 0;

    // 数据同步器
    private static final EntityDataAccessor<String> DATA_ANIMATION =
            SynchedEntityData.defineId(EntityIronMaiden.class, EntityDataSerializers.STRING);

    // 动画状态（通过数据同步器访问）
    private String currentAnimation = "empty";

    // 动画阶段：0=空闲, 1=闭合, 2=造成伤害中, 3=打开
    private int animationPhase = 0;
    private int animationTimer = 0;

    // 上一次发送警告的时间（用于防止刷屏）
    private int lastWarningTick = 0;

    // 随机数生成器
    private final Random random = new Random();

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
                EntityIronMaiden::isSacrificeCandidate);

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
        this.damageTimer = 0;
        this.energyTimer = 0;
        this.currentDamageInterval = 20.0f; // 初始1秒1次
        this.currentEnergyInterval = 100; // 5秒
        this.currentEnergyOutput = 1;
        this.lastWarningTick = 0;
        this.animationPhase = 1; // 闭合动画
        this.animationTimer = 0;
        setCurrentAnimation("closing"); // 闭合动画

        // 如果有牺牲目标，传送到机器内部并完全控制
        if (sacrifice != null) {
            sacrifice.teleportTo(this.getX(), this.getY() + 1, this.getZ());
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

            // 每20秒更新一次伤害参数
            if (usageTimer % 400 == 0) {
                updateDamageParameters();

                // 发送状态更新消息
                int seconds = usageTimer / 20;
                player.sendSystemMessage(Component.literal(
                        String.format("§6机器运行时间：%d秒 | 伤害间隔：%.2f秒",
                                seconds, currentDamageInterval / 20.0f)
                ));
            }

            // 每秒发送一次状态更新到客户端UI
            if (usageTimer % 20 == 0) {
                sendStatusUpdate(player);
            }

            // 造成伤害（在阶段2：造成伤害中）
            if (animationPhase == 2) {
                damageTimer++;
                if (damageTimer >= (int)currentDamageInterval) {
                    damageTimer = 0;

                    // 随机1-3点伤害
                    float damage = 1.0f + random.nextFloat() * 2.0f; // 1.0 到 3.0

                    // 优先选择10格范围内的村民或文职造成伤害
                    BlockPos pos = this.blockPosition();
                    AABB damageBox = new AABB(pos).inflate(10.0); // 10格范围
                    List<LivingEntity> nearbySacrifices = this.level().getEntitiesOfClass(LivingEntity.class,
                            damageBox, EntityIronMaiden::isSacrificeCandidate);

                    if (!nearbySacrifices.isEmpty()) {
                        // 有牺牲目标在范围内，随机选择一个造成伤害
                        LivingEntity targetSacrifice = nearbySacrifices.get(random.nextInt(nearbySacrifices.size()));
                        if (currentDamageInterval == 1.0f)
                            EntityUtil.clearHurtTime(targetSacrifice);
                        if (targetSacrifice instanceof EntityClerk clerk && damage >= clerk.getHealth()) {
                            EntityClerk.markNoTombstone(clerk);
                        }
                        targetSacrifice.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
                    } else {
                        if (currentDamageInterval == 1.0f)
                            EntityUtil.clearHurtTime(player);
                        player.hurt(
                                DamageHelper.getDamage(this, "lobotocraft:red"),
                                damage
                        );
                        if (usageTimer - lastWarningTick >= 600) {
                            lastWarningTick = usageTimer;
                            player.sendSystemMessage(Component.literal(
                                    String.format("§c机器正在对你造成红色伤害！(%.1f点/次)", damage)
                            ));
                        }
                    }

                    // 播放伤害音效
                    playSound("damage");
                }
            }

            // 产出能源
            energyTimer++;
            if (energyTimer >= currentEnergyInterval) {
                energyTimer = 0;
                produceEnergy(player);
            }
        }
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
                        "§a机器运转结束，共产出 " + totalEnergyProduced + " 个PE-BOX"
                ));
            }
            usingPlayerUUID = null;
        }
    }

    /**
     * 更新伤害参数
     * 每20秒调用一次，伤害间隔减少10tick（0.5秒）
     */
    private void updateDamageParameters() {
        // 伤害间隔逐渐缩短，最低1 tick（1秒20次）
        if (currentDamageInterval > 1.0f) {
            currentDamageInterval = Math.max(1.0f, currentDamageInterval - 10.0f);
        }
        // 能源间隔逐渐缩短（最低40 tick = 2秒）
        if (currentEnergyInterval > 40) {
            currentEnergyInterval = Math.max(40, currentEnergyInterval - 10);
        }

        // 能源产出增加
        currentEnergyOutput += 1;
    }

    /**
     * 产出能源
     */
    private void produceEnergy(ServerPlayer player) {
        // 给予PE-BOX物品
        ItemStack peBox = com.wzz.lobotocraft.item.PEBoxItem.create(
                this.getAbnormalityCode(),
                this.getAbnormalityName()
        );
        peBox.setCount(currentEnergyOutput);

        if (!player.addItem(peBox)) {
            player.drop(peBox, false);
        }

        // 增加总能源产出计数
        totalEnergyProduced += currentEnergyOutput;

        // 每次产出能源时发送状态更新
        sendStatusUpdate(player);
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
                        0, // 不显示固定伤害值，因为是随机的
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
    public void stopUsing(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§c取消失败..."));
    }

    /**
     * 检查员工是否恐慌
     */
    private boolean isPlayerPanicking(ServerPlayer player) {
        // 根据精神值系统判断恐慌
        return player.getCapability(com.wzz.lobotocraft.capability.MentalValueProvider.MENTAL_VALUE)
                .map(mental -> mental.getMentalValue() <= 0)
                .orElse(false);
    }

    /**
     * 重置使用状态（在结束动画播放完后调用）
     */
    private void resetUsage() {
        usingPlayerUUID = null;
        targetVillagerUUID = null;
        usageTimer = 0;
        damageTimer = 0;
        energyTimer = 0;
        currentDamageInterval = 20.0f;
        currentEnergyInterval = 100;
        currentEnergyOutput = 1;
        totalEnergyProduced = 0;
        setCurrentAnimation("empty");
        animationPhase = 0;
        animationTimer = 0;
        lastWarningTick = 0;
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
                "§c机器会对10格范围内的村民或使用者造成递增的红色伤害",
                "§c优先攻击范围内的村民，如果没有村民则伤害使用者",
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
        tag.putInt("UsageTimer", usageTimer);
        tag.putInt("DamageTimer", damageTimer);
        tag.putInt("EnergyTimer", energyTimer);
        tag.putFloat("CurrentDamageInterval", currentDamageInterval);
        tag.putInt("CurrentEnergyInterval", currentEnergyInterval);
        tag.putInt("CurrentEnergyOutput", currentEnergyOutput);
        tag.putInt("TotalEnergyProduced", totalEnergyProduced);
        tag.putString("CurrentAnimation", currentAnimation);
        tag.putInt("AnimationPhase", animationPhase);
        tag.putInt("AnimationTimer", animationTimer);
        tag.putInt("LastWarningTick", lastWarningTick);
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
        usageTimer = tag.getInt("UsageTimer");
        damageTimer = tag.getInt("DamageTimer");
        energyTimer = tag.getInt("EnergyTimer");
        currentDamageInterval = tag.getFloat("CurrentDamageInterval");
        currentEnergyInterval = tag.getInt("CurrentEnergyInterval");
        currentEnergyOutput = tag.getInt("CurrentEnergyOutput");
        totalEnergyProduced = tag.getInt("TotalEnergyProduced");
        currentAnimation = tag.getString("CurrentAnimation");
        animationPhase = tag.getInt("AnimationPhase");
        animationTimer = tag.getInt("AnimationTimer");
        lastWarningTick = tag.getInt("LastWarningTick");
    }
}
