package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 又大又可能很坏的狼 (F-02-58) —— WAW 级异想体。
 * 双形态模型:出逃前 bigbadwolf(idle/idle2吞人后),出逃后 bigbadwolf_big。
 * 吞人:同一天工作过小红帽(F-01-57)的员工完成工作 / 工作结果差且非本能 → 吞入腹中(旁观附身)+计数器-1。
 * 被吞:服务器无其他未被吞玩家 → 直接死亡;腹中有人时完成本能工作 → 吐出(血/精神变50%)。
 * 出逃:全屏CG(cg1 0.5秒→cg2 1秒渐隐)+嚎叫,前往最近再生反应堆并攻击周围。
 * 攻击:爪7-16红(40%)/咬10-14红(40%)/嚎叫(20%):屏幕扭曲、20x20范围30白伤并吸血、
 *  结束追加15-19红伤;嚎叫时血量<50% → 最近3只异想体计数器-1(按文档仅对出逃类生效)。
 * 每损失300血 → 黑雾(隐身+发光+白色粒子+潜狼音频)转移到下一个反应堆,移动期间免疫伤害。
 * 死亡:腹中有员工时被小红帽击杀 → 掉落特殊饰品"羊皮"并释放员工;被玩家击杀只释放。
 */
public class EntityBigBadWolf extends AbstractAbnormality {

    private final List<UUID> swallowedPlayers = new ArrayList<>();
    private int attackCooldown = 0;
    private int pendingHit = 0;
    private int pendingType = -1; // 0爪 1咬
    private LivingEntity pendingTarget = null;
    private int howlTimer = 0;    // 嚎叫阶段计时
    private int howlPhase = 0;    // 0无 1=skill1前摇 2=skill12嚎叫中 3=skill2收尾
    private float lastFogHealth = -1; // 上次进入黑雾时的血量基准
    private boolean inFog = false;
    private int fogTimer = 0;
    private BlockPos fogTargetReactor = null;
    private boolean killedByRedHood = false;

    public EntityBigBadWolf(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "F-02-58";
        this.abnormalityName = "又大又可能很坏的狼";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "RED";
        this.maxPEOutput = 22;
        float[] basePreferences = {0.40f, 0.30f, 0.45f, 0.0f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
        this.lastFogHealth = 2000f;
    }

    @Override
    public float[][] getFullWorkPreferences() {
        float[][] prefs = new float[4][5];
        prefs[WorkType.INSTINCT.ordinal()] = new float[]{0.40f, 0.45f, 0.45f, 0.45f, 0.50f};
        prefs[WorkType.INSIGHT.ordinal()] = new float[]{0.30f, 0.30f, 0.30f, 0.20f, 0.20f};
        prefs[WorkType.ATTACHMENT.ordinal()] = new float[]{0.45f, 0.50f, 0.50f, 0.55f, 0.55f};
        prefs[WorkType.REPRESSION.ordinal()] = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 3),                       // Ⅰ 速度+3
                new ObservationLevelBonus(0.03f, 0),                      // Ⅱ 成功率+3%
                new ObservationLevelBonus(0.0f, 3, true, false, false),   // Ⅲ 速度+3、饰品(郁蓝创痕)
                new ObservationLevelBonus(0.03f, 0, false, true, true)    // Ⅳ 成功率+3%、护甲武器
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
    public String getAbnormalityCode() { return "F-02-58"; }
    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }

    /** 构造期默认资源名。出逃后的资源切换通过 textureForCurrentState()/渲染器处理。 */
    @Override
    public String name() {
        return "bigbadwolf";
    }

    private String textureForCurrentState() {
        if (hasEscape()) {
            return "bigbadwolf_big";
        }
        return hasSwallowedPlayers() ? "bigbadwolf_swallowed" : "bigbadwolf";
    }

    public boolean hasSwallowedPlayers() {
        return !swallowedPlayers.isEmpty();
    }

