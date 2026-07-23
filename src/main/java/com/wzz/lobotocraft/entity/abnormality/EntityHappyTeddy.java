package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
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
import java.util.UUID;

/**
 * 快乐泰迪 (T-04-06)
 * 特殊能力：同一员工连续两次沟通工作会被拥抱致死
 */
public class EntityHappyTeddy extends AbstractAbnormality {

    // 最后一次工作的员工UUID
    private UUID lastWorkerUUID = null;

    // 最后一次工作类型
    private WorkType lastWorkType = null;

    // 数据同步器
    private static final EntityDataAccessor<String> DATA_ANIMATION =
            SynchedEntityData.defineId(EntityHappyTeddy.class, EntityDataSerializers.STRING);

    // 当前动画状态（通过数据同步器访问）
    private String currentAnimation = "empty";

    // 被拥抱的员工（正在执行死亡动画）
    private UUID huggedPlayerUUID = null;
    private int hugAnimationPhase = 0; // 0=拥抱开始, 1=拥抱中, 2=处决
    private int hugAnimationTimer = 0;
    private static final double IDLE_SOUND_RANGE = 8.0D;
    private static final int IDLE_SOUND_COOLDOWN_TICKS = 20 * 20;

    public EntityHappyTeddy(EntityType<? extends TamableAnimal> entityType, Level level) {
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
        this.abnormalityCode = "T-04-06";
        this.abnormalityName = "泰迪熊"; // 默认名称，观察等级2+会变为"快乐泰迪"
        this.riskLevel = RiskLevel.HE;
        this.damageType = "WHITE";
        this.maxPEOutput = 15;

        // 工作偏好（基础成功率）
        float[] basePreferences = {0.0f, 0.4f, 0.6f, 0.4f};
        // 本能0%，洞察40%，沟通60%，压迫40%
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.03f, 0),
                new ObservationLevelBonus(0.0f, 5, true, false, false),
                new ObservationLevelBonus(0.05f, 0, false, true, false),
                new ObservationLevelBonus(0.0f, 3, false, false, true)
        };
    }

    /**
     * 修改工作成功率
     * 如果是第二次沟通，强制成功率为0（必定失败）
     */
    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {
        // 只对沟通工作进行检查
        if (workType != WorkType.ATTACHMENT) {
            return null;  // 其他工作使用默认成功率
        }

        // 检查是否是第二次沟通
        if (isSecondAttachmentWork(player, workType)) {
            return 0.0f;  // 强制成功率为0，必定判差
        }

        return null;  // 第一次沟通使用默认成功率
    }

    /**
     * 是否强制工作结果
     * 第二次沟通强制返回BAD
     */
    @Override
    public boolean shouldForceWorkResult(ServerPlayer player, WorkType workType) {
        if (workType != WorkType.ATTACHMENT) {
            return false;
        }
        return isSecondAttachmentWork(player, workType);
    }

    /**
     * 获取强制的工作结果
     * 第二次沟通强制为BAD
     */
    @Override
    public WorkResult getForcedWorkResult(ServerPlayer player, WorkType workType) {
        return WorkResult.BAD;
    }

    /**
     * 工作开始前的回调
     * 如果是第二次沟通，显示警告
     */
    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        if (workType == WorkType.ATTACHMENT && isSecondAttachmentWork(player, workType)) {
            triggerHugDeath(player);
            return false;
        }
        return true;  // 允许工作
    }

    @Override
    public boolean canEscape() {
        return false;
    }

    @Override
    public int getBasicInfoCost() {
        return 16;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 16;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 16;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 9;
    }

    @Override
    public String getAbnormalityCode() {
        return "T-04-06";
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
        return "happy_teddy";
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“快乐泰迪”看起来就像一只每个人小时候都有抱过的泰迪熊。");
        logs.add("“快乐泰迪”总是想着拥抱孩子们。");
        logs.add("“快乐泰迪”喜欢拥抱，它的记忆始于温暖的怀抱。");
        logs.add("一只泰迪熊在幸福中诞生，所以它每时每刻都一定是快乐的。");
        logs.add("“快乐泰迪”似乎沉浸在自己的思绪之中，茫然地凝视着从它的接缝中伸出来的棉花。");
        logs.add("“快乐泰迪”脖子上那条脏兮兮的缎带正在空中摆动着，然而密闭的收容单元里不可能有风。缎带上写着的名字早已褪色，一点也看不到了。");
        logs.add("“快乐泰迪”还记得那个七岁的生日派对，那时的它被包装在一个精美的大盒子里...");
        logs.add("“快乐泰迪”还记得和那个孩子一同度过的美妙假期，小主人给自己取了个名字...叫做“小熊熊”...");
        return logs;
    }

    /**
     * 检查是否为第二次沟通工作
     * @param player 当前工作的员工
     * @param workType 当前工作类型
     * @return true如果是同一员工的第二次沟通工作
     */
    public boolean isSecondAttachmentWork(ServerPlayer player, WorkType workType) {
        UUID currentUUID = player.getUUID();

        // 必须是沟通工作
        if (workType != WorkType.ATTACHMENT) {
            return false;
        }

        // 检查是否与上次工作的员工相同，并且上次也是沟通工作
        return lastWorkerUUID != null &&
                lastWorkerUUID.equals(currentUUID) &&
                lastWorkType == WorkType.ATTACHMENT;
    }

    /**
     * 记录工作的员工和工作类型
     * @param player 工作的员工
     * @param workType 工作类型
     */
    public void recordWork(ServerPlayer player, WorkType workType) {
        this.lastWorkerUUID = player.getUUID();
        this.lastWorkType = workType;
    }

    /**
     * 触发拥抱死亡机制（必杀，机制杀）
     * @param player 被拥抱的员工
     */
    public void triggerHugDeath(ServerPlayer player) {
        this.huggedPlayerUUID = player.getUUID();
        this.hugAnimationPhase = 0;
        this.hugAnimationTimer = 0;
        setCurrentAnimation("model.1"); // 开始拥抱动画

        // 发送消息
        player.sendSystemMessage(Component.literal(
                "§4§l" + player.getName().getString() + " 不由自主地走向快乐泰迪..."
        ));

        // 限制玩家移动并让玩家坐下
        player.setNoGravity(true);
        player.setDeltaMovement(0, 0, 0);
        player.setShiftKeyDown(true); // 让玩家坐下（蹲下姿势）
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
        return 40;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 30;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 2;
    }

    @Override
    public float getGiftProbability() {
        return 0.04f;
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/happy_teddy_curio.png"),
                "熊熊抱",
                "眼部",
                "happy_teddy_curio",
                "最大精神值+4",
                "沟通工作的成功率+3%"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/happy_teddy_weapon.png"),
                "熊熊抱",
                getRiskLevel(),
                "WHITE",
                "2-3",
                "0.6",
                "10格",
                getWeaponDevelopmentMaxCount(),
                "happy_teddy_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/happy_teddy_armor.png"),
                "熊熊抱",
                getRiskLevel(),
                0.8f,
                1.0f,
                1.0f,
                1.5f,
                getArmorDevelopmentMaxCount(),
                "happy_teddy_armor"
        );
    }

    @Override
    public void tick() {
        super.tick();

        // 处理拥抱死亡动画
        if (huggedPlayerUUID != null && !this.level().isClientSide) {
            ServerPlayer player = ((ServerLevel)this.level()).getServer()
                    .getPlayerList().getPlayer(huggedPlayerUUID);

            if (player != null && player.isAlive()) {
                hugAnimationTimer++;

                // 动画阶段转换
                if (hugAnimationPhase == 0 && hugAnimationTimer >= 40) {
                    // 阶段0：拥抱开始 → 拥抱中（2秒）
                    hugAnimationPhase = 1;
                    hugAnimationTimer = 0;
                    setCurrentAnimation("model.2");
                    player.sendSystemMessage(Component.literal(
                            "§4§l快乐泰迪紧紧地抱住了你的脖子！"
                    ));
                } else if (hugAnimationPhase == 1 && hugAnimationTimer >= 60) {
                    // 阶段1：拥抱中 → 处决（3秒）
                    hugAnimationPhase = 2;
                    hugAnimationTimer = 0;
                    setCurrentAnimation("model.3");
                } else if (hugAnimationPhase == 2 && hugAnimationTimer >= 20) {
                    // 阶段2：处决 → 杀死玩家（1秒）

                    DamageSources damageSources = player.damageSources();
                    DamageSource genericKill = damageSources.fellOutOfWorld();
                    player.hurt(genericKill, Float.MAX_VALUE);
                    releaseHuggedPlayer(player);

                    player.sendSystemMessage(Component.literal(
                            "§c§l" + player.getName().getString() + " 被快乐泰迪勒死了..."
                    ));

                    // 重置状态
                    huggedPlayerUUID = null;
                    hugAnimationPhase = 0;
                    hugAnimationTimer = 0;
                    setCurrentAnimation("empty");
                }

                // 在处决前保持玩家位置（拉向快乐泰迪）
                if (hugAnimationPhase < 2) {
                    player.teleportTo(this.getX(), this.getY(), this.getZ());
                    player.setNoGravity(true);
                    player.setDeltaMovement(0, 0, 0);
                }
            } else {
                // 玩家已经死亡或离线，重置状态
                if (player != null) {
                    releaseHuggedPlayer(player);
                }
                huggedPlayerUUID = null;
                hugAnimationPhase = 0;
                hugAnimationTimer = 0;
                setCurrentAnimation("empty");
            }
        }
    }

    /** 恢复拥抱机制临时施加的移动状态，兼容免死、复活和限伤效果。 */
    private void releaseHuggedPlayer(ServerPlayer player) {
        player.setNoGravity(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.HAPPY_TEDDY_IDLE.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return IDLE_SOUND_COOLDOWN_TICKS;
    }

    @Override
    public double getAbnormalityAmbientSoundRange() {
        return IDLE_SOUND_RANGE;
    }

    @Override
    public SoundSource getAbnormalityAmbientSoundSource() {
        return SoundSource.AMBIENT;
    }

    // ==================== 工作结果奖励 ====================

    @Override
    public void onGoodWork(ServerPlayer player) {
        // 优秀工作：恢复3点精神值
        MentalValueUtil.addMentalValue(player, 3);
        player.sendSystemMessage(Component.literal(
                "§a快乐泰迪看起来很开心。§r(+3精神值)"
        ));
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        // 良好工作：恢复1点精神值
        MentalValueUtil.addMentalValue(player, 1);
        player.sendSystemMessage(Component.literal(
                "§e快乐泰迪接受了你的工作。§r(+1精神值)"
        ));
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // 差的工作：降低1点精神值
        MentalValueUtil.addMentalValue(player, -1);
        player.sendSystemMessage(Component.literal(
                "§c快乐泰迪看起来不太高兴。§r(-1精神值)"
        ));
    }

    @Override
    public void onWorkComplete(ServerPlayer player,
                               WorkType workType,
                               com.wzz.lobotocraft.work.WorkResult result) {
        // 检查是否为第二次沟通工作
        if (isSecondAttachmentWork(player, workType)) {
            // 触发拥抱死亡机制
            triggerHugDeath(player);
            player.sendSystemMessage(Component.literal(
                    "§4§l快乐泰迪发现了你连续两次来沟通工作..."
            ));
        }

        // 记录这次工作
        recordWork(player, workType);
    }

    @Override
    public void onQliphothMeltdown() {
        // 快乐泰迪不会突破收容
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityHappyTeddy> event) {
        // 从同步数据获取当前动画状态
        String anim = getCurrentAnimation();

        // 根据当前动画状态播放对应动画
        if ("model.1".equals(anim)) {
            // 拥抱开始动画 - hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("model.1"));
        } else if ("model.2".equals(anim)) {
            // 拥抱中动画 - loop: true
            return event.setAndContinue(RawAnimation.begin().thenLoop("model.2"));
        } else if ("model.3".equals(anim)) {
            // 处决动画 - hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("model.3"));
        }

        // 空闲状态，不播放动画但保持CONTINUE
        return PlayState.CONTINUE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.02D);
        builder = builder.add(Attributes.MAX_HEALTH, 100f);
        builder = builder.add(Attributes.ARMOR, 10);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 10);
        builder = builder.add(Attributes.FOLLOW_RANGE, 32);
        builder = builder.add(Attributes.FLYING_SPEED, 0.02D);
        return builder;
    }

    // ==================== NBT存储 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lastWorkerUUID != null) {
            tag.putUUID("LastWorkerUUID", lastWorkerUUID);
        }
        if (lastWorkType != null) {
            tag.putString("LastWorkType", lastWorkType.name());
        }
        if (huggedPlayerUUID != null) {
            tag.putUUID("HuggedPlayerUUID", huggedPlayerUUID);
            tag.putInt("HugAnimationPhase", hugAnimationPhase);
            tag.putInt("HugAnimationTimer", hugAnimationTimer);
        }
        tag.putString("CurrentAnimation", currentAnimation);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("LastWorkerUUID")) {
            lastWorkerUUID = tag.getUUID("LastWorkerUUID");
        }
        if (tag.contains("LastWorkType")) {
            try {
                lastWorkType = WorkType.valueOf(tag.getString("LastWorkType"));
            } catch (IllegalArgumentException e) {
                lastWorkType = null;
            }
        }
        if (tag.hasUUID("HuggedPlayerUUID")) {
            huggedPlayerUUID = tag.getUUID("HuggedPlayerUUID");
            hugAnimationPhase = tag.getInt("HugAnimationPhase");
            hugAnimationTimer = tag.getInt("HugAnimationTimer");
        }
        currentAnimation = tag.getString("CurrentAnimation");
    }
}
