package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.event.listener.SkadiBlessingManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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
 * 浊心斯卡蒂 (S-03-07) —— ALEPH 级异想体。
 * 不主动攻击;工作失误时为玩家回血;工作完成给予"同葬无光之愿"祝福;
 * 根据玩家状态发送只对该玩家可见的聊天消息(30秒冷却);
 * 被祝福玩家死亡时原地满血免死复活并使计数器 -1;
 * 计数器归零时消失,并在最近的再生反应堆处生成"伊莎玛拉"。
 */
public class EntityDarkSkadi extends AbstractAbnormality {

    // 持有"同葬无光之愿"祝福的玩家死亡时的免死复活逻辑由死亡事件读取该效果实现。

    // 聊天事件冷却(对每个玩家分别计时,这里用全局简化:600 tick=30秒)
    private int chatCooldown = 0;
    // 玩家死亡复活后需要触发的"复活台词"标记,由死亡事件设置后下一tick消费
    private int reviveLineTimer = 0;
    private boolean isharmlaTransitionPending;
    private UUID blessingEpoch = UUID.randomUUID();

    public EntityDarkSkadi(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "S-03-07";
        this.abnormalityName = "浊心斯卡蒂";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "WHITE";
        this.maxPEOutput = 37;

        // 工作偏好基础值(本能/洞察/沟通/压迫);压迫不可用设为0
        float[] basePreferences = {0.70f, 0.70f, 0.90f, 0.0f};
        initializeWorkPreferences(basePreferences);
        // 计数器极值5
        initializeQliphothCounter(5);
    }

    // ==================== 工作系统 ====================

