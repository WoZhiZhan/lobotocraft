package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 被遗弃的杀人魔 (T-01-54)
 * 特殊能力：
 * - 出逃类异想体
 * - 未出逃时8格范围内播放循环音效
 * - 第二次工作后停止音效并出逃
 * - 攻击造成2-4点红色伤害
 * - 死亡后回到出逃位置
 */
public class EntityAbandonedMurderer extends AbstractAbnormality {

    // 当前动画
    private String currentAnimation = "model.2"; // 初始状态：头盔隐藏

    // 攻击动画计时器
    private int attackAnimationTimer = 0;
    private boolean attackPending = false;

    private int attackCooldown = 0;

    // 攻击音效延迟计时器
    private int attackSoundTimer = 0;

    // 出逃动画播放标志
    private boolean hasPlayedEscapeAnimation = false;

    // 随机数生成器
    private final Random random = new Random();

    public EntityAbandonedMurderer(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.set(DATA_ANIMATION, "model.2"); // 初始状态：头盔隐藏
    }

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "T-01-54";
        this.abnormalityName = "被遗弃的杀人魔";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "RED";
        this.maxPEOutput = 14;

        // 工作偏好
        float[] basePreferences = {0.5f, 0.3f, 0.4f, -0.8f};
        // 本能50%，洞察30%，沟通40%，压迫-80%
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(1);
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.0f, 5),
                new ObservationLevelBonus(0.05f, 0, true, false, false),
                new ObservationLevelBonus(0.0f, 5, false, true, false),
                new ObservationLevelBonus(0.05f, 0, false, false, true)
        };
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        // 基础修正（如果不需要可以都设为0）
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];

        // 使用加法：级别1-3不变，级别4-5提升
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.1f, 0.1f};  // 本能: 50→50→50→60→60
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.1f, 0.1f};  // 洞察: 30→30→30→40→40
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.1f, 0.1f};  // 沟通: 40→40→40→50→50
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.8f, 1.0f, 1.1f}; // 压迫: -80→-80→0→20→30

        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));

        // 只攻击生存/冒险模式玩家
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (entity) -> entity instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“被遗弃的杀人魔”如同雕塑一般静静地跪在收容单元的角落。");
        logs.add("“被遗弃的杀人魔”有时会一边发抖一边自言自语。");
        logs.add("“被遗弃的杀人魔”会时不时地发出尖叫，但既然它被绑得死死的，所以不必太过担心。");
        logs.add("员工感到绝望。");
        logs.add("员工在绝望的氛围中呼吸着。");
        logs.add("这里为什么总是又黑又冷？");
        logs.add("“被遗弃的杀人魔”的大脑正在黑暗中糜烂。");
        logs.add("连“死亡”都抛弃了他，“被遗弃的杀人魔”将被永远囚禁在这里。");
        logs.add("这已不仅仅是愤怒，而是一种更加深沉，更加扭曲，更加疯狂的憎恨。");
        return logs;
    }

    @Override
    public boolean canEscape() {
        return true;  // 这是出逃类异想体
    }

    @Override
    public int getBasicInfoCost() {
        return 12;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 16;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 12;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 8;
    }

    @Override
    public String name() {
        return "abandoned_murderer";
    }

    // ==================== 工作回调 ====================

    @Override
    public void onWorkComplete(ServerPlayer player,
                               com.wzz.lobotocraft.work.WorkType workType,
                               com.wzz.lobotocraft.work.WorkResult result) {
        if (result == WorkResult.BAD && !hasEscape()) {
            triggerEscape();
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // 工作结果为差时归零计数器
        decreaseQliphothCounter(1);
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
        // 播放出逃动画（带头盔）- 这个动画会一直保持显示头盔状态
        setCurrentAnimation("model.1");
        hasPlayedEscapeAnimation = false; // 重置标志

        Player nearestPlayer = this.level().getNearestPlayer(this, 32.0);
        if (nearestPlayer != null && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
            this.setTarget(nearestPlayer);
        }
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        hasPlayedEscapeAnimation = false;
        attackPending = false;
        setCurrentAnimation("model.2");
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // 检查攻击冷却
        if (attackCooldown > 0) {
            return false;
        }

        if (target instanceof LivingEntity livingTarget) {
            // 不攻击创造模式玩家
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return false;
            }

            // 开始攻击动画
            attackPending = true;
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 255));
            attackAnimationTimer = 0;
            setTarget(livingTarget);
            setCurrentAnimation("model.3");  // 播放攻击动画

            // 延迟播放攻击音效（0.3秒 = 6 tick）
            attackSoundTimer = 6;

            return true;
        }
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        // 未出逃时强制清除目标并禁用所有AI
        if (!hasEscape()) {
            this.setTarget(null);
            this.getNavigation().stop();
            // 禁用所有AI目标
            this.goalSelector.getRunningGoals().forEach(goal -> goal.stop());
            this.targetSelector.getRunningGoals().forEach(goal -> goal.stop());
            return; // 直接返回，不执行后续逻辑
        }

        // 出逃后才执行下面的逻辑，攻击时不转向
        if (this.getTarget() != null && !attackPending) {
            LivingEntity target = this.getTarget();
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());
        // 减少攻击冷却
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // 处理延迟播放攻击音效
        if (attackSoundTimer > 0) {
            attackSoundTimer--;
            if (attackSoundTimer == 0) {
                playAttackSound();
            }
        }

        // 处理攻击动画
        if (attackPending && !this.level().isClientSide) {
            attackAnimationTimer++;

            // 0.5秒后造成伤害（10 tick）
            if (attackAnimationTimer == 10) {
                performAttackDamage();
            }

            // 1秒后清除攻击动画（20 tick）
            if (attackAnimationTimer >= 20) {
                attackPending = false;
                attackAnimationTimer = 0;
                attackCooldown = 40;
                setCurrentAnimation(hasEscape() ? "model.1" : "model.2");
            }
        }

        // 出逃后检查目标（只在没有播放过动画且没在攻击时）
        if (hasEscape() && this.isAlive() && !this.level().isClientSide && !attackPending) {
            if (this.tickCount % 20 == 0) { // 每秒检查一次
                if (this.getTarget() == null && !hasPlayedEscapeAnimation) {
                    // 第一次失去目标时播放出逃音效
                    stopAmbientSoundForAllPlayers();
                    playEscapeWarningSound();
                    playEscapeSound();
                    setCurrentAnimation("model.1");
                    hasPlayedEscapeAnimation = true;
                }

                // 寻找新目标（不包括创造模式）
                Player nearestPlayer = this.level().getNearestPlayer(this, 32.0);
                if (nearestPlayer != null && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
                    this.setTarget(nearestPlayer);
                }
            }
        }
    }

    @Override public int getWeaponDevelopmentCost() { return 25; }
    @Override public int getWeaponDevelopmentMaxCount() { return 2; }
    @Override public int getArmorDevelopmentCost()  { return 25; }
    @Override public int getArmorDevelopmentMaxCount() { return 2; }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/abandoned_murderer_curio.png"),
                "悔恨", "口罩", "abandoned_murderer_curio",
                "最大生命值+2,最大精神值+2,玩家手持任意EGO时伤害+1");
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/abandoned_murderer_weapon.png"),
                "悔恨", RiskLevel.TETH, "RED", "15", "慢", "锤",
                getWeaponDevelopmentMaxCount(), "abandoned_murderer_weapon");
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/abandoned_murderer_armor.png"),
                "悔恨", RiskLevel.TETH,
                0.7f, 1.2f, 0.8f, 2.0f,
                getArmorDevelopmentMaxCount(), "abandoned_murderer");
    }

    @Override public float[] getGiftRenderOffset()   { return new float[]{1f,    0.0f, 0.0f}; }
    @Override public float[] getWeaponRenderScale()  { return new float[]{0.9f,  0.7f, 0.7f}; }
    @Override public float[] getWeaponRenderOffset() { return new float[]{13.5f, 0.0f, 0.0f}; }

    private void setCurrentAnimation(String animation) {
        this.currentAnimation = animation;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_ANIMATION, animation);
        }
    }

    /**
     * 执行实际的伤害判定
     */
    private void performAttackDamage() {
        if (getTarget() == null || !getTarget().isAlive()) {
            setTarget(null);
            return;
        }

        // 只检查距离，不检查角度
        double distance = this.distanceTo(getTarget());

        if (distance <= 2.5) { // 放宽距离到2.5格
            // 造成2-4点随机红色伤害
            float damage = 2.0f + random.nextInt(3);
            getTarget().hurt(
                    DamageHelper.getDamage(this, "lobotocraft:red"),
                    damage
            );

            // 目标死亡后返回收容位置
            if (getTarget().isDeadOrDying()) {
                boolean hasPlayer = false;
                for (Player player : EntityUtil.findPlayersAround(this, 10, 32)) {
                    if (player != getTarget()) {
                        hasPlayer = true;
                        break;
                    }
                }
                if (!hasPlayer) {
                    stopEscape();
                }
            }
        }
        setTarget(null);
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 2.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), damage);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityAbandonedMurderer((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    /**
     * 获取当前动画
     */
    private String getCurrentAnimation() {
        if (this.level().isClientSide) {
            return this.entityData.get(DATA_ANIMATION);
        }
        return this.currentAnimation;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        // 控制器1：移动动画
        controllerRegistrar.add(new AnimationController<>(this, "movement", 0, this::movementPredicate));
        // 控制器2：动作动画（攻击、出逃等）
        controllerRegistrar.add(new AnimationController<>(this, "action", 0, this::actionPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntityAbandonedMurderer> event) {
        // 从同步数据获取出逃状态（确保客户端能正确读取）
        boolean escaped = this.entityData.get(DATA_ESCAPED);

        // 出逃后，如果在移动则播放移动动画
        if (escaped && event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.4"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionPredicate(AnimationState<EntityAbandonedMurderer> event) {
        // 从同步数据获取当前动画状态
        String anim = getCurrentAnimation();

        // 攻击动画优先级最高
        if ("model.3".equals(anim)) {
            // 攻击动画 - 播放一次
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.model.3"));
        } else if ("model.1".equals(anim)) {
            // 带头盔动画（出逃时）- hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.model.1"));
        } else if ("model.2".equals(anim)) {
            // 卸下头盔动画 - hold_on_last_frame
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.model.2"));
        }

        return PlayState.CONTINUE;
    }

    /**
     * 出逃音效（音频1）
     */
    @Override
    public SoundEvent getEscapeSound() {
        return com.wzz.lobotocraft.init.ModSounds.ABANDONED_MURDERER_ESCAPE.get();
    }

    /**
     * 出逃警告音效（音频2）
     */
    @Override
    public SoundEvent getEscapeWarningSound() {
        return com.wzz.lobotocraft.init.ModSounds.ABANDONED_MURDERER_WARNING.get();
    }

    /**
     * 攻击音效（音频3）
     */
    @Override
    public SoundEvent getAttackSound() {
        return com.wzz.lobotocraft.init.ModSounds.ABANDONED_MURDERER_ATTACK.get();
    }

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.ABANDONED_MURDERER_AMBIENT.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 220;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 270.0D)  // 270血量
                .add(Attributes.MOVEMENT_SPEED, 0.23D)  // 原版僵尸速度
                .add(Attributes.ATTACK_DAMAGE, 1.0D)  // 平均伤害3点（实际2-4随机）
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CurrentAnimation", currentAnimation);
        tag.putInt("AttackAnimationTimer", attackAnimationTimer);
        tag.putBoolean("AttackPending", attackPending);
        tag.putBoolean("HasPlayedEscapeAnimation", hasPlayedEscapeAnimation);
        tag.putInt("AttackSoundTimer", attackSoundTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        currentAnimation = tag.getString("CurrentAnimation");
        // 如果没有保存动画或为empty，根据出逃状态设置默认动画
        if (currentAnimation.isEmpty() || "empty".equals(currentAnimation)) {
            currentAnimation = hasEscape() ? "model.1" : "model.2";
        }
        this.entityData.set(DATA_ANIMATION, currentAnimation);
        attackAnimationTimer = tag.getInt("AttackAnimationTimer");
        attackPending = tag.getBoolean("AttackPending");
        hasPlayedEscapeAnimation = tag.getBoolean("HasPlayedEscapeAnimation");
        attackSoundTimer = tag.getInt("AttackSoundTimer");
    }
}
