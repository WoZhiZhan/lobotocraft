package com.wzz.lobotocraft.entity.abnormality;

import com.mojang.datafixers.util.Pair;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.entity.EntityLightOrb;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.*;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FullScreenRenderMessage;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;

public class EntityEndBird extends AbstractAbnormality {

    private final Random random = new Random();

    // ══════════════════════════════════════════════════════════════
    //  常量
    // ══════════════════════════════════════════════════════════════

    /** 5 秒攻击周期（100 ticks） */
    private static final int ATTACK_CYCLE_TICKS = 100;

    /** 在同一位置累计发动几次攻击后触发瞬移 */
    private static final int ATTACKS_BEFORE_TELEPORT = 3;

    /** 普通攻击 AABB 半扩展量（格） */
    private static final double NORMAL_ATTACK_RADIUS = 3.5;

    /** 光弹攻击覆盖半径（格），对应 20×20 区域 */
    private static final double ORB_ATTACK_RADIUS = 10.0;

    /** 玩家靠近多少格时自动解除魅惑 */
    private static final double CHARM_DISPEL_RANGE = 3.5;

    // ══════════════════════════════════════════════════════════════
    //  攻击周期状态
    // ══════════════════════════════════════════════════════════════

    private int attackCycleTimer    = 0;     // 当前周期已走的 tick 数
    private int attacksAtCurrentPos = 0;     // 本位置已发动次数
    private boolean isBusy          = false; // 正在执行攻击/瞬移时为 true，阻止周期推进
    private boolean justTeleported  = false; // 刚完成瞬移，下一次强制走特殊攻击分支
    private static final int MAX_BUSY_TICKS = 300;
    /**
     * 攻击次数达到阈值时置 true；等当前攻击动画结束（isBusy 归 false）后再真正触发瞬移，
     * 避免在 isBusy=true 时立刻调 scheduleTeleport() 被 guard 拦截而永久失效。
     */
    private boolean pendingTeleport = false;

    // ══════════════════════════════════════════════════════════════
    //  魅惑状态
    // ══════════════════════════════════════════════════════════════

    /** 当前被魅惑的玩家 UUID 集合 */
    private final Set<UUID> charmedPlayers = new HashSet<>();
    private Map<String, Pair<String, BlockPos>> birdReturnInfo = new HashMap<>();
    private UUID thinDuskRewardPlayer = null;

    // ══════════════════════════════════════════════════════════════
    //  构造 & 初始化
    // ══════════════════════════════════════════════════════════════

