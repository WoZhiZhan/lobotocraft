package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.EntityGallows;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlackForestDoorGoal;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.BlackForestEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class EntityApprovalBird extends AbstractAbnormality {

    private final Random random = new Random();

    private static final int CHARGE_TICKS = 140;
    /** 20 秒攻击冷却（ticks） */
    private static final int COOLDOWN_TICKS = 400;
    /** 检测"同一房间"的范围：水平 20 格，上下 8 格 */
    private static final double ROOM_HORIZONTAL = 20.0;
    private static final int    ROOM_Y_RANGE    = 8;

    private boolean isCharging   = false;
    private int     chargeTick   = 0;
    private int     cooldownTick = 0;

    public EntityApprovalBird(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode  = "0-02-62";
        this.abnormalityName  = "审判鸟";
        this.riskLevel        = RiskLevel.WAW;
        this.damageType       = "BLUE";
        this.maxPEOutput      = 24;

        float[] basePreferences = {0.2f, 0.2f, 0.2f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] m = new float[4][5];
        m[0] = new float[]{0.0f, 0.0f, 0.15f, 0.25f, 0.25f};
        m[1] = new float[]{0.0f, 0.0f, 0.20f, 0.30f, 0.30f};
        m[2] = new float[]{0.0f, 0.0f, 0.15f, 0.25f, 0.25f};
        m[3] = new float[]{0.0f, 0.0f, 0.00f, 0.00f, 0.00f};
        return m;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) return;

        // 未出逃：冻结 AI
        if (!hasEscape()) {
            resetChargeState();
            setNoAi(true);
            return;
        }

        if (level() instanceof ServerLevel serverLevel) {
            BlackForestEvent.BlackForestSavedData data =
                    BlackForestEvent.BlackForestSavedData.get(serverLevel);
            if (data.isDoorSpawned() && data.getEscapedBirdUUIDs().contains(this.getStringUUID())) {
                // 门已生成，清除蓄力状态，不攻击
                resetChargeState();
                setNoAi(false);
                ensureDoorGoalExists();
                return; // 跳过蓄力/攻击逻辑
            }
        }

        // ── 蓄力期间：冻结移动 ──────────────────────────────────
        if (isCharging) {
            setNoAi(true);
            chargeTick++;

            if (chargeTick >= CHARGE_TICKS) {
                executeAreaAttack();
                resetChargeState();
                cooldownTick = COOLDOWN_TICKS;
                setAnimation("idle");
                setNoAi(false);
            }
            return;
        }

        // ── 冷却倒计时 ───────────────────────────────────────────
        if (cooldownTick > 0) {
            cooldownTick--;
            setNoAi(false);
            return;
        }

        // ── 检测同一房间内是否有玩家或村民 ──────────────────────
        setNoAi(false);
        List<LivingEntity> targets = findRoomTargets();
        if (!targets.isEmpty()) {
            isCharging = true;
            chargeTick = 0;
            setNoAi(true);
            setAnimation("charge");
            playSound(ModSounds.APPROVAL_BIRD_WILL_ATTACK.get());
        }
    }

    private boolean doorGoalAdded = false;

    private void ensureDoorGoalExists() {
        if (doorGoalAdded) return;

        boolean hasDoorGoal = this.goalSelector.getRunningGoals()
                .anyMatch(goal -> goal.getGoal() instanceof MoveToBlackForestDoorGoal);

        if (!hasDoorGoal) {
            this.goalSelector.addGoal(0, new MoveToBlackForestDoorGoal(this, 1.2));
            doorGoalAdded = true;
        }
    }

    /**
     * 在"同一房间"范围内查找存活的玩家与村民
     */
    private List<LivingEntity> findRoomTargets() {
        return EntityUtil.findLivingEntitiesAround(this, ROOM_Y_RANGE, ROOM_HORIZONTAL)
                .stream()
                .filter(e -> e.isAlive() && (e instanceof Player player && (!player.isSpectator() || player.isCreative()) || e instanceof Villager))
                .collect(Collectors.toList());
    }

    /**
     * 对同一房间所有玩家和村民造成 30-40 蓝色伤害，
     * 杀死时触发绞刑架处决
     */
    private void executeAreaAttack() {
        List<LivingEntity> targets = findRoomTargets();
        for (LivingEntity target : targets) {
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            float damage = 30.0f + random.nextInt(11); // 30-40
            // 清除无敌帧确保伤害生效
            EntityUtil.clearHurtTime(target, () -> {
                boolean wasAlive = target.getHealth() - damage <= 0;
                if (!wasAlive)
                    target.hurt(DamageHelper.getDamage(this, "lobotocraft:blue"), damage);
                if (wasAlive) {
                    spawnGallows(target);
                }
            });
        }
        // 播放攻击音效
        playAttackSound();
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
        return super.hurt(damageSource, f);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityApprovalBird((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    /**
     * 在目标死亡位置生成绞刑架实体并播放处决动画
     */
    private void spawnGallows(LivingEntity victim) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        EntityGallows gallows = ModEntities.gallows.get().create(serverLevel);
        if (gallows == null) return;

        // 在目标脚下位置生成
        gallows.moveTo(victim.getX(), victim.getY(), victim.getZ(), 0f, 0f);
        // 绑定被处决者（用于动画同步）
        gallows.setVictimId(victim.getId());
        serverLevel.addFreshEntity(gallows);
    }

    // ── 出逃 / 收容 ─────────────────────────────────────────────
    @Override
    public void triggerEscape() {
        super.triggerEscape();
        resetChargeState();
        cooldownTick = 0;
        broadcastMessage("§c§l警告！审判鸟已经出逃！");
        setAnimation("idle");
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        resetChargeState();
        setAnimation("idle");
        setTarget(null);
    }

    private void resetChargeState() {
        isCharging = false;
        chargeTick = 0;
    }

    // ── 克苏勒计数器 ────────────────────────────────────────────
    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        float f = random.nextFloat();
        if (result == WorkResult.NORMAL && f <= 0.3f) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§c因你的工作失误，审判鸟的计数器减少了..."), false);
        }
        if (result == WorkResult.BAD && f <= 0.6f) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§c因你的工作失误，审判鸟的计数器减少了..."), false);
        }
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        triggerEscape();
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

    // ── 攻击失败惩罚（保留，用于工作失败场景） ─────────────────
    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 5.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:blue"), damage);
    }

    // ── 音效 ────────────────────────────────────────────────────
    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.APPROVAL_BIRD_ATTACK.get();
    }

    @Override
    public void playAttackSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                getAttackSound(), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    // ── 工作日志 ────────────────────────────────────────────────
    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("有句古话叫做“永远不要忘记审判之鸟。它迟早会找到你，无论这要花上多久的时间。”");
        logs.add("“审判鸟”从不会感到焦虑。");
        logs.add("也许“审判鸟”此刻就在你的身后。");
        logs.add("我们生活在一个没有宽恕的世界之中，我们的灵魂即是赎罪的祭品。");
        logs.add("人类都是狡诈的存在，他们无法救赎自己。这就是为什么“审判鸟”必须来到我们的身边。");
        logs.add("很少有人能意识到自己的罪恶，不过“审判鸟”会帮你们理解的。");
        logs.add("“审判鸟”曾是森林的守望者，可它守护森林的意愿过于强大了，以至于忽视了更加重要的东西。");
        logs.add("如今，无边的黑暗和永远的寒冷正笼罩着整片森林...");
        return logs;
    }

    // ── 属性 ────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,       800.0D)
                .add(Attributes.MOVEMENT_SPEED,   0.4D)
                .add(Attributes.ATTACK_DAMAGE,    1.0D)
                .add(Attributes.ARMOR,            0.0D)
                .add(Attributes.FOLLOW_RANGE,     32.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(),   0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(),  2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    // ── 存档 ────────────────────────────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsCharging",   isCharging);
        tag.putInt("ChargeTick",       chargeTick);
        tag.putInt("CooldownTick",     cooldownTick);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        isCharging   = tag.getBoolean("IsCharging");
        chargeTick   = tag.getInt("ChargeTick");
        cooldownTick = tag.getInt("CooldownTick");
    }

    // ── 动画 ────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    @Override
    public void die(DamageSource source) {
        setAnimation("die");
        super.die(source);
    }

    private PlayState animationPredicate(AnimationState<EntityApprovalBird> event) {
        return switch (getAnimation()) {
            case "charge" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.model.2"));
            case "die"    -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.4"));
            default -> {
                if (event.isMoving())
                    yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.3"));
                yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.model.1"));
            }
        };
    }

    // ── EGO / 观测信息（保持原样） ───────────────────────────────
    @Override public String name() { return "approval_bird"; }

    @Override public int getBasicInfoCost()         { return 20; }
    @Override public int getWorkPreferencesCost()   { return 6;  }
    @Override public int getSensitiveInfoCost()     { return 20; }
    @Override public int getManualCost(int i)       { return 15; }
    @Override public float getGiftProbability()     { return 0.02f; }
    @Override public int getWeaponDevelopmentCost() { return 150; }
    @Override public int getWeaponDevelopmentMaxCount() { return 1; }
    @Override public int getArmorDevelopmentCost()  { return 120; }
    @Override public int getArmorDevelopmentMaxCount() { return 1; }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/approval_bird_curio.png"),
                "正义裁决者", "眼部", "approval_bird_curio",
                "移动速度+6,攻击速度+6,压迫工作的成功率提高6%");
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.04f, 0),
                new ObservationLevelBonus(0.0f,  5),
                new ObservationLevelBonus(0.04f, 0, true,  false, false),
                new ObservationLevelBonus(0.0f,  2, false, true,  true)
        };
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/approval_bird_weapon.png"),
                "正义裁决者", RiskLevel.ALEPH, "BLUE", "2-4", "2.0", "近战",
                getWeaponDevelopmentMaxCount(), "approval_bird_weapon");
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/approval_bird_armor.png"),
                "正义裁决者", RiskLevel.ALEPH,
                0.5f, 0.5f, 0.5f, 0.5f,
                getArmorDevelopmentMaxCount(), "approval_bird");
    }

    @Override public float[] getGiftRenderOffset()   { return new float[]{1f,    0.0f, 0.0f}; }
    @Override public float[] getWeaponRenderScale()  { return new float[]{0.9f,  0.7f, 0.7f}; }
    @Override public float[] getWeaponRenderOffset() { return new float[]{13.5f, 0.0f, 0.0f}; }
}
