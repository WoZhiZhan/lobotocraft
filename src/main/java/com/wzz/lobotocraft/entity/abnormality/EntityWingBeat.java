package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
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

public class EntityWingBeat extends AbstractAbnormality {
    private static final EntityDataAccessor<Boolean> DATA_SMALL =
            SynchedEntityData.defineId(EntityWingBeat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_LEADER =
            SynchedEntityData.defineId(EntityWingBeat.class, EntityDataSerializers.BOOLEAN);

    public EntityWingBeat(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    private int playerID;
    private Player player;
    private int tick;
    private float orbitAngle = 0.0F;       // 当前角度
    private float orbitRadius = 1.5F;      // 轨道半径
    private float orbitHeight = 0.5F;      // 轨道高度（相对于玩家）
    private float orbitSpeed = 1.5F;       // 旋转速度
    private int orbitOffset = 0;           // 偏移量，用于防止所有小精灵同步

    @Override
    protected void initializeAbnormality() {
        // 基础信息
        this.abnormalityCode = "F-04-83";
        this.abnormalityName = "精灵盛宴";
        this.riskLevel = RiskLevel.ZAYIN;
        this.damageType = "RED";
        this.maxPEOutput = 10;

        // 工作偏好（基础成功率）
        float[] basePreferences = {0.7f, 0.5f, 0.7f, 0.5f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter();

        this.orbitAngle = this.random.nextFloat() * 360.0F;
        this.orbitOffset = this.random.nextInt(20);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SMALL, false);
        this.entityData.define(DATA_IS_LEADER, false);
    }

    public boolean isLeader() {
        return this.entityData.get(DATA_IS_LEADER);
    }

    public void setLeader(boolean value) {
        this.entityData.set(DATA_IS_LEADER, value);
    }

    public boolean isSmall() {
        return this.entityData.get(DATA_SMALL);
    }

    public void setSmall(boolean value) {
        this.entityData.set(DATA_SMALL, value);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.player != null) {
            tag.putInt("PlayerID", this.playerID);
            tag.putInt("Tick", this.tick);
        }
        tag.putFloat("OrbitAngle", this.orbitAngle);
        tag.putFloat("OrbitRadius", this.orbitRadius);
        tag.putFloat("OrbitHeight", this.orbitHeight);
        tag.putFloat("OrbitSpeed", this.orbitSpeed);
        tag.putInt("OrbitOffset", this.orbitOffset);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int savedPlayerID = tag.getInt("PlayerID");
        if (savedPlayerID != 0) {
            this.playerID = savedPlayerID;
            Entity entity = this.level.getEntity(this.playerID);
            if (entity instanceof Player p) {
                this.tick = tag.getInt("Tick");
                this.player = p;
            } else {
                setPlayer(null);
            }
        } else {
            setPlayer(null);
        }
        if (tag.contains("OrbitAngle")) {
            this.orbitAngle = tag.getFloat("OrbitAngle");
            this.orbitRadius = tag.getFloat("OrbitRadius");
            this.orbitHeight = tag.getFloat("OrbitHeight");
            this.orbitSpeed = tag.getFloat("OrbitSpeed");
            this.orbitOffset = tag.getInt("OrbitOffset");
        }
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float baseDamage = 1f + random.nextInt(2) + 1;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:" + this.damageType.toLowerCase()), baseDamage);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, -0.1f, -0.2f, -0.2f, -0.2f};
        levelModifiers[2] = new float[] {0.0f, -0.1f, -0.2f, -0.2f, -0.2f};
        levelModifiers[3] = new float[] {0.0f, -0.1f, -0.2f, -0.2f, -0.2f};
        return levelModifiers;
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/wingbeat_curio.png"),
                "翅振",
                "手套",
                "wingbeat_curio",
                "成功率+2",
                "工作速度+2"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/gui/ego/wingbeat_weapon.png"),
                "翅振",
                getRiskLevel(),
                "RED",
                "5-7",
                "2s",
                "近",
                getWeaponDevelopmentMaxCount(),
                "wingbeat_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/wingbeat_armor.png"),
                "翅振",
                getRiskLevel(),
                0.8f,
                0.8f,
                1.0f,
                2.0f,
                getArmorDevelopmentMaxCount(),
                "wingbeat"
        );
    }

    @Override
    public int getWeaponDevelopmentCost() { return 15; }

    @Override
    public int getArmorDevelopmentCost() { return 10; }

    @Override
    public int getBasicInfoCost() { return 8; }

    @Override
    public int getWorkPreferencesCost() { return 2; }

    @Override
    public int getSensitiveInfoCost() { return 8; }

    @Override
    public int getManualCost(int manualIndex) { return 3; }

    @Override
    public void onGoodWork(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§a你获得了祝福！"));
        player.getPersistentData().putBoolean("isInWingBeat", true);
        this.setPlayer(player);
        for (int i = 0; i < 3; i++) {
            EntityWingBeat wingBeat = new EntityWingBeat(ModEntities.wingbeat.get(), this.level);
            wingBeat.setPos(
                    player.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                    player.getY() + 1.0,
                    player.getZ() + (this.random.nextDouble() - 0.5) * 2.0
            );
            wingBeat.setSmall(true);
            wingBeat.setPlayer(player);
            wingBeat.orbitAngle = i * 120.0F;
            wingBeat.orbitRadius = 1.0F + this.random.nextFloat() * 0.5F;
            wingBeat.orbitHeight = 0.3F + this.random.nextFloat() * 0.4F;
            wingBeat.orbitSpeed = 1.0F + this.random.nextFloat() * 1.0F;
            wingBeat.setLeader(i == 0);
            this.level.addFreshEntity(wingBeat);
        }
    }

    @Override
    public void onNormalWork(ServerPlayer player) {
        onGoodWork(player);
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        player.playNotifySound(ModSounds.WINGBEAT_FEAST.get(), SoundSource.RECORDS, 1.0F, 1.0F);
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("员工可能会因为自己的独特之处而引起小精灵们的注意。");
        logs.add("大多数员工都不明白“小精灵的祝福”意味着什么。");
        logs.add("它们会用我们无法理解的独有语言进行交流。");
        logs.add("但凡你有读过一点书，都该知道小精灵们最讨厌那些利用自己善意的狡猾鼠辈。");
        return logs;
    }

    @Override
    public String name() { return "wingbeat"; }

    public Player getPlayer() { return player; }

    public void setPlayer(Player player) {
        this.player = player;
        if (player != null) {
            this.playerID = player.getId();
            this.tick = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide && this.player != null) {
            if (this.player.isDeadOrDying()) {
                this.player.getPersistentData().putBoolean("isInWingBeat", false);
                setPlayer(null);
                for (Entity entity : EntityUtil.findAllEntities(this, 100)) {
                    if (entity instanceof EntityWingBeat wingBeat && wingBeat.isSmall() && wingBeat.player != null) {
                        wingBeat.discard();
                    }
                }
                return;
            }
            // 玩家离开了盛宴所在维度,或距离过远(例如出了公司),立即结束舞会,
            // 避免舞会因实体停止tick而无法结束、留下残留特效、回来后误触机制杀
            if (this.player.level() != this.level || this.player.distanceToSqr(this) > 64 * 64) {
                this.player.getPersistentData().putBoolean("isInWingBeat", false);
                Player leaving = this.player;
                setPlayer(null);
                for (Entity entity : EntityUtil.findAllEntities(this, 100)) {
                    if (entity instanceof EntityWingBeat wingBeat && wingBeat.isSmall()) {
                        wingBeat.discard();
                    }
                }
                this.discard();
                return;
            }
            this.tick++;

            if (this.isSmall()) {
                performOrbit();
                if (this.tickCount % 20 == 0) {
                    this.player.heal(this.player.getMaxHealth() * 0.1f);
                }
                if (this.tick >= 160) {
                    this.discard();
                }
            } else {
                if (this.tick >= 100) {
                    this.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255));
                    this.player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10, 255));
                }
                if (this.tick >= 160) {
                    this.tick = 0;
                    this.player.sendSystemMessage(Component.literal("§a精灵的舞会结束了..."));
                    this.player.getPersistentData().putBoolean("isInWingBeat", false);
                    setPlayer(null);
                }
            }
        }
        if (this.level.isClientSide && this.player != null && this.isSmall() && this.isLeader()) {
            if (this.tickCount % 20 == 0) {
                this.player.playSound(ModSounds.WINGBEAT_HEAL_HEALTH.get());
                ParticleUtil.spawnParticlesAroundEntity(this.player, ParticleTypes.HEART, 5, 0.1D);
            }
        }
    }

    private void performOrbit() {
        if (this.player == null || !this.player.isAlive()) {
            return;
        }

        this.orbitAngle += this.orbitSpeed;
        if (this.orbitAngle >= 360.0F) {
            this.orbitAngle -= 360.0F;
        }

        float radiusVariation = 0.2F * (float) Math.sin((this.tickCount + this.orbitOffset) * 0.1F);
        float heightVariation = 0.1F * (float) Math.cos((this.tickCount + this.orbitOffset) * 0.05F);

        double angleRad = Math.toRadians(this.orbitAngle);
        double offsetX = Math.cos(angleRad) * (this.orbitRadius + radiusVariation);
        double offsetZ = Math.sin(angleRad) * (this.orbitRadius + radiusVariation);
        double offsetY = this.orbitHeight + heightVariation;

        double targetX = this.player.getX() + offsetX;
        double targetY = this.player.getY() + offsetY;
        double targetZ = this.player.getZ() + offsetZ;

        double dx = targetX - this.getX();
        double dy = targetY - this.getY();
        double dz = targetZ - this.getZ();

        double speed = 0.3D;
        this.setDeltaMovement(dx * speed, dy * speed, dz * speed);

        this.getLookControl().setLookAt(this.player, 30.0F, 30.0F);
        this.moveTo(targetX, targetY, targetZ, this.getYRot(), this.getXRot());

        if (this.distanceToSqr(this.player) > 16.0D) {
            this.teleportTo(this.player.getX(), this.player.getY(), this.player.getZ());
        }
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "dj", 0, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<EntityWingBeat> event) {
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.fairy_festival.idle"));
    }

    @Override
    public boolean canEscape() { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.02D)
                .add(Attributes.FLYING_SPEED, 0.02D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }
}
