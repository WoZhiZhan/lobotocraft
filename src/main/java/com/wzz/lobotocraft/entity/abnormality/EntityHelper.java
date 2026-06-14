package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 小帮手 (T-05-41) —— HE 级异想体。
 * 须知:工作结果良→50%倒计时后计数器-1;差→70%倒计时后-1。
 * 出逃(300血/抗性红0.5白1.0黑2.0蓝2.0):
 *  机制2:遇到任意单位 → 原地加速旋转蓄力3秒(big wind car 动画+旋转音效),
 *        然后以极快速度(速度5奔跑玩家)冲向目标,对路径上所有单位造成 20-30 点物理伤害(命中播放命中音效);
 *  机制3:冲刺撞到3格高的方块墙后停止,收起刀刃原地宕机一段时间(I sleep 动画),
 *        随后起身(播放启动成功音频)并重复机制2。
 * 双贴图:未出逃 helper.png / 出逃后 helper_escaped.png(单模型切换贴图)。
 */
public class EntityHelper extends AbstractAbnormality {

    private static final int STATE_IDLE = 0;
    private static final int STATE_SPIN = 1;   // 旋转蓄力
    private static final int STATE_DASH = 2;   // 冲刺
    private static final int STATE_STUNNED = 3;// 宕机

    private int state = STATE_IDLE;
    private int stateTimer = 0;
    private Vec3 dashDirection = null;
    private final Set<Integer> dashHitIds = new HashSet<>(); // 本次冲刺已命中的实体