    // ==================== 吞人机制 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        boolean swallow = false;
        // 机制1:同一天内工作过小红帽雇佣兵的玩家
        int today = getCurrentDay(player);
        if (today >= 0 && player.getPersistentData().getInt("lobotocraft_worked_redhat_day") == today) {
            swallow = true;
        }
        // 机制2:工作结果为差且工作类型不为本能
        if (result == WorkResult.BAD && workType != WorkType.INSTINCT) {
            swallow = true;
        }
        // 机制4(须知Ⅲ):腹中有人时完成本能工作 → 吐出
        if (workType == WorkType.INSTINCT && hasSwallowedPlayers()) {
            releaseSwallowed(true);
            return;
        }
        if (swallow) {
            swallowPlayer(player);
            decreaseQliphothCounter(1);
        }
    }

    private int getCurrentDay(ServerPlayer player) {
        final int[] day = {-1};
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA)
                .ifPresent(data -> day[0] = data.getCurrentDay());
        return day[0];
    }

    /** 吞入腹中:玩家切旁观并附身狼 */
    private void swallowPlayer(ServerPlayer player) {
        swallowedPlayers.add(player.getUUID());
        player.getPersistentData().putBoolean("wolf_swallowed", true);
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(this);
        setTexture("bigbadwolf_swallowed");
        setAnimation("idle2");
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 1.4f, 0.8f);
        }
        player.sendSystemMessage(Component.literal("§c你被“又大又可能很坏的狼”吞入了腹中……"));
        checkAllSwallowed();
    }

    /** 机制3:服务器内无其他未被吞玩家 → 被吞员工直接死亡 */
    private void checkAllSwallowed() {
        if (!(this.level() instanceof ServerLevel level)) return;
        boolean anyFree = false;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (!p.getPersistentData().getBoolean("wolf_swallowed")
                    && !p.isSpectator() && p.isAlive()) {
                anyFree = true;
                break;
            }
        }
        if (!anyFree) {
            releaseSwallowed(false);
        }
    }

    /**
     * 释放腹中员工。
     * @param spitOut true=吐出(损失50%生命与精神);false=全部判定死亡
     */
    public void releaseSwallowed(boolean spitOut) {
        if (!(this.level() instanceof ServerLevel level)) return;
        for (UUID id : new ArrayList<>(swallowedPlayers)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.getPersistentData().putBoolean("wolf_swallowed", false);
            p.setCamera(p);
            p.setGameMode(GameType.SURVIVAL);
            p.teleportTo(getX(), getY(), getZ());
            if (spitOut) {
                p.setHealth(Math.max(1f, p.getMaxHealth() * 0.5f));
                float half = MentalValueUtil.getEffectiveMaxMentalValue(p) * 0.5f;
                float cur = MentalValueUtil.getMentalValue(p);
                if (cur > half) {
                    MentalValueUtil.reduceMentalValue(p, cur - half);
                }
                p.sendSystemMessage(Component.literal("§6狼把你吐了出来……你失去了一半的生命与精神。"));
            } else {
                p.invulnerableTime = 0;
                p.hurt(DamageHelper.getDamage(this, "red"), Float.MAX_VALUE);
            }
        }
        swallowedPlayers.clear();
        setTexture(textureForCurrentState());
        setAnimation("idle");
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float dmg = 4 + this.random.nextInt(5); // 红伤4-8
        player.hurt(DamageHelper.getDamage(this, "red"), dmg);
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    // ==================== 出逃:CG + 前往反应堆 ====================

    @Override
    public void triggerEscape() {
        boolean was = hasEscape();
        super.triggerEscape();
        if (!was && hasEscape()) {
            setTexture("bigbadwolf_big");
            setAnimation("idle");
            if (this.level() instanceof ServerLevel level) {
                level.playSound(null, blockPosition(), ModSounds.WOLF_HOWL.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
                // 全屏CG:cg1 0.5秒 → cg2 持续1秒后渐隐
                List<net.minecraft.resources.ResourceLocation> cgs = new ArrayList<>();
                cgs.add(ResourceUtil.createInstance("textures/gui/wolf_cg/cg1.png"));
                cgs.add(ResourceUtil.createInstance("textures/gui/wolf_cg/cg2.png"));
                for (ServerPlayer p : level.players()) {
                    FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                            .showDuration(1500)
                            .fade(0, 500)
                            .textures(cgs, 500)
                            .build();
                    MessageLoader.getLoader().sendToPlayer(p, msg);
                }
                fogTargetReactor = findNearestReactor(level, blockPosition(), null);
            }
        }
    }

    @Override
    public void stopEscape() {
        boolean wasEscaped = hasEscape();
        super.stopEscape();
        if (!wasEscaped || hasEscape()) {
            return;
        }

        resetEscapeState();
        setTexture(textureForCurrentState());
        setAnimation(hasSwallowedPlayers() ? "idle2" : "idle");
    }

    private void resetEscapeState() {
        attackCooldown = 0;
        pendingHit = 0;
        pendingType = -1;
        pendingTarget = null;
        howlTimer = 0;
        howlPhase = 0;
        inFog = false;
        fogTimer = 0;
        fogTargetReactor = null;
        killedByRedHood = false;
        lastFogHealth = getHealth();
        this.removeEffect(MobEffects.INVISIBILITY);
        this.removeEffect(MobEffects.GLOWING);
        this.getNavigation().stop();
    }

    private BlockPos findNearestReactor(ServerLevel level, BlockPos origin, BlockPos exclude) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockEntity be : EntityUtil.findBlockEntities(level, origin, 128)) {
            if (be instanceof com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity) {
                if (exclude != null && be.getBlockPos().equals(exclude)) continue;
                double d = be.getBlockPos().distSqr(origin);
                if (d < bestD) { bestD = d; best = be.getBlockPos(); }
            }
        }
        return best;
    }

    // ==================== 抗性:红1.0 白0.7 黑0.7 蓝1.0;黑雾免疫 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        // 机制7:黑雾移动期间免疫一切伤害
        if (inFog) return false;
        if (hasEscape()) {
            float m = 1.0f;
            if (DamageHelper.isWhiteDamage(source)) m = 0.7f;
            else if (DamageHelper.isBlackDamage(source)) m = 0.7f;
            amount *= m;
            // 死亡来源记录:是否被小红帽击杀
            if (this.getHealth() - amount <= 0
                    && source.getEntity() instanceof EntityRedHoodMercenary) {
                killedByRedHood = true;
            }
        }
        boolean result = super.hurt(source, amount);
        // 机制7:累计损失300血进入黑雾转移
        if (result && hasEscape() && !inFog && lastFogHealth - this.getHealth() >= 300f) {
            enterFog();
        }
        return result;
    }

    private void enterFog() {
        inFog = true;
        fogTimer = 8 * 20;
        lastFogHealth = this.getHealth();
        this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, fogTimer + 20, 0, false, false));
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, fogTimer + 20, 0, false, false));
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), ModSounds.WOLF_STEALTH.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
            fogTargetReactor = findNearestReactor(level, blockPosition(), fogTargetReactor);
        }
    }

    // ==================== 死亡:羊皮 ====================

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && hasEscape()) {
            boolean hadSwallowed = hasSwallowedPlayers();
            // 机制8:腹中有员工时被小红帽击杀 → 掉落"羊皮"
            if (hadSwallowed && (killedByRedHood || source.getEntity() instanceof EntityRedHoodMercenary)) {
                this.level().addFreshEntity(new ItemEntity(this.level(),
                        getX(), getY(), getZ(), new ItemStack(ModItems.SHEEPSKIN_CURIO.get())));
            }
            // 无论谁击杀都释放腹中员工
            releaseSwallowed(true);
            // 小红帽须知Ⅵ/目标机制3:狼被镇压 → 通知所有小红帽
            if (this.level() instanceof ServerLevel level) {
                boolean byPlayer = source.getEntity() instanceof Player;
                for (EntityRedHoodMercenary redhat : level.getEntitiesOfClass(EntityRedHoodMercenary.class,
                        getBoundingBox().inflate(2048), LivingEntity::isAlive)) {
                    redhat.onWolfSuppressed(byPlayer && this.getPersistentData().getBoolean("redhat_marked"));
                }
            }
        }
        super.die(source);
        // 镇压重置后回到出逃前形态贴图
        if (!this.isRemoved()) {
            setTexture("bigbadwolf");
        }
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();
        if (!hasEscape()) {
            // 收容期检测(每秒,以收容单元附近16格近似"收容单元前的走廊")
            if (this.tickCount % 20 == 0) {
                // 须知Ⅴ:小红帽(出逃/委托状态)经过附近 → 直接突破收容
                if (!level.getEntitiesOfClass(EntityRedHoodMercenary.class,
                        getBoundingBox().inflate(16), r -> r.isAlive() && r.hasEscape()).isEmpty()) {
                    triggerEscape();
                    return;
                }
                // 须知Ⅳ:被小红帽所伤且正在流血(未满血)的单位经过附近 → 计数器立刻减少
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(16),
                        en -> en.isAlive() && en.getHealth() < en.getMaxHealth()
                                && en.getPersistentData().getLong("lobotocraft_redhat_wounded") > level.getGameTime())) {
                    e.getPersistentData().remove("lobotocraft_redhat_wounded");
                    decreaseQliphothCounter(1);
                    break;
                }
            }
            return;
        }

        // 黑雾转移
        if (inFog) {
            fogTimer--;
            // 白色粒子团
            if (this.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD,
                        getX(), getY() + getBbHeight() * 0.5, getZ(),
                        6, 1.0, 1.0, 1.0, 0.02);
            }
            if (fogTargetReactor != null) {
                this.getNavigation().moveTo(fogTargetReactor.getX() + 0.5, fogTargetReactor.getY(),
                        fogTargetReactor.getZ() + 0.5, 1.2);
                if (blockPosition().distSqr(fogTargetReactor) < 16) fogTimer = 0;
            }
            if (fogTimer <= 0) {
                inFog = false;
                this.removeEffect(MobEffects.INVISIBILITY);
                this.removeEffect(MobEffects.GLOWING);
            }
            return;
        }

        if (attackCooldown > 0) attackCooldown--;

        // 普攻出伤帧
        if (pendingHit > 0) {
            pendingHit--;
            if (pendingHit == 0 && pendingTarget != null && pendingTarget.isAlive()
                    && this.distanceToSqr(pendingTarget) <= 25) {
                float dmg = pendingType == 0 ? (7 + random.nextInt(10)) : (10 + random.nextInt(5));
                pendingTarget.hurt(DamageHelper.getDamage(this, "red"), dmg);
                pendingTarget = null;
                setAnimation("idle");
            }
        }

        // 嚎叫阶段机
        if (howlPhase > 0) {
            tickHowl(level);
            return;
        }

        // 索敌(攻击周围所有生物)
        LivingEntity target = findTarget(level);
        if (target == null) {
            // 无目标:前往反应堆附近/原地游荡
            if (fogTargetReactor != null && blockPosition().distSqr(fogTargetReactor) > 36) {
                this.getNavigation().moveTo(fogTargetReactor.getX() + 0.5, fogTargetReactor.getY(),
                        fogTargetReactor.getZ() + 0.5, 1.0);
            }
            return;
        }
        this.getLookControl().setLookAt(target);
        double dist = this.distanceTo(target);
        if (dist <= 4.5 && attackCooldown <= 0) {
            float roll = random.nextFloat();
            if (roll < 0.40f) {
                setAnimation("attack1");
                level.playSound(null, blockPosition(), ModSounds.WOLF_CLAW.get(), SoundSource.HOSTILE, 1.3f, 1.0f);
                pendingType = 0; pendingHit = 8; pendingTarget = target;
            } else if (roll < 0.80f) {
                setAnimation("attack2");
                level.playSound(null, blockPosition(), ModSounds.WOLF_CLAW.get(), SoundSource.HOSTILE, 1.3f, 0.8f);
                pendingType = 1; pendingHit = 8; pendingTarget = target;
            } else {
                beginHowl(level);
            }
            attackCooldown = 35;
        } else if (dist > 4.5) {
            this.getNavigation().moveTo(target, 1.1);
        }
    }

    private void beginHowl(ServerLevel level) {
        howlPhase = 1;
        howlTimer = 15;
        setAnimation("skill1");
    }

    private void tickHowl(ServerLevel level) {
        this.setDeltaMovement(0, getDeltaMovement().y, 0);
        howlTimer--;
        if (howlPhase == 1 && howlTimer <= 0) {
            howlPhase = 2;
            howlTimer = 60; // 嚎叫持续3秒
            setAnimation("skill12");
            level.playSound(null, blockPosition(), ModSounds.WOLF_HOWL.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
            // 屏幕扭曲(碧蓝新星同款,持续期间;速度差异由时长近似)
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24))) {
                MessageLoader.getLoader().sendToPlayer(p, new TriggerShakePacket(howlTimer, 5.0F)); // 碧蓝新星一半强度
            }
            // 20x20 范围 30 白伤,并按造成伤害吸血
            float healed = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(10, 5, 10), this::isValidTarget)) {
                if (e instanceof ServerPlayer sp) {
                    MentalValueUtil.reduceMentalValue(sp, 30f);
                } else {
                    e.hurt(DamageHelper.getDamage(this, "white"), 30f);
                }
                healed += 30f;
                // 小红帽目标机制2:受到狼嚎伤害的小红帽强制以狼为委托目标
                if (e instanceof EntityRedHoodMercenary redhat) {
                    redhat.forceWolfTarget(this);
                }
            }
            this.heal(healed);
            // 嚎叫时血量<50% → 最近3只异想体计数器-1(按文档仅对出逃类生效)
            if (this.getHealth() < this.getMaxHealth() * 0.5f) {
                List<AbstractAbnormality> abs = level.getEntitiesOfClass(AbstractAbnormality.class,
                        getBoundingBox().inflate(64), a -> a != this && a.isAlive());
                abs.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
                for (int i = 0; i < Math.min(3, abs.size()); i++) {
                    AbstractAbnormality ab = abs.get(i);
                    if (ab.hasEscape()) {
                        ab.decreaseQliphothCounter(1);
                    }
                }
            }
            // 小红帽须知Ⅳ:附近未出逃的小红帽听到嚎叫直接突破收容
            for (EntityRedHoodMercenary redhat : level.getEntitiesOfClass(EntityRedHoodMercenary.class,
                    getBoundingBox().inflate(48), r -> r.isAlive() && !r.hasEscape())) {
                redhat.triggerEscape();
                redhat.forceWolfTarget(this);
            }
        } else if (howlPhase == 2 && howlTimer <= 0) {
            howlPhase = 3;
            howlTimer = 15;
            setAnimation("skill2");
            // 嚎叫结束:额外 15-19 物理伤害
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(10, 5, 10), this::isValidTarget)) {
                e.hurt(DamageHelper.getDamage(this, "red"), 15 + random.nextInt(5));
            }
        } else if (howlPhase == 3 && howlTimer <= 0) {
            howlPhase = 0;
            setAnimation("idle");
        }
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e instanceof AbstractAbnormality) return e instanceof EntityRedHoodMercenary; // 异想体里只与小红帽为敌
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
        // 小红帽须知Ⅴ:相遇后更具攻击性 —— 优先以小红帽为目标
        for (EntityRedHoodMercenary redhat : level.getEntitiesOfClass(EntityRedHoodMercenary.class,
                getBoundingBox().inflate(20), r -> r.isAlive() && r.hasEscape())) {
            redhat.forceWolfTarget(this);
            return redhat;
        }
        return best;
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("说实话我根本就不在乎，因为我必须是一只又大又坏的狼...");
        logs.add("员工总觉得它的肚子在动。");
        logs.add("它注视着员工的眼神，像是在打量一块肉。");
        logs.add("一些员工报告说，他们从它的腹中听到了员工的呼救声。");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityBigBadWolf> event) {
        String anim = getAnimation();
        if (!hasEscape()) {
            // 出逃前:idle / idle2(吞人后)
            if (hasSwallowedPlayers() || "idle2".equals(anim)) {
                return event.setAndContinue(RawAnimation.begin().thenLoop("idle2"));
            }
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }
        switch (anim) {
            case "attack1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack1")); }
            case "attack2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("attack2")); }
            case "skill1" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skill1")); }
            case "skill12" -> { return event.setAndContinue(RawAnimation.begin().thenLoop("skill12")); }
            case "skill2" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("skill2")); }
            case "die" -> { return event.setAndContinue(RawAnimation.begin().thenPlay("die")); }
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityBigBadWolf((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)  // 奔跑4玩家
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("LastFogHealth", lastFogHealth);
        tag.putBoolean("InFog", inFog);
        CompoundTag list = new CompoundTag();
        int i = 0;
        for (UUID id : swallowedPlayers) list.putUUID("p" + (i++), id);
        list.putInt("count", i);
        tag.put("Swallowed", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastFogHealth = tag.contains("LastFogHealth") ? tag.getFloat("LastFogHealth") : this.getMaxHealth();
        inFog = tag.getBoolean("InFog");
        swallowedPlayers.clear();
        if (tag.contains("Swallowed")) {
            CompoundTag list = tag.getCompound("Swallowed");
            int count = list.getInt("count");
            for (int i = 0; i < count; i++) {
                if (list.hasUUID("p" + i)) swallowedPlayers.add(list.getUUID("p" + i));
            }
        }
        if (!hasSwallowedPlayers()) {
            setTexture(textureForCurrentState());
        }
    }
}
