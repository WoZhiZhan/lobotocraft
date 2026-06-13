package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ExplosionPacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EntityFourthMatchFlame extends AbstractAbnormality {
    private final Random random = new Random();

    // 数据同步器
    private static final EntityDataAccessor<Boolean> DATA_EXPLODING =
            SynchedEntityData.defineId(EntityFourthMatchFlame.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_EXPLOSION_TIMER =
            SynchedEntityData.defineId(EntityFourthMatchFlame.class, EntityDataSerializers.INT);

    // 自爆参数
    private static final int EXPLOSION_COUNTDOWN = 100; // 5秒 = 100 ticks
    private static final double EXPLOSION_TRIGGER_DISTANCE = 5.0;
    private static final double EXPLOSION_RANGE = 32.0;
    private static final float EXPLOSION_DAMAGE = 300.0f;

    // 目标玩家
    private UUID targetPlayerUUID = null;
    private ServerPlayer cachedTargetPlayer = null;

    public EntityFourthMatchFlame(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityFourthMatchFlame((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIMATION, "idle");
        this.entityData.define(DATA_EXPLODING, false);
        this.entityData.define(DATA_EXPLOSION_TIMER, 0);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "F-01-02";
        this.abnormalityName = "焦化少女";
        this.riskLevel = RiskLevel.TETH;
        this.damageType = "RED";
        this.maxPEOutput = 12;

        float[] basePreferences = {0.4f, 0.6f, 0.3f, 0.5f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, -0.1f, -0.1f, -0.1f};
        levelModifiers[2] = new float[] {0.0f, -0.15f, -0.3f, -0.7f, -0.8f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, -0.1f, -0.1f, -0.1f};
        return levelModifiers;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TargetPlayerGoal(this)); // 追踪目标玩家
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        float f = this.random.nextFloat();
        if (result == WorkResult.NORMAL && f <= 0.5f) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§c因你的工作失误，焦化少女的计数器减少了..."), false);
        }
        if (result == WorkResult.BAD && f <= 0.7f) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.literal("§c因你的工作失误，焦化少女的计数器减少了..."), false);
        }
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        triggerEscape();
    }

    @Override
    public void onBadWork(ServerPlayer player) {
    }

    @Override
    public void onGoodWork(ServerPlayer player) {
        super.onGoodWork(player);
        increaseQliphothCounter(1);
    }

    @Override
    public void triggerEscape() {
        super.triggerEscape();
        broadcastMessage("§c§l警告！焦化少女已经出逃！");

        // 随机选择一名玩家作为目标
        if (level() instanceof ServerLevel serverLevel) {
            selectRandomTarget(serverLevel);
        }
    }

    @Override
    public void stopEscape() {
        super.stopEscape();
        setTarget(null);

        // 清除目标和自爆状态
        targetPlayerUUID = null;
        cachedTargetPlayer = null;
        setExploding(false);
        setExplosionTimer(0);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            // 客户端特效
            if (isExploding()) {
                spawnExplosionParticles();
            }
            return;
        }

        if (!hasEscape()) {
            setNoAi(true);
            if (!getAnimation().equals("idle")) {
                setAnimation("idle");
            }
            return;
        }

        setNoAi(false);

        // 出逃后的逻辑
        handleEscapeLogic();
    }

    /**
     * 处理出逃后的逻辑
     */
    private void handleEscapeLogic() {
        // 更新目标玩家缓存
        updateTargetPlayer();

        if (cachedTargetPlayer == null) {
            // 目标玩家消失，重新选择
            if (level() instanceof ServerLevel serverLevel) {
                selectRandomTarget(serverLevel);
            }
            return;
        }

        // 计算与目标玩家的距离
        double distance = this.distanceTo(cachedTargetPlayer);

        if (isExploding()) {
            // 已经在自爆倒计时
            handleExplosionCountdown();
        } else {
            // 检查是否触发自爆
            if (distance <= EXPLOSION_TRIGGER_DISTANCE) {
                startExplosionCountdown();
            }
        }
    }

    /**
     * 随机选择一名玩家作为目标
     */
    private void selectRandomTarget(ServerLevel serverLevel) {
        List<ServerPlayer> players = serverLevel.getPlayers(p -> !p.isSpectator() && !p.isCreative());

        if (players.isEmpty()) {
            return;
        }

        ServerPlayer target = players.get(random.nextInt(players.size()));
        targetPlayerUUID = target.getUUID();
        cachedTargetPlayer = target;

        broadcastMessage("§c焦化少女锁定了 " + target.getName().getString() + "！");
    }

    /**
     * 更新目标玩家缓存
     */
    private void updateTargetPlayer() {
        if (targetPlayerUUID == null) {
            cachedTargetPlayer = null;
            return;
        }

        if (cachedTargetPlayer != null && cachedTargetPlayer.getUUID().equals(targetPlayerUUID)) {
            // 检查玩家是否还有效
            if (cachedTargetPlayer.isRemoved() || !cachedTargetPlayer.isAlive()) {
                cachedTargetPlayer = null;
                targetPlayerUUID = null;
            }
            return;
        }

        // 重新查找玩家
        if (level() instanceof ServerLevel serverLevel) {
            cachedTargetPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(targetPlayerUUID);
            if (cachedTargetPlayer == null) {
                targetPlayerUUID = null;
            }
        }
    }

    /**
     * 开始自爆倒计时
     */
    private void startExplosionCountdown() {
        setExploding(true);
        setExplosionTimer(EXPLOSION_COUNTDOWN);

        // 停止移动
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        setAnimation("idle");
        broadcastMessage("§4§l危险！焦化少女开始自爆倒计时！");

        // 播放警告音效
        level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.FOURTH_MATCH_FLAME.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
    }

    /**
     * 处理自爆倒计时
     */
    private void handleExplosionCountdown() {
        int timer = getExplosionTimer();

        if (timer <= 0) {
            // 爆炸！
            explode();
            return;
        }

        // 倒计时-1
        setExplosionTimer(timer - 1);

        // 每秒播放一次提示音
        if (timer % 20 == 0) {
            level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.HOSTILE, 1.0f, 2.0f);
        }
    }

    /**
     * 爆炸逻辑
     */
    private void explode() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 播放爆炸音效
        level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0f, 1.0f);

        // 对范围内玩家和村民造成伤害
        AABB damageArea = new AABB(
                this.getX() - EXPLOSION_RANGE, this.getY() - EXPLOSION_RANGE, this.getZ() - EXPLOSION_RANGE,
                this.getX() + EXPLOSION_RANGE, this.getY() + EXPLOSION_RANGE, this.getZ() + EXPLOSION_RANGE
        );

        // 伤害玩家
        List<Player> players = level().getEntitiesOfClass(Player.class, damageArea);
        for (Player player : players) {
            if (!player.isSpectator() && !player.isCreative()) {
                player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), EXPLOSION_DAMAGE);
            }
        }
        List<Player> players2 = EntityUtil.findPlayersAround(this, 64, 100);
        for (Player player : players2) {
            if (player instanceof ServerPlayer serverPlayer) {
                MessageLoader.getLoader().sendToPlayer(serverPlayer, new ExplosionPacket(this.getX(), this.getY() + 1, this.getZ(), 60, 40));
            }
        }

        // 伤害村民
        List<Villager> villagers = level().getEntitiesOfClass(Villager.class, damageArea);
        for (Villager villager : villagers) {
            villager.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), EXPLOSION_DAMAGE);
        }

        // 在原位置重生新的焦化少女
        respawnAtEscapePosition(serverLevel);

        // 移除当前实体
        this.discard();
    }

    /**
     * 客户端特效：自爆倒计时粒子
     */
    private void spawnExplosionParticles() {
        if (random.nextFloat() < 0.3f) {
            double offsetX = (random.nextDouble() - 0.5) * 0.5;
            double offsetY = random.nextDouble();
            double offsetZ = (random.nextDouble() - 0.5) * 0.5;

            level().addParticle(
                    ParticleTypes.FLAME,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0, 0.05, 0
            );
        }

        if (random.nextFloat() < 0.2f) {
            level().addParticle(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + 0.5,
                    this.getZ(),
                    0, 0.1, 0
            );
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!level().isClientSide && hasEscape()) {
            // 出逃状态下被打死
            if (isExploding()) {
                // 自爆倒计时中被打死 = 镇压成功
                broadcastMessage("§a焦化少女已被镇压！");

                // 取消爆炸
                setExploding(false);
                setExplosionTimer(0);
            } else {
                // 追踪途中被打死 = 镇压成功
                broadcastMessage("§a焦化少女已被镇压！");
            }
        }

        super.die(damageSource);
    }

    // ========== 数据访问器方法 ==========

    public boolean isExploding() {
        return this.entityData.get(DATA_EXPLODING);
    }

    public void setExploding(boolean exploding) {
        this.entityData.set(DATA_EXPLODING, exploding);
    }

    public int getExplosionTimer() {
        return this.entityData.get(DATA_EXPLOSION_TIMER);
    }

    public void setExplosionTimer(int timer) {
        this.entityData.set(DATA_EXPLOSION_TIMER, timer);
    }

    public ServerPlayer getTargetPlayer() {
        return cachedTargetPlayer;
    }

    // ========== AI 目标：追踪目标玩家 ==========

    private static class TargetPlayerGoal extends Goal {
        private final EntityFourthMatchFlame entity;

        public TargetPlayerGoal(EntityFourthMatchFlame entity) {
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            // 只在出逃且未自爆时追踪
            return entity.hasEscape() && !entity.isExploding() && entity.getTargetPlayer() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            entity.setAnimation("walk");
        }

        @Override
        public void tick() {
            ServerPlayer target = entity.getTargetPlayer();
            if (target == null) {
                return;
            }
            if (!entity.getAnimation().equals("walk")) {
                entity.setAnimation("walk");
            }
            entity.getNavigation().moveTo(target, 1.2);
            entity.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        @Override
        public void stop() {
            entity.getNavigation().stop();
            entity.setAnimation("idle");
        }
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("“焦化少女”一看到员工就躲进了角落里。");
        logs.add("“焦化少女”一边退到墙边，一边观察着员工的动作。");
        logs.add("“焦化少女”站在角落里注视着员工的工作。");
        logs.add("“焦化少女”看起来很害怕，它把头转向墙角。");
        logs.add("“焦化少女”一动不动地站着，就仿佛身上燃烧的火焰一点儿也不痛。");
        logs.add("贯穿“焦化少女”的火柴正在剧烈地燃烧。");
        logs.add("与火柴上疯狂燃烧的火焰不同，“焦化少女”一动不动地站在收容单元中。");
        logs.add("火柴燃烧着，但女孩丝毫不为所动。");
        logs.add("员工正仔细检查着火柴仍未燃烧的部分。");
        logs.add("如果火柴近乎烧光，那就会发生非常危险的事件。员工正紧张地看着“焦化少女”。");
        logs.add("幸运的是，火柴还能烧上一段时间。但是当它烧尽的时候...");
        logs.add("灰烬四散，一半的火柴似乎仍未燃烧。");
        logs.add("员工完成了工作，火柴上的火焰正在轻轻地摇曳着。");
        logs.add("火焰中没有任何东西，但“焦化少女”的眼睛闪烁着光芒，就仿佛看到了某人。");
        return logs;
    }

    @Override
    public int getBasicInfoCost() {
        return 12;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 4;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 12;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 4;
    }

    @Override
    public String name() {
        return "fourth_match_flame";
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 2.0f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
    }

    @Override
    public void playAttackSound() {
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/fourth_match_flame_curio.png"),
                "终末火柴之光",
                "口部",
                "fourth_match_flame_curio",
                "最大生命值+4"
        );
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.05f, 0),
                new ObservationLevelBonus(0.0f, 5, true, false, false),
                new ObservationLevelBonus(0.05f, 0, false, true, false),
                new ObservationLevelBonus(0.0f, 5, false, false, true)
        };
    }

    @Override
    public float getGiftProbability() {
        return 0.02f;
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 35;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 25;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/fourth_match_flame_weapon.png"),
                "终末火柴之光",
                getRiskLevel(),
                "RED",
                "20-30",
                "极慢",
                "远",
                getWeaponDevelopmentMaxCount(),
                "fourth_match_flame_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/fourth_match_flame_armor.png"),
                "终末火柴之光",
                getRiskLevel(),
                0.6f,
                1.0f,
                1.2f,
                2.0f,
                getArmorDevelopmentMaxCount(),
                "fourth_match_flame"
        );
    }

    @Override
    public float[] getGiftRenderOffset() {
        return new float[] {1f, 0.0f, 0.0f};
    }

    @Override
    public float[] getWeaponRenderScale() {
        return new float[] {0.9f, 0.7f, 0.7f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {13.5f, 0.0f, 0.0f};
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.scorched_girl.idle");
    private static final RawAnimation WALK =
            RawAnimation.begin().thenLoop("animation.scorched_girl.walk");

    private PlayState animationPredicate(AnimationState<EntityFourthMatchFlame> event) {
        String currentAnim = getAnimation();
        if ("walk".equals(currentAnim)) {
            return event.setAndContinue(WALK);
        }
        return event.setAndContinue(IDLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D) // 增加追踪范围
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Exploding", isExploding());
        tag.putInt("ExplosionTimer", getExplosionTimer());
        if (targetPlayerUUID != null) {
            tag.putUUID("TargetPlayerUUID", targetPlayerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setExploding(tag.getBoolean("Exploding"));
        setExplosionTimer(tag.getInt("ExplosionTimer"));
        if (tag.hasUUID("TargetPlayerUUID")) {
            targetPlayerUUID = tag.getUUID("TargetPlayerUUID");
        }
    }
}