    @Override
    public float[][] getFullWorkPreferences() {
        // 本能1-5级70%、洞察1-5级70%、沟通1-5级90%、压迫不可用(0)
        float[][] prefs = new float[4][5];
        for (int lv = 0; lv < 5; lv++) {
            prefs[WorkType.INSTINCT.ordinal()][lv] = 0.70f;
            prefs[WorkType.INSIGHT.ordinal()][lv] = 0.70f;
            prefs[WorkType.ATTACHMENT.ordinal()][lv] = 0.90f;
            prefs[WorkType.REPRESSION.ordinal()][lv] = 0.0f;
        }
        return prefs;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.04f, 0),                       // Ⅰ 成功率+4%
                new ObservationLevelBonus(0.0f, 2),                        // Ⅱ 工作速度+2
                new ObservationLevelBonus(0.04f, 0),                       // Ⅲ 成功率+4%
                new ObservationLevelBonus(0.0f, 2, true, true, true)       // Ⅳ 速度+2、解锁饰品信息/护甲/武器
        };
    }

    @Override
    public int getBasicInfoCost() { return 30; }

    @Override
    public int getWeaponDevelopmentCost() { return 222; }

    @Override
    public int getArmorDevelopmentCost() { return 120; }

    @Override
    public int getWeaponDevelopmentMaxCount() { return 1; }

    @Override
    public int getArmorDevelopmentMaxCount() { return 1; }

    /** Observation unlocks the information; only suppression awards the actual trophy. */
    @Override
    public float getGiftProbability() { return 0.0F; }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/curio/must_open_path_curio.png"),
                "必须开辟的道路", "头部", "must_open_path_curio",
                "最大生命值+10", "攻击速度+20"
        );
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/item/must_open_path_weapon.png"),
                "必须开辟的道路", getRiskLevel(), "RED", "27-32", "0.6", "不变",
                getWeaponDevelopmentMaxCount(), "must_open_path_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/armor/must_open_path_chestplate.png"),
                "必须开辟的道路", getRiskLevel(), 1.0F, 1.0F, 1.0F, 1.0F,
                getArmorDevelopmentMaxCount(), "must_open_path"
        );
    }

    @Override
    public int getSensitiveInfoCost() { return 30; }

    @Override
    public int getManualCost(int manualIndex) { return 7; }

    @Override
    public int getWorkPreferencesCost() { return 10; }

    @Override
    public boolean canEscape() {
        return false; // 该异想体不会突破收容
    }

    @Override
    public String getAbnormalityCode() { return "S-03-07"; }

    @Override
    public RiskLevel getRiskLevel() { return riskLevel; }

    @Override
    public String name() { return "skadi_corrupted"; }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // 机制1:工作失误不造成伤害,反而为玩家恢复 3-6 点生命值
        if (player instanceof ServerPlayer) {
            float heal = 3 + this.random.nextInt(4); // 3-6
            player.heal(heal);
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.abnormality.skadi.healed", (int) heal));
        }
    }

    // ==================== 工作结果 ====================

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        // 机制2:工作完成后获得"同葬无光之愿"祝福(无限时长,计数器归零时统一移除)
        grantBlessing(player);
        setAnimation("idle");
    }

    private void grantBlessing(ServerPlayer player) {
        if (qliphothCounter <= 0 || isRemoved()) return;
        player.addEffect(new MobEffectInstance(
                ModMobEffects.WISH_WITHOUT_LIGHT.get(),
                -1, 0, false, false, true
        ));
        SkadiBlessingManager.bind(player, this);
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.abnormality.skadi.blessing_obtained"));
    }

    @Override
    public void onQliphothMeltdown() {
        isharmlaTransitionPending = true;
        onCounterZero();
    }

    // ==================== tick:聊天事件 + 复活台词 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (isharmlaTransitionPending && this.tickCount % 20 == 0) {
            onCounterZero();
            if (this.isRemoved()) return;
        }

        if (chatCooldown > 0) chatCooldown--;
        if (reviveLineTimer > 0) {
            reviveLineTimer--;
        }

        // 机制3:根据附近玩家状态,发送仅该玩家可见的消息,每次发送30秒冷却
        if (this.tickCount % 20 == 0 && chatCooldown <= 0) {
            trySendChatEvent();
        }
    }

    private void trySendChatEvent() {
        // 公司维度内有斯卡蒂时,针对附近玩家根据状态发消息
        List<Player> players = this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(48), p -> p.isAlive());
        if (players.isEmpty()) return;

        for (Player p : players) {
            if (!(p instanceof ServerPlayer player)) continue;
            float maxHp = player.getMaxHealth();
            boolean lowHp = player.getHealth() < maxHp * 0.5f;
            boolean lowMental = MentalValueUtil.getMentalValue(player)
                    < MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.5f;

            String messageKey;
            if (lowHp || lowMental) {
                // 生命值/精神值低于一半:立刻随机出现担忧台词
                String[] worriedKeys = {
                        "message.lobotocraft.abnormality.skadi.worried.1",
                        "message.lobotocraft.abnormality.skadi.worried.2",
                        "message.lobotocraft.abnormality.skadi.worried.3",
                        "message.lobotocraft.abnormality.skadi.worried.4"
                };
                messageKey = worriedKeys[this.random.nextInt(worriedKeys.length)];
            } else {
                // 状态健康:每30秒随机出现陪伴台词
                String[] calmKeys = {
                        "message.lobotocraft.abnormality.skadi.calm.1",
                        "message.lobotocraft.abnormality.skadi.calm.2",
                        "message.lobotocraft.abnormality.skadi.calm.3",
                        "message.lobotocraft.abnormality.skadi.calm.4",
                        "message.lobotocraft.abnormality.skadi.calm.5",
                        "message.lobotocraft.abnormality.skadi.calm.6"
                };
                messageKey = calmKeys[this.random.nextInt(calmKeys.length)];
            }
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.abnormality.skadi.chat",
                    Component.translatable(messageKey)));
        }
        chatCooldown = 600; // 30秒冷却
    }

    /**
     * 由免死复活逻辑调用:计数器 -1,播放复活台词。
     * 当计数器归零时执行消失与生成伊莎玛拉。
     */
    public void onBlessedPlayerRevive(ServerPlayer player) {
        decreaseQliphothCounter(1);
        // 复活台词
        String[] reviveLineKeys = {
                "message.lobotocraft.abnormality.skadi.revive.1",
                "message.lobotocraft.abnormality.skadi.revive.2",
                "message.lobotocraft.abnormality.skadi.revive.3",
                "message.lobotocraft.abnormality.skadi.revive.4"
        };
        String messageKey = reviveLineKeys[this.random.nextInt(reviveLineKeys.length)];
        player.sendSystemMessage(Component.translatable(
                "message.lobotocraft.abnormality.skadi.chat",
                Component.translatable(messageKey)));

    }

    /**
     * 机制5:计数器归零,斯卡蒂消失,在最近的再生反应堆处生成"伊莎玛拉",并播放出逃音频。
     */
    private void onCounterZero() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        isharmlaTransitionPending = true;
        IsharmlaSpawnData.get(serverLevel.getServer()).enqueue(
                getUUID(), serverLevel.dimension().location(), blockPosition());
        SkadiBlessingManager.revokeSource(serverLevel.getServer(), getUUID());
        for (ServerPlayer online : serverLevel.players()) {
            serverLevel.playSound(null, online.blockPosition(), ModSounds.SKADI_ESCAPE.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        com.wzz.lobotocraft.item.SkadiBanishData.get(serverLevel).setSkadiOrigin(
                serverLevel.dimension().location(), getX(), getY(), getZ(),
                getHomeRoomDimension(), getHomeRoomMarker());
        discard();
    }

    public UUID getBlessingEpoch() {
        return blessingEpoch;
    }

    public void invalidateBlessings() {
        blessingEpoch = UUID.randomUUID();
        if (level() instanceof ServerLevel serverLevel) {
            SkadiBlessingManager.revokeSource(serverLevel.getServer(), getUUID());
        }
    }

    @Override
    public void discard() {
        if (level() instanceof ServerLevel serverLevel) {
            blessingEpoch = UUID.randomUUID();
            SkadiBlessingManager.revokeSource(serverLevel.getServer(), getUUID());
        }
        super.discard();
    }

    public boolean tryConsumeBlessingCharge(ServerPlayer player) {
        if (qliphothCounter <= 0 || isRemoved() || !(level() instanceof ServerLevel)) return false;
        onBlessedPlayerRevive(player);
        return true;
    }

    // ==================== 环境音效 ====================

    @Override
    public boolean hasAbnormalityAmbientSound() {
        return true;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getAbnormalityAmbientSound() {
        return ModSounds.SKADI_AMBIENT.get();
    }

    @Override
    public int getAbnormalityAmbientSoundInterval() {
        return 400; // 20秒
    }

    // ==================== 工作日志 ====================

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("快走吧，博士......逃走吧，从这里，从我身边......逃走吧。");
        logs.add("该异想体未表现出攻击倾向，并且在工作失误时为玩家回复了精神值。");
        logs.add("该异想体在玩家完成工作后，会给予玩家祝福。");
        logs.add("当有玩家因为各种原因血量和精神值变低时，该异想体展现出了额外的担心。当玩家状态健康时，它似乎也很开心。");
        logs.add("当被祝福的玩家死亡时，该异想体减少了计数器，但是本该死亡的玩家却无事发生一样。");
        logs.add("当计数器下降到0时....");
        return logs;
    }

    // ==================== 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 5, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityDarkSkadi> event) {
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.skadi_corrupted.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.skadi_corrupted.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 250.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ChatCooldown", chatCooldown);
        tag.putBoolean("IsharmlaTransitionPending", isharmlaTransitionPending);
        tag.putUUID("BlessingEpoch", blessingEpoch);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chatCooldown = tag.getInt("ChatCooldown");
        isharmlaTransitionPending = tag.getBoolean("IsharmlaTransitionPending") || qliphothCounter <= 0;
        blessingEpoch = tag.hasUUID("BlessingEpoch") ? tag.getUUID("BlessingEpoch") : UUID.randomUUID();
    }
}