    public EntityHelper(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "T-05-41";
        this.abnormalityName = "小帮手";
        this.riskLevel = RiskLevel.HE;
        this.damageType = "RED";
        this.maxPEOutput = 16;
        float[] basePreferences = {0.50f, 0.0f, 0.35f, 0.35f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        // 本能: 50/55/55/50/45
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.50f, 0.55f, 0.55f, 0.50f, 0.45f};
        // 洞察: 0/0/-30/-60/-90 (高等级洞察工作反而危险)
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.0f, 0.0f, -0.30f, -0.60f, -0.90f};
        // 沟通: 35/40/40/35/30
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.35f, 0.40f, 0.40f, 0.35f, 0.30f};
        // 压迫: 35/55/55/50/45
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.35f, 0.55f, 0.55f, 0.50f, 0.45f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 4),                       // Ⅰ 速度+4
                new ObservationLevelBonus(0.04f, 0, true, false, false),  // Ⅱ 成功率+4%、解锁饰品
                new ObservationLevelBonus(0.0f, 4, false, true, false),   // Ⅲ 速度+4、解锁护甲
                new ObservationLevelBonus(0.04f, 0, false, false, true)   // Ⅳ 成功率+4%、解锁武器
        };
    }

    @Override
    public int getBasicInfoCost() { return 16; }
    @Override
    public int getSensitiveInfoCost() { return 16; }
    @Override
    public int getManualCost(int manualIndex) { return 9; }
    @Override
    public int getWorkPreferencesCost() { return 5; }

    @Override
    public String getAbnormalityCode() { return "T-05-41"; }
    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }
    @Override
    public String name() { return "helper"; }

    // ==================== 管理须知 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // Ⅰ:良→50% 倒计时后减少 / Ⅱ:差→70% 倒计时后减少
        if (result == WorkResult.NORMAL && this.random.nextFloat() < 0.50f) {
            decreaseQliphothCounter(1);
        } else if (result == WorkResult.BAD && this.random.nextFloat() < 0.70f) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float dmg = 3 + this.random.nextInt(3); // 红伤3-5
        player.hurt(DamageHelper.getDamage(this, "red"), dmg);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        boolean was = hasEscape();
        super.triggerEscape();
        if (!was && hasEscape()) {
            setTexture("helper_escaped");
            setAnimation("get strange"); // 出逃动画
            state = STATE_IDLE;
            stateTimer = 25; // 出逃动画播放时间
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 镇压重置后回到未出逃贴图与状态
        if (!this.isRemoved()) {
            setTexture("helper");
            state = STATE_IDLE;
            stateTimer = 0;
            dashDirection = null;
        }
    }

    // ==================== 出逃抗性:红0.5 白1.0 黑2.0 蓝2.0 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (hasEscape()) {
            float m = 1.0f;
            if (DamageHelper.isRedDamage(source)) m = 0.5f;
            else if (DamageHelper.isBlackDamage(source)) m = 2.0f;
            else if (DamageHelper.isBlueDamage(source)) m = 2.0f;
            amount *= m;
        }
        return super.hurt(source, amount);
    }

    // ==================== tick: 旋转蓄力 → 冲刺 → 撞墙宕机 循环 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();
        if (!hasEscape()) return;

        if (stateTimer > 0) stateTimer--;

        switch (state) {
            case STATE_IDLE -> {
                if (stateTimer > 0) return; // 出逃动画/缓冲期
                LivingEntity target = findTarget(level);
                if (target != null) {
                    // 机制2:遇到单位,原地旋转蓄力3秒
                    state = STATE_SPIN;
                    stateTimer = 3 * 20;
                    setAnimation("big wind car");
                    level.playSound(null, blockPosition(), ModSounds.HELPER_SPIN.get(),
                            SoundSource.HOSTILE, 1.4f, 1.0f);
                    this.getLookControl().setLookAt(target);
                }
            }
            case STATE_SPIN -> {
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
                this.getNavigation().stop();
                // 蓄力期间持续锁定最近目标方向
                LivingEntity target = findTarget(level);
                if (target != null) {
                    Vec3 dir = target.position().subtract(position());
                    dashDirection = new Vec3(dir.x, 0, dir.z).normalize();
                    this.getLookControl().setLookAt(target);
                }
                if (stateTimer <= 0) {
                    if (dashDirection == null) {
                        state = STATE_IDLE;
                        setAnimation("shake head");
                        return;
                    }
                    // 蓄力完成:开始冲刺
                    state = STATE_DASH;
                    stateTimer = 4 * 20; // 冲刺时长上限,防止无墙时无限冲
                    dashHitIds.clear();
                }
            }
            case STATE_DASH -> {
                if (dashDirection == null) {
                    endDashToIdle();
                    return;
                }
                // 极快冲刺(速度5奔跑玩家,约0.75格/tick)
                this.setDeltaMovement(dashDirection.x * 0.75, getDeltaMovement().y, dashDirection.z * 0.75);
                // 路径伤害:对触碰到的所有单位造成20-30物理伤,每次冲刺每个目标只结算一次
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(1.2), this::isValidTarget)) {
                    if (dashHitIds.add(e.getId())) {
                        e.hurt(DamageHelper.getDamage(this, "red"), 20 + random.nextInt(11));
                        level.playSound(null, e.blockPosition(), ModSounds.HELPER_HIT.get(),
                                SoundSource.HOSTILE, 1.3f, 1.0f);
                    }
                }
                // 机制3:撞到3格高的方块墙 → 宕机
                if (this.horizontalCollision) {
                    if (isWallAhead(level)) {
                        state = STATE_STUNNED;
                        stateTimer = 4 * 20; // 宕机约4秒
                        dashDirection = null;
                        setAnimation("I sleep");
                        this.setDeltaMovement(0, getDeltaMovement().y, 0);
                        return;
                    } else {
                        // 撞到低矮障碍:结束本次冲刺
                        endDashToIdle();
                        return;
                    }
                }
                if (stateTimer <= 0) {
                    endDashToIdle();
                }
            }
            case STATE_STUNNED -> {
                this.setDeltaMovement(0, getDeltaMovement().y, 0);
                if (stateTimer <= 0) {
                    // 起身:播放启动成功音频,回到待机重复机制2
                    level.playSound(null, blockPosition(), ModSounds.HELPER_BOOT.get(),
                            SoundSource.HOSTILE, 1.4f, 1.0f);
                    endDashToIdle();
                }
            }
        }
    }

    private void endDashToIdle() {
        state = STATE_IDLE;
        stateTimer = 10;
        dashDirection = null;
        setAnimation("shake head");
    }

    /** 冲刺方向前方是否为3格高的方块墙 */
    private boolean isWallAhead(ServerLevel level) {
        Vec3 dir = dashDirection != null ? dashDirection
                : Vec3.directionFromRotation(0, getYRot()).normalize();
        BlockPos front = BlockPos.containing(position().add(dir.scale(1.0)));
        int solid = 0;
        for (int dy = 0; dy < 3; dy++) {
            if (!level.getBlockState(front.above(dy)).isAir()) solid++;
        }
        return solid >= 3;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e instanceof AbstractAbnormality ab) return ab.hasEscape();
        return true;
    }

    private LivingEntity findTarget(ServerLevel level) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(20), this::isValidTarget)) {
            double d = e.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“小帮手”正在员工的面前飞快地旋转，但员工只专注于手头的工作。");
        logs.add("“小帮手”最出名的功能是它的清洁功能，这个机器人是专为家政服务而设计的。");
        logs.add("当“小帮手”的致命缺陷被发现后，它被偷偷地送给了一户人家。");
        logs.add("地板上满是鲜血，人们四散而逃。“小帮手”渐渐明白了“清洁”意味着什么。");
        logs.add("“小帮手”有着洁白、光滑的外壳和短小精悍的机械腿，它被制作出来的目的是为了帮助人们。");
        logs.add("“小帮手”的创造者，XX有限公司，以其机器人生产线而闻名。");
        logs.add("“小帮手”有着各式各样的功能。从防盗警报、家庭监控，到泡制咖啡和灯光调节，一个家庭所需的一切它都能做到。");
        logs.add("各种各样的必要工具都被紧凑地塞进了这个小小的机器人中。如果把它拆开来看，你肯定会惊讶于它的创造者是怎么把这么多东西塞进去的。");
        logs.add("如果“小帮手”有感情的话，它也许会为自己能够帮助人们而感到自豪。");
        logs.add("“小帮手”时刻注意着这里，它想知道是否有什么事儿可以让它帮上忙。");
        logs.add("实际上，“小帮手”能泡制出上好的咖啡，大多数人都不知道。当然，没人会想要的。");
        logs.add("在看到了“小帮手”以后，员工打消了购置一台清洁机器人的想法。");
        logs.add("员工祈祷“小帮手”的清洁模式永远不要被激活。");
        logs.add("员工完成了工作。“小帮手”说：“如果您需要帮助的话，小帮手随叫随到！”");
        logs.add("员工完成了工作。“小帮手”高兴地说：“如果您准备吃午餐的话，小帮手可以为您提供咖啡！请问您想加多少方糖？”");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityHelper> event) {
        String anim = getAnimation();
        switch (anim) {
            case "get strange" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("get strange")); }
            case "big wind car" -> { return event.setAndContinue(RawAnimation.begin().thenLoop("big wind car")); }
            case "I sleep" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("I sleep")); }
        }
        // 待机(出逃前后均为 shake head)
        return event.setAndContinue(RawAnimation.begin().thenLoop("shake head"));
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityHelper((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HelperState", state);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = tag.getInt("HelperState");
        if (state == STATE_DASH) state = STATE_IDLE; // 冲刺方向不持久化,重载后回待机
    }
}