    public EntityEndBird(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityEndBird((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-02-63";
        this.abnormalityName  = "终末鸟";
        this.riskLevel        = RiskLevel.ALEPH;
        this.damageType       = "???";
        this.maxPEOutput      = -1;

        float[] basePreferences = {-1f, -1f, -1f, -1f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ══════════════════════════════════════════════════════════════
    //  Tick 主逻辑
    // ══════════════════════════════════════════════════════════════

    @Override
    public void aiStep() {
        super.aiStep();
        if (hasEscape() && getTarget() != null) {
            this.yBodyRot = this.getYRot();
        }
    }

    private int busyWatchdog = 0;

    @Override
    public void tick() {
        super.tick();
        setNoAi(!hasEscape());

        // 朝向目标平滑旋转
        if (hasEscape() && getTarget() != null && getTarget().isAlive()) {
            double dx = getTarget().getX() - getX();
            double dz = getTarget().getZ() - getZ();
            float targetYaw  = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
            float currentYaw = getYRot();
            float deltaYaw   = Mth.wrapDegrees(targetYaw - currentYaw);
            float newYaw     = currentYaw + Mth.clamp(deltaYaw, -10.0F, 10.0F);
            setYRot(newYaw);
            setYHeadRot(newYaw);
            yBodyRot = newYaw;
        }

        if (level().isClientSide || !hasEscape()) return;
        if (isBusy) {
            busyWatchdog++;
            if (busyWatchdog >= MAX_BUSY_TICKS) {
                isBusy = false;
                pendingTeleport = false;
                busyWatchdog = 0;
                setAnimation("idle");
            }
        } else {
            busyWatchdog = 0;
        }
        // 持续刷新魅惑效果 & 接近解除检测
        tickCharmEffects();

        // ── 5 秒攻击周期 ──────────────────────────────────────────
        // isBusy 为 true 时暂停计时，等待当前攻击/瞬移动画完成
        if (!isBusy) {
            attackCycleTimer++;
            if (attackCycleTimer >= ATTACK_CYCLE_TICKS) {
                attackCycleTimer = 0;
                executeAttack(selectNextAttack());
            }
        }
    }

    /**
     * 从当前可用攻击中按权重随机选择一种。
     *
     * <ul>
     *   <li>普通攻击始终在候选池（权重 20%）。</li>
     *   <li>光弹攻击：权重 20%（需要大眼存活）。</li>
     *   <li>巨口攻击：权重 20%（需要小喙存活）。</li>
     *   <li>天平攻击：权重 10%（需要长臂存活）。</li>
     *   <li>魅惑攻击：权重 10%（需要大眼存活）。</li>
     *   <li>剩余 20% 权重由所有可用攻击均摊。</li>
     *   <li>若 {@code justTeleported == true}，强制从特殊攻击中选取，
     *       实现"传送后使用特殊攻击"；若特殊全不可用则退回普通攻击。</li>
     * </ul>
     */
    private String selectNextAttack() {
        // 构建可用攻击及其基础权重
        Map<String, Double> attackWeights = new LinkedHashMap<>();

        // 普通攻击始终可用，基础权重 20%
        attackWeights.put("normal", 0.20);

        // 光弹 & 魅惑：需要"大眼"存活
        if (isEggEyeAlive()) {
            attackWeights.put("light_orb", 0.20);
            attackWeights.put("charm", 0.10);
        }

        // 巨口：需要"小喙"存活
        if (isEggSmallAlive()) {
            attackWeights.put("giant_mouth", 0.20);
        }

        // 天平：需要"长臂"存活
        if (isEggHighAlive()) {
            attackWeights.put("scale", 0.10);
        }

        // 传送后强制从特殊攻击中选取
        if (justTeleported) {
            justTeleported = false;

            // 从可用攻击中移除普通攻击，只保留特殊攻击
            Map<String, Double> specialAttacks = new LinkedHashMap<>(attackWeights);
            specialAttacks.remove("normal");

            if (specialAttacks.isEmpty()) {
                return "normal";
            }

            // 按权重从特殊攻击中随机选择
            return weightedRandomSelect(specialAttacks);
        }

        // 计算已分配的总权重
        double allocatedWeight = attackWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        // 剩余 20% 权重均摊给所有可用攻击
        double remainingWeight = Math.max(0.0, 1.0 - allocatedWeight);
        if (remainingWeight > 0 && !attackWeights.isEmpty()) {
            double extraPerAttack = remainingWeight / attackWeights.size();
            attackWeights.replaceAll((attack, weight) -> weight + extraPerAttack);
        }

        // 按权重随机选择
        return weightedRandomSelect(attackWeights);
    }

    /**
     * 根据权重映射随机选择一个攻击类型
     *
     * @param attackWeights 攻击类型及其权重的映射，总权重应该为 1.0
     * @return 随机选择的攻击类型
     */
    private String weightedRandomSelect(Map<String, Double> attackWeights) {
        double totalWeight = attackWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        // 如果总权重为 0（所有攻击都不可用），返回普通攻击
        if (totalWeight <= 0.0) {
            return "normal";
        }

        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;

        for (Map.Entry<String, Double> entry : attackWeights.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randomValue <= cumulativeWeight) {
                return entry.getKey();
            }
        }

        // 兜底：返回第一个可用攻击
        return attackWeights.keySet().iterator().next();
    }

    /**
     * 分发攻击并统计本位置发动次数；达到阈值后触发瞬移。
     */
    private void executeAttack(String attack) {
        switch (attack) {
            case "normal"      -> performNormalAttack();
            case "light_orb"   -> performLightOrbAttack();
            case "giant_mouth" -> performGiantMouthAttack();
            case "scale"       -> performScaleAttack();
            case "charm"       -> performCharmAttack();
        }
        attacksAtCurrentPos++;
        if (attacksAtCurrentPos >= ATTACKS_BEFORE_TELEPORT) {
            attacksAtCurrentPos = 0;
            pendingTeleport = true;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ① 普通攻击（attack1 / attack2）
    // ══════════════════════════════════════════════════════════════

    /**
     * 随机选 attack1（爪子拍击）或 attack2（伸出爪子碾碎）。
     * <ol>
     *   <li>播放"伸出爪子攻击和拍击前摇"音效，切换动画。</li>
     *   <li>第 20 tick：播放"伸出爪子攻击"+"爪子拍击"音效，
     *       AABB 范围内造成 23-43 黑色伤害；玩家在 24×24 范围内触发屏幕震动。</li>
     *   <li>第 40 tick：切回 idle，释放 isBusy。</li>
     * </ol>
     */
    private void performNormalAttack() {
        isBusy = true;
        String anim = random.nextBoolean() ? "attack1" : "attack2";

        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_WILL_ATTACK_2.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        setAnimation(anim);

        TimerEntry<AbstractAbnormality> timer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull AbstractAbnormality living) {
                int exec = getExecutions();

                if (exec == 20) {
                    level().playSound(null, getX(), getY(), getZ(),
                            ModSounds.END_BIRD_ATTACK_1.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                    level().playSound(null, getX(), getY(), getZ(),
                            ModSounds.END_BIRD_ATTACK_2.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

                    AABB hitBox = getBoundingBox().inflate(NORMAL_ATTACK_RADIUS, 2.0, NORMAL_ATTACK_RADIUS);
                    int dmg = 23 + random.nextInt(21);
                    level().getEntitiesOfClass(LivingEntity.class, hitBox, EntityEndBird.this::canEndBirdDamage)
                            .forEach(e -> e.hurt(DamageHelper.getDamage(EntityEndBird.this, "black"), dmg));

                    applyScreenShake(12.0);
                }

                if (exec == 40) {
                    setAnimation("idle");
                    isBusy = false;
                    removeTimer(EntityEndBird.this.getUUID());
                    if (pendingTeleport) { pendingTeleport = false; scheduleTeleport(); }
                }
            }

            @Override
            public void onEnd(@NotNull AbstractAbnormality living) {
                // 兜底：万一 timer 提前结束，确保 isBusy 复位
                if (isBusy) {
                    setAnimation("idle");
                    isBusy = false;
                }
            }
        };
        timer.setRequireMainThread(true);
        timer.addSkillTimer(this, 0, 2000, 20);
    }

    /** 向半径 {@code radius} 格内所有 ServerPlayer 发送屏幕震动叠加层。 */
    private void applyScreenShake(double radius) {
        if (!(level() instanceof ServerLevel)) return;
        AABB box = new AABB(blockPosition()).inflate(radius);
        level().getEntitiesOfClass(ServerPlayer.class, box, p -> true).forEach(player -> {
            MessageLoader.getLoader().sendToPlayer(player, new TriggerShakePacket(20));
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  ② 光弹攻击（skilla1 → skill3 → skilla2）
    // ══════════════════════════════════════════════════════════════

    /**
     * 三段式光弹攻击：skilla1（蓄力）→ skill3（射弹）→ skilla2（收尾）。
     * <ol>
     *   <li>播放"魅惑技能(光弹前摇)"音频，切 skilla1。</li>
     *   <li>第 20 tick：切 skill3，播放"翅膀发射光弹"音频，向范围内目标分配 26 颗光弹（各 8-14 黑色）。</li>
     *   <li>第 50 tick：切 skilla2（收尾）。</li>
     *   <li>第 70 tick：切回 idle，释放 isBusy。</li>
     * </ol>
     */
    private void performLightOrbAttack() {
        isBusy = true;

        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_WILL_SKILL_3.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        setAnimation("skilla1");

        TimerEntry<AbstractAbnormality> timer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull AbstractAbnormality living) {
                int exec = getExecutions();

                if (exec == 20) {
                    setAnimation("skill3");
                    level().playSound(null, getX(), getY(), getZ(),
                            ModSounds.END_BIRD_SKILL_3.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                    fireOrbsToTargets();
                }
                if (exec == 50) {
                    setAnimation("skilla2");
                }
                if (exec == 70) {
                    setAnimation("idle");
                    isBusy = false;
                    removeTimer(EntityEndBird.this.getUUID());
                    if (pendingTeleport) { pendingTeleport = false; scheduleTeleport(); }
                }
            }

            @Override
            public void onEnd(@NotNull AbstractAbnormality living) {
                if (isBusy) {
                    setAnimation("idle");
                    isBusy = false;
                }
            }
        };
        timer.setRequireMainThread(true);
        timer.addSkillTimer(this, 0, 3500, 20);
    }

    /**
     * 将 26 颗光弹随机分配给 20×20 范围内所有出逃异想体和玩家。
     * 光球飞行到达目标时造成 8-14 黑色伤害。
     * 若范围内无目标则不产生光球。
     */
    private void fireOrbsToTargets() {
        AABB area = new AABB(blockPosition()).inflate(ORB_ATTACK_RADIUS);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, area,
                e -> canEndBirdDamage(e)
                        && (e instanceof Player
                        || (e instanceof AbstractAbnormality ab && ab.hasEscape())));

        if (targets.isEmpty()) return;

        for (int i = 0; i < 26; i++) {
            LivingEntity target = targets.get(random.nextInt(targets.size()));
            int dmg = 8 + random.nextInt(7);

            EntityLightOrb orb = new EntityLightOrb(ModEntities.light_orb.get(), level());

            double spread = 4.0;
            double ox = (random.nextDouble() - 0.5) * spread * 2;
            double oy = random.nextDouble() * 6.0;
            double oz = (random.nextDouble() - 0.5) * spread * 2;
            orb.setPos(getX() + ox, getY() + 1.5 + oy, getZ() + oz);

            orb.setTarget(target);
            orb.setDamage(dmg);
            orb.setOwner(this);
            level().addFreshEntity(orb);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ③ 巨口攻击（skill1）
    // ══════════════════════════════════════════════════════════════

    /**
     * 巨口攻击：爪子弹至身体后方、张开巨口，弹射红色粒子，对朝向 90° 扇形内目标造成 2 次 50-115 红色伤害。
     * <ol>
     *   <li>切 skill1，播放"巨口攻击"音频，发射粒子。</li>
     *   <li>第 20 tick：第 1 次伤害。</li>
     *   <li>第 35 tick：第 2 次伤害，切回 idle，释放 isBusy。</li>
     * </ol>
     */
    private void performGiantMouthAttack() {
        isBusy = true;
        setAnimation("skill1");
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_SKILL_1.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

        TimerEntry<AbstractAbnormality> timer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull AbstractAbnormality living) {
                int exec = getExecutions();
                if (exec == 49) {
                    if (level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.FALLING_LAVA,
                                getX(), getY() + 1.5, getZ(), 60, 1.2, 1.0, 1.2, 0.25);
                        serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                                getX(), getY() + 1.5, getZ(), 30, 0.8, 0.5, 0.8, 0.1);
                    }
                    ParticleUtil.spawnLineParticles(level, EntityEndBird.this, ParticleUtil.getDustParticle(1,0,0,0.8f), 100, 0.1d, 30);
                    dealGiantMouthDamage();
                }
            }

            @Override
            public void onEnd(@NotNull AbstractAbnormality living) {
                if (isBusy) {
                    setAnimation("idle");
                    isBusy = false;
                }
            }
        };
        timer.setRequireMainThread(true);
        timer.addSkillTimer(this, 0, 3250, 20);
    }

    /** 对巨口朝向 90° 扇形（半角 45°）、10 格以内的所有目标造成 50-115 随机红色伤害。 */
    private void dealGiantMouthDamage() {
        AABB area = new AABB(blockPosition()).inflate(10, 5, 10);
        int dmg = 50 + random.nextInt(66); // 50-115
        level().getEntitiesOfClass(LivingEntity.class, area,
                        e -> canEndBirdDamage(e) && isInFront(e, 45.0f, 10.0))
                .forEach(e -> e.hurt(DamageHelper.getDamage(this, "red"), dmg));
    }

    private boolean canEndBirdDamage(LivingEntity target) {
        return target != this && target.isAlive() && !isEndBirdEgg(target);
    }

    private boolean isEndBirdEgg(LivingEntity target) {
        return target instanceof EntityEndBirdEggSmall
                || target instanceof EntityEndBirdEggHigh
                || target instanceof EntityEndBirdEggEye;
    }

    /**
     * 判断目标是否在终末鸟面朝方向的扇形内。
     *
     * @param target       目标实体
     * @param halfAngleDeg 扇形半角（90° 扇形传 45）
     * @param maxDist      最大检测距离（格）
     */
    private boolean isInFront(LivingEntity target, float halfAngleDeg, double maxDist) {
        double dx   = target.getX() - getX();
        double dz   = target.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > maxDist) return false;

        double rad     = Math.toRadians(getYRot());
        double facingX = -Math.sin(rad);
        double facingZ =  Math.cos(rad);

        double dot   = (dx / dist) * facingX + (dz / dist) * facingZ;
        double angle = Math.toDegrees(Math.acos(Mth.clamp((float) dot, -1f, 1f)));
        return angle <= halfAngleDeg;
    }

    // ══════════════════════════════════════════════════════════════
    //  ④ 天平攻击（skill2）
    // ══════════════════════════════════════════════════════════════

    /**
     * 天平审判：旋转头颅审判所有人的罪恶，对设施内所有玩家造成 10-15 灵魂伤害 + 5% 最大生命值。
     * <ol>
     *   <li>播放"天平攻击前摇"音频，切 skill2。</li>
     *   <li>第 30 tick：播放"天平攻击"音频，全服伤害判定，切回 idle。</li>
     * </ol>
     */
    private void performScaleAttack() {
        isBusy = true;
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_WILL_SKILL_2.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        setAnimation("skill2");

        TimerEntry<AbstractAbnormality> timer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull AbstractAbnormality living) {
                if (getExecutions() == 30) {
                    level().playSound(null, getX(), getY(), getZ(),
                            ModSounds.END_BIRD_SKILL_2.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                    dealScaleDamage();
                    setAnimation("idle");
                    isBusy = false;
                    removeTimer(EntityEndBird.this.getUUID());
                    if (pendingTeleport) { pendingTeleport = false; scheduleTeleport(); }
                }
            }

            @Override
            public void onEnd(@NotNull AbstractAbnormality living) {
                if (isBusy) {
                    setAnimation("idle");
                    isBusy = false;
                }
            }
        };
        timer.setRequireMainThread(true);
        timer.addSkillTimer(this, 0, 1500, 20);
    }

    /** 对全服所有玩家造成 10-15 灵魂（pale）伤害 + 目标最大生命值 5% 的额外伤害。 */
    private void dealScaleDamage() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getServer().getPlayerList().getPlayers().forEach(player -> {
            float base    = 10 + random.nextInt(6);        // 10-15
            float extraHp = player.getMaxHealth() * 0.05f; // +5% 最大生命值
            player.hurt(DamageHelper.getDamage(this, "pale"), base + extraHp);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  ⑤ 魅惑攻击
    // ══════════════════════════════════════════════════════════════

    /**
     * 魅惑攻击：无动画，仅播放"魅惑技能(光弹前摇)"作为音效提示。
     *
     * <p>对附近随机 1-2 名玩家施加持续黑暗效果；
     * 终末鸟随即瞬移到距最近被魅惑玩家最近的再生反应堆或鸟蛋处。
     * 多个玩家被魅惑时取距终末鸟最近的玩家作为参照。
     *
     * <p>解除条件：
     * <ul>
     *   <li>玩家走到终末鸟 {@value #CHARM_DISPEL_RANGE} 格内自动解除。</li>
     *   <li>魅惑期间持续左键攻击终末鸟，每次 10% 概率解除（见 {@link #onPlayerClickAttemptDispel}）。</li>
     * </ul>
     *
     * <p>此攻击不占用 {@code isBusy}，攻击周期正常推进。
     */
    private void performCharmAttack() {
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_WILL_SKILL_3.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

        if (!(level() instanceof ServerLevel serverLevel)) return;

        List<ServerPlayer> nearbyPlayers = new ArrayList<>(
                serverLevel.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(blockPosition()).inflate(32),
                        p -> !p.isCreative() && !p.isSpectator()));
        if (nearbyPlayers.isEmpty()) return;

        Collections.shuffle(nearbyPlayers, random);
        List<ServerPlayer> charmed = nearbyPlayers.subList(0, Math.min(2, nearbyPlayers.size()));

        for (ServerPlayer player : charmed) {
            charmedPlayers.add(player.getUUID());
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 1, false, false));
        }

        // 瞬移到距被魅惑玩家最近的反应堆 / 鸟蛋旁
        charmed.stream()
                .min(Comparator.comparingDouble(p -> p.distanceTo(this)))
                .ifPresent(nearest -> teleportNearTarget(nearest.blockPosition()));

        // 魅惑攻击不占 isBusy，此时可以安全触发瞬移
        if (pendingTeleport) { pendingTeleport = false; scheduleTeleport(); }
    }

    /**
     * 设置三鸟信息
     */
    public void setBirdReturnInfo(Map<String, Pair<String, BlockPos>> info) {
        this.birdReturnInfo = info;
    }

    @Override
    public void setHealth(float f) {
        if (f <= 0f && this.isAlive()) {
            setAnimation("die");
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.END_BIRD_DIE.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

            if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
                // 清理追踪器
                EndBirdEggTracker.get(serverLevel).clear();

                // 解除所有魅惑
                for (UUID uuid : charmedPlayers) {
                    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) dispelCharm(player);
                }
                charmedPlayers.clear();

                for (ServerPlayer player : EntityUtil.findAllPlayer(this)) {
                    FullScreenRenderMessage msg = FullScreenRenderMessage.builder()
                            .showDuration(4000)
                            .fade(500, 1000)
                            .texture(ResourceUtil.createInstance("textures/gui/end_bird_cg/cg8.png"))
                            .build();
                    MessageLoader.getLoader().sendToPlayer(player, msg);
                    ItemUtil.addItem(player, new ItemStack(ModItems.END_BIRD_CURIO.get()));
                }
                ServerPlayer rewardPlayer = resolveThinDuskRewardPlayer(serverLevel);
                if (rewardPlayer != null) {
                    grantThinDuskEquipment(rewardPlayer);
                }

                restoreThreeBirds(serverLevel);

                // hurt() 被全部拦截导致原版 die() 永远不触发，必须手动 discard
                TimerEntry dieTimer = new TimerEntry() {
                    @Override
                    public void onRunning(@NotNull LivingEntity living) {
                        if (getExecutions() >= 60) { // 约 3 秒后消失
                            discard();
                            removeTimer(EntityEndBird.this.getUUID());
                        }
                    }
                };
                dieTimer.addSkillTimer(this, 0, 80, 1);
            }
        }
        super.setHealth(f);
    }

    private void setThinDuskRewardPlayer(@Nullable ServerPlayer player) {
        if (player != null) {
            thinDuskRewardPlayer = player.getUUID();
        }
    }

    @Nullable
    private ServerPlayer resolveThinDuskRewardPlayer(ServerLevel serverLevel) {
        if (thinDuskRewardPlayer != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(thinDuskRewardPlayer);
            if (player != null) return player;
        }
        return serverLevel.getServer().getPlayerList().getPlayers().stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(this)))
                .orElse(null);
    }

    private void grantThinDuskEquipment(ServerPlayer player) {
        ItemUtil.addItem(player, new ItemStack(ModItems.END_BIRD_WEAPON.get()));
        ItemUtil.addItem(player, new ItemStack(ModItems.END_BIRD_CHESTPLATE.get()));
        ItemUtil.addItem(player, new ItemStack(ModItems.END_BIRD_LEGGINGS.get()));
        ItemUtil.addItem(player, new ItemStack(ModItems.END_BIRD_BOOTS.get()));
        player.sendSystemMessage(Component.literal("§5你取得了薄暝的武器与装备。"));
    }

    /**
     * 恢复三鸟到出逃前的位置
     */
    private void restoreThreeBirds(ServerLevel serverLevel) {
        if (birdReturnInfo.isEmpty()) return;

        for (Map.Entry<String, Pair<String, BlockPos>> entry : birdReturnInfo.entrySet()) {
            String abnormalityCode = entry.getKey();
            BlockPos returnPos = entry.getValue().getSecond();

            if (returnPos == null) continue;

            AbstractAbnormality bird = createBirdByCode(abnormalityCode, serverLevel);
            if (bird != null) {
                bird.setPos(returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5);
                bird.setEscape(false);
                bird.setQliphothCounter(bird.getMaxQliphothCounter());
                serverLevel.addFreshEntity(bird);
            }
        }
    }

    /**
     * 根据异常体编号创建对应的鸟
     */
    @Nullable
    private AbstractAbnormality createBirdByCode(String code, ServerLevel level) {
        return switch (code) {
            // 新 key（修复后生成的存档）
            case "large_bird"    -> new EntityLargeBird(ModEntities.large_bird.get(), level);
            case "approval_bird" -> new EntityApprovalBird(ModEntities.approval_bird.get(), level);
            case "punishing_bird"-> new EntityPunishingBird(ModEntities.punishing_bird.get(), level);
            // 旧 abnormalityCode（向后兼容已有存档，按需保留）
            case "O-02-40"       -> new EntityLargeBird(ModEntities.large_bird.get(), level);
            case "O-02-56"       -> new EntityPunishingBird(ModEntities.punishing_bird.get(), level);
            default              -> null;
        };
    }

    @Override
    public boolean hasEscape() {
        return true;
    }

    /**
     * 每 tick 刷新被魅惑玩家的黑暗 buff，并检测玩家接近自动解除。
     * 已死亡 / 下线的玩家同时从集合中移除。
     */
    private void tickCharmEffects() {
        if (charmedPlayers.isEmpty()) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        charmedPlayers.removeIf(uuid -> {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                if (player != null) dispelCharm(player);
                return true;
            }

            if (player.distanceTo(this) <= CHARM_DISPEL_RANGE) {
                dispelCharm(player);
                return true;
            }

            // 刷新黑暗效果，防止自然到期
            if (!player.hasEffect(MobEffects.BLINDNESS)) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false));
            }
            return false;
        });
    }

    private void dispelCharm(ServerPlayer player) {
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    /**
     * 玩家左键攻击终末鸟时调用，每次有 10% 概率解除魅惑。
     */
    public void onPlayerClickAttemptDispel(ServerPlayer player) {
        if (!charmedPlayers.contains(player.getUUID())) return;
        if (random.nextFloat() < 0.10f) {
            charmedPlayers.remove(player.getUUID());
            dispelCharm(player);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  瞬移逻辑
    // ══════════════════════════════════════════════════════════════

    /**
     * 播放 move 动画和"使用瞬移"音效，约 1 秒后按概率选择目标点并移动：
     * <ul>
     *   <li>小喙蛋旁 30%</li>
     *   <li>长臂蛋旁 30%</li>
     *   <li>大眼蛋旁 30%</li>
     *   <li>再生反应堆旁 10%</li>
     * </ul>
     * 瞬移完成后设 {@code justTeleported = true}，令下次攻击强制走特殊攻击分支。
     */
    private void scheduleTeleport() {
        isBusy = true;

        setAnimation("move");
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.END_BIRD_TRANSFER.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

        TimerEntry<AbstractAbnormality> timer = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull AbstractAbnormality living) {
                if (getExecutions() == 20) {
                    doTeleport();
                    justTeleported = true;
                    setAnimation("idle");
                    isBusy = false;
                    removeTimer(EntityEndBird.this.getUUID());
                }
            }

            @Override
            public void onEnd(@NotNull AbstractAbnormality living) {
                if (isBusy) {
                    doTeleport();
                    justTeleported = true;
                    setAnimation("idle");
                    isBusy = false;
                }
            }
        };
        timer.addSkillTimer(this, 0, 40, 1);
    }

    /**
     * 按概率权重选择目标位置并执行瞬移。
     * 若随机到的目标不存在则通过 {@link #findAnyEggOrReactorPos()} 降级处理。
     */
    private void doTeleport() {
        float roll = random.nextFloat();

        BlockPos targetPos;
        if      (roll < 0.30f) targetPos = findEggPos(EntityEndBirdEggSmall.class); // 小喙 30%
        else if (roll < 0.60f) targetPos = findEggPos(EntityEndBirdEggHigh.class);  // 长臂 30%
        else if (roll < 0.90f) targetPos = findEggPos(EntityEndBirdEggEye.class);   // 大眼 30%
        else                   targetPos = findNearestReactorPos(blockPosition());   // 反应堆 10%

        if (targetPos == null) targetPos = findAnyEggOrReactorPos();
        if (targetPos == null) return;

        BlockPos safe = EntityUtil.findSafeGroundPosition(level(), targetPos, 5);
        setPos(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }

    /**
     * 瞬移到以 {@code reference} 为参照点、距其最近的再生反应堆或鸟蛋处（魅惑攻击专用）。
     */
    private void teleportNearTarget(BlockPos reference) {
        BlockPos best    = null;
        double   bestDist = Double.MAX_VALUE;

        // 遍历三种蛋，找距参照点最近的
        for (Class<? extends LivingEntity> cls : List.of(
                EntityEndBirdEggSmall.class,
                EntityEndBirdEggHigh.class,
                EntityEndBirdEggEye.class)) {
            for (LivingEntity egg : level().getEntitiesOfClass(cls,
                    new AABB(reference).inflate(64), LivingEntity::isAlive)) {
                double d = egg.blockPosition().distSqr(reference);
                if (d < bestDist) { bestDist = d; best = egg.blockPosition(); }
            }
        }

        // 遍历再生反应堆
        for (BlockEntity be : EntityUtil.findBlockEntities(level(), reference, 64)) {
            if (be instanceof RegenerationReactorBlockEntity) {
                double d = be.getBlockPos().distSqr(reference);
                if (d < bestDist) { bestDist = d; best = be.getBlockPos(); }
            }
        }

        if (best == null) return;
        BlockPos safe = EntityUtil.findSafeGroundPosition(level(), best, 5);
        setPos(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }

    // ══════════════════════════════════════════════════════════════
    //  位置查找辅助
    // ══════════════════════════════════════════════════════════════

    private <T extends LivingEntity> BlockPos findEggPos(Class<T> eggClass) {
        return level().getEntitiesOfClass(eggClass,
                        new AABB(blockPosition()).inflate(512), LivingEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this)))
                .map(Entity::blockPosition)
                .orElse(null);
    }

    private BlockPos findNearestReactorPos(BlockPos center) {
        BlockPos nearest = null;
        double   minDist = Double.MAX_VALUE;
        for (BlockEntity be : EntityUtil.findBlockEntities(level(), center, 300)) {
            if (be instanceof RegenerationReactorBlockEntity) {
                double d = be.getBlockPos().distSqr(center);
                if (d < minDist) { minDist = d; nearest = be.getBlockPos(); }
            }
        }
        return nearest;
    }

    @Override
    public void heal(float p_21116_) {
    }

    /** 降级查找：依次尝试所有存活的蛋，再尝试反应堆 */
    private BlockPos findAnyEggOrReactorPos() {
        for (Class<? extends LivingEntity> cls : List.of(
                EntityEndBirdEggSmall.class, EntityEndBirdEggHigh.class, EntityEndBirdEggEye.class)) {
            BlockPos pos = findEggPos(cls);
            if (pos != null) return pos;
        }
        return findNearestReactorPos(blockPosition());
    }

    // ══════════════════════════════════════════════════════════════
    //  鸟蛋存活检测
    // ══════════════════════════════════════════════════════════════

    private boolean isEggEyeAlive()   { return eggExists(EntityEndBirdEggEye.class);   }
    private boolean isEggSmallAlive() { return eggExists(EntityEndBirdEggSmall.class); }
    private boolean isEggHighAlive()  { return eggExists(EntityEndBirdEggHigh.class);  }

    private boolean eggExists(Class<? extends LivingEntity> cls) {
        return !level().getEntitiesOfClass(cls,
                new AABB(blockPosition()).inflate(512), LivingEntity::isAlive).isEmpty();
    }

    /**
     * 鸟蛋死亡时调用，扣除终末鸟血量。所有蛋死亡或血量归零时直接击杀。
     */
    public static void endBirdEggDie(Level level) {
        endBirdEggDie(level, null);
    }

    public static void endBirdEggDie(Level level, @Nullable ServerPlayer eggKiller) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            EndBirdEggTracker tracker = EndBirdEggTracker.get(serverLevel);
            if (tracker.getEndBirdUUID() != null) {
                Entity entity = serverLevel.getEntity(tracker.getEndBirdUUID());
                if (entity instanceof EntityEndBird endBird && endBird.isAlive()) {
                    float newHealth = endBird.getHealth() - 110000f;
                    if (tracker.areAllEggsDestroyed() || newHealth <= 0) {
                        endBird.setThinDuskRewardPlayer(eggKiller);
                        endBird.setHealth(0);
                    } else {
                        endBird.setHealth(newHealth);
                    }
                }
            }
            if (tracker.areAllEggsDestroyed()) tracker.clear();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  NBT 保存 / 读取
    // ══════════════════════════════════════════════════════════════

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AttacksAtPos",       attacksAtCurrentPos);
        tag.putInt("AttackCycleTimer",   attackCycleTimer);
        tag.putBoolean("JustTeleported", justTeleported);
        tag.putBoolean("PendingTeleport", pendingTeleport);
        ListTag charmedList = new ListTag();
        for (UUID uuid : charmedPlayers) charmedList.add(StringTag.valueOf(uuid.toString()));
        tag.put("CharmedPlayers", charmedList);
        CompoundTag birdInfoTag = new CompoundTag();
        for (Map.Entry<String, Pair<String, BlockPos>> entry : birdReturnInfo.entrySet()) {
            CompoundTag birdTag = new CompoundTag();
            birdTag.putString("UUID", entry.getValue().getFirst());
            BlockPos pos = entry.getValue().getSecond();
            if (pos != null) {
                birdTag.putInt("X", pos.getX());
                birdTag.putInt("Y", pos.getY());
                birdTag.putInt("Z", pos.getZ());
            }
            birdInfoTag.put(entry.getKey(), birdTag);
        }
        tag.put("BirdReturnInfo", birdInfoTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        attacksAtCurrentPos = tag.getInt("AttacksAtPos");
        attackCycleTimer    = tag.getInt("AttackCycleTimer");
        justTeleported      = tag.getBoolean("JustTeleported");
        pendingTeleport     = tag.getBoolean("PendingTeleport");
        ListTag charmedList = tag.getList("CharmedPlayers", 8);
        for (int i = 0; i < charmedList.size(); i++) {
            try { charmedPlayers.add(UUID.fromString(charmedList.getString(i))); }
            catch (IllegalArgumentException ignored) {}
        }
        birdReturnInfo.clear();
        if (tag.contains("BirdReturnInfo")) {
            CompoundTag birdInfoTag = tag.getCompound("BirdReturnInfo");
            for (String key : birdInfoTag.getAllKeys()) {
                CompoundTag birdTag = birdInfoTag.getCompound(key);
                String uuid = birdTag.getString("UUID");
                BlockPos pos = null;
                if (birdTag.contains("X")) {
                    pos = new BlockPos(birdTag.getInt("X"), birdTag.getInt("Y"), birdTag.getInt("Z"));
                }
                birdReturnInfo.put(key, Pair.of(uuid, pos));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  动画控制器
    // ══════════════════════════════════════════════════════════════

    @Override
    public String getAnimation() {
        String action = super.getAnimation();
        if (action == null || action.isEmpty()) { setAnimation("idle"); action = "idle"; }
        return action;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityEndBird> event) {
        return switch (getAnimation()) {
            case "attack1"  -> event.setAndContinue(RawAnimation.begin().thenPlay("attack1"));
            case "attack2"  -> event.setAndContinue(RawAnimation.begin().thenPlay("attack2"));
            case "skill1"   -> event.setAndContinue(RawAnimation.begin().thenPlay("skill1"));
            case "skill2"   -> event.setAndContinue(RawAnimation.begin().thenPlay("skill2"));
            case "skilla1"  -> event.setAndContinue(RawAnimation.begin().thenPlay("skilla1"));
            case "skill3"   -> event.setAndContinue(RawAnimation.begin().thenPlay("skill3"));
            case "skilla2"  -> event.setAndContinue(RawAnimation.begin().thenPlay("skilla2"));
            case "move"     -> event.setAndContinue(RawAnimation.begin().thenPlay("move"));
            case "die"      -> event.setAndContinue(RawAnimation.begin().thenPlay("die"));
            default         -> event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        };
    }

    @Override public void onQliphothMeltdown() { super.onQliphothMeltdown(); triggerEscape(); }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            EndBirdEggTracker.get(serverLevel).registerEndBird(this.getStringUUID());
        }
    }

    @Override
    public boolean hurt(DamageSource ds, float f) {
        if (ds.getEntity() instanceof ServerPlayer player) {
            this.onPlayerClickAttemptDispel(player);
        }
        return false;
    }
    @Override public boolean isPushable()                         { return false; }
    @Override public void push(double x, double y, double z)      {}
    @Override public void pushEntities()                          {}
    @Override public void doPush(Entity e)                        {}
    @Override public void push(Entity e)                          {}
    @Override public void knockback(double s, double x, double z) {}

    @Override public float getGiftProbability()           { return 0.0f; }
    @Override public int   getWeaponDevelopmentCost()     { return -1; }
    @Override public int   getWeaponDevelopmentMaxCount() { return 1; }
    @Override public int   getArmorDevelopmentCost()      { return -1; }
    @Override public int   getArmorDevelopmentMaxCount()  { return 1; }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.0f, 0, true, true, true),
                new ObservationLevelBonus(0.0f, 0),
                new ObservationLevelBonus(0.0f, 0),
                new ObservationLevelBonus(0.0f, 0)
        };
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/end_bird_curio.png"),
                "薄暝", "右背", "end_bird_curio",
                "最大生命值+7,最大精神值+7,成功率+7",
                "工作速度+7,移动速度+7,攻击速度+7",
                "玩家造成所有类型的伤害提高10%");
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/end_bird_weapon.png"),
                "薄暝", RiskLevel.ALEPH, "ALL", "18", "普通", "4格",
                getWeaponDevelopmentMaxCount(), "end_bird_weapon");
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/end_bird_armor.png"),
                "薄暝", RiskLevel.ALEPH,
                0.3f, 0.3f, 0.3f, 0.5f,
                getArmorDevelopmentMaxCount(), "end_bird");
    }

    @Override public float[] getGiftRenderOffset()   { return new float[]{1f,    0.0f, 0.0f}; }
    @Override public float[] getWeaponRenderScale()  { return new float[]{0.9f,  0.7f, 0.7f}; }
    @Override public float[] getWeaponRenderOffset() { return new float[]{13.5f, 0.0f, 0.0f}; }

    @Override public String  name()                               { return "end_bird"; }
    @Override public boolean onOpenWorkScreen(ServerPlayer p)     { return false; }
    @Override public void    attackPlayerOnFailure(Player p, WorkType t) {}
    @Override public int     getBasicInfoCost()                   { return 0; }
    @Override public int     getWorkPreferencesCost()             { return 0; }
    @Override public int     getSensitiveInfoCost()               { return 0; }
    @Override public int     getManualCost(int i)                 { return 0; }

    // ══════════════════════════════════════════════════════════════
    //  属性
    // ══════════════════════════════════════════════════════════════

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,          330000D)
                .add(Attributes.FLYING_SPEED,         0.0D)
                .add(Attributes.MOVEMENT_SPEED,       0.0D)
                .add(Attributes.ATTACK_DAMAGE,        1.0D)
                .add(Attributes.ARMOR,                0.0D)
                .add(Attributes.FOLLOW_RANGE,         128.0D)
                .add(ForgeMod.ENTITY_REACH.get(),     3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(),   0.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(),  0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1024D);
    }
}
