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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import java.util.UUID;

public class EntityWingBeat extends AbstractAbnormality {
    public static final String ACTIVE_FLAG = "isInWingBeat";
    public static final String SESSION_KEY = "lobotocraft_wingbeat_session";
    public static final String SESSION_EXPIRY_KEY = "lobotocraft_wingbeat_expires_at";
    private static final long SESSION_DURATION_TICKS = 160L;
    private static final EntityDataAccessor<Boolean> DATA_SMALL =
            SynchedEntityData.defineId(EntityWingBeat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_LEADER =
            SynchedEntityData.defineId(EntityWingBeat.class, EntityDataSerializers.BOOLEAN);

    public EntityWingBeat(EntityType<? extends TamableAnimal> p_21803_, Level p_21804_) {
        super(p_21803_, p_21804_);
    }

    private int playerID;
    private Player player;
    private UUID playerUUID;
    private UUID sessionUUID;
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
        tag.putBoolean("IsSmall", this.isSmall());
        if (this.player != null || playerUUID != null) {
            tag.putInt("PlayerID", this.playerID);
            tag.putInt("Tick", this.tick);
        }
        if (playerUUID != null) tag.putUUID("PlayerUUID", playerUUID);
        if (sessionUUID != null) tag.putUUID("WingBeatSession", sessionUUID);
        tag.putFloat("OrbitAngle", this.orbitAngle);
        tag.putFloat("OrbitRadius", this.orbitRadius);
        tag.putFloat("OrbitHeight", this.orbitHeight);
        tag.putFloat("OrbitSpeed", this.orbitSpeed);
        tag.putInt("OrbitOffset", this.orbitOffset);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("IsSmall")) {
            this.setSmall(tag.getBoolean("IsSmall"));
        }
        playerUUID = tag.hasUUID("PlayerUUID") ? tag.getUUID("PlayerUUID") : null;
        sessionUUID = tag.hasUUID("WingBeatSession") ? tag.getUUID("WingBeatSession") : null;
        this.tick = tag.getInt("Tick");
        int savedPlayerID = tag.getInt("PlayerID");
        if (savedPlayerID != 0) {
            this.playerID = savedPlayerID;
            Entity entity = this.level.getEntity(this.playerID);
            if (entity instanceof Player p && (playerUUID == null || playerUUID.equals(p.getUUID()))) {
                this.player = p;
            } else {
                this.player = null;
            }
        } else {
            this.player = null;
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
        clearSession(player);
        UUID newSession = UUID.randomUUID();
        player.getPersistentData().putBoolean(ACTIVE_FLAG, true);
        player.getPersistentData().putUUID(SESSION_KEY, newSession);
        player.getPersistentData().putLong(SESSION_EXPIRY_KEY,
                player.server.overworld().getGameTime() + SESSION_DURATION_TICKS);
        this.sessionUUID = newSession;
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
            wingBeat.sessionUUID = newSession;
            wingBeat.setNoGravity(true);
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
            this.playerUUID = player.getUUID();
            this.tick = 0;
        }
    }

    private void clearOwnerBinding() {
        this.player = null;
        this.playerID = 0;
        this.playerUUID = null;
        this.sessionUUID = null;
        this.tick = 0;
    }

    public UUID getWingBeatOwnerUUID() { return playerUUID; }

    public UUID getWingBeatSessionUUID() { return sessionUUID; }

    public static void clearSession(ServerPlayer player) {
        UUID owner = player.getUUID();
        List<EntityWingBeat> boundEntities = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityWingBeat wingBeat
                        && owner.equals(wingBeat.getWingBeatOwnerUUID())) {
                    boundEntities.add(wingBeat);
                }
            }
        }
        for (EntityWingBeat wingBeat : boundEntities) {
            if (wingBeat.isSmall()) wingBeat.discard();
            else wingBeat.clearOwnerBinding();
        }
        player.getPersistentData().putBoolean(ACTIVE_FLAG, false);
        player.getPersistentData().remove(SESSION_KEY);
        player.getPersistentData().remove(SESSION_EXPIRY_KEY);
    }

    public static void clearSessionIfMatches(ServerPlayer player, UUID expectedSession) {
        CompoundTag data = player.getPersistentData();
        if (expectedSession != null && data.hasUUID(SESSION_KEY)
                && expectedSession.equals(data.getUUID(SESSION_KEY))) {
            clearSession(player);
        }
    }

    public static boolean expireSessionIfNeeded(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        UUID session = data.hasUUID(SESSION_KEY) ? data.getUUID(SESSION_KEY) : null;
        long expiresAt = data.getLong(SESSION_EXPIRY_KEY);
        long now = player.server.overworld().getGameTime();
        if (WingBeatSessionPolicy.isActive(data.getBoolean(ACTIVE_FLAG), session, expiresAt, now)) {
            return false;
        }
        if (data.getBoolean(ACTIVE_FLAG) || session != null || data.contains(SESSION_EXPIRY_KEY)) {
            clearSession(player);
        }
        return true;
    }

    public static boolean recoverOrClearSession(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        UUID session = data.hasUUID(SESSION_KEY) ? data.getUUID(SESSION_KEY) : null;
        if (!WingBeatSessionPolicy.isActive(data.getBoolean(ACTIVE_FLAG), session,
                data.getLong(SESSION_EXPIRY_KEY), player.server.overworld().getGameTime())) {
            clearSession(player);
            return false;
        }
        List<EntityWingBeat> staleEntities = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityWingBeat wingBeat && wingBeat.isSmall()
                        && player.getUUID().equals(wingBeat.getWingBeatOwnerUUID())) {
                    if (session.equals(wingBeat.getWingBeatSessionUUID()) && wingBeat.isAlive()) {
                    } else {
                        staleEntities.add(wingBeat);
                    }
                }
            }
        }
        staleEntities.forEach(Entity::discard);
        return true;
    }

    private static void clearPlayerSessionFlag(Player player) {
        player.getPersistentData().putBoolean(ACTIVE_FLAG, false);
        player.getPersistentData().remove(SESSION_KEY);
        player.getPersistentData().remove(SESSION_EXPIRY_KEY);
    }

    private boolean matchesPlayerSession(Player candidate) {
        CompoundTag data = candidate.getPersistentData();
        UUID activeSession = data.hasUUID(SESSION_KEY) ? data.getUUID(SESSION_KEY) : null;
        long now = candidate instanceof ServerPlayer serverPlayer
                ? serverPlayer.server.overworld().getGameTime() : Long.MIN_VALUE;
        return WingBeatSessionPolicy.canRebind(sessionUUID, data.getBoolean(ACTIVE_FLAG), activeSession)
                && data.getLong(SESSION_EXPIRY_KEY) > now;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && !isSmall() && reason.shouldDestroy()
                && playerUUID != null && sessionUUID != null
                && level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (owner != null) clearSessionIfMatches(owner, sessionUUID);
        }
        super.remove(reason);
    }

    private void finishSession(boolean notifyPlayer) {
        Player owner = this.player;
        if (owner != null) {
            if (owner instanceof ServerPlayer serverPlayer) clearSession(serverPlayer);
            else {
                clearAllWingBeats();
                clearPlayerSessionFlag(owner);
            }
            if (notifyPlayer) {
                owner.sendSystemMessage(Component.literal("§a精灵的舞会结束了..."));
            }
        }
        clearOwnerBinding();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide && this.player == null && playerUUID != null
                && this.level instanceof ServerLevel serverLevel) {
            ServerPlayer candidate = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (candidate != null) {
                if (!matchesPlayerSession(candidate)) {
                    if (this.isSmall()) this.discard();
                    else clearOwnerBinding();
                    return;
                }
                this.player = candidate;
                this.playerID = candidate.getId();
            }
        }
        if (!this.level.isClientSide && this.player != null && !matchesPlayerSession(this.player)) {
            if (this.isSmall()) this.discard();
            else clearOwnerBinding();
            return;
        }
        boolean canRun = false;
        if (!this.level.isClientSide && this.player != null) {
            if (this.player.isDeadOrDying()) {
                if (!this.isSmall()) {
                    finishSession(true);
                } else {
                    clearPlayerSessionFlag(this.player);
                    clearOwnerBinding();
                    this.discard();
                }
                return;
            }
            if (this.player.level() != this.level) {
                if (!this.isSmall()) {
                    finishSession(true);
                } else {
                    clearPlayerSessionFlag(this.player);
                    clearOwnerBinding();
                    this.discard();
                }
                return;
            }
            this.tick++;
            if (this.isSmall()) {
                canRun = true;
                performOrbit();
                if (this.tickCount % 20 == 0) {
                    this.player.heal(this.player.getMaxHealth() * 0.1f);
                }
                if (this.tick >= 160) {
                    this.discard();
                }
            } else {
                if (this.tick >= 160) {
                    finishSession(true);
                }
            }
        }
        if (this.level.isClientSide && this.player != null && this.isSmall()) {
            if (canRun)
                performOrbit();
            if (this.tickCount % 20 == 0 && this.isLeader()) {
                this.player.playSound(ModSounds.WINGBEAT_HEAL_HEALTH.get());
                ParticleUtil.spawnParticlesAroundEntity(this.player, ParticleTypes.HEART, 5, 0.1D);
            }
        }
    }

    /**
     * 清除所有小精灵
     */
    private void clearAllWingBeats() {
        for (Entity entity : EntityUtil.findAllEntities(this, 100)) {
            if (entity instanceof EntityWingBeat wingBeat && wingBeat.isSmall()
                    && playerUUID != null && playerUUID.equals(wingBeat.playerUUID)
                    && sessionUUID != null && sessionUUID.equals(wingBeat.sessionUUID)) {
                wingBeat.discard();
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

        // 直接设置位置
        this.setPos(targetX, targetY, targetZ);

        // 手动计算并设置朝向，而不是用 setLookAt
        double dx = this.player.getX() - this.getX();
        double dy = this.player.getY() + this.player.getEyeHeight() - this.getEyeY();
        double dz = this.player.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));

        this.setYRot(targetYaw);
        this.yRotO = targetYaw;
        this.setXRot(targetPitch);
        this.xRotO = targetPitch;
        this.yHeadRot = targetYaw;
        this.yHeadRotO = targetYaw;

        // 传送保护，阈值放大
        if (this.distanceToSqr(this.player) > 400.0D) {  // 20格
            this.teleportTo(this.player.getX(), this.player.getY() + this.orbitHeight, this.player.getZ());
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
